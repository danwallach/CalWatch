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
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.MotionEvent.*
import android.view.View
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.warn
import java.util.*
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

class ClockFaceConfigView(context: Context, attrs: AttributeSet): View(context, attrs), AnkoLogger {
    private val clockFace = ClockFace(true)
    private val blackPaint = PaintCan.getCalendarGreyPaint(Color.BLACK)

    private var w: Int = -1
    private var h: Int = -1


    init {
        viewRefMap[this] = true
    }

    override fun onVisibilityChanged(changedView: View?, visibility: Int) = invalidate()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        this.w = w
        this.h = h

        info { "onSizeChanged: $w, $h" }
        clockFace.setSize(w, h)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
//        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        if (w == -1 || h == -1) {
            warn { "onDraw: no width or height yet!" }
            return
        }

        info { "onDraw: $w, $h" }

        // we don't want to clear everything, only the central circle
        canvas.drawCircle(w/2f, h/2f, w/2f, blackPaint)
        clockFace.drawBackgroundOnly(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent) = when (event.action) {
        ACTION_UP, ACTION_BUTTON_RELEASE, ACTION_POINTER_UP -> {
            val rawx = event.x
            val rawy = event.y
            info { String.format("onTouchEvent: %.1f, %.1f", rawx, rawy) }

            if (h == -1 || w == -1) {
                warn("We don't know the real screen size yet, bailing!")
                super.onTouchEvent(event)
            } else {
                val y = (h / 2) - rawy
                val x = rawx - ( w / 2 )
                info { String.format("onTouchEvent: %.1f, %.1f --> %.1f, %.1f", rawx, rawy, x, y) }

                // theta ranges from -180 to 180 (degrees)
                val theta = atan2(y, x) * 180.0 / PI
                val radius = sqrt(x * x + y * y) / (w / 2) // as a fraction of total width

                if ((theta > 135 || theta < -135) && radius > 0.2) { // left quarter of watchface
                    ClockState.showDayDate = !ClockState.showDayDate
                    PreferencesHelper.savePreferences(context)
                    Utilities.redrawEverything()
                }
            }
            true
        }
        else -> true // super.onTouchEvent(event)
    }

    companion object {
        private var viewRefMap = WeakHashMap<ClockFaceConfigView, Boolean>()

        // Finds all extant ClockFaceConfigViews and invalidates them all. On Wear 1.x, we
        // expect there to be exactly none of them. On Wear 2+, we're expecting at most
        // one at a time, but we're being slightly general-purpose here.
        fun redraw() = viewRefMap.keys.forEach { it.invalidate() }
    }
}