package org.dwallach.calwatch;

import java.util.List;

/**
 * new variant of event layout, this time with a full-blown constraint solver to make things
 * as pretty as possible
 */
public class EventLayoutUniform {
    private final static String TAG = "EventLayoutUniform";

    /**
     * Takes a list of calendar events and mutates their minLevel and maxLevel for calendar side-by-side
     * non-overlapping layout.
     *
     * @param events list of events
     * @return maximum level of any calendar event
     */
    public static int go(List<EventWrapper> events) {
        int i, j, k, nEvents;
        int maxLevelAnywhere = 0;

        if (events == null) return 0;  // degenerate case, shouldn't happen
        nEvents = events.size();
        if (nEvents == 0) return 0;    // another degnerate case

        for (i = 1; i < nEvents; i++) {
            EventWrapper e = events.get(i);

            // not sure this is necessary but it can't hurt
            e.setMinLevel(0);
            e.setMaxLevel(0);
            e.getPathCache().set(null);
        }

        return maxLevelAnywhere;
    }
}
