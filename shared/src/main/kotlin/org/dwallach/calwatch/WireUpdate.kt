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
data class WireUpdate(val faceMode: Int, val showSecondHand: Boolean, val showDayDate: Boolean, val showStepCounter: Boolean) {

    fun toByteArray() = "$HEADER3;$faceMode;$showSecondHand;$showDayDate;$showStepCounter;$TRAILER".toByteArray()

    companion object: AnkoLogger {
        private const val HEADER2 = "CWDATA2" // corresponding to CalWatch release3-4xxx
        private const val HEADER3 = "CWDATA3" // corresponding to CalWatch release5xxx
        private const val TRAILER = "$"

        fun parseFrom(input: ByteArray): WireUpdate {
            val inputStr = String(input)
            verbose { "parseFrom: $inputStr" }

            val inputs = inputStr.split(";".toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()

            if (inputs.size == 5 && inputs[0] == HEADER2 && inputs[4] == TRAILER) {
                val result = WireUpdate(inputs[1].toInt(), inputs[2].toBoolean(), inputs[3].toBoolean(), Constants.DefaultShowStepCounter)
                verbose { "parsed: ${result.toString()}" }
                return result
            } else if (inputs.size == 6 && inputs[0] == HEADER3 && inputs[5] == TRAILER) {
                val result = WireUpdate(inputs[1].toInt(), inputs[2].toBoolean(), inputs[3].toBoolean(), inputs[4].toBoolean())
                verbose { "parsed: ${result.toString()}" }
                return result
            } else {
                // if we got a malformed message, then something really bad is happening; time for a kaboom
                errorLogAndThrow("Got bogus wire message: $inputStr")
            }
        }
    }
}
