/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch2

import android.annotation.SuppressLint
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MotionEvent
import android.view.MotionEvent.*
import android.view.View
import android.view.View.OnTouchListener
import android.widget.RadioButton
import org.dwallach.R
import org.dwallach.calwatch2.ClockState.FACE_LITE
import org.dwallach.calwatch2.ClockState.FACE_NUMBERS
import org.dwallach.calwatch2.ClockState.FACE_TOOL
import org.jetbrains.anko.*
import kotlin.math.PI
import kotlin.math.atan2

@SuppressLint("ClickableViewAccessibility")
class StylePickerActivity : AppCompatActivity(), AnkoLogger, OnTouchListener {
    // We're looking for any touch event of any sort, and then we'll just take it and run.
    // This isn't exactly normal behavior, but it's simple. Simple is good enough.
    override fun onTouch(v: View?, event: MotionEvent?) =
            if (event == null || v == null) false else when(event.action) {
                ACTION_BUTTON_PRESS, ACTION_BUTTON_RELEASE, ACTION_DOWN,
                ACTION_UP, ACTION_POINTER_DOWN, ACTION_POINTER_UP -> {
                    val rawx = event.rawX
                    val rawy = event.rawY
                    info { "Click: $rawx, $rawy" }

                    if (ClockState.screenY == -1) {
                        warn("We don't know the real screen size yet, bailing!")
                        false
                    } else {
                        val y = ClockState.screenY - rawy
                        val theta = atan2(y, rawx) * 180 / PI

                        ClockState.faceMode = when {
                            theta < 90 || theta > 330 -> FACE_NUMBERS
                            theta < 210 -> FACE_TOOL
                            else -> FACE_LITE
                        }

                        val thetaStr = String.format("%.2f", theta)

                        info { "Theta: $thetaStr --> new face mode ${ ClockState.faceMode }" }

                        PreferencesHelper.savePreferences(v.context)
                        ClockState.notifyObservers()

                        true
                    }
                }

                else -> false
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        info("onCreate")
        setContentView(R.layout.activity_style_picker)
        val toolButton = findViewById<RadioButton>(R.id.radioButtonTool)
        val liteButton = findViewById<RadioButton>(R.id.radioButtonLite)
        val numbersButton = findViewById<RadioButton>(R.id.radioButtonNumbers)
        info {"original faceMode = ${ClockState.faceMode}" }

        liteButton.isChecked = ClockState.faceMode == FACE_LITE
        numbersButton.isChecked = ClockState.faceMode == FACE_NUMBERS
        toolButton.isChecked = ClockState.faceMode == FACE_TOOL

        numbersButton.setOnTouchListener(this)
        liteButton.setOnTouchListener(this)
        toolButton.setOnTouchListener(this)
    }

    companion object: AnkoLogger {
        /**
         * Call this to launch the style picker dialog.
         */
        fun kickStart(context: Context) {
            verbose("kickStart")
            context.startActivity(context.intentFor<StylePickerActivity>().newTask())
        }
    }
}
