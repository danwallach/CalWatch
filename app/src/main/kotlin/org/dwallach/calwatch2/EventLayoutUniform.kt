/*
 * CalWatch / CalWatch2
 * Copyright © 2014-2019 by Dan S. Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch2

import EDU.Washington.grad.gjb.cassowary.*
import org.jetbrains.anko.*

/**
 * Event layout with the Cassowary linear constraint solver.
 */
object EventLayoutUniform : AnkoLogger {
    /**
     * Given a list of events, return another list that corresponds to the set of
     * events visible in the next twelve hours, with events that would be off-screen
     * clipped to the 12-hour dial.
     */
    fun clipToVisible(events: List<CalendarEvent>): Pair<List<EventWrapper>, Int> {
        val gmtOffset = TimeWrapper.gmtOffset

        val localClipTime = TimeWrapper.localFloorHour
        val clipStartMillis = localClipTime - gmtOffset // convert from localtime back to GMT time for looking at events
        val clipEndMillis = clipStartMillis + 43200000  // 12 hours later

        val clippedEvents = events.map {
            it.clip(clipStartMillis, clipEndMillis)
        }.filter {
            // require events to be onscreen
            it.endTime > clipStartMillis && it.startTime < clipEndMillis &&
                // require events to not fill the full screen
                !(it.endTime == clipEndMillis && it.startTime == clipStartMillis) &&
                // require events to have some non-zero thickness (clipping can sometimes yield events that start and end at the same time)
                it.endTime > it.startTime
        }.map {
            // apply GMT offset, and then wrap with EventWrapper, where the layout will happen
            EventWrapper(it + gmtOffset)
        }

        // now, we run off and do screen layout
        val lMaxLevel: Int

        if (clippedEvents.isNotEmpty()) {
            // first, try the fancy constraint solver
            if (go(clippedEvents)) {
                // yeah, we succeeded
                lMaxLevel = MAXLEVEL
            } else {
                error("event layout failed!") // in years of testing, this failure apparently *never* happened
                return Pair(emptyList(), 0)
            }

            sanityTest(clippedEvents, lMaxLevel, "After new event layout")
            verbose { "maxLevel for visible events: $lMaxLevel" }
            verbose { "number of visible events: ${clippedEvents.size}" }

            return Pair(clippedEvents, lMaxLevel)
        } else {
            verbose("no events visible!")
            return Pair(emptyList(), 0)
        }
    }

    private const val MAXLEVEL = 10000 // we'll go from 0 to MAXLEVEL, inclusive

    /**
     * Takes a list of calendar events and mutates their minLevel and maxLevel for calendar side-by-side
     * non-overlapping layout.
     *
     * @param events list of events
     * @return true if it worked, false if it failed
     */
    private fun go(events: List<EventWrapper>): Boolean {
        info { "Running uniform event layout with %d events".format(events.size) }

        val nEvents = events.size
        if (nEvents == 0) return true // degenerate case, in which we trivially succeed

        val overlapCounter = IntArray(nEvents)

        events.forEach {
            // not sure this is necessary but it can't hurt
            it.minLevel = 0
            it.maxLevel = 0
            it.path = null
        }

        try {
            val solver = ClSimplexSolver()

            val startLevels = Array(nEvents) { ClVariable("start$it") }
            val sizes = Array(nEvents) { ClVariable("size$it") }

            var sumSizes = ClLinearExpression(0.0)

            for (i in 0 until nEvents) {
                // constraints: variables have to fit between 0 and max
                solver.addBounds(startLevels[i], 0.0, MAXLEVEL.toDouble())
                solver.addBounds(sizes[i], 0.0, MAXLEVEL.toDouble())

                // constraints: add them together and they're still constrained by MAXLEVEL
                val levelPlusSize = ClLinearExpression(startLevels[i]).plus(sizes[i])
                val liq = ClLinearInequality(
                    levelPlusSize,
                    CL.LEQ,
                    ClLinearExpression(MAXLEVEL.toDouble()),
                    ClStrength.required
                )
                solver.addConstraint(liq)

                sumSizes = sumSizes.plus(sizes[i])
            }

            // constraint: the sum of all the sizes is greater than the maximum it could ever be under the absolute best of cases
            // (this constraint's job is to force us out of degenerate cases when the solver might prefer zeros everywhere)
            val sumSizesEq = ClLinearInequality(
                sumSizes,
                CL.GEQ,
                ClLinearExpression((MAXLEVEL * nEvents).toDouble()),
                ClStrength.weak
            )
            solver.addConstraint(sumSizesEq)

            for (i in 0 until nEvents) {
                for (j in i + 1 until nEvents) {
                    if (events[i].overlaps(events[j])) {
                        overlapCounter[i]++
                        overlapCounter[j]++

                        // constraint: base level + its size < base level of next dependency
                        val levelPlusSize = ClLinearExpression(startLevels[i]).plus(sizes[i])
                        val liq = ClLinearInequality(levelPlusSize, CL.LEQ, startLevels[j], ClStrength.required)
                        solver.addConstraint(liq)

                        // weak constraint: constrained segments should have the same size (0.5x weight of other weak constraints)
                        // TODO introduce ratios here based on the time-duration of the event, so longer events are thinner than shorter ones
                        // -- doing this properly will change up the aesthetics a lot, so not something to be done casually.
                        val eqSize = ClLinearEquation(sizes[i], ClLinearExpression(sizes[j]), ClStrength.weak, 0.5)
                        solver.addConstraint(eqSize)
                    }
                }

                // stronger constraint: each block size is greater than 1/N of the size, for overlap of N
                // (turns out that this didn't change the results, but removing it sped things up significantly)
                //                ClLinearInequality equalBlockSize = new ClLinearInequality(sizes[i], CL.GEQ, MAXLEVEL / (1+overlapCounter[i]), ClStrength.strong)
                //                solver.addConstraint(equalBlockSize)
            }

            // and... away we go!
            solver.solve()

            verbose("Event layout success.")

            for (i in 0 until nEvents) {
                val e = events[i]
                val start = startLevels[i].value().toInt()
                val size = sizes[i].value().toInt()

                e.minLevel = start
                e.maxLevel = start + size
            }
        } catch (e: ExCLInternalError) {
            error("solver failed", e)
            return false
        } catch (e: ExCLRequiredFailure) {
            error("solver failed", e)
            return false
        } catch (e: ExCLNonlinearExpression) {
            error("solver failed", e)
            return false
        }

        return true
    }

    /**
     * Takes a list of calendar events and mutates their minLevel and maxLevel for calendar side-by-side
     * non-overlapping layout. Note that this class is obsoleted by EventLayoutUniform, which uses a
     * fancy simplex solver, but which might on rare occasions blow up. This class serves as our fallbaack.
     *
     * @param events list of events
     * @return maximum level of any calendar event
     */
    private fun sanityTest(events: List<EventWrapper>, maxLevel: Int, blurb: String) =
        events.forEach {
            if (it.minLevel < 0 || it.maxLevel > maxLevel) {
                error { "malformed eventwrapper ($blurb): $it" }
            }
        }
}
