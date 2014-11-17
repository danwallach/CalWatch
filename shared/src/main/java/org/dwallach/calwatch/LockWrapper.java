/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * There are times when the drawing thread and the UI thread are stomping on each other, resulting in ANRs and
 * other associated badness. The solution? It hurts, but it's one great big lock. Yes this forces a lack of
 * concurrency in many things. Too bad.
 */
public class LockWrapper {
    // Note the use of ReentrantLock, which ensures that if a single thread calls lock() more than once,
    // everything will work as expected -- an internal counter goes up by one and will go down when that
    // same thread calls unlock(). This is also the behavior of Java's default lock, that you get with
    // synchronized(), but here we've got method bodies that we can annotate with other desired behaviors
    // later on if things are getting wonky.
    private static Lock lock = new ReentrantLock();

    public static void lock() {
        lock.lock();
    }

    public static void unlock() {
        lock.unlock();
    }
}
