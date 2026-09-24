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

import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.ListPreference
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.PreferenceActivity
import android.preference.PreferenceCategory
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Log

class SettingsActivity : PreferenceActivity() {

    companion object {
        const val EXTRA_WIDGET_ID: String = "widgetId"
        private const val TAG = "AgendaWidget"
        private val BIRTHDAY_PREFERENCES: Array<String> = arrayOf(
            BirthdaySetting.BIRTHDAY_SPECIAL,
            BirthdaySetting.BIRTHDAY_NORMAL,
            BirthdaySetting.BIRTHDAY_HIDE
        ).map { it.persistenceValue }.toTypedArray()

        private val DATE_FORMAT_PREFERENCES: Array<String> = arrayOf(
            DateFormat.DOT_DAY_MONTH,
            DateFormat.SLASH_DAY_MONTH,
            DateFormat.SLASH_MONTH_DAY,
            DateFormat.SLASH_YEAR_MONTH_DAY
        ).map { it.persistenceValue }.toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1)
        Log.d(TAG, "SettingsActivity.onCreate($widgetId)")
        if (widgetId == -1) {
            return
        }
        val info = WidgetPreferencesPersistence(widgetId, this).load()

        val screen = preferenceManager.createPreferenceScreen(this)
        setPreferenceScreen(screen)

        val display = PreferenceCategory(this)
        display.setTitle(R.string.settings_display)
        screen.addPreference(display)

        val scrollablePref = CheckBoxPreference(this)
        scrollablePref.setDefaultValue(info.scrollable)
        scrollablePref.setKey(WidgetPreferencesPersistence.Keys.SCROLLABLE.getKey(widgetId))
        scrollablePref.setTitle(R.string.settings_scrollable)
        scrollablePref.setSummaryOn(R.string.settings_scrollable_yes)
        scrollablePref.setSummaryOff(R.string.settings_scrollable_no)
        display.addPreference(scrollablePref)

        val linesPref = ListPreference(this)
        linesPref.setTitle(R.string.settings_display_lines)
        linesPref.setKey(WidgetPreferencesPersistence.Keys.LINES.getKey(widgetId))
        linesPref.setEntries(R.array.settings_display_lines_entries)
        linesPref.entryValues = arrayOf(
            "3", "4", "5", "6", "7", "8", "9",
            "10", "11", "12", "13", "14", "15", "16", "17", "18", "19",
            "20", "21", "22", "23", "24", "25"
        )
        linesPref.setDefaultValue(info.lines)
        val linesChangeListener = OnPreferenceChangeListener { pref, newValue ->
            pref.summary = resources.getString(R.string.settings_display_lines_summary, newValue)
            true
        }
        linesChangeListener.onPreferenceChange(linesPref, info.lines)
        linesPref.onPreferenceChangeListener = linesChangeListener
        display.addPreference(linesPref)

        val fontSizePref = ListPreference(this)
        fontSizePref.setTitle(R.string.settings_display_size)
        fontSizePref.setKey(WidgetPreferencesPersistence.Keys.SIZE.getKey(widgetId))
        fontSizePref.setEntries(R.array.settings_display_size_entries)
        fontSizePref.entryValues = arrayOf("50", "75", "100", "125", "150", "200", "250")
        fontSizePref.setDefaultValue(info.size)
        val fontSizeChangeListener = OnPreferenceChangeListener { pref, newValue ->
            pref.summary = resources.getString(R.string.settings_display_size_summary, newValue)
            true
        }
        fontSizeChangeListener.onPreferenceChange(fontSizePref, info.size)
        fontSizePref.onPreferenceChangeListener = fontSizeChangeListener
        display.addPreference(fontSizePref)

        display.addPreference(OpacityPreference(this, info, WidgetPreferencesPersistence.Keys.OPACITY.getKey(widgetId)))

        val birthdayPref = ListPreference(this)
        birthdayPref.setTitle(R.string.settings_birthdays)
        birthdayPref.setKey(WidgetPreferencesPersistence.Keys.BIRTHDAYS.getKey(widgetId))
        birthdayPref.setEntries(R.array.settings_birthdays_entries)
        birthdayPref.entryValues = BIRTHDAY_PREFERENCES
        birthdayPref.setDefaultValue(info.birthdays.persistenceValue)
        val birthdayChangedListener = OnPreferenceChangeListener { pref, newValue ->
            Log.d(TAG, "SettingsActivity: birthdayPref.onPreferenceChange: $newValue")
            val summaries = resources.getStringArray(R.array.settings_birthdays_summaries)
            pref.summary = summaries[BIRTHDAY_PREFERENCES.indexOf(newValue)]
            true
        }
        birthdayChangedListener.onPreferenceChange(birthdayPref, info.birthdays.persistenceValue)
        birthdayPref.onPreferenceChangeListener = birthdayChangedListener
        display.addPreference(birthdayPref)

        val weekdayPref = CheckBoxPreference(this)
        weekdayPref.setDefaultValue(info.weekday)
        weekdayPref.setKey(WidgetPreferencesPersistence.Keys.WEEKDAY.getKey(widgetId))
        weekdayPref.setTitle(R.string.settings_weekday)
        weekdayPref.setSummaryOn(R.string.settings_weekday_yes)
        weekdayPref.setSummaryOff(R.string.settings_weekday_no)
        display.addPreference(weekdayPref)

