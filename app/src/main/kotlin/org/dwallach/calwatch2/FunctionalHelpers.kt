/*
 * CalWatch
 * Copyright (C) 2014-2018 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch2

/**
 * A super-cheesy Either class, since that's what we need to be returning in some places.
 * Note the type restrictions' use of Any vs. Any? -- when you make an [Either] using
 * [eitherLeft] or [eitherRight], you have to put in a non-null value. If you try to
 * get the [Either.left] or [Either.right] values out, the types will be possibly null.
 * [Either.match] solves the need to do null checking by encapsulating that with two
 * lambdas, one of which will be appropriate.
 */
interface Either<out A: Any, out B: Any> {
    val isLeft: Boolean
    val isRight: Boolean
    val left: A?
    val right: B?
}

private data class EitherImpl<out A: Any, out B: Any>(val l: A?, val r: B?): Either<A, B> {
    override val isLeft
        get() = l != null

    override val isRight
        get() = r != null

    override val left
        get() = l

    override val right
        get() = r
}

fun <A: Any, B: Any> eitherLeft(left: A): Either<A, B> = EitherImpl(left, null)
fun <A: Any, B: Any> eitherRight(right: B): Either<A, B> = EitherImpl(null, right)

inline fun <A: Any, B: Any, R> Either<A, B>.match(leftF: (A) -> R, rightF: (B) -> R): R = when {
    isLeft -> leftF(left as A)
    isRight -> rightF(right as B)
    else -> throw RuntimeException("Something bad happened here")
}

/**
 * Given any function from K to V, returns another function, also from K to V, which
 * memoizes the results, only calling the internal function exactly once for each input.
 */
fun <K, V: Any, F : (K) -> V> F.memoize(): (K) -> V {
    val map = mutableMapOf<K, V>()
    return {
        if (it in map)
            map[it] ?: kotlin.error("unexpected null from memoized function")
        else {
            val newV = this(it)
            map[it] = newV
            newV
        }
    }
}
