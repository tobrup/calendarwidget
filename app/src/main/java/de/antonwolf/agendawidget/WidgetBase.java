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

package de.antonwolf.agendawidget;

import java.util.Arrays;

import android.Manifest;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import androidx.core.content.ContextCompat;

/**
 * @author Anton Wolf
 * 
 *         Base class for each widget
 */
abstract class WidgetBase extends AppWidgetProvider {

	final static Class<?>[] WIDGET_CLASSES = new Class[]{
			Widget2x1.class,
			Widget3x1.class,
			Widget3x2.class,
			Widget3x3.class,
			Widget4x1.class,
			Widget4x2.class,
			Widget4x3.class,
			Widget4x4.class,
	};

	private ContentObserver calendarInstancesObserver;
	static final String TAG = "AgendaWidget";

	@Override
	public void onReceive(Context context, Intent intent) {
		if (!intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
				&& intent.getAction() == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
			ComponentName name = new ComponentName(context, this.getClass());
			AppWidgetManager m = AppWidgetManager.getInstance(context);
			int[] ids = m.getAppWidgetIds(name);
			Log.i(TAG, "WidgetBase.onReceive(" + Arrays.toString(ids) + ")");
			onUpdate(context, m, ids);
		} else {
			Log.d(TAG, "WidgetBase.onReceive: without widgetIds");
			super.onReceive(context, intent);
		}
	}

	@Override
	public void onEnabled(Context context) {
		Log.d(TAG, "WidgetBase.onEnabled()");
		registerContentObserver(context);
	}

	@Override
	public void onDisabled(Context context) {
		Log.d(TAG, "WidgetBase.onDisabled()");
		unregisterContentObserver(context);
	}
	
	@Override
	public void onDeleted(Context context, int[] appWidgetIds) {
		Log.i(TAG, "WidgetBase.onDeleted(" + Arrays.toString(appWidgetIds) + ")");
		for (final int widgetId : appWidgetIds)
			WidgetInfo.delete(context, widgetId);
	}

	@Override
	public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
		Log.i(TAG, "WidgetBase.onUpdate(" + Arrays.toString(ids) + ")");

		unregisterContentObserver(context);
		registerContentObserver(context);

		Log.d(TAG, "WidgetBase.onUpdate: schedule widget update");
		WidgetService.scheduleServiceOnce(context);
	}

	private void unregisterContentObserver(Context context) {
		Log.d(TAG, "WidgetBase.unregisterContentObserver()");
		if (calendarInstancesObserver != null)
			context.getContentResolver().unregisterContentObserver(
					calendarInstancesObserver);
	}

	private void registerContentObserver(final Context context) {
		boolean hasPermissions = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
		if (!hasPermissions) {
			Log.w(TAG, "WidgetBase.registerContentObserver: no permissions granted");
			return;
		}
		if (calendarInstancesObserver == null) {
			final ComponentName name = new ComponentName(context,
					this.getClass());

			calendarInstancesObserver = new ContentObserver(new Handler()) {
				@Override
				public void onChange(boolean selfChange) {
					AppWidgetManager m = AppWidgetManager.getInstance(context);
					int[] ids = m.getAppWidgetIds(name);
					Log.i(TAG, "ContentObserver.onChange: update widgetIds "+Arrays.toString(ids));
					onUpdate(context, m, ids);
				}
			};
		}
		Log.d(TAG, "WidgetBase.registerContentObserver()");
		String uriString = "content://com.android.calendar";
		Uri instancesUri = Uri.parse(uriString);
		context.getContentResolver().registerContentObserver(instancesUri,
				true, calendarInstancesObserver);
	}
}
