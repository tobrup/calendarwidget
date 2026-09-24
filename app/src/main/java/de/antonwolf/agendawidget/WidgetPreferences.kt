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

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.core.content.edit
import androidx.core.net.toUri
import de.antonwolf.agendawidget.WidgetPreferencesPersistence.CalendarPreferences
import java.util.Locale

data class WidgetPreferences(
    val birthdays: BirthdaySetting,
    val scrollable: Boolean,
    val lines: String,
    val size: String,
    val opacity: Float,

    val calendarColor: Boolean,
    val tomorrowYesterday: Boolean,
    val weekday: Boolean,

    val showReminder: Boolean,
    val endTime: Boolean,
    val twentyfourHours: Boolean,
    val dateFormat: DateFormat,
    val calendars: Map<Int?, CalendarPreferences?>
) {
    companion object {
        val Default = WidgetPreferences(
            birthdays = BirthdaySetting.BIRTHDAY_SPECIAL,
            scrollable = false,
            lines = "5",
            size = "100",
            opacity = 0.6f,
            calendarColor = true,
            tomorrowYesterday = true,
            weekday = true,
            showReminder = true,
            endTime = true,
            twentyfourHours = true,
            dateFormat = DateFormat.DOT_DAY_MONTH,
            calendars = emptyMap()
        )

        val CalenderEnabledDefault = true
    }
}

enum class DateFormat(val shortFormat: String, val longFormat: String) {
    DOT_DAY_MONTH($$"%1$te.%1$tm", $$"%1$te.%1$tm.%1$ty"),
    SLASH_DAY_MONTH($$"%1$te/%1$tm", $$"%1$te/%1$tm/%1$ty"),
    SLASH_MONTH_DAY($$"%1$tm/%1$td", $$"%1$tm/%1$td/%1$ty"),
    SLASH_YEAR_MONTH_DAY($$"%1$tm/%1$td", $$"%1$ty/%1$tm/%1$td");

    val persistenceValue = name

    companion object {
        fun fromPersistence(value: String): DateFormat = DateFormat.entries.find { it.persistenceValue == value }!!
    }
}

enum class BirthdaySetting(val persistenceValue: String) {
    BIRTHDAY_SPECIAL("special"),
    BIRTHDAY_NORMAL("normal"),
    BIRTHDAY_HIDE("hidden");

    companion object {
        fun fromPersistence(value: String): BirthdaySetting = BirthdaySetting.entries.find { it.persistenceValue == value }!!
    }
}

class WidgetPreferencesPersistence(val widgetId: Int, val context: Context) {

    enum class Keys(private val format: String) {
        BIRTHDAYS("%d_birthdays"),
        LINES("%d_lines"),
        SCROLLABLE("%d_scrollable"),
        SIZE("%d_size"),
        OPACITY("%d_opacityFloat"),

        //        OLD_OPACITY( "%d_opacity"),
        CALENDAR_COLOR("%d_calendarColor"),
        TOMORROW_YESTERDAY("%d_tommorowYesterday"),
        WEEKDAY("%d_weekday"),
        END_TIME("%d_endTime"),
        SHOW_REMINDER("%d_reminder"),
        TWENTYFOUR_HOURS("%d_twentyfourHours"),
        DATE_FORMAT("%d_dateFormat"),
        CALENDARS_PREFIX("%d_calendarActive"),
        CALENDARS_SUFFIX("%d");

        fun getKey(widgetId: Int): String = String.format(Locale.US, format, widgetId)
    }

    class CalendarPreferences(
        prefs: SharedPreferences,
        widgetId: Int,
        calendarId: Int,
        val displayName: String?,
        val color: Int
    ) {
        val key: String = Keys.CALENDARS_PREFIX.getKey(widgetId) + Keys.CALENDARS_SUFFIX.getKey(calendarId)
        val enabled: Boolean = prefs.getBoolean(key, WidgetPreferences.CalenderEnabledDefault)
    }

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

    fun delete() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val calenderPrefKeyPrefix = Keys.CALENDARS_PREFIX.getKey(widgetId)
        // make a new list instance, because the key list may be mutated during edit
        val calPrefs = ArrayList(preferences.all.keys.filter { it.startsWith(calenderPrefKeyPrefix) })
        preferences.edit(commit = true) {
            remove(Keys.BIRTHDAYS.getKey(widgetId))
            remove(Keys.SCROLLABLE.getKey(widgetId))
            remove(Keys.LINES.getKey(widgetId))
            remove(Keys.SIZE.getKey(widgetId))
            remove(Keys.OPACITY.getKey(widgetId))
            remove(Keys.CALENDAR_COLOR.getKey(widgetId))
            remove(Keys.TOMORROW_YESTERDAY.getKey(widgetId))
            remove(Keys.WEEKDAY.getKey(widgetId))
            remove(Keys.END_TIME.getKey(widgetId))
            remove(Keys.SHOW_REMINDER.getKey(widgetId))
            remove(Keys.TWENTYFOUR_HOURS.getKey(widgetId))
            remove(Keys.DATE_FORMAT.getKey(widgetId))
            calPrefs.forEach {
                remove(it)
            }
        }
    }


    fun load(): WidgetPreferences {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val birthdays = BirthdaySetting.fromPersistence(prefs.getString(Keys.BIRTHDAYS.getKey(widgetId), WidgetPreferences.Default.birthdays.persistenceValue)!!)
        val scrollable = prefs.getBoolean(Keys.SCROLLABLE.getKey(widgetId), WidgetPreferences.Default.scrollable)
        val lines = prefs.getString(Keys.LINES.getKey(widgetId), WidgetPreferences.Default.lines)!!
        val size = prefs.getString(Keys.SIZE.getKey(widgetId), WidgetPreferences.Default.size)!!
        val opacity = prefs.getFloat(Keys.OPACITY.getKey(widgetId), WidgetPreferences.Default.opacity)
        val calendarColor = prefs.getBoolean(Keys.CALENDAR_COLOR.getKey(widgetId), WidgetPreferences.Default.calendarColor)
        val tomorrowYesterday = prefs.getBoolean(Keys.TOMORROW_YESTERDAY.getKey(widgetId), WidgetPreferences.Default.tomorrowYesterday)
        val weekday = prefs.getBoolean(Keys.WEEKDAY.getKey(widgetId), WidgetPreferences.Default.weekday)
        val endTime = prefs.getBoolean(Keys.END_TIME.getKey(widgetId), WidgetPreferences.Default.endTime)
        val showReminder = prefs.getBoolean(Keys.SHOW_REMINDER.getKey(widgetId), WidgetPreferences.Default.showReminder)
        val twentyfourHours = prefs.getBoolean(Keys.TWENTYFOUR_HOURS.getKey(widgetId), WidgetPreferences.Default.twentyfourHours)
        val dateFormat = DateFormat.fromPersistence(prefs.getString(Keys.DATE_FORMAT.getKey(widgetId), WidgetPreferences.Default.dateFormat.persistenceValue)!!)
        val calendars = getCalendars(context, widgetId)

        return WidgetPreferences(
            birthdays = birthdays,
            scrollable = scrollable,
            lines = lines,
            size = size,
            opacity = opacity,
            calendarColor = calendarColor,
            tomorrowYesterday = tomorrowYesterday,
            weekday = weekday,
            endTime = endTime,
            showReminder = showReminder,
            twentyfourHours = twentyfourHours,
            dateFormat = dateFormat,
            calendars = calendars
        )
    }
}
