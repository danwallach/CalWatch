/*
 * CalWatch / CalWatch2
 * Copyright © 2014-2019 by Dan S. Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch2

import org.dwallach.complications.AnalogComplicationConfigRecyclerViewAdapter
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error

object Utilities : AnkoLogger {
    /**
     * This function, called from all over the place, is used to indicate that
     * every instance of a ClockFace (whether it's on the watchface or inside
     * the config panel) is now invalid and needs to be redrawn.
     */
    fun redrawEverything() {
        ClockFace.wipeAllCaches()
        AnalogComplicationConfigRecyclerViewAdapter.reloadAllToggles()
        ClockFaceConfigView.redraw()
        CalWatchFaceService.redraw()
    }
}

/**
 * Kludge to combine Kotlin's intrinsic [kotlin.error] with [AnkoLogger.error].
 * (Both have the same name, we want to call both.) AnkoLogger's version logs
 * the given message. Kotlin's version throws an [IllegalStateException]. We want both behaviors.
 * The optional Throwable parameter is used for AnkoLogger and is not rethrown.
 */
fun AnkoLogger.errorLogAndThrow(message: Any, thr: Throwable? = null): Nothing {
    // see also: https://discuss.kotlinlang.org/t/interaction-of-ankologger-error-and-kotlin-error/1508
    error(message, thr) // AnkoLogger
    kotlin.error(message)
}

/**
 * Given any function from K to V, returns another function, also from K to V, which
 * memoizes the results, only calling the internal function exactly once for each input.
 */
fun <K, V : Any, F : (K) -> V> F.memoize(): (K) -> V {
    val map = mutableMapOf<K, V>()
    return {
        map.getOrPut(it) { this(it) }
    }

    // Rant: Notice how we're constraining the V type parameter to be Any rather than
    // the most unconstrained Any? type? The Kotlin stdlib really should do something
    // similar but doesn't. Go look at the code for getOrPut(), and you'll see that
    // its V type can be anything. This means that they cannot differentiate the case
    // when the underlying hashmap returns null on a get() -- and therefore you need to
    // compute and store a new value -- versus the case when your function returned
    // null, and now you'll be recomputing it every time.

    // The root issue here is that when you use the Kotlin type system to manage nullity,
    // using null like it's Option.None and non-null like Option.Some, then you need
    // to make sure you never try to have a hashmap or whatever where the value type
    // is itself nullable.

    // Also, there's a secondary rant to be had here. Java8 defines Map.computeIfAbsent()
    // which does roughly the same thing as Kotlin's MutableMap.getOrPut(), except it's
    // somewhat more efficient. Here you're creating a new closure to send to getOrPut()
    // every time where Java can reuse the same lambda every time. This is all irrelevant
    // for how we're actually using F.memoize(), but it's the sort of API design issue
    // that could creep up on you if you weren't being careful. But even if we preferred
    // the Java8 function, it's not available for SDK23 and we're preferring Kotlin stdlib
    // functions whenever available.
}
