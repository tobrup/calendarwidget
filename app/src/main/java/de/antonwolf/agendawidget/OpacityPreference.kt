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
import android.preference.DialogPreference
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView

class OpacityPreference(context: Context?, info: WidgetPreferences, key: String) : DialogPreference(context, null), OnSeekBarChangeListener {

    companion object {
        private const val STEP = 1f / 20f
    }

    private lateinit var text: TextView
    private lateinit var bar: SeekBar
    private lateinit var checkerboard: ImageView
    private val defaultValue: Float

    init {
        dialogLayoutResource = R.layout.preference_opacity
        setTitle(R.string.settings_display_opacity)
        setDialogTitle(R.string.settings_display_opacity)
        setKey(key)
        defaultValue = WidgetPreferences.Default.opacity
        setDefaultValue(defaultValue)
        val opacityPercent = (100 * info.opacity).toInt()
        summary = getContext().resources.getString(R.string.settings_display_opacity_summary, opacityPercent)
    }

    private fun displayProgress(value: Float) {
        val valuePercent = (value * 100).toInt()
        text.text = context.resources.getString(R.string.settings_display_opacity_dialog, valuePercent)
        checkerboard.setImageLevel(valuePercent)
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        val value = getPersistedFloat(defaultValue)
        text = view.findViewById(R.id.value)
        bar = view.findViewById(R.id.bar)
        bar.setMax((1 / STEP).toInt())
        bar.progress = (value / STEP).toInt()
        bar.setOnSeekBarChangeListener(this)
        checkerboard = view.findViewById(R.id.checkerboard)
        displayProgress(value)
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        super.onDialogClosed(positiveResult)

        if (positiveResult) {
            persistFloat(bar.progress * STEP)
            val opacityPercent = (100 * bar.progress * STEP).toInt()
            summary = context.resources.getString(R.string.settings_display_opacity_summary, opacityPercent)
        }
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        displayProgress(progress * STEP)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
    }
}