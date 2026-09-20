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

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.content.edit
import androidx.core.net.toUri

class WidgetInfo(widgetId: Int, context: Context) {

    companion object {
        const val BIRTHDAY_SPECIAL: String = "special"
        const val BIRTHDAY_NORMAL: String = "normal"
        const val BIRTHDAY_HIDE: String = "hidden"

        private const val BIRTHDAYS_KEY = "%dbirthdays"

        private const val LINES_KEY = "%dlines"

        private const val SIZE_KEY = "%dsize"

        private const val OPACITY_KEY = "%dopacityFloat"

        private const val OLD_OPACITY_KEY = "%dopacity"

        private const val CALENDAR_COLOR_KEY = "%dcalendarColor"

        private const val TOMORROW_YESTERDAY_KEY = "%dtommorowYesterday"

        private const val WEEKDAY_KEY = "%dweekday"

        private const val END_TIME_KEY = "%dendTime"

        private const val TWENTYFOUR_HOURS_KEY = "%dtwentyfourHours"

        private const val DATE_FORMAT_KEY = "%ddateFormat"

        private const val CALENDARS_KEY_PREFIX = "%dcalendarActive"
        private const val CALENDARS_KEY_SUFFIX = "%d"

        private fun getCalendars(context: Context, widgetId: Int): Map<Int?, CalendarPreferences?> {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return context.contentResolver.query(
                "content://com.android.calendar/calendars".toUri(),
                arrayOf("_id", "calendar_displayName", "calendar_color"),
                null,
                null,
                "calendar_displayName ASC"
            )?.use { cursor ->
                val calendars: MutableMap<Int?, CalendarPreferences?> = LinkedHashMap(cursor.count)
                while (cursor.moveToNext()) {
                    val calenderId = cursor.getInt(0)
                    calendars[calenderId] = CalendarPreferences(
                        prefs,
                        widgetId,
                        calenderId,
                        cursor.getString(1),
                        cursor.getInt(2)
                    )
                }
                calendars
            } ?: emptyMap()
        }

        fun delete(context: Context, widgetId: Int) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val calenderPrefKeyPrefix = String.format(CALENDARS_KEY_PREFIX, widgetId)
            // make a new list instance, because the key list may be mutated during edit
            val calPrefs = ArrayList(preferences.all.keys.filter { it.startsWith(calenderPrefKeyPrefix) })
            preferences.edit(commit = true) {
                remove(String.format(BIRTHDAYS_KEY, widgetId))
                remove(String.format(LINES_KEY, widgetId))
                remove(String.format(SIZE_KEY, widgetId))
                remove(String.format(OPACITY_KEY, widgetId))
                remove(String.format(CALENDAR_COLOR_KEY, widgetId))
                remove(String.format(TOMORROW_YESTERDAY_KEY, widgetId))
                remove(String.format(WEEKDAY_KEY, widgetId))
                remove(String.format(END_TIME_KEY, widgetId))
                remove(String.format(TWENTYFOUR_HOURS_KEY, widgetId))
                remove(String.format(DATE_FORMAT_KEY, widgetId))
                calPrefs.forEach {
                    remove(it)
                }
            }
        }

    }

    class CalendarPreferences(
        prefs: SharedPreferences,
        widgetId: Int,
        calendarId: Int,
        val displayName: String?,
        val color: Int
    ) {
        val key: String = String.format(CALENDARS_KEY_PREFIX, widgetId) + String.format(CALENDARS_KEY_SUFFIX, calendarId)
        val enabledDefault: Boolean = true
        val enabled: Boolean = prefs.getBoolean(key, enabledDefault)
    }

    enum class DateFormat(val shortFormat: String, val longFormat: String) {
        DOT_DAY_MONTH($$"%1$te.%1$tm", $$"%1$te.%1$tm.%1$ty"),
        SLASH_DAY_MONTH($$"%1$te/%1$tm", $$"%1$te/%1$tm/%1$ty"),
        SLASH_MONTH_DAY($$"%1$tm/%1$td", $$"%1$tm/%1$td/%1$ty"),
        SLASH_YEAR_MONTH_DAY($$"%1$tm/%1$td", $$"%1$ty/%1$tm/%1$td");
    }

    val birthdays: String
    val birthdaysDefault: String
    val birthdaysKey: String
    val lines: String
    val linesDefault: String
    val linesKey: String
    val size: String
    val sizeDefault: String = "100"
    val sizeKey: String
    val opacity: Float
    val opacityDefault: Float = 0.6f
    val opacityKey: String

    // DELETE SOMETIME
    val oldOpacityDefault: String = "60"
    val calendarColor: Boolean
    val calendarColorDefault: Boolean = true
    val calendarColorKey: String
    val tomorrowYesterday: Boolean
    val tomorrowYesterdayDefault: Boolean = true
    val tomorrowYesterdayKey: String
    val weekday: Boolean
    val weekdayDefault: Boolean = true
    val weekdayKey: String
    val endTime: Boolean
    val endTimeDefault: Boolean
    val endTimeKey: String
    val twentyfourHours: Boolean
    val twentyfourHoursDefault: Boolean
    val twentyfourHoursKey: String
    val dateFormat: DateFormat
    val dateFormatDefault: DateFormat
    val dateFormatKey: String
    val calendars: Map<Int?, CalendarPreferences?>

    init {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val manager = AppWidgetManager.getInstance(context)
        val widgetInfo = manager.getAppWidgetInfo(widgetId)

        val winManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        winManager.defaultDisplay.getMetrics(metrics)

        val heightInCells = (widgetInfo.minHeight / metrics.density + 2).toInt() / 74
        val widthInCells = (widgetInfo.minWidth / metrics.density + 2).toInt() / 74

        birthdaysKey = String.format(BIRTHDAYS_KEY, widgetId)
        birthdaysDefault = if (widthInCells > 2) BIRTHDAY_SPECIAL else BIRTHDAY_NORMAL
        birthdays = prefs.getString(birthdaysKey, birthdaysDefault)!!

        val linesInt = 5 + ((heightInCells - 1) * 5.9).toInt()
        linesDefault = linesInt.toString()
        linesKey = String.format(LINES_KEY, widgetId)
        lines = prefs.getString(linesKey, linesDefault)!!

        sizeKey = String.format(SIZE_KEY, widgetId)
        size = prefs.getString(sizeKey, sizeDefault)!!

        opacityKey = String.format(OPACITY_KEY, widgetId)
        val oldOpacityKey = String.format(OLD_OPACITY_KEY, widgetId)
        val oldOpacityValue = prefs.getString(oldOpacityKey, oldOpacityDefault)!!
        opacity = prefs.getFloat(opacityKey, oldOpacityValue.toFloat() / 100f)

        calendarColorKey = String.format(CALENDAR_COLOR_KEY, widgetId)
        calendarColor = prefs.getBoolean(calendarColorKey, calendarColorDefault)

        tomorrowYesterdayKey = String.format(TOMORROW_YESTERDAY_KEY, widgetId)
        tomorrowYesterday = prefs.getBoolean(tomorrowYesterdayKey, tomorrowYesterdayDefault)

        weekdayKey = String.format(WEEKDAY_KEY, widgetId)
        weekday = prefs.getBoolean(weekdayKey, weekdayDefault)

        endTimeKey = String.format(END_TIME_KEY, widgetId)
        endTimeDefault = widthInCells > 2
        endTime = prefs.getBoolean(endTimeKey, endTimeDefault)

        twentyfourHoursKey = String.format(TWENTYFOUR_HOURS_KEY, widgetId)
        twentyfourHoursDefault = context.resources.getBoolean(R.bool.format_24hours)
        twentyfourHours = prefs.getBoolean(twentyfourHoursKey, twentyfourHoursDefault)

        dateFormatKey = String.format(DATE_FORMAT_KEY, widgetId)
        dateFormatDefault = DateFormat.valueOf(context.resources.getString(R.string.format_date))
        dateFormat = DateFormat.valueOf(prefs.getString(dateFormatKey, dateFormatDefault.toString())!!)

        calendars = getCalendars(context, widgetId)
    }
}
