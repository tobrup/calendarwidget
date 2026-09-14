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
package de.antonwolf.agendawidget;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Arrays;

/**
 * The activity that launches when the user clicks on the widget
 *
 * @author Anton Wolf
 *
 */
public final class PickActionActivity extends Activity {
    /**
     * An OnClickListener that starts an Activity described by an Intent
     *
     * @author Anton Wolf
     *
     */
    private final static class StartActivityOnClick implements
            View.OnClickListener {
        final Intent intent;

        StartActivityOnClick(Intent intent) {
            this.intent = intent;
        }

        @Override
        public void onClick(View v) {
            v.getContext().startActivity(intent);
        }
    }

    /**
     * The key under which the Intent's AppWidget ID is stored
     */
    public final static String EXTRA_WIDGET_ID = "widgetId";

    private final static int REQUEST_PERMISSIONS_CODE = 100;

    private final static Class<?>[] widgetClasses = new Class[]{
            Widget2x1.class,
            Widget3x1.class,
            Widget3x2.class,
            Widget3x3.class,
            Widget4x1.class,
            Widget4x2.class,
            Widget4x3.class,
            Widget4x4.class,
    };

    /**
     * Tag for Log messages
     */
    private final static String TAG = "AgendaWidget";

    /**
     * Sets up the Activity's GUI
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final int widgetId = getIntent().getIntExtra(EXTRA_WIDGET_ID, -1);
        Log.d(TAG, "PickActionActivity.onCreate(" + widgetId + ")");

        setContentView(R.layout.pick_action);

        final Intent calendar = new Intent(Intent.ACTION_VIEW,
                Uri.parse("content://com.android.calendar/time"));
        findViewById(R.id.open_calendar).setOnClickListener(new StartActivityOnClick(calendar));

        if (widgetId == -1) {
            findViewById(R.id.open_settings).setVisibility(View.GONE);
        } else {
            findViewById(R.id.open_settings).setVisibility(View.VISIBLE);
            final Intent settings = new Intent(this, SettingsActivity.class);
            settings.putExtra(SettingsActivity.EXTRA_WIDGET_ID, widgetId);
            findViewById(R.id.open_settings).setOnClickListener(new StartActivityOnClick(settings));
        }

        updatePermissionsValueText();
        findViewById(R.id.permissions_entry).setOnClickListener(v -> requestPermissions());
    }

    private void updatePermissionsValueText() {
        boolean hasPermissions = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
        int textRes = hasPermissions ? R.string.permissions_list_entry_granted : R.string.permissions_list_entry_denied;
        ((TextView) findViewById(R.id.permissions_entry_value)).setText(textRes);
    }

    private void requestPermissions() {
        boolean hasPermissions = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
        if (hasPermissions) {
            Log.d(TAG, "PickActionActivity.requestPermissions: Already granted");
        } else {
            Log.d(TAG, "PickActionActivity.requestPermissions: requestPermissions...");
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.READ_CALENDAR},
                    REQUEST_PERMISSIONS_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(TAG, "PickActionActivity.onRequestPermissionsResult: code: " + requestCode + ", permissions: " + Arrays.toString(permissions) + ", grantResults: " + Arrays.toString(grantResults));
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS_CODE &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "PickActionActivity.onRequestPermissionsResult: permissions granted.");
            updateWidgets();
        } else {
            Log.w(TAG, "PickActionActivity.onRequestPermissionsResult: permissions not granted");
        }
        updatePermissionsValueText();
    }

    private void updateWidgets() {
        for (Class<?> c : widgetClasses) {
            final ComponentName name = new ComponentName(this, c);
            AppWidgetManager m = AppWidgetManager.getInstance(this);
            int[] widgetIds = m.getAppWidgetIds(name);
            if (widgetIds.length > 0) {
                Log.d(TAG, "PickActionActivity.updateWidgets: update widgets " + Arrays.toString(widgetIds) + " for class " + c.getName());

                Intent broadcastIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                broadcastIntent.setComponent(name);
                broadcastIntent.putExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_IDS,
                        widgetIds
                );
                Log.d(TAG, "PickActionActivity.updateWidgets: Sending Broadcast Intent " + broadcastIntent);
                sendBroadcast(broadcastIntent);

                for (int widgetId : widgetIds) {
                    Intent serviceIntent = new Intent("update", Uri.parse("widget://" + widgetId),
                            this, WidgetService.class);
                    Log.d(TAG, "PickActionActivity.updateWidgets: Sending Intent " + serviceIntent);
                    startService(serviceIntent);
                }
            }
        }
    }
}
