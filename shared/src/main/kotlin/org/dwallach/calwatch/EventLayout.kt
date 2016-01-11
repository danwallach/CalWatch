/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.util.Log


object EventLayout {
    private const val TAG = "EventLayout"

    /**
     * Takes a list of calendar events and mutates their minLevel and maxLevel for calendar side-by-side
     * non-overlapping layout. Note that this class is obsoleted by EventLayoutUniform, which uses a
     * fancy simplex solver, but which might on rare occasions blow up. This class serves as our fallbaack.
     *
     * @param events list of events
     * @return maximum level of any calendar event
     */
    fun go(events: List<EventWrapper>): Int {
        Log.i(TAG, "Running event layout with %d events".format(events.size))

        // We're going to execute a greedy O(n^2) algorithm that runs like this:

        // Levels go from 0 to MAXINT. Every event will have a minLevel and maxLevelAnywhere. In the
        // degenerate case, with a single event, it will go from level 0 to 0.

        // Algorithm is iterative. Assume that entries [0, N-1] are laid out properly with
        // their levels such that the boxes don't overlap.

        // When considering entry N, we have to query each of the [0, N-1] entries that overlap with it
        // and take the *union* of every level that's occupied in that set. If there's something absent
        // from that union, that means we can shove the new event into that hole, and we'll have a preference
        // for the largest contiguous hole.

        // Otherwise, we need a new level. The new event gets that level all to itself, initially.
        // Then, we need to make another pass on the [0,N-1] prior events. Any of them which occupies
        // the previous top level *and* doesn't overlap with the new event can expand by one to occupy
        // the new space.

        // (In degenerate cases, this could actually get to O(n^3). Highly unlikely.)

        var i: Int
        var j: Int
        var k: Int
        val nEvents: Int
        var maxLevelAnywhere = 0

        nEvents = events.size
        if (nEvents == 0) return 0    // another degnerate case

        var e = events[0] // first event
        val levelsFull = BooleanArray(nEvents + 1)
        val printLevelsFull = CharArray(nEvents + 1)
        e.minLevel = 0
        e.maxLevel = 0

        i = 1
        while (i < nEvents) {
            e = events[i]

            // not sure this is necessary but it can't hurt
            e.minLevel = 0
            e.maxLevel = 0
            e.path = null

            // clear the levels used mask
            j = 0
            while (j <= nEvents) {
                levelsFull[j] = false
                printLevelsFull[j] = '.'
                j++
            }

            // now fill out the levels based on events from [0, N-1]
            j = 0
            while (j < i) {
                val pe = events[j]

                // note: all of these loops for bit manipulation seem gratuitously inefficient and
                // if we really cared, we could probably just limit the world to 64 levels and do everything
                // with masked 64-bit bitvectors or something. As they say, premature optimization is
                // the root of all evil. So maybe later. Probably never.
                if (e.overlaps(pe)) {
                    val peMaxLevel = pe.maxLevel
                    k = pe.minLevel
                    while (k <= peMaxLevel) {
                        levelsFull[k] = true
                        printLevelsFull[k] = '@'
                        k++
                    }
                }
                j++
            }
            levelsFull[maxLevelAnywhere + 1] = true // one extra one on the end to make the state machine below run cleanly

            // Log.v(TAG, "inserting event "+i+" (" + e.title +
            //        "), fullLevels(" + String.valueOf(printLevelsFull) +
            //        "), maxLevelAnywhere (" + maxLevelAnywhere + ")");


            // now, discover the first open hole, from the lowest level, then expand to fill
            // available space

            var searching = true
            var holeStart = -1
            var holeEnd = -1

            // note the <= here; we need to run one level beyond, to have the state machine
            // hit a slot that's full no matter what, so it always sorts out the best hole
            k = 0
            while (k <= maxLevelAnywhere) {
                if (searching) {
                    if (!levelsFull[k]) {
                        // Log.v(TAG, "--> found start level: " + k);
                        searching = false
                        holeStart = k
                        holeEnd = k
                    } // else {
                    // haven't found anything yet; keep searching
                    // }
                } else {
                    // onward we go!
                    if (!levelsFull[k]) {
                        holeEnd = k
                        // Log.v(TAG, "--> expanded end level: "+ k);
                    } else {
                        // Log.v(TAG, "--> no further holes");
                        break
                    }// sad, this search is over
                }
                k++
            }
            if (holeStart != -1) {
                // okay, we found a hole for the new event
                e.minLevel = holeStart
                e.maxLevel = holeEnd

                // Log.v(TAG, "--> hole found: (" + e.minLevel + "," + e.maxLevel + ")");
            } else {
                // Log.v(TAG, "--> adding a level");
                e.minLevel = maxLevelAnywhere + 1
                e.maxLevel = maxLevelAnywhere + 1

                // Sigh. Now we need to loop through all the previous events to see if
                // anybody else can expand out to occupy the new level
                j = 0
                while (j < i) {
                    val pe = events[j]
                    val peMaxLevel = pe.maxLevel

                    if (!e.overlaps(pe) && peMaxLevel == maxLevelAnywhere) {
                        // Log.v(TAG, "=== expanding event " + j);
                        pe.maxLevel = peMaxLevel + 1
                    }
                    j++
                }

                maxLevelAnywhere++
            }
            i++
        }
        return maxLevelAnywhere
    }

    fun sanityTest(events: List<EventWrapper>, maxLevel: Int, blurb: String) {
        val nEvents = events.size

        for (i in 0..nEvents - 1) {
            val e = events[i]
            if (e.minLevel < 0 || e.maxLevel > maxLevel) {
                Log.e(TAG, "malformed eventwrapper (" + blurb + "): " + e.toString())
            }
        }
    }
}
