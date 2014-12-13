/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch;

import android.graphics.Paint;
import android.util.Log;
import android.util.SparseArray;

/**
 * Cheesy helper for getting Paint values for calendar events and making sure we don't allocate
 * the same color twice.
 * Created by dwallach on 8/15/14.
 */
public class PaintCan {
    private static SparseArray<Paint> map = null;
    private final static String TAG = "PaintCan";

    public static Paint getPaint(int argb) {
        Paint retPaint;
        Integer argbInt = argb;

        if(map == null)
            map = new SparseArray<Paint>();

        retPaint = map.get(argbInt);
        if(retPaint == null) {
            retPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            retPaint.setStrokeJoin(Paint.Join.BEVEL);
            retPaint.setColor(argb);
            retPaint.setStyle(Paint.Style.FILL);

            map.put(argbInt, retPaint);

        }
        // Log.v(TAG, "get paint: " + Integer.toHexString(argb));

        return retPaint;
    }

    public static Paint getGreyPaint(int argb) {
        int a =  argb & 0xff000000;
        int r = (argb & 0xff0000) >> 16;
        int g = (argb & 0xff00) >> 8;
        int b = argb & 0xff;

        int grey = (r+g+b) / 3;

        return getPaint(a | (grey<<16) | (grey << 8) | grey);
    }
}
