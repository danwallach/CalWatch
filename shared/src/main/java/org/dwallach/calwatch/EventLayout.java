package org.dwallach.calwatch;

import android.content.Context;
import android.text.format.DateUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class EventLayout {
    private final static String TAG = "EventLayout";

    /**
     * Takes a list of calendar events and mutates their minLevel and maxLevel for calendar side-by-side
     * non-overlapping layout.
     * @param events list of events
     * @return maximum level of any calendar event
     */
    public static int go(List<EventWrapper> events) {
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

        int i, j, k, nEvents;
        int maxLevelAnywhere = 0;

        if(events == null) return 0;  // degenerate case, shouldn't happen
        nEvents = events.size();
        if(nEvents == 0) return 0;    // another degnerate case

        EventWrapper e = events.get(0); // first event
        boolean levelsFull[] = new boolean[nEvents+1];
        char printLevelsFull[] = new char[nEvents+1];
        e.setMinLevel(0);
        e.setMaxLevel(0);

        for(i=1; i<nEvents; i++) {
            e = events.get(i);

            // not sure this is necessary but it can't hurt
            e.setMinLevel(0);
            e.setMaxLevel(0);
            e.getPathCache().set(null);

            // clear the levels used mask
            for(j=0; j<= nEvents; j++) {
                levelsFull[j] = false;
                printLevelsFull[j] = '.';
            }

            // now fill out the levels based on events from [0, N-1]
            for(j=0; j<i; j++) {
                EventWrapper pe = events.get(j);

                // note: all of these loops for bit manipulation seem gratuitously inefficient and
                // if we really cared, we could probably just limit the world to 64 levels and do everything
                // with masked 64-bit bitvectors or something. As they say, premature optimization is
                // the root of all evil. So maybe later. Probably never.
                if(e.overlaps(pe)) {
                    int peMaxLevel = pe.getMaxLevel();
                    for (k = pe.getMinLevel(); k <= peMaxLevel; k++) {
                        levelsFull[k] = true;
                        printLevelsFull[k] = '@';
                    }
                }
            }
            levelsFull[maxLevelAnywhere+1] = true; // one extra one on the end to make the state machine below run cleanly

            // Log.v(TAG, "inserting event "+i+" (" + e.title +
            //        "), fullLevels(" + String.valueOf(printLevelsFull) +
            //        "), maxLevelAnywhere (" + maxLevelAnywhere + ")");


            // now, discover the first open hole, from the lowest level, then expand to fill
            // available space

            boolean searching = true;
            int holeStart = -1, holeEnd = -1;

            // note the <= here; we need to run one level beyond, to have the state machine
            // hit a slot that's full no matter what, so it always sorts out the best hole
            for(k=0; k<= maxLevelAnywhere; k++) {
                if(searching) {
                    if (!levelsFull[k]) {
                        // Log.v(TAG, "--> found start level: " + k);
                        searching = false;
                        holeStart = k;
                        holeEnd = k;
                    } // else {
                    // haven't found anything yet; keep searching
                    // }
                } else {
                    // onward we go!
                    if (!levelsFull[k]) {
                        holeEnd = k;
                        // Log.v(TAG, "--> expanded end level: "+ k);
                    }
                    // sad, this search is over
                    else {
                        // Log.v(TAG, "--> no further holes");
                        break;
                    }
                }
            }
            if(holeStart != -1) {
                // okay, we found a hole for the new event
                e.setMinLevel(holeStart);
                e.setMaxLevel(holeEnd);

                // Log.v(TAG, "--> hole found: (" + e.minLevel + "," + e.maxLevel + ")");
            } else {
                // Log.v(TAG, "--> adding a level");
                e.setMinLevel(maxLevelAnywhere + 1);
                e.setMaxLevel(maxLevelAnywhere + 1);

                // Sigh. Now we need to loop through all the previous events to see if
                // anybody else can expand out to occupy the new level
                for(j=0; j<i; j++) {
                    EventWrapper pe = events.get(j);
                    int peMaxLevel = pe.getMaxLevel();

                    if(!e.overlaps(pe) && peMaxLevel == maxLevelAnywhere) {
                        // Log.v(TAG, "=== expanding event " + j);
                        pe.setMaxLevel(peMaxLevel+1);
                    }
                }

                maxLevelAnywhere++;
            }
        }
        return maxLevelAnywhere;
    }

    public static void sanityTest(List<EventWrapper> events, int maxLevel, String blurb) {
        int  nEvents = events.size();

        for(int i=0; i<nEvents; i++) {
            EventWrapper e = events.get(i);
            if (e.getMinLevel() < 0 || e.getMaxLevel() > maxLevel) {
                Log.e(TAG, "malformed eventwrapper (" + blurb + "): " + e.toString());
            }
        }
    }
}
