/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.util.Log

import java.io.IOException

/**
 * This data structure deals with serializing and deserializing all the state that we want to
 * get from the phone to the watch. Originally, when we had to send the entire serialized calendar,
 * we used protobufs, which was total overkill with later versions of Wear, where Google dealt
 * with moving the calendars around. We simplified down to this business. We could probably
 * go a step further and replace this with distinct entries in the DataMap, but we might want
 * to add more things later on, so it's easiest to just keep this around. It's expansion room
 * for later on.
 */
data class WireUpdate(val faceMode: Int, val showSecondHand: Boolean, val showDayDate: Boolean) {

    fun toByteArray() = "$HEADER;$faceMode;$showSecondHand;$showDayDate;$TRAILER".toByteArray()

    companion object {
        private const val TAG = "WireUpdate"


        private const val HEADER = "CWDATA2"
        private const val TRAILER = "$"

        @Throws(IOException::class)
        fun parseFrom(input: ByteArray): WireUpdate {
            val inputStr = String(input)
            Log.v(TAG, "parseFrom: $inputStr")
            val inputs = inputStr.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            if (inputs.size == 5 && inputs[0] == HEADER && inputs[4] == TRAILER) {
                val result = WireUpdate(Integer.parseInt(inputs[1]),
                        java.lang.Boolean.parseBoolean(inputs[2]),
                        java.lang.Boolean.parseBoolean(inputs[3]))
                Log.v(TAG, "parsed: ${result.toString()}")
                return result
            } else error("Got bogus wire message: $inputStr")
        }
    }
}
