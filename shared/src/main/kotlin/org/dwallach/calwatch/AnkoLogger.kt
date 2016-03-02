package org.dwallach.calwatch

import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error

/**
 * Kludge to combine Kotlin's intrinsic error function with AnkoLogger's
 * logging function of the same name. Expected use is that you throw
 * the result of calling this function.
 */
fun AnkoLogger.errorAndLog(message: Any, thr: Throwable? = null): Throwable {
    error(message, thr) // AnkoLogger

    // we really would rather just call kotlin.error(message), but that
    // has magical properties where the caller will infer that control flow
    // isn't coming back, and we can't reflect those magical properties to
    // our own caller.

    return if(thr == null) IllegalStateException(message.toString()) else thr;
}
