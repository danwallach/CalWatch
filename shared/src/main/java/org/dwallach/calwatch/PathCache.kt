/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.graphics.Path

/**
 * Used to hold onto paths between runs, for make benefit much efficiency.
 */
class PathCache {
    private var cache: Path? = null
    fun get(): Path? {
        return cache
    }

    fun set(p: Path?) {
        cache = p
    }
}
