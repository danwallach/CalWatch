package org.dwallach.calwatch;

import android.os.SystemClock;
import android.util.Log;

import java.util.List;
import org.jacop.core.*;
import org.jacop.constraints.*;
import org.jacop.search.*;

/**
 * new variant of event layout, this time with a full-blown constraint solver to make things
 * as pretty as possible
 */
public class EventLayoutUniform {
    private final static String TAG = "EventLayoutUniform";
    private final static int MAXLEVEL = 10000; // we'll go from 0 to MAXLEVEL, inclusive

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
            e.setLocalID(i);
        }

        long nanoStart = SystemClock.elapsedRealtimeNanos();

        Store store = new Store();
        IntVar[] startLevels = new IntVar[nEvents];
        IntVar[] sizes = new IntVar[nEvents];
        IntVar[] allVars = new IntVar[nEvents*2];
        BoundDomain bound = new BoundDomain(0, MAXLEVEL);
        IntVar cost = new IntVar(store, "cost", 0, MAXLEVEL*nEvents);
        IntVar maxSum = new IntVar(store, "maxSum", 0, MAXLEVEL*nEvents);

        j=0;
        for(i=0; i<nEvents; i++) {
            startLevels[i] = new IntVar(store, "startLevel" + i, bound);
            sizes[i] = new IntVar(store, "size" + i, bound);
            allVars[j++] = startLevels[i];
            allVars[j++] = sizes[i];
        }

        for(i=0; i<nEvents; i++) {
            // constraint: base level + its size is bounded by MAXLEVEL
            IntVar[] v = {startLevels[i], sizes[i]};
            store.impose(new Linear(store, v, new int[] {1,1}, "<=", MAXLEVEL));

            for (j = i + 1; j < nEvents; j++)
                if (events.get(i).overlaps(events.get(j))) {
                    // constraint: base level + its size < next level's start
                    // (note the use of the negative weight to implement this)
                    IntVar[] vv = {startLevels[i], sizes[i], startLevels[j]};
                    store.impose(new Linear(store, vv, new int[] {1, 1, -1}, "<=", 0));

                    // cost optimization: minimize differences of widths when there's
                    // a dependency
                    store.impose(new Distance(sizes[i], sizes[j], cost));
                }
        }

        store.impose(new Sum())

        if(!store.consistency())
            Log.v(TAG, "inconsistent constraints, no solution?!");

        Search<IntVar> label = new DepthFirstSearch<IntVar>();
        SelectChoicePoint<IntVar> select = new SimpleSelect<IntVar>(allVars,
                new SmallestDomain<IntVar>(),
                new IndomainMax<IntVar>());

        boolean result = label.labeling(store, select, cost);


        long nanoStop = SystemClock.elapsedRealtimeNanos();

        Log.v(TAG, "Constraing solver, completed: " + (Double.toString((nanoStop - nanoStart) / 1000000.0)) + " ms");

        return maxLevelAnywhere;
    }
}
