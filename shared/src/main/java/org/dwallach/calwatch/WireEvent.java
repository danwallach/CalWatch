/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch;

/**
 * Created by dwallach on 12/18/14.
 */
public final class WireEvent {
    public final Long startTime;
    public final Long endTime;
    public final Integer displayColor;

    public WireEvent(Long startTime, Long endTime, Integer displayColor) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.displayColor = displayColor;
    }
}
