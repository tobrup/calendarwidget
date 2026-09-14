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
import android.net.Uri
import android.os.Build
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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager.Companion.getInstance
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.Formatter
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.ceil

class WidgetService(context: Context, params: WorkerParameters) : Worker(context, params) {
    private class Event {
        var allDay: Boolean = false
        var color: Int = 0
        var endDay: Int = 0
        var endMillis: Long = 0
        var endTime: Time? = null
        var hasAlarm: Boolean = false
        var isBirthday: Boolean = false
        var location: String? = null
        var startMillis: Long = 0
        var startTime: Time? = null
        var startDay: Int = 0
        var title: String? = null

        override fun equals(o: Any?): Boolean {
            if (o !is Event) return false

            val other = o

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

        val hasPermissions = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (!hasPermissions) {
            Log.w(TAG, "WidgetService.doWork: no permissions granted")
        }

        computeTimeRanges()
        for (widgetId in widgetIds) {
            Log.d(TAG, "WidgetService.doWork: update widgetId " + widgetId)
            if (hasPermissions) {
                updateWidget(widgetId!!)
            } else {
                updateWidgetNoPermissions(widgetId!!)
            }
        }
        return Result.success()
    }

    private fun updateWidget(widgetId: Int) {
        val manager = AppWidgetManager.getInstance(getApplicationContext())
        val widgetInfo = manager.getAppWidgetInfo(widgetId)

        val info = WidgetInfo(widgetId, getApplicationContext())
        val maxLines = info.lines.toInt()
        val birthdayEvents: MutableList<Event?> = ArrayList<Event?>(maxLines * 2)
        val agendaEvents: MutableList<Event> = ArrayList<Event>(maxLines)

        var cursor: Cursor? = null
        try {
            cursor = this.cursor

            while (true) {
                val widgetFull = ceil(birthdayEvents.size / 2.0) + agendaEvents.size >= maxLines
                val evenBirthdayCount = birthdayEvents.size % 2 == 0
                if (widgetFull && evenBirthdayCount) break // widget is full


                var event: Event? = null
                while (event == null && !cursor!!.isAfterLast()) event = readEvent(cursor, info)
                if (event == null) break // no further events


                if (event.isBirthday) {
                    if (!birthdayEvents.contains(event)) birthdayEvents.add(event)
                } else if (!widgetFull) agendaEvents.add(event)
            }
        } finally {
            if (cursor != null) cursor.close()
        }

        val packageName = getApplicationContext().getPackageName()
        val widget = RemoteViews(
            getApplicationContext().getPackageName(),
            widgetInfo.initialLayout
        )
        widget.removeAllViews(R.id.widget)
        widget.setOnClickPendingIntent(
            R.id.widget,
            getOnClickPendingIntent(widgetId)
        )

        val calendarColor = info.calendarColor

        val bdayIterator = birthdayEvents.iterator()
        while (bdayIterator.hasNext()) {
            val view = RemoteViews(
                packageName,
                R.layout.birthdays
            )
            view.setTextViewText(
                R.id.birthday1_text,
                formatEventText(bdayIterator.next(), calendarColor, info)
            )
            if (bdayIterator.hasNext()) view.setTextViewText(
                R.id.birthday2_text,
                formatEventText(bdayIterator.next(), false, info)
            )
            else view.setTextViewText(R.id.birthday2_text, "")
            widget.addView(R.id.widget, view)
        }

        for (event in agendaEvents) {
            val view = RemoteViews(
                packageName,
                R.layout.event
            )
            view.setTextViewText(
                R.id.event_text,
                formatEventText(event, calendarColor, info)
            )
            val alarmFlag = if (event.hasAlarm) View.VISIBLE else View.GONE
            view.setViewVisibility(R.id.event_alarm, alarmFlag)
            widget.addView(R.id.widget, view)
        }

        val opacityPercent = (100 * info.opacity).toInt()
        widget.setInt(R.id.background, "setImageLevel", opacityPercent)

        manager.updateAppWidget(widgetId, widget)
        scheduleNextUpdate(agendaEvents, widgetId)
    }

