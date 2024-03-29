/*
 * CalWatch / CalWatch2
 * Copyright © 2014-2022 by Dan S. Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_BUTTON_RELEASE
import android.view.MotionEvent.ACTION_POINTER_UP
import android.view.MotionEvent.ACTION_UP
import android.view.View
import java.util.WeakHashMap
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

private val TAG = "ClockFaceConfigView"

/**
 * As part of our on-watch configuration dialog, the easiest way to make a background image
 * was to just use our existing [ClockFace] code within an Android [View] that can appear
 * in any layout.
 */
class ClockFaceConfigView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val clockFace = ClockFace(true)
    private val blackPaint = PaintCan.getCalendarGreyPaint(Color.BLACK)

    private var w: Int = -1
    private var h: Int = -1

    init {
        viewRefMap[this] = true
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        this.w = w
        this.h = h

        Log.i(TAG, "onSizeChanged: $w, $h")
        clockFace.setSize(w, h)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
//        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        if (w == -1 || h == -1) {
            Log.w(TAG, "onDraw: no width or height yet!")
            return
        }

        Log.i(TAG, "onDraw: $w, $h")

        // We don't want to clear everything, only the central circle.
        // Leaves the background color of the configuration panel alone,
        // which is typically *not* black.
        canvas.drawCircle(w / 2f, h / 2f, w / 2f, blackPaint)

        // Doesn't draw the full watchface -- no hands, no calendar wedges --
        // only the background style, since that's all we want in the configuration
        // dialog panel.
        clockFace.drawBackgroundOnly(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent) = when (event.action) {
        ACTION_UP, ACTION_BUTTON_RELEASE, ACTION_POINTER_UP -> {
            val rawx = event.x
            val rawy = event.y
            Log.i(TAG, "onTouchEvent: %.1f, %.1f".format(rawx, rawy))

            if (h == -1 || w == -1) {
                Log.w(TAG, "We don't know the real screen size yet, bailing!")
                super.onTouchEvent(event)
            } else {
                val y = (h / 2) - rawy
                val x = rawx - (w / 2)
                Log.i(TAG, "onTouchEvent: %.1f, %.1f --> %.1f, %.1f".format(rawx, rawy, x, y))

                // theta ranges from -180 to 180 (degrees)
                val theta = atan2(y, x) * 180.0 / PI
                val radius = sqrt(x * x + y * y) / (w / 2) // as a fraction of total width

                // We only care about clicks in the left quarter of the watchface, which
                // we'll interpret as a request to toggle our built-in day/date complication.
                // This widget will be overlaid with other buttons to deal with the WearOS
                // complication system and they'll deal with their own click events.
                if ((theta > 135 || theta < -135) && radius > 0.2) {
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
