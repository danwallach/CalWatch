/*
 * CalWatch
 * Copyright (C) 2014-2018 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */


package org.dwallach.calwatch2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.MotionEvent.*
import android.view.View
import org.dwallach.R
import org.dwallach.calwatch2.ClockState.FACE_LITE
import org.dwallach.calwatch2.ClockState.FACE_NUMBERS
import org.dwallach.calwatch2.ClockState.FACE_TOOL
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.warn
import kotlin.math.PI
import kotlin.math.atan2

class StylePickerView(context: Context, attrs: AttributeSet) : View(context, attrs), AnkoLogger {
    private var w: Int = -1
    private var h: Int = -1

    private val toolSelected = context.getDrawable(R.drawable.ic_tool_selected)
    private val liteSelected = context.getDrawable(R.drawable.ic_lite_selected)
    private val numbersSelected = context.getDrawable(R.drawable.ic_numbers_selected)

    private val allDrawables = listOf(toolSelected, liteSelected, numbersSelected)


    override fun onVisibilityChanged(changedView: View?, visibility: Int) = invalidate()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        this.w = w
        this.h = h

        allDrawables.forEachIndexed { i, d ->
            d?.setBounds(0, 0, w, h) ?: warn { "drawable resource $i is null!" }
        }

        info { "onSizeChanged: $w, $h" }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
//        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        if (w == -1 || h == -1) {
            warn { "onDraw: no width or height yet!" }
            return
        }

        info { "onDraw: $w, $h: ${ClockState.faceMode}" }

        when (ClockState.faceMode) {
            ClockState.FACE_NUMBERS -> numbersSelected?.draw(canvas)
            ClockState.FACE_LITE -> liteSelected?.draw(canvas)
            ClockState.FACE_TOOL -> toolSelected?.draw(canvas)
            else -> warn { "onDraw: Unexpected faceMode: ${ClockState.faceMode}" }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent) = when (event.action) {
        ACTION_UP, ACTION_BUTTON_RELEASE, ACTION_POINTER_UP -> {
            val rawx = event.x
            val rawy = event.y
            info { "onTouchEvent: %.1f, %.1f".format(rawx, rawy) }

            if (h == -1 || w == -1) {
                warn("We don't know the real screen size yet, bailing!")
                super.onTouchEvent(event)
            } else {
                val y = (h / 2) - rawy
                val x = rawx - ( w / 2 )
                info { "onTouchEvent: %.1f, %.1f --> %.1f, %.1f".format(rawx, rawy, x, y) }

                // theta ranges from -180 to 180 (degrees)
                val theta = atan2(y, x) * 180.0 / PI

                ClockState.faceMode = when {
                    theta < 90 && theta > -30 -> FACE_NUMBERS
                    theta <= -30 && theta > -150 -> FACE_LITE
                    else -> FACE_TOOL
                }

                val thetaStr = "%.2f".format(theta)

                info { "Theta: $thetaStr --> new face mode ${ ClockState.faceMode }" }

                PreferencesHelper.savePreferences(context)
                Utilities.redrawEverything()
                invalidate()
            }
            true
        }
        else -> true // super.onTouchEvent(event)
    }
}