    private fun updateWidgetNoPermissions(widgetId: Int) {
        val manager = AppWidgetManager.getInstance(getApplicationContext())
        val widgetInfo = manager.getAppWidgetInfo(widgetId)

        val packageName = getApplicationContext().getPackageName()
        val widget = RemoteViews(
            getApplicationContext().getPackageName(),
            widgetInfo.initialLayout
        )
        widget.removeAllViews(R.id.widget)
        widget.setOnClickPendingIntent(
            R.id.widget,
            getOnClickPendingIntent(widgetId)
        )

        val view = RemoteViews(
            packageName,
            R.layout.event
        )
        view.setTextViewText(R.id.event_text, "Missing Permissions")
        widget.addView(R.id.widget, view)

        widget.setInt(R.id.background, "setImageLevel", 100)

        manager.updateAppWidget(widgetId, widget)
    }

    private fun collectWidgetIds(): MutableList<Int> {
        val allWidgetIds: MutableList<Int> = ArrayList<Int>()
        for (c in WidgetBase.Companion.WIDGET_CLASSES) {
            val name: ComponentName = ComponentName(getApplicationContext(), c)
            val m = AppWidgetManager.getInstance(getApplicationContext())
            val widgetIds = m.getAppWidgetIds(name)
            for (i in widgetIds) {
                allWidgetIds.add(i)
            }
        }
        return allWidgetIds
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
            Log.d(TAG, "WidgetService.scheduleNextUpdate: delay: " + delay + ", now: " + now)
            scheduleServiceWithDelay(getApplicationContext(), delay + 1000, widgetId)
        } else {
            Log.w(TAG, "WidgetService.scheduleNextUpdate: delay would be " + delay + ", now: " + now)
        }
    }

    private fun readEvent(cursor: Cursor, info: WidgetInfo): Event? {
        if (!cursor.moveToNext()) return null // no next item

        if (!info.calendars.get(cursor.getInt(COL_CALENDAR))!!.enabled) return null // Calendar is disabled


        val event = Event()

        if (1 == cursor.getInt(COL_ALL_DAY)) event.allDay = true

        event.endDay = cursor.getInt(COL_END_DAY)
        event.endTime = Time()

        if (event.allDay) {
            event.endTime!!.timezone = Time.getCurrentTimezone()
            event.endMillis = event.endTime!!.setJulianDay(event.endDay)
        } else {
            event.endMillis = cursor.getLong(COL_END_MILLIS)
            event.endTime!!.set(event.endMillis)
        }
        if ((event.allDay && event.endMillis < todayStart)
            || (!event.allDay && event.endMillis <= System
                .currentTimeMillis())
        ) return null // Skip events in the past


        event.title = cursor.getString(COL_TITLE)
        if (event.title == null) event.title = ""

        if (event.allDay && info.birthdays != WidgetInfo.Companion.BIRTHDAY_NORMAL) for (pattern in this.birthdayPatterns!!) {
            val matcher: Matcher = pattern.matcher(event.title)
            if (!matcher.find()) continue
            event.title = matcher.group(1)
            event.isBirthday = true
            break
        }

        // Skip birthday events if necessary
        if (event.isBirthday && info.birthdays == WidgetInfo.Companion.BIRTHDAY_HIDE) return null

        event.startDay = cursor.getInt(COL_START_DAY)
        event.startTime = Time()
        if (event.allDay) {
            event.startTime!!.timezone = event.endTime!!.timezone
            event.startMillis = event.startTime!!.setJulianDay(event.startDay)
        } else {
            event.startMillis = cursor.getLong(COL_START_MILLIS)
            event.startTime!!.set(event.startMillis)
        }

        event.location = cursor.getString(COL_LOCATION)
        if (event.location != null
            && IS_EMPTY_PATTERN.matcher(event.location).find()
        ) event.location = null

        event.color = cursor.getInt(COL_COLOR)
        event.hasAlarm = cursor.getInt(COL_HAS_ALARM) == 1
        return event
    }

