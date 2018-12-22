package org.dwallach.calwatch2

/**
 * A super-cheesy Either class, since that's what we need to be returning in some places.
 * Note the type restrictions that there's no way to have a possibly-null type parameter.
 */

interface Either<out A: Any, out B: Any> {
    fun isLeft(): Boolean
    fun isRight(): Boolean

}

private data class EitherImpl<out A: Any, out B: Any>(val left: A?, val right: B?): Either<A, B> {
    override fun isLeft() = left != null
    override fun isRight() = right != null
}

fun <A: Any, B: Any> eitherLeft(left: A): Either<A, B> = EitherImpl(left, null)
fun <A: Any, B: Any> eitherRight(right: B): Either<A, B> = EitherImpl(null, right)

fun <A: Any, B: Any, R> Either<A, B>.match(leftF: (A) -> R, rightF: (B) -> R): R = when {
    this is EitherImpl && left != null -> leftF(left)
    this is EitherImpl && right != null -> rightF(right)
    else -> throw RuntimeException("Something bad happened here")
}
