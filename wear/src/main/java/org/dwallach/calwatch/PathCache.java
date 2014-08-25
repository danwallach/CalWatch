package org.dwallach.calwatch;

import android.graphics.Path;

/**
 * Created by dwallach on 8/25/14.
 */
public class PathCache {
    private Path cache = null;
    public PathCache() {  }
    public Path get() { return cache; }
    public void set(Path p) { cache = p; }
}