    private fun getOnClickPendingIntent(widgetId: Int): PendingIntent? {
        val pickAction = Intent(
            "pick", Uri.parse(
                "widget://"
                        + widgetId
            ), getApplicationContext(), PickActionActivity::class.java
        )
        pickAction.putExtra(PickActionActivity.Companion.EXTRA_WIDGET_ID, widgetId)
        return PendingIntent.getActivity(getApplicationContext(), 0, pickAction, PendingIntent.FLAG_IMMUTABLE)
    }

    private val cursor: Cursor?
        get() {
            val start: Long = todayStart - 1000 * 60 * 60 * 24
            val end: Long = start + SEARCH_DURATION

            val projection: Array<String>

            if (Build.VERSION.SDK_INT < 14) projection = arrayOf<String>(
                "title",
                "color", "eventLocation", "allDay", "startDay", "endDay", "end",
                "hasAlarm", "calendar_id", "begin"
            )
            else projection = arrayOf<String>(
                "title",
                "calendar_color", "eventLocation", "allDay", "startDay", "endDay", "end",
                "hasAlarm", "calendar_id", "begin"
            )

            val uriString = String.format(CURSOR_FORMAT, start, end)
            return getApplicationContext().getContentResolver().query(
                Uri.parse(uriString),
                projection, null, null, CURSOR_SORT
            )
        }

    private fun computeTimeRanges() {
        val now = Time()
        now.setToNow()
        val julianDay = Time.getJulianDay(
            System.currentTimeMillis(),
            now.gmtoff
        )

        yearStart = now.setJulianDay(julianDay - now.yearDay)
        now.year++
        yearEnd = now.toMillis(false)
        yesterdayStart = now.setJulianDay(julianDay - 1)
        todayStart = now.setJulianDay(julianDay)
        tomorrowStart = now.setJulianDay(julianDay + 1)
        dayAfterTomorrowStart = now.setJulianDay(julianDay + 2)
        oneWeekFromNow = now.setJulianDay(julianDay + 8)
    }

    @get:Synchronized
    private val birthdayPatterns: Array<Pattern>?
        get() {
            if (Companion.birthdayPatterns == null) {
                val strings = getApplicationContext().getResources().getStringArray(
                    R.array.birthday_patterns
                )
                Companion.birthdayPatterns = Array<Pattern>(strings.size){
                    Pattern.compile(strings[it])
                }
            }
            return Companion.birthdayPatterns
        }

