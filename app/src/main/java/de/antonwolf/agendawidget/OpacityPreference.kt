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

class OpacityPreference(context: Context?, info: WidgetInfo) : DialogPreference(context, null), OnSeekBarChangeListener {
    private var text: TextView? = null
    private var bar: SeekBar? = null
    private var checkerboard: ImageView? = null
    private val defaultValue: Float

    init {
        setDialogLayoutResource(R.layout.preference_opacity)
        setTitle(R.string.settings_display_opacity)
        setDialogTitle(R.string.settings_display_opacity)
        setKey(info.opacityKey)
        defaultValue = info.opacityDefault
        setDefaultValue(defaultValue)
        val opacityPercent = (100 * info.opacity).toInt()
        setSummary(
            getContext().getResources().getString(
                R.string.settings_display_opacity_summary, opacityPercent
            )
        )
    }

    private fun displayProgress(value: Float) {
        val valuePercent = (value * 100).toInt()
        text!!.setText(
            getContext().getResources().getString(
                R.string.settings_display_opacity_dialog, valuePercent
            )
        )
        checkerboard!!.setImageLevel(valuePercent)
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        val value = getPersistedFloat(defaultValue)
        text = view.findViewById<View?>(R.id.value) as TextView
        bar = view.findViewById<View?>(R.id.bar) as SeekBar
        bar!!.setMax((1 / step).toInt())
        bar!!.setProgress((value / step).toInt())
        bar!!.setOnSeekBarChangeListener(this)
        checkerboard = view.findViewById<View?>(R.id.checkerboard) as ImageView
        displayProgress(value)
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        super.onDialogClosed(positiveResult)

        if (positiveResult) {
            persistFloat(bar!!.getProgress() * step)
            val opacityPercent = (100 * bar!!.getProgress() * step).toInt()
            setSummary(
                getContext().getResources().getString(
                    R.string.settings_display_opacity_summary, opacityPercent
                )
            )
        }
    }

    override fun onProgressChanged(
        seekBar: SeekBar?, progress: Int,
        fromUser: Boolean
    ) {
        displayProgress(progress * step)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
    }

    companion object {
        private val step = 1f / 20f
    }
}