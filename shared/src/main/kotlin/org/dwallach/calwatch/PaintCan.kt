/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.graphics.Color
import android.graphics.Paint
import android.util.Log

/**
 * Cheesy helper for getting Paint values for calendar events and making sure we don't allocate
 * the same color twice.
 * Created by dwallach on 8/15/14.
 */
object PaintCan {
    private const val TAG = "PaintCan"
    private var paintMap = emptyMap<Int,Paint>()

    /**
     * This gets a Paint of a given color. If the same color is requested more than once,
     * the Paint will only be created once and is internally cached. We use this for the
     * calendar wedges, where the colors are coming from the user's calendar.
     *
     * This is *not* meant for use in low-bit mode, when anti-aliasing is forbidden.
     * In that mode, we behave completely differently, drawing thin arcs rather than
     * large wedges.
     */
    fun getCalendarPaint(argb: Int): Paint {
        // Log.v(TAG, "get paint: " + Integer.toHexString(argb));
        return paintMap[argb] ?: {
            val newPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            newPaint.strokeJoin = Paint.Join.BEVEL
            newPaint.color = argb
            newPaint.style = Paint.Style.FILL

            paintMap = paintMap.plus(Pair(argb, newPaint))
            newPaint
        }.invoke()
    }

    private lateinit var palette: Array<Array<Paint?>>

    const val styleNormal = 0
    const val styleAmbient = 1
    const val styleLowBit = 2
    const val styleMax = 2

    /**
     * This generates all the Paint that we'll need for drawing the watchface. These are all cached
     * in the palette and accessed elsewhere via PaintCan.get().
     */
    private fun watchfacePaint(argb: Int, style: Int, textSize: Float, strokeWidth: Float): Paint {
        val retPaint = Paint(Paint.SUBPIXEL_TEXT_FLAG or Paint.HINTING_ON)
        retPaint.strokeCap = Paint.Cap.SQUARE
        retPaint.strokeWidth = strokeWidth
        retPaint.textSize = textSize
        retPaint.style = Paint.Style.FILL
        retPaint.textAlign = Paint.Align.CENTER

        when (style) {
            styleNormal -> {
                retPaint.isAntiAlias = true
                retPaint.color = argb
            }

            styleLowBit -> {
                retPaint.isAntiAlias = false
                if (argb and 16777215 > 0)
                    retPaint.color = -1 // any non-black color mapped to pure white
                else
                    retPaint.color = -16777216
            }

            styleAmbient -> {
                retPaint.isAntiAlias = true
                retPaint.color = argbToGreyARGB(argb)
            }

            else -> Log.e(TAG, "watchfacePaint: unknown style: " + style)
        }

        return retPaint
    }

    const val colorBatteryLow = 0
    const val colorBatteryCritical = 1
    const val colorSecondHand = 2
    const val colorSecondHandShadow = 3
    const val colorMinuteHand = 4
    const val colorHourHand = 5
    const val colorArcShadow = 6
    const val colorSmallTextAndLines = 7
    const val colorBigTextAndLines = 8
    const val colorBlackFill = 9
    const val colorSmallShadow = 10
    const val colorBigShadow = 11
    const val colorStopwatchStroke = 12
    const val colorStopwatchFill = 13
    const val colorStopwatchSeconds = 14
    const val colorTimerStroke = 15
    const val colorTimerFill = 16
    const val colorMax = 16


