package org.dwallach.calwatch

import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error

/**
 * Kludge to combine Kotlin's intrinsic error function with AnkoLogger's
 * logging function of the same name. This first logs an error via AnkoLogger,
 * then throw an IllegalStateException with the same message via kotlin.error. The optional
 * Throwable parameter is used for AnkoLogger and is not rethrown.
 * @see AnkoLogger.error
 */
fun AnkoLogger.errorLogAndThrow(message: Any, thr: Throwable? = null): Nothing {
    // see also: https://discuss.kotlinlang.org/t/interaction-of-ankologger-error-and-kotlin-error/1508
    error(message, thr) // AnkoLogger
    kotlin.error(message)
}
