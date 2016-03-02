/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import org.jetbrains.anko.*

/**
 * This data structure deals with serializing and deserializing all the state that we want to
 * get from the phone to the watch. Originally, when we had to send the entire serialized calendar,
 * we used protobufs, which was total overkill with later versions of Wear, where Google dealt
 * with moving the calendars around. We simplified down to this business. We could probably
 * go a step further and replace this with distinct entries in the DataMap, but we might want
 * to add more things later on, so it's easiest to just keep this around.
 */
data class WireUpdate(val faceMode: Int, val showSecondHand: Boolean, val showDayDate: Boolean) {

    fun toByteArray() = "$HEADER;$faceMode;$showSecondHand;$showDayDate;$TRAILER".toByteArray()

    companion object: AnkoLogger {
        private const val HEADER = "CWDATA2"
        private const val TRAILER = "$"

        fun parseFrom(input: ByteArray): WireUpdate {
            val inputStr = String(input)
            verbose { "parseFrom: $inputStr" }

            val inputs = inputStr.split(";".toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()

            if (inputs.size == 5 && inputs[0] == HEADER && inputs[4] == TRAILER) {
                val result = WireUpdate(inputs[1].toInt(), inputs[2].toBoolean(), inputs[3].toBoolean())
                verbose { "parsed: ${result.toString()}" }
                return result
            } else {
                // if we got a malformed message, then something really bad is happening; time for a kaboom
                throw errorAndLog("Got bogus wire message: $inputStr")
            }
        }
    }
}
