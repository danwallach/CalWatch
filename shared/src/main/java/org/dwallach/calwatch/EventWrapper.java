/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch;

import android.graphics.Paint;

/**
 * Created by dwallach on 8/25/14.
 */
public class EventWrapper {
    private final static String TAG = "EventWrapper";
    private WireEvent wireEvent;
    private PathCache pathCache;
    private Paint paint, greyPaint, lowBitPaint;
    private int minLevel, maxLevel;


    public EventWrapper(WireEvent wireEvent) {
        this.wireEvent = wireEvent;
        this.pathCache = new PathCache();
        this.paint = PaintCan.getCalendarPaint(wireEvent.displayColor);
        this.greyPaint = PaintCan.getCalendarGreyPaint(wireEvent.displayColor);
        this.lowBitPaint = PaintCan.get(true, true, PaintCan.colorBlackFill);
        this.minLevel = this.maxLevel = 0;  // fill this in later on...
    }

    public WireEvent getWireEvent() {
        return wireEvent;
    }

    public PathCache getPathCache() {
        return pathCache;
    }

    public Paint getPaint(boolean ambientLowBit, boolean ambientMode) {
        if(ambientMode)
            if(ambientLowBit)
                return lowBitPaint;
            else
                return greyPaint;
        else
            return paint;
    }

    public int getMinLevel() { return minLevel; }

    public void setMinLevel(int minLevel) { this.minLevel = minLevel; }

    public int getMaxLevel() { return maxLevel; }

    public void setMaxLevel(int maxLevel) { this.maxLevel = maxLevel; }

    public boolean overlaps(EventWrapper e) {
        return this.wireEvent.startTime < e.wireEvent.endTime && e.wireEvent.startTime < this.wireEvent.endTime;
    }

    public String toString() {
        return wireEvent.startTime + "->" + wireEvent.endTime + " color(" + Integer.toHexString(wireEvent.displayColor) + "), levels(" + minLevel + "," + maxLevel + ")";
    }
}
