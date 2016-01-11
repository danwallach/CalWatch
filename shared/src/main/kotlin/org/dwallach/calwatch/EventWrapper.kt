/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.graphics.Paint
import android.graphics.Path

class EventWrapper(val wireEvent: WireEvent) {
    /**
     * The first time this event is rendered, it will be rendered to a Path, so subsequent calls to
     * render it will go much faster.
     */
    var path: Path? = null
    private val paint: Paint
    private val greyPaint: Paint
    private val lowBitPaint: Paint
    var minLevel: Int = 0
    var maxLevel: Int = 0


    init {
        this.paint = PaintCan.getCalendarPaint(wireEvent.displayColor)
        this.greyPaint = PaintCan.getCalendarGreyPaint(wireEvent.displayColor)
        this.lowBitPaint = PaintCan[true, true, PaintCan.colorBlackFill]
        this.minLevel = 0
        this.maxLevel = 0  // fill this in later on...
    }

    fun getPaint(ambientLowBit: Boolean, ambientMode: Boolean): Paint {
        if (ambientMode)
            if (ambientLowBit)
                return lowBitPaint
            else
                return greyPaint
        else
            return paint
    }

    fun overlaps(e: EventWrapper): Boolean {
        return this.wireEvent.startTime < e.wireEvent.endTime && e.wireEvent.startTime < this.wireEvent.endTime
    }

    override fun toString(): String {
        return "%d -> %d, color(%08x), levels(%d,%d)".format(wireEvent.startTime, wireEvent.endTime, wireEvent.displayColor, minLevel, maxLevel)
    }
}
