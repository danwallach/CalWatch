/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.graphics.Color
import android.graphics.Paint
import org.jetbrains.anko.*

/**
 * Cheesy helper for getting Paint values for calendar events and making sure we don't allocate
 * the same color twice.
 * Created by dwallach on 8/15/14.
 */
object PaintCan: AnkoLogger {
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
        return paintMap[argb] ?: {
            val newPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeJoin = Paint.Join.BEVEL
                color = argb
                style = Paint.Style.FILL
            }

            paintMap += argb to newPaint // cache the resulting Paint object for next time
            newPaint
        }()
    }

    private lateinit var palette: Array<Array<Paint?>>

    const val styleNormal = 0
    const val styleAmbient = 1
    const val styleLowBit = 2
    const val styleAntiBurnIn = 3
    const val styleMax = 3

    /**
     * This generates all the Paint that we'll need for drawing the watchface. These are all cached
     * in the palette and accessed elsewhere via PaintCan.get().
     */
    private fun watchfacePaint(_argb: Int, _style: Int, _textSize: Float, _strokeWidth: Float, forceColorAmbient: Boolean = false) =
        // underscores in the formal parameters to fix the variable shadowing in the apply block, below
        Paint(Paint.SUBPIXEL_TEXT_FLAG or Paint.HINTING_ON).apply {
            strokeCap = Paint.Cap.SQUARE
            strokeWidth = _strokeWidth
            textSize = _textSize
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            isAntiAlias = _style == styleNormal || _style == styleAmbient // anti-aliasing is forbidden in the low-bit modes

            // In specific cases, we're going to force colors to be colorful even in ambient mode (see below)
            val effectiveStyle = if(_style == styleAmbient && forceColorAmbient) styleNormal else _style

            color = when (effectiveStyle) {
                styleNormal -> _argb

                styleLowBit, styleAntiBurnIn -> when(_argb) {
                    //
                    // Quoth the Google: One reduced color space power saving method is to use a
                    // "low-bit" mode. In low-bit mode, the available colors are limited to
                    // black, white, blue, red, magenta, green, cyan, and yellow.
                    //
                    // http://developer.android.com/design/wear/watchfaces.html
                    //
                    // So we'll pass those colors through unmolested. On some watches, like the Moto 360 Sport,
                    // you still only get black & white, so all of these non-black colors just become white.
                    //
                    Color.BLACK, Color.WHITE, Color.BLUE, Color.RED, Color.MAGENTA,
                    Color.GREEN, Color.CYAN, Color.YELLOW -> _argb

                    //
                    // Any other color is mashed into being pure white or black
                    //
                    else -> if (_argb and 0xffffff > 0) 0xffffffff.toInt() else 0xff000000.toInt()
                }

                styleAmbient -> argbToGreyARGB(_argb)

                else -> {
                    error { "watchfacePaint: unknown style: $_style" }
                    0
                }
            }
        }

    const val colorBatteryLow = 0
    const val colorBatteryCritical = 1
    const val colorSecondHand = 2
    const val colorHandShadow = 3
    const val colorTickShadow = 4
    const val colorMinuteHand = 5
    const val colorHourHand = 6
    const val colorArcShadow = 7
    const val colorStepCountText = 8
    const val colorSmallTextAndLines = 9
    const val colorBigTextAndLines = 10
    const val colorBlackFill = 11
    const val colorLowBitCalendarFill = 12
    const val colorSmallShadow = 13
    const val colorBigShadow = 14
    const val colorStopwatchStroke = 15
    const val colorStopwatchFill = 16
    const val colorStopwatchSeconds = 17
    const val colorTimerStroke = 18
    const val colorTimerFill = 19
    const val colorStepCount = 20
    const val colorStepCountShadow = 21
    const val colorMax = 21


    /**
     * Create all the Paint instances used in the watch face. Call this every time the
     * watch radius changes, perhaps because of a resize event on the phone. Make sure
     * to call this *before* trying to get() any colors, or you'll get a NullPointerException.

     * @param radius Radius of the watch face, used to scale all of the line widths
     */
    fun initPaintBucket(radius: Float) {
        verbose { "initPaintBucket" }

        val textSize = radius / 3f
        val smTextSize = radius / 6f
        val lineWidth = radius / 20f

        palette = Array(styleMax + 1) { arrayOfNulls<Paint>(colorMax + 1) }

        for (style in 0..styleMax) {
            // Quoth the Google: "You can use color elements for up to 5 percent of total pixels." We're going to
            // do this exclusively for the remaining battery indicator, which is well under the required 5%.
            // http://developer.android.com/design/wear/watchfaces.html
            palette[style][colorBatteryLow] = watchfacePaint(Color.YELLOW, style, smTextSize, lineWidth / 3f, true)
            palette[style][colorBatteryCritical] = watchfacePaint(Color.RED, style, smTextSize, lineWidth / 3f, true)

            palette[style][colorSecondHand] = watchfacePaint(Color.RED, style, smTextSize, lineWidth / 3f)
            palette[style][colorHandShadow] = watchfacePaint(Color.BLACK, style, smTextSize, lineWidth / 8f)
            palette[style][colorTickShadow] = watchfacePaint(Color.BLACK, style, smTextSize, lineWidth / 8f)
            palette[style][colorMinuteHand] = watchfacePaint(Color.WHITE, style, textSize, lineWidth)
            palette[style][colorHourHand] = watchfacePaint(Color.WHITE, style, textSize, lineWidth * 1.5f)
            palette[style][colorArcShadow] = watchfacePaint(Color.BLACK, style, smTextSize, lineWidth / 6f)
            palette[style][colorStepCountText] = watchfacePaint(Color.GREEN, style, smTextSize * 0.8f, lineWidth / 6f)
            palette[style][colorSmallTextAndLines] = watchfacePaint(Color.WHITE, style, smTextSize, lineWidth / 3f)
            palette[style][colorBigTextAndLines] = watchfacePaint(Color.WHITE, style, textSize, lineWidth)
            palette[style][colorBigShadow] = watchfacePaint(Color.BLACK, style, textSize, lineWidth / 2f)
            palette[style][colorBlackFill] = watchfacePaint(Color.BLACK, style, textSize, lineWidth)
            palette[style][colorLowBitCalendarFill] = watchfacePaint(Color.WHITE, style, textSize, lineWidth) // the docs claim we have access to other colors here, like CYAN, but that's not true at least on the Moto 360 Sport
            palette[style][colorSmallShadow] = watchfacePaint(Color.BLACK, style, smTextSize, lineWidth / 4f)
            palette[style][colorStepCount] = watchfacePaint(Color.GREEN, style, smTextSize, lineWidth / 2f)
            palette[style][colorStepCountShadow] = watchfacePaint(Color.BLACK, style, smTextSize, lineWidth / 8f)

            // toInt because of a Kotlin bug: https://youtrack.jetbrains.com/issue/KT-4749
            palette[style][colorStopwatchSeconds] = watchfacePaint(0xFF80A3F2.toInt(), style, smTextSize, lineWidth / 8f)  // light blue
            palette[style][colorStopwatchStroke] = watchfacePaint(0xFF80A3F2.toInt(), style, smTextSize, lineWidth / 3f)  // light blue
            palette[style][colorStopwatchFill] = watchfacePaint(0x9080A3F2.toInt(), style, smTextSize, lineWidth / 3f)  // light blue + transparency
            palette[style][colorTimerStroke] = watchfacePaint(0xFFF2CF80.toInt(), style, smTextSize, lineWidth / 3f) // orange-ish
            palette[style][colorTimerFill] = watchfacePaint(0x90F2CF80.toInt(), style, smTextSize, lineWidth / 3f) // orange-ish + transparency

            // shadows are stroke, not fill, so we fix that here
            palette[style][colorHandShadow]?.style = Paint.Style.STROKE
            palette[style][colorTickShadow]?.style = Paint.Style.STROKE
            palette[style][colorArcShadow]?.style = Paint.Style.STROKE
            palette[style][colorSmallShadow]?.style = Paint.Style.STROKE
            palette[style][colorBigShadow]?.style = Paint.Style.STROKE
            palette[style][colorStopwatchStroke]?.style = Paint.Style.STROKE
            palette[style][colorTimerStroke]?.style = Paint.Style.STROKE
            palette[style][colorStepCountShadow]?.style = Paint.Style.STROKE

            // by default, text is centered, but some styles want it on the left
            // (these are the places where we'll eventually have to do more work for RTL languages)
            palette[style][colorStepCountText]?.textAlign = Paint.Align.CENTER
            palette[style][colorSmallTextAndLines]?.textAlign = Paint.Align.LEFT
            palette[style][colorSmallShadow]?.textAlign = Paint.Align.LEFT
        }

        // In low-bit anti-burnin mode: we'll be drawing some "shadows" as
        // white outlines around black centers, "hollowing out" the hands as we're supposed to do.
        palette[styleAntiBurnIn][colorHandShadow]?.color = Color.WHITE
        palette[styleAntiBurnIn][colorHandShadow]?.strokeWidth = lineWidth / 6f

        palette[styleAntiBurnIn][colorMinuteHand]?.color = Color.BLACK
        palette[styleAntiBurnIn][colorMinuteHand]?.style = Paint.Style.STROKE

        palette[styleAntiBurnIn][colorHourHand]?.color = Color.BLACK
        palette[styleAntiBurnIn][colorHourHand]?.style = Paint.Style.STROKE

        palette[styleAntiBurnIn][colorArcShadow]?.color = Color.WHITE
        palette[styleAntiBurnIn][colorSmallShadow]?.color = Color.WHITE
        palette[styleAntiBurnIn][colorBigShadow]?.color = Color.WHITE

        // Also, we're still going to change the style for the stopwatch and timer rendering, since our only
        // choices are black or white, we'll go with black for the fills. The outlines will still be white
        // (see the "stroke" paints versus the "fill" paints).

        // Technically, we have access to other colors when in low-bit, but they're not consistently supported
        // across Wear devices. We can only count on black and white, and nothing else, so we might as well
        // try to make it look tolerable that way. Here, the fill colors will be black, and the outlines
        // will be white (per the colorTimerStroke and colorStopwatchStroke above). It would look better
        // in b&w to have the whole outer ring be white, but this would violate Google's rules about
        // max percentage of pixels on while in low-bit mode.
        palette[styleLowBit][colorTimerFill]?.color = Color.BLACK
        palette[styleLowBit][colorStopwatchFill]?.color = Color.BLACK
        palette[styleLowBit][colorTimerStroke]?.strokeWidth = lineWidth / 6f
        palette[styleLowBit][colorStopwatchStroke]?.strokeWidth = lineWidth / 6f

        palette[styleAntiBurnIn][colorTimerFill]?.color = Color.BLACK
        palette[styleAntiBurnIn][colorStopwatchFill]?.color = Color.BLACK
        palette[styleAntiBurnIn][colorTimerStroke]?.strokeWidth = lineWidth / 6f
        palette[styleAntiBurnIn][colorStopwatchStroke]?.strokeWidth = lineWidth / 6f
    }

    /**
     * This will return a Paint of a given style and colorID, where those are the constants
     * defined in this file. Anything else will cause an exception.
     */
    operator fun get(style: Int, colorID: Int): Paint =
        requireNotNull(palette[style][colorID], { "undefined paintcan color, style($style), colorID($colorID)" })

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
