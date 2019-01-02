/*
 * CalWatch
 * Copyright (C) 2014-2018 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch2

import android.graphics.Path

/**
 * This data structure contains each calendar event. We only care about a handful of fields.
 * It's separate from [EventWrapper] because we might want to save these things for later, while the
 * wrapper parts are easily reconstructed. We used to send them from phone to watch, but the
 * data is all now available locally.
 *
 * Note that all times are in *milliseconds*, as returned by various Android time functions.
 * See also [TimeWrapper].
 */
data class CalendarEvent(val startTime: Long, val endTime: Long, val displayColor: Int) {
    operator fun plus(offset: Long) =
        CalendarEvent(startTime + offset, endTime + offset, displayColor)

    operator fun plus(offset: Int) = plus(offset.toLong())

    fun clip(clipStart: Long, clipEnd: Long) = CalendarEvent(
            startTime = if(startTime < clipStart) clipStart else startTime,
            endTime = if(endTime > clipEnd) clipEnd else endTime,
            displayColor = displayColor)
}

/**
 * This class wraps a calendar event with a number of other fields.
 */
class EventWrapper(val calendarEvent: CalendarEvent) {
    /**
     * The first time this event is rendered, it will be rendered to a Path, so subsequent calls to
     * render it will go much faster.
     */
    var path: Path? = null
    val paint = PaintCan.getCalendarPaint(calendarEvent.displayColor)
    var minLevel: Int = 0
    var maxLevel: Int = 0

    fun overlaps(e: EventWrapper) =
            this.calendarEvent.startTime < e.calendarEvent.endTime && e.calendarEvent.startTime < this.calendarEvent.endTime

    override fun toString() =
            "%d -> %d, color(%08x), levels(%d,%d)"
                    .format(calendarEvent.startTime, calendarEvent.endTime, calendarEvent.displayColor, minLevel, maxLevel)
}
