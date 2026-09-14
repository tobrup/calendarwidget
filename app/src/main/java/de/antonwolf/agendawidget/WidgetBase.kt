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
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * @author Anton Wolf
 * 
 * Base class for each widget
 */
abstract class WidgetBase : AppWidgetProvider() {
    private var calendarInstancesObserver: ContentObserver? = null
    override fun onReceive(context: Context, intent: Intent) {
        if (!intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            && intent.getAction() === AppWidgetManager.ACTION_APPWIDGET_UPDATE
        ) {
            val name = ComponentName(context, this.javaClass)
            val m = AppWidgetManager.getInstance(context)
            val ids = m.getAppWidgetIds(name)
            Log.i(TAG, "WidgetBase.onReceive(" + ids.contentToString() + ")")
            onUpdate(context, m, ids)
        } else {
            Log.d(TAG, "WidgetBase.onReceive: without widgetIds")
            super.onReceive(context, intent)
        }
    }

    override fun onEnabled(context: Context) {
        Log.d(TAG, "WidgetBase.onEnabled()")
        registerContentObserver(context)
    }

    override fun onDisabled(context: Context) {
        Log.d(TAG, "WidgetBase.onDisabled()")
        unregisterContentObserver(context)
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray) {
        Log.i(TAG, "WidgetBase.onDeleted(" + appWidgetIds.contentToString() + ")")
        for (widgetId in appWidgetIds) WidgetInfo.Companion.delete(context!!, widgetId)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager?, ids: IntArray?) {
        Log.i(TAG, "WidgetBase.onUpdate(" + ids.contentToString() + ")")

        unregisterContentObserver(context)
        registerContentObserver(context)

        Log.d(TAG, "WidgetBase.onUpdate: schedule widget update")
        WidgetService.Companion.scheduleServiceOnce(context)
    }

    private fun unregisterContentObserver(context: Context) {
        Log.d(TAG, "WidgetBase.unregisterContentObserver()")
        if (calendarInstancesObserver != null) context.getContentResolver().unregisterContentObserver(
            calendarInstancesObserver!!
        )
    }

    private fun registerContentObserver(context: Context) {
        val hasPermissions = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (!hasPermissions) {
            Log.w(TAG, "WidgetBase.registerContentObserver: no permissions granted")
            return
        }
        if (calendarInstancesObserver == null) {
            val name = ComponentName(
                context,
                this.javaClass
            )

            calendarInstancesObserver = object : ContentObserver(Handler()) {
                override fun onChange(selfChange: Boolean) {
                    val m = AppWidgetManager.getInstance(context)
                    val ids = m.getAppWidgetIds(name)
                    Log.i(TAG, "ContentObserver.onChange: update widgetIds " + ids.contentToString())
                    onUpdate(context, m, ids)
                }
            }
        }
        Log.d(TAG, "WidgetBase.registerContentObserver()")
        val uriString = "content://com.android.calendar"
        val instancesUri = Uri.parse(uriString)
        context.getContentResolver().registerContentObserver(
            instancesUri,
            true, calendarInstancesObserver!!
        )
    }

    companion object {
        val WIDGET_CLASSES: Array<Class<*>> = arrayOf<Class<*>>(
            Widget2x1::class.java,
            Widget3x1::class.java,
            Widget3x2::class.java,
            Widget3x3::class.java,
            Widget4x1::class.java,
            Widget4x2::class.java,
            Widget4x3::class.java,
            Widget4x4::class.java,
        )

        const val TAG: String = "AgendaWidget"
    }
}
