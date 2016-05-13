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

    const val STYLE_NORMAL = 0
    const val STYLE_AMBIENT = 1
    const val STYLE_LOWBIT = 2
    const val STYLE_ANTI_BURNIN = 3
    const val STYLE_MAX = 3

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
            isAntiAlias = _style == STYLE_NORMAL || _style == STYLE_AMBIENT // anti-aliasing is forbidden in the low-bit modes

            // In specific cases, we're going to force colors to be colorful even in ambient mode (see below)
            val effectiveStyle = if(_style == STYLE_AMBIENT && forceColorAmbient) STYLE_NORMAL else _style

            color = when (effectiveStyle) {
                STYLE_NORMAL -> _argb

                STYLE_LOWBIT, STYLE_ANTI_BURNIN -> when(_argb) {
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

                STYLE_AMBIENT -> argbToGreyARGB(_argb)

                else -> {
                    error { "watchfacePaint: unknown style: $_style" }
                    0
                }
            }
        }

    const val COLOR_BATTERY_LOW = 0
    const val COLOR_BATTERY_CRITICAL = 1
    const val COLOR_SECOND_HAND = 2
    const val COLOR_HAND_SHADOW = 3
    const val COLOR_TICK_SHADOW = 4
    const val COLOR_MINUTE_HAND = 5
    const val COLOR_HOUR_HAND = 6
    const val COLOR_ARC_SHADOW = 7
    const val COLOR_STEP_COUNT_TEXT = 8
    const val COLOR_SMALL_TEXT_AND_LINES = 9
    const val COLOR_BIG_TEXT_AND_LINES = 10
    const val COLOR_BLACK_FILL = 11
    const val COLOR_LOWBIT_CALENDAR_FILL = 12
    const val COLOR_SMALL_SHADOW = 13
    const val COLOR_BIG_SHADOW = 14
    const val COLOR_STOPWATCH_STROKE = 15
    const val COLOR_STOPWATCH_FILL = 16
    const val COLOR_STOPWATCH_SECONDS = 17
    const val COLOR_TIMER_STROKE = 18
    const val COLOR_TIMER_FILL = 19
    const val COLOR_STEP_COUNT = 20
    const val COLOR_STEP_COUNT_SHADOW = 21
    const val COLOR_MAX = 21


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

        palette = Array(STYLE_MAX + 1) { arrayOfNulls<Paint>(COLOR_MAX + 1) }

        for (style in 0..STYLE_MAX) {
            // Quoth the Google: "You can use color elements for up to 5 percent of total pixels." We're going to
            // do this for the remaining battery indicator and step counter, which are well under the required 5%.
            // http://developer.android.com/design/wear/watchfaces.html
            palette[style][COLOR_BATTERY_LOW] = watchfacePaint(Color.YELLOW, style, smTextSize, lineWidth / 3f, true)
            palette[style][COLOR_BATTERY_CRITICAL] = watchfacePaint(Color.RED, style, smTextSize, lineWidth / 3f, true)

            palette[style][COLOR_SECOND_HAND] = watchfacePaint(Color.RED, style, smTextSize, lineWidth / 3f)
            palette[style][COLOR_HAND_SHADOW] = watchfacePaint(Color.BLACK, style, smTextSize, lineWidth / 8f)
            palette[style][COLOR_TICK_SHADOW] = watchfacePaint(Color.BLACK, style, smTextSize, lineWidth / 8f)
            palette[style][COLOR_MINUTE_HAND] = watchfacePaint(Color.WHITE, style, textSize, lineWidth)
            palette[style][COLOR_HOUR_HAND] = watchfacePaint(Color.WHITE, style, textSize, lineWidth * 1.5f)
            palette[style][COLOR_ARC_SHADOW] = watchfacePaint(Color.BLACK, style, smTextSize, lineWidth / 6f)
            palette[style][COLOR_STEP_COUNT_TEXT] = watchfacePaint(Color.GREEN, style, smTextSize * 0.6f, lineWidth / 6f)
            palette[style][COLOR_SMALL_TEXT_AND_LINES] = watchfacePaint(Color.WHITE, style, smTextSize, lineWidth / 3f)
            palette[style][COLOR_BIG_TEXT_AND_LINES] = watchfacePaint(Color.WHITE, style, textSize, lineWidth)
            palette[style][COLOR_BIG_SHADOW] = watchfacePaint(Color.BLACK, style, textSize, lineWidth / 2f)
            palette[style][COLOR_BLACK_FILL] = watchfacePaint(Color.BLACK, style, textSize, lineWidth)
            palette[style][COLOR_LOWBIT_CALENDAR_FILL] = watchfacePaint(Color.WHITE, style, textSize, lineWidth) // the docs claim we have access to other colors here, like CYAN, but that's not true at least on the Moto 360 Sport
            palette[style][COLOR_SMALL_SHADOW] = watchfacePaint(Color.BLACK, style, smTextSize, lineWidth / 4f)
            palette[style][COLOR_STEP_COUNT] = watchfacePaint(Color.GREEN, style, smTextSize, lineWidth / 2f)
            palette[style][COLOR_STEP_COUNT_SHADOW] = watchfacePaint(Color.BLACK, style, smTextSize, lineWidth / 8f)

            // toInt because of a Kotlin bug: https://youtrack.jetbrains.com/issue/KT-4749
            palette[style][COLOR_STOPWATCH_SECONDS] = watchfacePaint(0xFF80A3F2.toInt(), style, smTextSize, lineWidth / 8f)  // light blue
            palette[style][COLOR_STOPWATCH_STROKE] = watchfacePaint(0xFF80A3F2.toInt(), style, smTextSize, lineWidth / 3f)  // light blue
            palette[style][COLOR_STOPWATCH_FILL] = watchfacePaint(0x9080A3F2.toInt(), style, smTextSize, lineWidth / 3f)  // light blue + transparency
            palette[style][COLOR_TIMER_STROKE] = watchfacePaint(0xFFF2CF80.toInt(), style, smTextSize, lineWidth / 3f) // orange-ish
            palette[style][COLOR_TIMER_FILL] = watchfacePaint(0x90F2CF80.toInt(), style, smTextSize, lineWidth / 3f) // orange-ish + transparency

            // shadows are stroke, not fill, so we fix that here
            palette[style][COLOR_HAND_SHADOW]?.style = Paint.Style.STROKE
            palette[style][COLOR_TICK_SHADOW]?.style = Paint.Style.STROKE
            palette[style][COLOR_ARC_SHADOW]?.style = Paint.Style.STROKE
            palette[style][COLOR_SMALL_SHADOW]?.style = Paint.Style.STROKE
            palette[style][COLOR_BIG_SHADOW]?.style = Paint.Style.STROKE
            palette[style][COLOR_STOPWATCH_STROKE]?.style = Paint.Style.STROKE
            palette[style][COLOR_TIMER_STROKE]?.style = Paint.Style.STROKE
            palette[style][COLOR_STEP_COUNT_SHADOW]?.style = Paint.Style.STROKE

            // by default, text is centered, but some styles want it on the left
            // (these are the places where we'll eventually have to do more work for RTL languages)
            palette[style][COLOR_STEP_COUNT_TEXT]?.textAlign = Paint.Align.CENTER
            palette[style][COLOR_SMALL_TEXT_AND_LINES]?.textAlign = Paint.Align.LEFT
            palette[style][COLOR_SMALL_SHADOW]?.textAlign = Paint.Align.LEFT
        }

        // In low-bit anti-burnin mode: we'll be drawing some "shadows" as
        // white outlines around black centers, "hollowing out" the hands as we're supposed to do.
        palette[STYLE_ANTI_BURNIN][COLOR_HAND_SHADOW]?.color = Color.WHITE
        palette[STYLE_ANTI_BURNIN][COLOR_HAND_SHADOW]?.strokeWidth = lineWidth / 6f

        palette[STYLE_ANTI_BURNIN][COLOR_MINUTE_HAND]?.color = Color.BLACK
        palette[STYLE_ANTI_BURNIN][COLOR_MINUTE_HAND]?.style = Paint.Style.STROKE

        palette[STYLE_ANTI_BURNIN][COLOR_HOUR_HAND]?.color = Color.BLACK
        palette[STYLE_ANTI_BURNIN][COLOR_HOUR_HAND]?.style = Paint.Style.STROKE

        palette[STYLE_ANTI_BURNIN][COLOR_ARC_SHADOW]?.color = Color.WHITE
        palette[STYLE_ANTI_BURNIN][COLOR_SMALL_SHADOW]?.color = Color.WHITE
        palette[STYLE_ANTI_BURNIN][COLOR_BIG_SHADOW]?.color = Color.WHITE

        // Also, we're still going to change the style for the stopwatch and timer rendering, since our only
        // choices are black or white, we'll go with black for the fills. The outlines will still be white
        // (see the "stroke" paints versus the "fill" paints).

        // Technically, we have access to other colors when in low-bit, but they're not consistently supported
        // across Wear devices. We can only count on black and white, and nothing else, so we might as well
        // try to make it look tolerable that way. Here, the fill colors will be black, and the outlines
        // will be white (per the colorTimerStroke and colorStopwatchStroke above). It would look better
        // in b&w to have the whole outer ring be white, but this would violate Google's rules about
        // max percentage of pixels on while in low-bit mode.
        palette[STYLE_LOWBIT][COLOR_TIMER_FILL]?.color = Color.BLACK
        palette[STYLE_LOWBIT][COLOR_STOPWATCH_FILL]?.color = Color.BLACK
        palette[STYLE_LOWBIT][COLOR_TIMER_STROKE]?.strokeWidth = lineWidth / 6f
        palette[STYLE_LOWBIT][COLOR_STOPWATCH_STROKE]?.strokeWidth = lineWidth / 6f

        palette[STYLE_ANTI_BURNIN][COLOR_TIMER_FILL]?.color = Color.BLACK
        palette[STYLE_ANTI_BURNIN][COLOR_STOPWATCH_FILL]?.color = Color.BLACK
        palette[STYLE_ANTI_BURNIN][COLOR_TIMER_STROKE]?.strokeWidth = lineWidth / 6f
        palette[STYLE_ANTI_BURNIN][COLOR_STOPWATCH_STROKE]?.strokeWidth = lineWidth / 6f
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
