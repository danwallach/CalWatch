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
 * Created by dwallach on 12/18/14.
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
