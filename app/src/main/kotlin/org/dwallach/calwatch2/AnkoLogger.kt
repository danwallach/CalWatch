/*
 * CalWatch
 * Copyright (C) 2014-2018 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch2

import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error

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