        val tomorrowYesterdayPref = CheckBoxPreference(this)
        tomorrowYesterdayPref.setDefaultValue(info.tomorrowYesterday)
        tomorrowYesterdayPref.setKey(WidgetPreferencesPersistence.Keys.TOMORROW_YESTERDAY.getKey(widgetId))
        tomorrowYesterdayPref.setTitle(R.string.settings_tommorow_yesterday)
        tomorrowYesterdayPref.setSummaryOn(R.string.settings_tommorow_yesterday_yes)
        tomorrowYesterdayPref.setSummaryOff(R.string.settings_tommorow_yesterday_no)
        display.addPreference(tomorrowYesterdayPref)

        val endTimePref = CheckBoxPreference(this)
        endTimePref.setDefaultValue(info.endTime)
        endTimePref.setKey(WidgetPreferencesPersistence.Keys.END_TIME.getKey(widgetId))
        endTimePref.setTitle(R.string.settings_end_time)
        endTimePref.setSummaryOn(R.string.settings_end_time_yes)
        endTimePref.setSummaryOff(R.string.settings_end_time_no)
        display.addPreference(endTimePref)

        val reminderPref = CheckBoxPreference(this)
        reminderPref.setDefaultValue(info.showReminder)
        reminderPref.setKey(WidgetPreferencesPersistence.Keys.SHOW_REMINDER.getKey(widgetId))
        reminderPref.setTitle(R.string.settings_show_reminder)
        reminderPref.setSummaryOn(R.string.settings_show_reminder_yes)
        reminderPref.setSummaryOff(R.string.settings_show_reminder_no)
        display.addPreference(reminderPref)

        val calendarColorPref = CheckBoxPreference(this)
        calendarColorPref.setDefaultValue(info.calendarColor)
        calendarColorPref.setKey(WidgetPreferencesPersistence.Keys.CALENDAR_COLOR.getKey(widgetId))
        calendarColorPref.setTitle(R.string.settings_calendar_color)
        calendarColorPref.setSummaryOn(R.string.settings_calendar_color_show)
        calendarColorPref.setSummaryOff(R.string.settings_calendar_color_hide)
        display.addPreference(calendarColorPref)

        val now = System.currentTimeMillis()
        val dateFormatPref = ListPreference(this)
        dateFormatPref.setTitle(R.string.settings_date_format)
        dateFormatPref.setKey(WidgetPreferencesPersistence.Keys.DATE_FORMAT.getKey(widgetId))
        val dateFormatEntries = resources.getStringArray(R.array.settings_date_format_entries)
        val dateFormatDisplayStrings = dateFormatEntries.map {
            String.format(it, now)
        }
        dateFormatPref.entries = dateFormatDisplayStrings.toTypedArray()
        dateFormatPref.entryValues = DATE_FORMAT_PREFERENCES
        dateFormatPref.setDefaultValue(info.dateFormat.persistenceValue)
        val dateFormatSummary = resources.getString(R.string.settings_date_format_summary)

        val dateFormatChangedListener = OnPreferenceChangeListener { pref, newValue ->
            val ordinal = DateFormat.fromPersistence(newValue as String).ordinal
            pref.summary = String.format(dateFormatSummary, dateFormatDisplayStrings[ordinal])
            true
        }
        dateFormatChangedListener.onPreferenceChange(dateFormatPref, info.dateFormat.persistenceValue)
        dateFormatPref.onPreferenceChangeListener = dateFormatChangedListener
        display.addPreference(dateFormatPref)

        val twentyFourHoursPref = CheckBoxPreference(this)
        twentyFourHoursPref.setDefaultValue(info.twentyfourHours)
        twentyFourHoursPref.setKey(WidgetPreferencesPersistence.Keys.TWENTYFOUR_HOURS.getKey(widgetId))
        twentyFourHoursPref.setTitle(R.string.settings_twentyfour_hours)
        twentyFourHoursPref.setSummaryOn(String.format(resources.getString(R.string.settings_twentyfour_hours_yes), now))
        twentyFourHoursPref.setSummaryOff(String.format(resources.getString(R.string.settings_twentyfour_hours_no), now))
        display.addPreference(twentyFourHoursPref)

        val calendars = PreferenceCategory(this)
        calendars.setTitle(R.string.settings_calendars)
        screen.addPreference(calendars)

        info.calendars.entries.forEach { cInfo ->
            val calendarPref = CheckBoxPreference(this)
            val value = cInfo.value!!
            calendarPref.setDefaultValue(value.enabled)
            calendarPref.setKey(value.key)

            val title = SpannableStringBuilder("■ ")
            title.setSpan(ForegroundColorSpan(value.color), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            title.append(value.displayName)
            calendarPref.title = title

            calendarPref.setSummaryOn(resources.getString(R.string.settings_calendars_show, value.displayName))
            calendarPref.setSummaryOff(resources.getString(R.string.settings_calendars_hide, value.displayName))
            calendars.addPreference(calendarPref)
        }
    }

    override fun onResume() {
        super.onResume()

        val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1)
        Log.d(TAG, "SettingsActivity.onResume($widgetId)")
    }

    override fun onPause() {
        super.onPause()
        val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1)
        Log.d(TAG, "SettingsActivity.onPause($widgetId). Schedule widget update")
        WidgetService.scheduleServiceOnce(this)
    }

}