    /**
     * Create all the Paint instances used in the watch face. Call this every time the
     * watch radius changes, perhaps because of a resize event on the phone. Make sure
     * to call this *before* trying to get() any colors, or you'll get a NullPointerException.

     * @param radius Radius of the watch face, used to scale all of the line widths
     */
    fun initPaintBucket(radius: Float) {
        Log.v(TAG, "initPaintBucket")

        val textSize = radius / 3f
        val smTextSize = radius / 6f
        val lineWidth = radius / 20f

        palette = Array(styleMax + 1) { arrayOfNulls<Paint>(colorMax + 1) }

        for (style in 0..styleMax) {
            palette[style][colorBatteryLow] = watchfacePaint(Color.YELLOW, style, smTextSize, lineWidth / 3f)
            palette[style][colorBatteryCritical] = watchfacePaint(Color.RED, style, smTextSize, lineWidth / 3f)
            palette[style][colorSecondHand] = watchfacePaint(Color.RED, style, smTextSize, lineWidth / 3f)
            palette[style][colorSecondHandShadow] = watchfacePaint(Color.BLACK, style, smTextSize, lineWidth / 8f)
            palette[style][colorMinuteHand] = watchfacePaint(Color.WHITE, style, textSize, lineWidth)
            palette[style][colorHourHand] = watchfacePaint(Color.WHITE, style, textSize, lineWidth * 1.5f)
            palette[style][colorArcShadow] = watchfacePaint(Color.BLACK, style, smTextSize, lineWidth / 6f)
            palette[style][colorSmallTextAndLines] = watchfacePaint(Color.WHITE, style, smTextSize, lineWidth / 3f)
            palette[style][colorBigTextAndLines] = watchfacePaint(Color.WHITE, style, textSize, lineWidth)
            palette[style][colorBigShadow] = watchfacePaint(Color.BLACK, style, textSize, lineWidth / 2f)
            palette[style][colorBlackFill] = watchfacePaint(Color.BLACK, style, textSize, lineWidth)
            palette[style][colorSmallShadow] = watchfacePaint(Color.BLACK, style, smTextSize, lineWidth / 4f)

            // toInt because of a Kotlin bug: https://youtrack.jetbrains.com/issue/KT-4749
            palette[style][colorStopwatchSeconds] = watchfacePaint(0xFF80A3F2.toInt(), style, smTextSize, lineWidth / 8f)  // light blue
            palette[style][colorStopwatchStroke] = watchfacePaint(0xFF80A3F2.toInt(), style, smTextSize, lineWidth / 3f)  // light blue
            palette[style][colorStopwatchFill] = watchfacePaint(0x9080A3F2.toInt(), style, smTextSize, lineWidth / 3f)  // light blue + transparency
            palette[style][colorTimerStroke] = watchfacePaint(0xFFF2CF80.toInt(), style, smTextSize, lineWidth / 3f) // orange-ish
            palette[style][colorTimerFill] = watchfacePaint(0x90F2CF80.toInt(), style, smTextSize, lineWidth / 3f) // orange-ish + transparency

            // shadows are stroke, not fill, so we fix that here
            palette[style][colorSecondHandShadow]?.style = Paint.Style.STROKE
            palette[style][colorArcShadow]?.style = Paint.Style.STROKE
            palette[style][colorSmallShadow]?.style = Paint.Style.STROKE
            palette[style][colorBigShadow]?.style = Paint.Style.STROKE
            palette[style][colorStopwatchStroke]?.style = Paint.Style.STROKE
            palette[style][colorTimerStroke]?.style = Paint.Style.STROKE

            // by default, text is centered, but some styles want it on the left
            // (these are the places where we'll eventually have to do more work for RTL languages)
            palette[style][colorSmallTextAndLines]?.textAlign = Paint.Align.LEFT
            palette[style][colorSmallShadow]?.textAlign = Paint.Align.LEFT
        }

        // a couple things that are backwards for low-bit mode: we'll be drawing some "shadows" as
        // white outlines around black centers, "hollowing out" the hands as we're supposed to do
        palette[styleLowBit][colorSecondHandShadow]?.color = Color.WHITE
        palette[styleLowBit][colorSecondHandShadow]?.strokeWidth = lineWidth / 6f
        palette[styleLowBit][colorMinuteHand]?.color = Color.BLACK
        palette[styleLowBit][colorHourHand]?.color = Color.BLACK
        palette[styleLowBit][colorArcShadow]?.color = Color.WHITE
        palette[styleLowBit][colorSmallShadow]?.color = Color.WHITE
        palette[styleLowBit][colorBigShadow]?.color = Color.WHITE
        palette[styleLowBit][colorTimerFill]?.color = Color.BLACK
        palette[styleLowBit][colorStopwatchFill]?.color = Color.BLACK
        palette[styleLowBit][colorTimerStroke]?.strokeWidth = lineWidth / 6f
        palette[styleLowBit][colorStopwatchStroke]?.strokeWidth = lineWidth / 6f
    }

    /**
     * This will return a Paint of a given style and colorID, where those are the constants
     * defined in this file. Anything else will cause an exception.
     */
    operator fun get(style: Int, colorID: Int): Paint =
        palette[style][colorID] ?: throw RuntimeException("undefined paintcan color, style($style), colorID($colorID)")

    /**
     * This will return a Paint for the specified ambient modes and the given colorID, based on the constants
     * defined in this class. Anything else will cause an exception.
     */
    operator fun get(ambientLowBit: Boolean, ambientMode: Boolean, colorID: Int): Paint = when {
        ambientLowBit && ambientMode -> get(styleLowBit, colorID)
        ambientMode -> get(styleAmbient, colorID)
        else -> get(styleNormal, colorID)
    }

    fun getCalendarGreyPaint(argb: Int) = getCalendarPaint(argbToGreyARGB(argb))

    private fun argbToLuminance(argb: Int): Float {
        val r = (argb and 0xff0000) shr 16
        val g = (argb and 0xff00) shr 8
        val b = argb and 255

        val fr = r / 255.0f
        val fg = g / 255.0f
        val fb = b / 255.0f

        // Many different codes (for reference):
        // http://www.f4.fhtw-berlin.de/~barthel/ImageJ/ColorInspector/HTMLHelp/farbraumJava.htm
        var fy = .299f * fr + .587f * fg + .114f * fb

        // clipping shouldn't be necessary, but paranoia is warranted
        if (fy > 1.0f) fy = 1.0f

        return fy

    }

    private fun argbToGreyARGB(argb: Int): Int {
        val a = (argb and 0xff000000.toInt()) shr 24 // toInt because of a Kotlin bug: https://youtrack.jetbrains.com/issue/KT-4749
        val y = (argbToLuminance(argb) * 255f).toInt()
        return a shl 24 or (y shl 16) or (y shl 8) or y
    }
}
