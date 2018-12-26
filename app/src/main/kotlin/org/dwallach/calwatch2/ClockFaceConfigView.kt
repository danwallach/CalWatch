/*
 * CalWatch
 * Copyright (C) 2014-2018 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch2

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.warn
import java.lang.ref.WeakReference
import java.util.*

class ClockFaceConfigView(context: Context, attrs: AttributeSet): View(context, attrs), AnkoLogger {
    val clockFace = ClockFace(true)
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

    companion object {
        private var viewRefMap = WeakHashMap<ClockFaceConfigView, Boolean>()

        // Finds all extant ClockFaceConfigViews and invalidates them all. On Wear 1.x, we
        // expect there to be exactly none of them. On Wear 2+, we're expecting at most
        // one at a time, but we're being slightly general-purpose here.
        fun redraw() = viewRefMap.keys.forEach { it.invalidate() }
    }
}