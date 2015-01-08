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

    private static Paint[][] palette;

    public static final int styleNormal = 0;
    public static final int styleAmbient = 1;
    public static final int styleLowBit = 2;
    public static final int styleMax = 2;

    private static final float gamma = 2.2f;

    private static Paint newPaint(int argb, int style, float textSize, float strokeWidth) {
        Paint retPaint = new Paint(Paint.SUBPIXEL_TEXT_FLAG | Paint.HINTING_ON);
        retPaint.setStrokeCap(Paint.Cap.SQUARE);
        retPaint.setStrokeWidth(strokeWidth);
        retPaint.setTextSize(textSize);
        retPaint.setStyle(Paint.Style.FILL);
        retPaint.setTextAlign(Paint.Align.CENTER);

        switch(style) {
            case styleNormal:
                retPaint.setAntiAlias(true);
                retPaint.setColor(argb);
                break;

            case styleLowBit:
                retPaint.setAntiAlias(false);
                if((argb & 0xffffff) > 0)
                    retPaint.setColor(0xffffffff); // any non-black color mapped to pure white
                else
                    retPaint.setColor(0xff000000);
                break;

            case styleAmbient:
                retPaint.setAntiAlias(true);
                retPaint.setColor(argbToGrey(argb));
                break;

            default:
                Log.e(TAG, "newPaint: unknown style: " + style);
                break;
        }

        return retPaint;
    }

    public static final int colorBatteryLow = 0;
    public static final int colorBatteryCritical = 1;
    public static final int colorSecondHand = 2;
    public static final int colorSecondHandShadow = 3;
    public static final int colorMinuteHand = 4;
    public static final int colorHourHand = 5;
    public static final int colorArcShadow = 6;
    public static final int colorSmallTextAndLines = 7;
    public static final int colorBigTextAndLines = 8;
    public static final int colorBlackFill = 9;
    public static final int colorSmallShadow = 10;
    public static final int colorBigShadow = 11;
    public static final int colorStopwatchStroke = 12;
    public static final int colorStopwatchFill = 13;
    public static final int colorStopwatchSeconds = 14;
    public static final int colorTimerStroke = 15;
    public static final int colorTimerFill = 16;
    public static final int colorMax = 16;


    /**
     * Create all the Paint instances used in the watch face. Call this every time the
     * watch radius changes, perhaps because of a resize event on the phone. Make sure
     * to call this *before* trying to get() any colors, or you'll get a NullPointerException.
     *
     * @param radius Radius of the watch face, used to scale all of the line widths
     */
    public static void initPaintBucket(float radius) {
        Log.v(TAG, "initPaintBucket");

        float textSize = radius / 3f;
        float smTextSize = radius / 6f;
        float lineWidth = radius / 20f;

        palette = new Paint[styleMax+1][colorMax+1];

        for(int style=0; style <= styleMax; style++) {
            palette[style][colorBatteryLow] = newPaint(Color.YELLOW, style, smTextSize, lineWidth / 3f);
            palette[style][colorBatteryCritical] = newPaint(Color.RED, style, smTextSize, lineWidth / 3f);
            palette[style][colorSecondHand] = newPaint(Color.RED, style, smTextSize, lineWidth / 3f);
            palette[style][colorSecondHandShadow] = newPaint(Color.BLACK, style, smTextSize, lineWidth / 8f);
            palette[style][colorMinuteHand] = newPaint(Color.WHITE, style, textSize, lineWidth);
            palette[style][colorHourHand] = newPaint(Color.WHITE, style, textSize, lineWidth * 1.5f);
            palette[style][colorArcShadow] = newPaint(Color.BLACK, style, smTextSize, lineWidth / 6f);
            palette[style][colorSmallTextAndLines] = newPaint(Color.WHITE, style, smTextSize, lineWidth / 3f);
            palette[style][colorBigTextAndLines] = newPaint(Color.WHITE, style, textSize, lineWidth);
            palette[style][colorBlackFill] = newPaint(Color.BLACK, style, textSize, lineWidth);
            palette[style][colorSmallShadow] = newPaint(Color.BLACK, style, smTextSize, lineWidth / 4f);
            palette[style][colorBigShadow] = newPaint(Color.BLACK, style, smTextSize, lineWidth / 2f);

            palette[style][colorStopwatchSeconds] = newPaint(0xFF80A3F2, style, smTextSize, lineWidth / 8f);  // light blue
            palette[style][colorStopwatchStroke] = newPaint(0xFF80A3F2, style, smTextSize, lineWidth / 3f);  // light blue
            palette[style][colorStopwatchFill] = newPaint(0x9080A3F2, style, smTextSize, lineWidth / 3f);  // light blue + transparency
            palette[style][colorTimerStroke] = newPaint(0xFFF2CF80, style, smTextSize, lineWidth / 3f); // orange-ish
            palette[style][colorTimerFill] = newPaint(0x90F2CF80, style, smTextSize, lineWidth / 3f); // orange-ish + transparency

            // shadows are stroke, not fill, so we fix that here
            palette[style][colorSecondHandShadow].setStyle(Paint.Style.STROKE);
            palette[style][colorArcShadow].setStyle(Paint.Style.STROKE);
            palette[style][colorSmallShadow].setStyle(Paint.Style.STROKE);
            palette[style][colorBigShadow].setStyle(Paint.Style.STROKE);
            palette[style][colorStopwatchStroke].setStyle(Paint.Style.STROKE);
            palette[style][colorTimerStroke].setStyle(Paint.Style.STROKE);

            // by default, text is centered, but some styles want it on the left
            // (these are the places where we'll eventually have to do more work for RTL languages)
            palette[style][colorSmallTextAndLines].setTextAlign(Paint.Align.LEFT);
            palette[style][colorSmallShadow].setTextAlign(Paint.Align.LEFT);
        }

        // a couple things that are backwards for low-bit mode: we'll be drawing some "shadows" as
        // white outlines around black centers, "hollowing out" the hands as we're supposed to do
        palette[styleLowBit][colorSecondHandShadow].setColor(Color.WHITE);
        palette[styleLowBit][colorSecondHandShadow].setStrokeWidth(lineWidth / 6f);
        palette[styleLowBit][colorMinuteHand].setColor(Color.BLACK);
        palette[styleLowBit][colorHourHand].setColor(Color.BLACK);
        palette[styleLowBit][colorArcShadow].setColor(Color.WHITE);
        palette[styleLowBit][colorSmallShadow].setColor(Color.WHITE);
        palette[styleLowBit][colorBigShadow].setColor(Color.WHITE);
        palette[styleLowBit][colorTimerFill].setColor(Color.BLACK);
        palette[styleLowBit][colorStopwatchFill].setColor(Color.BLACK);
        palette[styleLowBit][colorTimerStroke].setStrokeWidth(lineWidth / 6f);
        palette[styleLowBit][colorStopwatchStroke].setStrokeWidth(lineWidth / 6f);
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
