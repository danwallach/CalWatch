/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch;

import android.util.Log;

import java.io.IOException;

/**
 * This data structure deals with serializing and deserializing all the state that we want to
 * get from the phone to the watch. Originally, when we had to send the entire serialized calendar,
 * we used protobufs, which was total overkill with later versions of Wear, where Google dealt
 * with moving the calendars around. We simplified down to this business. We could probably
 * go a step further and replace this with distinct entries in the DataMap, but we might want
 * to add more things later on, so it's easiest to just keep this around. It's expansion room
 * for later on.
 */
public class WireUpdate {
    private static final String TAG = "WireUpdate";

    public final int faceMode;
    public final boolean showSecondHand;
    public final boolean showDayDate;

    public WireUpdate(int faceMode, boolean showSecondHand, boolean showDayDate) {
        this.faceMode = faceMode;
        this.showSecondHand = showSecondHand;
        this.showDayDate = showDayDate;
    }


    private static final String HEADER = "CWDATA2";
    private static final String TRAILER = "$";

    public byte[] toByteArray() {
        String output =  HEADER + ";" +
                         faceMode + ";" +
                         showSecondHand + ";" +
                         showDayDate + ";" +
                         TRAILER;
        return output.getBytes();
    }

    public static WireUpdate parseFrom(byte[] input) throws IOException {
        String inputStr = new String(input);
        Log.v(TAG, "parseFrom: " + inputStr);
        String[] inputs = inputStr.split(";");

        if(inputs.length == 5 && inputs[0].equals(HEADER) && inputs[4].equals(TRAILER)) {
            WireUpdate result =  new WireUpdate(Integer.parseInt(inputs[1]),
                                                Boolean.parseBoolean(inputs[2]),
                                                Boolean.parseBoolean(inputs[3]));
            Log.v(TAG, "parsed: " + result.toString());
            return result;
        } else {
            // got something bogus
            throw new IOException("Got bogus wire message: " + inputStr);
        }
    }

    public String toString() {
        return "facemode(" + faceMode + "), showSecondHand(" + showSecondHand + "), showDayDate(" + showDayDate + ")";
    }
}
