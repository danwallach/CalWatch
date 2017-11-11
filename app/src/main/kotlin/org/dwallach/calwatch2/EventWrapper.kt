/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch2

import android.graphics.Path

/**
 * This data structure contains each calendar event. We only care about a handful of fields.
 * It's separate from EventWrapper because we might want to save these things for later, while the
 * wrapper parts are easily reconstructed. We used to send them from phone to watch, but the
 * data is all now available locally.
 */
data class CalendarEvent(val startTime: Long, val endTime: Long, val displayColor: Int)

/**
 * This class wraps a calendar event with a number of other fields.
 */
class EventWrapper(val calendarEvent: CalendarEvent) {
    /**
     * The first time this event is rendered, it will be rendered to a Path, so subsequent calls to
     * render it will go much faster.
     */
    var path: Path? = null
    private val paint = PaintCan.getCalendarPaint(calendarEvent.displayColor)
    private val greyPaint = PaintCan.getCalendarGreyPaint(calendarEvent.displayColor)
    private val lowBitPaint = PaintCan[PaintCan.STYLE_LOWBIT, PaintCan.COLOR_LOWBIT_CALENDAR_FILL]
    var minLevel: Int = 0
    var maxLevel: Int = 0

    fun getPaint(drawStyle: Int) = when(drawStyle) {
        PaintCan.STYLE_ANTI_BURNIN,PaintCan.STYLE_LOWBIT -> lowBitPaint // for now anyway, we're behaving identically for lowBit and for burnInProtection
        PaintCan.STYLE_AMBIENT -> greyPaint
        else -> paint
    }

    fun overlaps(e: EventWrapper) =
            this.calendarEvent.startTime < e.calendarEvent.endTime && e.calendarEvent.startTime < this.calendarEvent.endTime

    override fun toString() =
            "%d -> %d, color(%08x), levels(%d,%d)"
                    .format(calendarEvent.startTime, calendarEvent.endTime, calendarEvent.displayColor, minLevel, maxLevel)
}
