package org.dwallach.calwatch;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * There are times when the drawing thread and the UI thread are stomping on each other, resulting in ANRs and
 * other associated badness. The solution? It hurts, but it's one great big lock. Yes this forces a lack of
 * concurrency in many things. Too bad.
 */
public class LockWrapper {
    private static Lock lock = new ReentrantLock();

    public static void lock() {
        lock.lock();
    }

    public static void unlock() {
        lock.unlock();
    }
}
