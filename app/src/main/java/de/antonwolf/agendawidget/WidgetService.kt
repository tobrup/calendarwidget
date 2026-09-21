/*
 * Copyright (C) 2011 by Anton Wolf
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.antonwolf.agendawidget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.format.DateUtils
import android.text.format.Time
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager.Companion.getInstance
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.Formatter
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.ceil

class WidgetService(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {

        fun scheduleServiceOnce(context: Context) {
            Log.i(TAG, "WidgetService.scheduleServiceOnce")
            val request = OneTimeWorkRequest.Builder(WidgetService::class.java).build()
            getInstance(context).enqueueUniqueWork("calendar-widget-refresh", ExistingWorkPolicy.REPLACE, request)
        }

        fun scheduleServiceWithDelay(context: Context, delay: Long, widgetId: Int) {
            Log.i(TAG, "WidgetService.scheduleServiceWithDelay$delay ms")
            val request = OneTimeWorkRequest.Builder(WidgetService::class.java)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            getInstance(context).enqueueUniqueWork("calendar-widget-refresh-delayed-$widgetId", ExistingWorkPolicy.REPLACE, request)
        }

        private const val TAG = "AgendaWidget"

        private var yesterdayStart: Long = 0
        private var todayStart: Long = 0
        private var tomorrowStart: Long = 0
        private var dayAfterTomorrowStart: Long = 0
        private var oneWeekFromNow: Long = 0
        private var yearStart: Long = 0
        private var yearEnd: Long = 0
        private var cachedBirthdayPatterns: List<Pattern>? = null
        private const val CURSOR_FORMAT = $$"content://com.android.calendar/instances/when/%1$s/%2$s"
        private const val SEARCH_DURATION = 2 * DateUtils.YEAR_IN_MILLIS
        private const val CURSOR_SORT = "begin ASC, end DESC, title ASC"
        private const val COL_TITLE = 0
        private const val COL_COLOR = 1
        private const val COL_LOCATION = 2
        private const val COL_ALL_DAY = 3
        private const val COL_START_DAY = 4
        private const val COL_END_DAY = 5
        private const val COL_END_MILLIS = 6
        private const val COL_HAS_ALARM = 7
        private const val COL_CALENDAR = 8
        private const val COL_START_MILLIS = 9

        private const val COLOR_DOT = "■\t"
        private const val COLOR_HIDDEN = "\t"
        private const val SEPARATOR_COMMA = ", "

        private const val DAY_IN_MILLIS = (24 * 60 * 60 * 1000).toLong()

        private val IS_EMPTY_PATTERN: Pattern = Pattern.compile("^\\s*$")
        private const val DATETIME_COLOR = -0x47000001
    }
    
    private data class Event(
        val allDay: Boolean = false,
        val color: Int = 0,
        val endDay: Int = 0,
        val endMillis: Long = 0,
        val endTime: Time? = null,
        val hasAlarm: Boolean = false,
        val isBirthday: Boolean = false,
        val location: String? = null,
        val startMillis: Long = 0,
        val startTime: Time? = null,
        val startDay: Int = 0,
        val title: String? = null,
    ) {

        fun isSameBirthday(other: Event): Boolean {
            return isBirthday && other.isBirthday
                    && other.startDay == this.startDay && other.title == this.title
        }
    }

    override fun doWork(): Result {
        Log.d(TAG, "WidgetService.doWork")

        val widgetIds = collectWidgetIds()
        if (widgetIds.isEmpty()) {
            Log.d(TAG, "WidgetService.doWork: no widgets active")
            return Result.success()
        }

        val hasPermissions = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (!hasPermissions) {
            Log.w(TAG, "WidgetService.doWork: no permissions granted")
        }

        computeTimeRanges()
        for (widgetId in widgetIds) {
            Log.d(TAG, "WidgetService.doWork: update widgetId $widgetId")
            if (hasPermissions) {
                updateWidget(widgetId)
            } else {
                updateWidgetNoPermissions(widgetId)
            }
        }
        return Result.success()
    }

    private fun updateWidget(widgetId: Int) {
        val manager = AppWidgetManager.getInstance(applicationContext)
        val widgetInfo = manager.getAppWidgetInfo(widgetId)

        val info = WidgetInfo(widgetId, applicationContext)
        val maxLines = info.lines.toInt()
        val birthdayEvents: MutableList<Event> = ArrayList(maxLines * 2)
        val agendaEvents: MutableList<Event> = ArrayList(maxLines)

        createCursor()?.use { cursor ->
            while (true) {
                val widgetFull = ceil(birthdayEvents.size / 2.0) + agendaEvents.size >= maxLines
                val evenBirthdayCount = birthdayEvents.size % 2 == 0
                if (widgetFull && evenBirthdayCount) {
                    break // widget is full
                }

                var event: Event? = null
                while (event == null && !cursor.isAfterLast) {
                    event = readEvent(cursor, info)
                }
                if (event == null) {
                    break // no further events
                }

                if (event.isBirthday) {
                    if (birthdayEvents.none { it.isSameBirthday(event) }) {
                        birthdayEvents.add(event)
                    }
                } else if (!widgetFull) {
                    agendaEvents.add(event)
                }
            }
        }

        val packageName = applicationContext.packageName
        val widget = RemoteViews(packageName, widgetInfo.initialLayout)
        widget.removeAllViews(R.id.widget)
        widget.setViewVisibility(R.id.loadingText, View.GONE)
        widget.setOnClickPendingIntent(R.id.widget, getOnClickPendingIntent(widgetId))

        val calendarColor = info.calendarColor

        val bdayIterator = birthdayEvents.iterator()
        while (bdayIterator.hasNext()) {
            val view = RemoteViews(packageName, R.layout.birthdays)
            view.setTextViewText(R.id.birthday1_text, formatEventText(bdayIterator.next(), calendarColor, info))
            if (bdayIterator.hasNext()) {
                view.setTextViewText(R.id.birthday2_text, formatEventText(bdayIterator.next(), false, info))
            } else {
                view.setTextViewText(R.id.birthday2_text, "")
            }
            widget.addView(R.id.widget, view)
        }

        for (event in agendaEvents) {
            val view = RemoteViews(packageName, R.layout.event)
            view.setTextViewText(R.id.event_text, formatEventText(event, calendarColor, info))
            view.setViewVisibility(R.id.event_alarm, if (event.hasAlarm && info.showReminder) View.VISIBLE else View.GONE)
            widget.addView(R.id.widget, view)
        }

        val opacityPercent = (100 * info.opacity).toInt()
        widget.setInt(R.id.background, "setImageLevel", opacityPercent)

        manager.updateAppWidget(widgetId, widget)
        scheduleNextUpdate(agendaEvents, widgetId)
    }

    private fun updateWidgetNoPermissions(widgetId: Int) {
        val manager = AppWidgetManager.getInstance(applicationContext)
        val widgetInfo = manager.getAppWidgetInfo(widgetId)

        val packageName = applicationContext.packageName
        val widget = RemoteViews(packageName, widgetInfo.initialLayout)
        widget.removeAllViews(R.id.widget)
        widget.setOnClickPendingIntent(R.id.widget, getOnClickPendingIntent(widgetId))

        val view = RemoteViews(packageName, R.layout.event)
        view.setTextViewText(R.id.event_text, "Missing Permissions")
        widget.addView(R.id.widget, view)

        widget.setInt(R.id.background, "setImageLevel", 100)

        manager.updateAppWidget(widgetId, widget)
    }

    private fun collectWidgetIds(): List<Int> {
        val name = ComponentName(applicationContext, WidgetResizable::class.java)
        val manager = AppWidgetManager.getInstance(applicationContext)
        val widgetIds = manager.getAppWidgetIds(name)
        return widgetIds.toList()
    }

    private fun scheduleNextUpdate(search: MutableList<Event>, widgetId: Int) {
        var nextUpdate: Long = tomorrowStart
        for (event in search) {
            if (!event.allDay && event.endMillis < nextUpdate) {
                nextUpdate = event.endMillis
            }
        }

        val now = Time()
        now.setToNow()
        val nowMillis = now.toMillis(false)
        val delay = nextUpdate - nowMillis
        if (delay > 0) {
            Log.d(TAG, "WidgetService.scheduleNextUpdate: delay: $delay, now: $now")
            scheduleServiceWithDelay(applicationContext, delay + 1000, widgetId)
        } else {
            Log.w(TAG, "WidgetService.scheduleNextUpdate: delay would be $delay, now: $now")
        }
    }

    private fun readEvent(cursor: Cursor, info: WidgetInfo): Event? {
        if (!cursor.moveToNext()) {
            return null // no next item
        }

        if (!info.calendars[cursor.getInt(COL_CALENDAR)]!!.enabled) {
            return null // Calendar is disabled
        }

        val allDay = (1 == cursor.getInt(COL_ALL_DAY))

        val endDay = cursor.getInt(COL_END_DAY)
        val endTime = Time()
        val endMillis: Long

        if (allDay) {
            endTime.timezone = Time.getCurrentTimezone()
            endMillis = endTime.setJulianDay(endDay)
        } else {
            endMillis = cursor.getLong(COL_END_MILLIS)
            endTime.set(endMillis)
        }

        if ((allDay && endMillis < todayStart)
            || (!allDay && endMillis <= System.currentTimeMillis())
        ) {
            return null // Skip events in the past
        }

        var title = cursor.getString(COL_TITLE) ?: ""
        val isBirthday: Boolean
        val matcher = getBirthdayPatters().asSequence().map {
            it.matcher(title)
        }.find { it.find() }
        if (matcher != null) {
            isBirthday = true
            title = matcher.group(1) ?: title
        } else {
            isBirthday = false
        }

        // Skip birthday events if necessary
        if (isBirthday && info.birthdays == WidgetInfo.BIRTHDAY_HIDE) {
            return null
        }

        val startDay = cursor.getInt(COL_START_DAY)
        val startTime = Time()
        val startMillis: Long
        if (allDay) {
            startTime.timezone = endTime.timezone
            startMillis = startTime.setJulianDay(startDay)
        } else {
            startMillis = cursor.getLong(COL_START_MILLIS)
            startTime.set(startMillis)
        }

        val location = cursor.getString(COL_LOCATION).takeUnless {
            IS_EMPTY_PATTERN.matcher(it).find()
        }

        val color = cursor.getInt(COL_COLOR)
        val hasAlarm = cursor.getInt(COL_HAS_ALARM) == 1
        return Event(
            allDay = allDay,
            color = color,
            endDay = endDay,
            endMillis = endMillis,
            endTime = endTime,
            hasAlarm = hasAlarm,
            isBirthday = isBirthday,
            location = location,
            startMillis = startMillis,
            startTime = startTime,
            startDay = startDay,
            title = title
        )
    }

    private fun getOnClickPendingIntent(widgetId: Int): PendingIntent? {
        val pickAction = Intent("pick", "widget://$widgetId".toUri(), applicationContext, PickActionActivity::class.java)
        pickAction.putExtra(PickActionActivity.EXTRA_WIDGET_ID, widgetId)
        return PendingIntent.getActivity(applicationContext, 0, pickAction, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createCursor(): Cursor? {
        val start: Long = todayStart - 1000 * 60 * 60 * 24
        val end: Long = start + SEARCH_DURATION
        val projection = arrayOf("title", "calendar_color", "eventLocation", "allDay", "startDay", "endDay", "end", "hasAlarm", "calendar_id", "begin")

        val uriString = String.format(CURSOR_FORMAT, start, end)
        return applicationContext.contentResolver.query(uriString.toUri(), projection, null, null, CURSOR_SORT)
    }

    private fun computeTimeRanges() {
        val now = Time()
        now.setToNow()
        val julianDay = Time.getJulianDay(System.currentTimeMillis(), now.gmtoff)

        yearStart = now.setJulianDay(julianDay - now.yearDay)
        now.year++
        yearEnd = now.toMillis(false)
        yesterdayStart = now.setJulianDay(julianDay - 1)
        todayStart = now.setJulianDay(julianDay)
        tomorrowStart = now.setJulianDay(julianDay + 1)
        dayAfterTomorrowStart = now.setJulianDay(julianDay + 2)
        oneWeekFromNow = now.setJulianDay(julianDay + 8)
    }

    private fun getBirthdayPatters(): List<Pattern> {
        return cachedBirthdayPatterns ?: run {
            applicationContext.resources.getStringArray(R.array.birthday_patterns).toList().map {
                Pattern.compile(it)
            }
        }.also {
            cachedBirthdayPatterns = it
        }
    }

    private fun formatEventText(event: Event, showColor: Boolean, info: WidgetInfo): CharSequence {
        val builder = SpannableStringBuilder()

        if (showColor) {
            if (event.isBirthday) {
                builder.append(COLOR_HIDDEN)
            } else {
                builder.append(COLOR_DOT)
                builder.setSpan(ForegroundColorSpan(event.color), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        val timeStartPos = builder.length
        formatTime(builder, event, info)
        builder.append(' ')
        val timeEndPos = builder.length
        builder.setSpan(ForegroundColorSpan(DATETIME_COLOR), timeStartPos, timeEndPos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        builder.append(event.title)
        val titleEndPos = builder.length
        builder.setSpan(ForegroundColorSpan(-0x1), timeEndPos, titleEndPos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        if (event.location != null) {
            builder.append(SEPARATOR_COMMA)
            builder.append(event.location)
            builder.setSpan(ForegroundColorSpan(DATETIME_COLOR), titleEndPos, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val size = info.size.toInt() / 100f
        builder.setSpan(RelativeSizeSpan(size), 0, builder.length, 0)

        return builder
    }

    private fun formatTime(builder: SpannableStringBuilder, event: Event, info: WidgetInfo) {
        val formatter = Formatter(builder)

        val isStartToday = (event.startMillis in todayStart..tomorrowStart)
        val isEndToday = (event.endMillis in todayStart..tomorrowStart)
        val showStartDay = !isStartToday || !isEndToday || event.allDay

        // all-Day events
        if (event.allDay) {
            appendDay(formatter, builder, event.startMillis, event.startTime!!, info)

            if (event.startDay != event.endDay) {
                builder.append('-')
                appendDay(formatter, builder, event.endMillis, event.endTime!!, info)
            }
            return
        }

        // events with no duration
        if (!info.endTime || event.startMillis == event.endMillis) {
            if (showStartDay) {
                appendDay(formatter, builder, event.startMillis, event.startTime!!, info)
                builder.append(' ')
            }
            appendHour(formatter, builder, event.startMillis, info)
            return
        }

        // events with duration
        if (showStartDay) {
            appendDay(formatter, builder, event.startMillis, event.startTime!!, info)
            builder.append(' ')
        }
        appendHour(formatter, builder, event.startMillis, info)
        builder.append('-')

        if (abs(event.endMillis - event.startMillis) > DAY_IN_MILLIS) {
            appendDay(formatter, builder, event.endMillis, event.endTime!!, info)
            builder.append(' ')
        }
        appendHour(formatter, builder, event.endMillis, info)
    }

    private fun appendHour(formatter: Formatter, builder: SpannableStringBuilder, time: Long, info: WidgetInfo) {
        if (info.twentyfourHours) {
            formatter.format($$"%1$tk:%1$tM", time)
        } else {
            formatter.format($$"%1$tl:%1$tM", time)
            val start = builder.length
            formatter.format($$"%1$tp", time)
            val end = builder.length
            builder.setSpan(RelativeSizeSpan(0.7f), start, end, 0)
        }
    }

    private fun appendDay(formatter: Formatter, builder: SpannableStringBuilder, time: Long, day: Time, info: WidgetInfo) {
        val tomorrowYesterday = info.tomorrowYesterday
        val specialStart: Long = if (tomorrowYesterday) yesterdayStart else todayStart
        val specialEnd: Long = if (tomorrowYesterday) dayAfterTomorrowStart else tomorrowStart
        val weekday = info.weekday
        val weekEnd: Long = if (weekday) oneWeekFromNow else tomorrowStart

        val resources = applicationContext.resources
        when (time) {
            in specialStart..<specialEnd -> {
                val from = builder.length
                val dayRes = when {
                    time < todayStart -> R.string.format_yesterday
                    time < tomorrowStart -> R.string.format_today
                    else -> R.string.format_tomorrow
                }
                builder.append(resources.getString(dayRes))

                val smaller = RelativeSizeSpan(0.7f)
                builder.setSpan(smaller, from, builder.length, 0)
            }

            // this week?
            in todayStart..<weekEnd -> builder.append(resources.getStringArray(R.array.format_day_of_week)[day.weekDay])

            // this year?
            in yearStart..<yearEnd -> formatter.format(info.dateFormat.shortFormat, time)

            else -> formatter.format(info.dateFormat.longFormat, time)
        }
    }
}