    private fun formatEventText(
        event: Event?,
        showColor: Boolean, info: WidgetInfo
    ): CharSequence {
        if (event == null) return ""

        val builder = SpannableStringBuilder()

        if (showColor) {
            if (event.isBirthday) builder.append(COLOR_HIDDEN)
            else {
                builder.append(COLOR_DOT)
                builder.setSpan(
                    ForegroundColorSpan(event.color), 0, 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        val timeStartPos = builder.length
        formatTime(builder, event, info)
        builder.append(' ')
        val timeEndPos = builder.length
        builder.setSpan(
            ForegroundColorSpan(DATETIME_COLOR), timeStartPos,
            timeEndPos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        builder.append(event.title)
        val titleEndPos = builder.length
        builder.setSpan(
            ForegroundColorSpan(-0x1), timeEndPos,
            titleEndPos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        if (event.location != null) {
            builder.append(SEPARATOR_COMMA)
            builder.append(event.location)
            builder.setSpan(
                ForegroundColorSpan(DATETIME_COLOR),
                titleEndPos, builder.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val size = info.size.toInt() / 100f
        builder.setSpan(RelativeSizeSpan(size), 0, builder.length, 0)

        return builder
    }

    private fun formatTime(
        builder: SpannableStringBuilder,
        event: Event, info: WidgetInfo
    ) {
        val formatter = Formatter(builder)

        val isStartToday = (todayStart <= event.startMillis && event.startMillis <= tomorrowStart)
        val isEndToday = (todayStart <= event.endMillis && event.endMillis <= tomorrowStart)
        val showStartDay = !isStartToday || !isEndToday || event.allDay

        // all-Day events
        if (event.allDay) {
            if (showStartDay) appendDay(
                formatter, builder, event.startMillis,
                event.startTime!!, info
            )

            if (event.startDay != event.endDay) {
                builder.append('-')
                appendDay(
                    formatter, builder, event.endMillis, event.endTime!!,
                    info
                )
            }
            return
        }

        // events with no duration
        if (!info.endTime || event.startMillis == event.endMillis) {
            if (showStartDay) {
                appendDay(
                    formatter, builder, event.startMillis,
                    event.startTime!!, info
                )
                builder.append(' ')
            }
            appendHour(formatter, builder, event.startMillis, info)
            return
        }

        // events with duration
        if (showStartDay) {
            appendDay(
                formatter, builder, event.startMillis, event.startTime!!,
                info
            )
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

    private fun appendHour(
        formatter: Formatter,
        builder: SpannableStringBuilder, time: Long,
        info: WidgetInfo
    ) {
        if (info.twentyfourHours) formatter.format("%1\$tk:%1\$tM", time)
        else {
            formatter.format("%1\$tl:%1\$tM", time)
            val start = builder.length
            formatter.format("%1\$tp", time)
            val end = builder.length
            builder.setSpan(RelativeSizeSpan(0.7f), start, end, 0)
        }
    }

    private fun appendDay(
        formatter: Formatter,
        builder: SpannableStringBuilder, time: Long,
        day: Time, info: WidgetInfo
    ) {
        val tomorrowYesterday = info.tomorrowYesterday
        val specialStart: Long = if (tomorrowYesterday)
            yesterdayStart
        else
            todayStart
        val specialEnd: Long = if (tomorrowYesterday)
            dayAfterTomorrowStart
        else
            tomorrowStart
        val weekday = info.weekday
        val weekEnd: Long = if (weekday) oneWeekFromNow else tomorrowStart

        if (specialStart <= time && time < specialEnd) {
            val from = builder.length
            if (time < todayStart) builder.append(
                getApplicationContext().getResources().getString(
                    R.string.format_yesterday
                )
            )
            else if (time < tomorrowStart) builder.append(getApplicationContext().getResources().getString(R.string.format_today))
            else builder.append(
                getApplicationContext().getResources().getString(
                    R.string.format_tomorrow
                )
            )

            val smaller = RelativeSizeSpan(0.7f)
            builder.setSpan(smaller, from, builder.length, 0)
        } else if (todayStart <= time && time < weekEnd)  // this week?
            builder.append(
                getApplicationContext().getResources().getStringArray(
                    R.array.format_day_of_week
                )[day.weekDay]
            )
        else if (yearStart <= time && time < yearEnd)  // this year?
            formatter.format(info.dateFormat.shortFormat, time)
        else  // not this year
            formatter.format(info.dateFormat.longFormat, time)
    }

    companion object {
        fun scheduleServiceOnce(context: Context) {
            Log.i(TAG, "WidgetService.scheduleServiceOnce")
            val request = OneTimeWorkRequest.Builder(WidgetService::class.java).build()
            getInstance(context).enqueueUniqueWork(
                "calendar-widget-refresh",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun scheduleServiceWithDelay(context: Context, delay: Long, widgetId: Int) {
            Log.i(TAG, "WidgetService.scheduleServiceWithDelay" + delay + " ms")
            val request = OneTimeWorkRequest.Builder(WidgetService::class.java)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            getInstance(context).enqueueUniqueWork(
                "calendar-widget-refresh-delayed-" + widgetId,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        private const val TAG = "AgendaWidget"

        private var yesterdayStart: Long = 0
        private var todayStart: Long = 0
        private var tomorrowStart: Long = 0
        private var dayAfterTomorrowStart: Long = 0
        private var oneWeekFromNow: Long = 0
        private var yearStart: Long = 0
        private var yearEnd: Long = 0

        private var birthdayPatterns: Array<Pattern>? = null

        private const val CURSOR_FORMAT = "content://com.android.calendar/instances/when/%1\$s/%2\$s"
        private val SEARCH_DURATION = 2 * DateUtils.YEAR_IN_MILLIS
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

        private val DAY_IN_MILLIS = (24 * 60 * 60 * 1000).toLong()

        private val IS_EMPTY_PATTERN: Pattern = Pattern.compile("^\\s*$")
        private const val DATETIME_COLOR = -0x47000001
    }
}
