/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

/**
 * This data structure handles a given calendar event. At one point in history, we were sending
 * these things from the phone to the watch, but that's no longer necessary as it's available
 * locally.
 */
data class WireEvent(val startTime: Long, val endTime: Long, val displayColor: Int)
