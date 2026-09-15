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
 * THE SOFTWARE
 */
package de.antonwolf.agendawidget

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/**
 * The activity that launches when the user clicks on the widget
 * 
 * @author Anton Wolf
 */
class PickActionActivity : Activity() {

    companion object {
        /**
         * The key under which the Intent's AppWidget ID is stored
         */
        const val EXTRA_WIDGET_ID: String = "widgetId"

        private const val REQUEST_PERMISSIONS_CODE = 100

        /**
         * Tag for Log messages
         */
        private const val TAG = "AgendaWidget"
    }

    /**
     * An OnClickListener that starts an Activity described by an Intent
     * 
     * @author Anton Wolf
     */
    private class StartActivityOnClick(val intent: Intent?) : View.OnClickListener {
        override fun onClick(v: View) {
            v.context.startActivity(intent)
        }
    }

    /**
     * Sets up the Activity's GUI
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1)
        Log.d(TAG, "PickActionActivity.onCreate($widgetId)")

        setContentView(R.layout.pick_action)

        val calendar = Intent(Intent.ACTION_VIEW, "content://com.android.calendar/time".toUri())
        findViewById<View>(R.id.open_calendar).setOnClickListener(StartActivityOnClick(calendar))

        val openSettingsEntry = findViewById<View>(R.id.open_settings)
        if (widgetId == -1) {
            openSettingsEntry.visibility = View.GONE
        } else {
            openSettingsEntry.visibility = View.VISIBLE
            val settings = Intent(this, SettingsActivity::class.java)
            settings.putExtra(SettingsActivity.EXTRA_WIDGET_ID, widgetId)
            openSettingsEntry.setOnClickListener(StartActivityOnClick(settings))
        }

        updatePermissionsValueText()
        findViewById<View>(R.id.permissions_entry).setOnClickListener { _: View? ->
            requestPermissions()
        }
    }

    private fun updatePermissionsValueText() {
        val hasPermissions = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val textRes = if (hasPermissions) R.string.permissions_list_entry_granted else R.string.permissions_list_entry_denied
        findViewById<TextView>(R.id.permissions_entry_value).setText(textRes)
    }

    private fun requestPermissions() {
        val hasPermissions = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (hasPermissions) {
            Log.d(TAG, "PickActionActivity.requestPermissions: Already granted")
        } else {
            Log.d(TAG, "PickActionActivity.requestPermissions: requestPermissions...")
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CALENDAR), REQUEST_PERMISSIONS_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        Log.d(TAG, "PickActionActivity.onRequestPermissionsResult: code: " + requestCode + ", permissions: " + permissions.contentToString() + ", grantResults: " + grantResults.contentToString())
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS_CODE && grantResults.getOrNull(0) == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "PickActionActivity.onRequestPermissionsResult: permissions granted.")
            updateWidgets()
        } else {
            Log.w(TAG, "PickActionActivity.onRequestPermissionsResult: permissions not granted")
        }
        updatePermissionsValueText()
    }

    private fun updateWidgets() {
        val name = ComponentName(this, WidgetResizable::class.java)
        val manager = AppWidgetManager.getInstance(this)
        val widgetIds = manager.getAppWidgetIds(name)
        if (widgetIds.isNotEmpty()) {
            Log.d(TAG, "PickActionActivity.updateWidgets: update widgets ${widgetIds.joinToString()}")

            val broadcastIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            broadcastIntent.setComponent(name)
            broadcastIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            Log.d(TAG, "PickActionActivity.updateWidgets: Sending Broadcast Intent $broadcastIntent")
            sendBroadcast(broadcastIntent)
        }
        Log.d(TAG, "PickActionActivity.updateWidgets: schedule widget update")
        WidgetService.scheduleServiceOnce(this)
    }
}
