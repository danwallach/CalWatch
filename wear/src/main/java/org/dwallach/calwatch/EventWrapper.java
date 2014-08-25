package org.dwallach.calwatch;

import android.graphics.Paint;

import org.dwallach.calwatch.proto.WireEvent;

/**
 * Created by dwallach on 8/25/14.
 */
public class EventWrapper {
    private WireEvent wireEvent;
    private PathCache pathCache;
    private Paint paint;

    public EventWrapper(WireEvent wireEvent) {
        this.wireEvent = wireEvent;
        this.pathCache = new PathCache();
        this.paint = PaintCan.getPaint(wireEvent.eventColor);
    }

    public WireEvent getWireEvent() {
        return wireEvent;
    }

    public PathCache getPathCache() {
        return pathCache;
    }

    public Paint getPaint() {
        return paint;
    }
}
