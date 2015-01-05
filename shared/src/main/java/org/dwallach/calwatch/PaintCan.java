/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch;

import android.graphics.Color;
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

    public static Paint getCalendarPaint(int argb) {
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

    private static Paint palette[][];

    public static final int styleNormal = 0;
    public static final int styleAmbient = 1;
    public static final int styleLowBit = 2;
    public static final int styleMax = 2;

    private static final float gamma = 2.2f;

    private static Paint getPaint(int argb, int style, float textSize, float strokeWidth) {
        Paint retPaint = new Paint(Paint.SUBPIXEL_TEXT_FLAG | Paint.HINTING_ON);
        retPaint.setAntiAlias(true);
        retPaint.setStrokeCap(Paint.Cap.SQUARE);
        retPaint.setStrokeWidth(strokeWidth);
        retPaint.setTextSize(textSize);
        retPaint.setStyle(Paint.Style.FILL);
        retPaint.setTextAlign(Paint.Align.CENTER);

        switch(style) {
            case styleNormal:
                retPaint.setColor(argb);
                break;

            case styleLowBit:
                if((argb & 0xffffff) > 0)
                    retPaint.setColor(0xffffffff);
                else
                    retPaint.setColor(0xff000000);
                retPaint.setAntiAlias(false);
                break;

            case styleAmbient:
                retPaint.setColor(argbToGrey(argb));
                break;

            default:
                Log.e(TAG, "getPaint: unknown style: " + style);
                break;
        }

        return retPaint;
    }

    public static final int colorBatteryLow = 0;
    public static final int colorBatteryCritical = 1;
    public static final int colorSecondHand = 2;
    public static final int colorSecondHandShadow = 3;
    public static final int colorMax = 2;


    private static void initPaintBucket(float radius) {
        Log.v(TAG, "initPaintBucket");

        float textSize = radius / 3f;
        float smTextSize = radius / 6f;
        float lineWidth = radius / 20f;

        palette = new Paint[styleMax+1][colorMax+1];

        for(int style=0; style <= styleMax; style++) {
            palette[style][colorBatteryLow] = getPaint(Color.YELLOW, style, smTextSize, lineWidth / 3f);
            palette[style][colorBatteryCritical] = getPaint(Color.RED, style, smTextSize, lineWidth / 3f);
            palette[style][colorSecondHand] = getPaint(Color.RED, style, smTextSize, lineWidth / 3f);
            palette[style][colorSecondHandShadow] = getPaint(Color.BLACK, style, smTextSize, lineWidth / 8f);
            palette[style][colorSecondHandShadow].setStyle(Paint.Style.STROKE);
        }

        // a couple things that are backwards for low-bit mode
        palette[styleLowBit][colorSecondHandShadow].setColor(Color.WHITE);
    }

    public static Paint get(int style, int colorID) {
        return palette[style][colorID];
    }

    public static Paint get(boolean ambientLowBit, boolean ambientMode, int colorID) {
        if(ambientLowBit && ambientMode)
            return get(styleLowBit, colorID);
        if(ambientMode)
            return get(styleAmbient, colorID);
        return get(styleNormal, colorID);
    }

    public static Paint getCalendarGreyPaint(int argb) {
        return getCalendarPaint(argbToGrey(argb));
    }

    private static int argbToGrey(int argb) {
        // CIE standard for luminance. Because overkill.
        int a = (argb & 0xff000000) >> 24;
        int r = (argb & 0xff0000) >> 16;
        int g = (argb & 0xff00) >> 8;
        int b = argb & 0xff;

        float fr = r / 255.0f;
        float fg = g / 255.0f;
        float fb = b / 255.0f;

        float fy = (float) (.2126f * Math.pow(fr, gamma) + .7152f * Math.pow(fg, gamma) + .0722f * Math.pow(fb, gamma));

        if (fy > 1.0f) fy = 1.0f;

        int y = (int) (fy * 255);

        return (a << 24) | (y << 16) | (y << 8) | y;
    }
}
