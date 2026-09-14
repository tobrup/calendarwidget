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
import android.preference.Preference
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.PreferenceActivity
import android.preference.PreferenceCategory
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Log

class SettingsActivity : PreferenceActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val widgetId = getIntent().getIntExtra(EXTRA_WIDGET_ID, -1)
        Log.d(TAG, "SettingsActivity.onCreate(" + widgetId + ")")
        if (-1 == widgetId) return
        val info = WidgetInfo(widgetId, this)

        val screen = getPreferenceManager()
            .createPreferenceScreen(this)
        setPreferenceScreen(screen)

        val display = PreferenceCategory(this)
        display.setTitle(R.string.settings_display)
        screen.addPreference(display)

        val lines = ListPreference(this)
        lines.setTitle(R.string.settings_display_lines)
        lines.setKey(info.linesKey)
        lines.setEntries(R.array.settings_display_lines_entries)
        lines.setEntryValues(
            arrayOf<String>(
                "3", "4", "5", "6", "7", "8", "9",
                "10", "11", "12", "13", "14", "15", "16", "17", "18", "19",
                "20", "21", "22", "23", "24", "25"
            )
        )
        lines.setDefaultValue(info.linesDefault)
        val linesChanged: OnPreferenceChangeListener = object : OnPreferenceChangeListener {
            override fun onPreferenceChange(
                pref: Preference,
                newValue: Any?
            ): Boolean {
                pref.setSummary(
                    getResources().getString(
                        R.string.settings_display_lines_summary, newValue
                    )
                )
                return true
            }
        }
        linesChanged.onPreferenceChange(lines, info.lines)
        lines.setOnPreferenceChangeListener(linesChanged)
        display.addPreference(lines)

        val size = ListPreference(this)
        size.setTitle(R.string.settings_display_size)
        size.setKey(info.sizeKey)
        size.setEntries(R.array.settings_display_size_entries)
        size.setEntryValues(
            arrayOf<String>(
                "50", "75", "100", "125", "150",
                "200", "250"
            )
        )
        size.setDefaultValue(info.sizeDefault)
        val sizeChanged: OnPreferenceChangeListener = object : OnPreferenceChangeListener {
            override fun onPreferenceChange(
                pref: Preference,
                newValue: Any?
            ): Boolean {
                pref.setSummary(
                    getResources().getString(
                        R.string.settings_display_size_summary, newValue
                    )
                )
                return true
            }
        }
        sizeChanged.onPreferenceChange(size, info.size)
        size.setOnPreferenceChangeListener(sizeChanged)
        display.addPreference(size)

        display.addPreference(OpacityPreference(this, info))

        val birthdays = ListPreference(this)
        birthdays.setTitle(R.string.settings_birthdays)
        birthdays.setKey(info.birthdaysKey)
        birthdays.setEntries(R.array.settings_birthdays_entries)
        birthdays.setEntryValues(BIRTHDAY_PREFERENCES)
        birthdays.setDefaultValue(info.birthdaysDefault)
        val birthdaysChanged: OnPreferenceChangeListener = object : OnPreferenceChangeListener {
            override fun onPreferenceChange(
                pref: Preference,
                newValue: Any
            ): Boolean {
                Log.d(TAG, "SettingsActivity.onPreferenceChange: " + newValue.toString())
                var i = 0
                while (i < BIRTHDAY_PREFERENCES.size) {
                    if (BIRTHDAY_PREFERENCES[i] == newValue) break
                    i++
                }
                val summaries = getResources().getStringArray(
                    R.array.settings_birthdays_summaries
                )
                pref.setSummary(summaries[i])
                return true
            }
        }
        birthdaysChanged.onPreferenceChange(birthdays, info.birthdays)
        birthdays.setOnPreferenceChangeListener(birthdaysChanged)
        display.addPreference(birthdays)

        val weekday = CheckBoxPreference(this)
        weekday.setDefaultValue(info.weekday)
        weekday.setKey(info.weekdayKey)
        weekday.setTitle(R.string.settings_weekday)
        weekday.setSummaryOn(R.string.settings_weekday_yes)
        weekday.setSummaryOff(R.string.settings_weekday_no)
        display.addPreference(weekday)

        val tomorrowYesterday = CheckBoxPreference(
            this
        )
        tomorrowYesterday.setDefaultValue(info.tomorrowYesterdayDefault)
        tomorrowYesterday.setKey(info.tomorrowYesterdayKey)
        tomorrowYesterday.setTitle(R.string.settings_tommorow_yesterday)
        tomorrowYesterday
            .setSummaryOn(R.string.settings_tommorow_yesterday_yes)
        tomorrowYesterday
            .setSummaryOff(R.string.settings_tommorow_yesterday_no)
        display.addPreference(tomorrowYesterday)

        val endTime = CheckBoxPreference(this)
        endTime.setDefaultValue(info.endTimeDefault)
        endTime.setKey(info.endTimeKey)
        endTime.setTitle(R.string.settings_end_time)
        endTime.setSummaryOn(R.string.settings_end_time_yes)
        endTime.setSummaryOff(R.string.settings_end_time_no)
        display.addPreference(endTime)

        val calendarColor = CheckBoxPreference(this)
        calendarColor.setDefaultValue(info.calendarColorDefault)
        calendarColor.setKey(info.calendarColorKey)
        calendarColor.setTitle(R.string.settings_calendar_color)
        calendarColor.setSummaryOn(R.string.settings_calendar_color_show)
        calendarColor.setSummaryOff(R.string.settings_calendar_color_hide)
        display.addPreference(calendarColor)

        val dateFormat = ListPreference(this)
        dateFormat.setTitle(R.string.settings_date_format)
        dateFormat.setKey(info.dateFormatKey)
        val dateFormatEntries = getResources().getStringArray(
            R.array.settings_date_format_entries
        )
        for (i in dateFormatEntries.indices) dateFormatEntries[i] = String.format(
            dateFormatEntries[i]!!,
            System.currentTimeMillis()
        )
        dateFormat.setEntries(dateFormatEntries)
        dateFormat.setEntryValues(DATE_FORMAT_PREFERENCES)
        dateFormat.setDefaultValue(info.dateFormatDefault.toString())
        val dateFormatSummary = getResources().getString(
            R.string.settings_date_format_summary
        )

        val dateFormatChanged: OnPreferenceChangeListener = object : OnPreferenceChangeListener {
            override fun onPreferenceChange(
                pref: Preference,
                newValue: Any?
            ): Boolean {
                val ordinal = WidgetInfo.DateFormat.valueOf(
                    (newValue as kotlin.String?)!!
                ).ordinal
                pref.setSummary(
                    String.format(
                        dateFormatSummary,
                        dateFormatEntries[ordinal]
                    )
                )
                return true
            }
        }
        dateFormatChanged.onPreferenceChange(dateFormat, info.dateFormat.toString())
        dateFormat.setOnPreferenceChangeListener(dateFormatChanged)
        display.addPreference(dateFormat)

        val twentyfourHours = CheckBoxPreference(this)
        twentyfourHours.setDefaultValue(info.twentyfourHoursDefault)
        twentyfourHours.setKey(info.twentyfourHoursKey)
        twentyfourHours.setTitle(R.string.settings_twentyfour_hours)
        twentyfourHours.setSummaryOn(
            String.format(
                getResources()
                    .getString(R.string.settings_twentyfour_hours_yes),
                System.currentTimeMillis()
            )
        )
        twentyfourHours.setSummaryOff(
            String.format(
                getResources().getString(
                    R.string.settings_twentyfour_hours_no
                ),
                System.currentTimeMillis()
            )
        )
        display.addPreference(twentyfourHours)

        val calendars = PreferenceCategory(this)
        calendars.setTitle(R.string.settings_calendars)
        screen.addPreference(calendars)

        for (cinfo in info.calendars
            .entries) {
            val calendar = CheckBoxPreference(this)
            calendar.setDefaultValue(cinfo.value.enabledDefault)
            calendar.setKey(cinfo.value.key)

            val title = SpannableStringBuilder(
                "■ "
            )
            title.setSpan(
                ForegroundColorSpan(cinfo.value.color), 0,
                1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            title.append(cinfo.value.displayName)
            calendar.setTitle(title)

            calendar.setSummaryOn(
                getResources().getString(
                    R.string.settings_calendars_show,
                    cinfo.value.displayName
                )
            )
            calendar.setSummaryOff(
                getResources().getString(
                    R.string.settings_calendars_hide,
                    cinfo.value.displayName
                )
            )
            calendars.addPreference(calendar)
        }
    }

    override fun onResume() {
        super.onResume()

        val widgetId = getIntent().getIntExtra(EXTRA_WIDGET_ID, -1)
        Log.d(TAG, "SettingsActivity.onResume(" + widgetId + ")")
    }

    override fun onPause() {
        super.onPause()
        val widgetId = getIntent().getIntExtra(EXTRA_WIDGET_ID, -1)
        Log.d(TAG, "SettingsActivity.onPause(" + widgetId + "). Schedule widget update")
        WidgetService.Companion.scheduleServiceOnce(this)
    }

    companion object {
        const val EXTRA_WIDGET_ID: String = "widgetId"
        private const val TAG = "AgendaWidget"
        private val BIRTHDAY_PREFERENCES: Array<String?> = arrayOf<String>(
            WidgetInfo.Companion.BIRTHDAY_SPECIAL, WidgetInfo.Companion.BIRTHDAY_NORMAL,
            WidgetInfo.Companion.BIRTHDAY_HIDE
        )
        private val DATE_FORMAT_PREFERENCES: Array<String?> = arrayOf<String>(
            WidgetInfo.DateFormat.DOT_DAY_MONTH.toString(),
            WidgetInfo.DateFormat.SLASH_DAY_MONTH.toString(),
            WidgetInfo.DateFormat.SLASH_MONTH_DAY.toString(),
            WidgetInfo.DateFormat.SLASH_YEAR_MONTH_DAY.toString()
        )
    }
}
