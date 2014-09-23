package org.dwallach.calwatch;

import android.os.SystemClock;
import android.util.Log;

import java.util.List;

import EDU.Washington.grad.gjb.cassowary.*;

/**
 * new variant of event layout, this time with a full-blown constraint solver to make things
 * as pretty as possible
 */
public class EventLayoutUniform {
    private final static String TAG = "EventLayoutUniform";
    public final static int MAXLEVEL = 10000; // we'll go from 0 to MAXLEVEL, inclusive

    /**
     * Takes a list of calendar events and mutates their minLevel and maxLevel for calendar side-by-side
     * non-overlapping layout.
     *
     * @param events list of events
     * @return true if it worked, false if it failed
     */
    public static boolean go(List<EventWrapper> events) {
        int i, j, k, nEvents;
        int maxLevelAnywhere = 0;

        if (events == null) return true; // degenerate case, in which we trivially succeed
        nEvents = events.size();
        if (nEvents == 0) return true; // degenerate case, in which we trivially succeed

        int overlapCounter[] = new int[nEvents];

        for (i = 1; i < nEvents; i++) {
            EventWrapper e = events.get(i);

            // not sure this is necessary but it can't hurt
            e.setMinLevel(0);
            e.setMaxLevel(0);
            e.getPathCache().set(null);
        }

        long nanoStart = SystemClock.elapsedRealtimeNanos();

        try {
            ClSimplexSolver solver = new ClSimplexSolver();

            ClVariable[] startLevels = new ClVariable[nEvents];
            ClVariable[] sizes = new ClVariable[nEvents];

            ClLinearExpression sumSizes = new ClLinearExpression(0.0);

            j = 0;
            for (i = 0; i < nEvents; i++) {
                startLevels[i] = new ClVariable("start" + i);
                sizes[i] = new ClVariable("size" + i);

                // constraints: variables have to fit between 0 and max
                solver.addBounds(startLevels[i], 0, MAXLEVEL);
                solver.addBounds(sizes[i], 0, MAXLEVEL);

                // constraints: add them together and they're still constrained by MAXLEVEL
                ClLinearExpression levelPlusSize = new ClLinearExpression(startLevels[i]).plus(sizes[i]);
                ClLinearInequality liq = new ClLinearInequality(levelPlusSize, CL.LEQ, new ClLinearExpression(MAXLEVEL), ClStrength.required);
                solver.addConstraint(liq);

                sumSizes = sumSizes.plus(sizes[i]);
            }

            // constraint: the sum of all the sizes is greater than the maximum it could ever be under the absolute best of cases
            // (this constraint's job is to force us out of degenerate cases when the solver might prefer zeros everywhere)
            ClLinearInequality sumSizesEq = new ClLinearInequality(sumSizes, CL.GEQ, new ClLinearExpression(MAXLEVEL*nEvents), ClStrength.weak);
            solver.addConstraint(sumSizesEq);

            for (i = 0; i < nEvents; i++) {
                for (j = i + 1; j < nEvents; j++) {
                    if (events.get(i).overlaps(events.get(j))) {
                        overlapCounter[i]++;
                        overlapCounter[j]++;

                        // constraint: base level + its size < base level of next dependency
                        ClLinearExpression levelPlusSize = new ClLinearExpression(startLevels[i]).plus(sizes[i]);
                        ClLinearInequality liq = new ClLinearInequality(levelPlusSize, CL.LEQ, startLevels[j], ClStrength.required);
                        solver.addConstraint(liq);

                        // weak constraint: constrained segments should have the same size (0.5x weight of other weak constraints)
                        ClLinearEquation eqSize = new ClLinearEquation(sizes[i], new ClLinearExpression(sizes[j]), ClStrength.weak, 0.5);
                        solver.addConstraint(eqSize);
                    }
                }

                // stronger constraint: each block size is greater than 1/N of the size, for overlap of N
                // (turns out that this didn't change the results, but removing it sped things up significantly)
//                ClLinearInequality equalBlockSize = new ClLinearInequality(sizes[i], CL.GEQ, MAXLEVEL / (1+overlapCounter[i]), ClStrength.strong);
//                solver.addConstraint(equalBlockSize);
            }

            // and... away we go!
            solver.solve();

            Log.v(TAG, "solver completed!");

            for(i=0; i<nEvents; i++) {
                EventWrapper e = events.get(i);
                double start = startLevels[i].value();
                double size = sizes[i].value();

                e.setMinLevel((int) start);
                e.setMaxLevel((int) (start + size));
            }
        } catch (ExCLInternalError e) {
            Log.e(TAG, e.toString());
            return false;
        } catch (ExCLRequiredFailure e) {
            Log.e(TAG, e.toString());
            return false;
        } catch (ExCLNonlinearExpression e) {
            Log.e(TAG, e.toString());
            return false;
        } finally {
            long nanoStop = SystemClock.elapsedRealtimeNanos();
            Log.v(TAG, "Constraing solver, completed: " + (Double.toString((nanoStop - nanoStart) / 1000000.0)) + " ms");
        }

        return true;
    }
}
