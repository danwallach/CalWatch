/*
 * CalWatch / CalWatch2
 * Copyright © 2014-2019 by Dan S. Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch2

import android.graphics.Color
import android.graphics.Paint
import org.dwallach.calwatch2.PaintCan.Brush.*
import org.jetbrains.anko.*

/**
 * Cheesy helper for getting Paint values for calendar events and making sure we don't allocate
 * the same color twice.
 */
class PaintCan(private val radius: Float) : AnkoLogger {
    private var palette: Array<Array<Paint?>>

    enum class Style {
        NORMAL,
        AMBIENT,
        AMBIENT_ANTI_BURNIN,
        LOWBIT,
        LOWBIT_ANTI_BURNIN,
        MAX
    }

    enum class Brush {
        BATTERY_LOW,
        BATTERY_CRITICAL,
        SECOND_HAND,
        HAND_SHADOW,
        TICK_SHADOW,
        MINUTE_HAND,
        HOUR_HAND,
        ARC_SHADOW,
        MONTHBOX_TEXT,
        MONTHBOX_SHADOW,
        SMALL_LINES,
        BIG_TEXT_AND_LINES,
        BLACK_FILL,
        LOWBIT_CALENDAR_FILL,
        BIG_SHADOW,
        MAX
    }

    /**
     * Create all the Paint instances used in the watch face. Call this every time the
     * watch radius changes, perhaps because of a resize event on the phone. Make sure
     * to call this *before* trying to get() any colors, or you'll get a NullPointerException.
     */
    init {
        verbose { "initPaintBucket: $radius" }

        val textSize = radius / 3f
        val smTextSize = radius / 6f
        val lineWidth = radius / 20f

        palette = Array(Style.MAX.ordinal) { arrayOfNulls<Paint>(MAX.ordinal) }

        for (style in Style.values()) {
            if (style == Style.MAX) break
            val i = style.ordinal

            // Quoth the Google: "You can use color elements for up to 5 percent of total pixels."
            // http://developer.android.com/design/wear/watchfaces.html
            palette[i][BATTERY_LOW.ordinal] = watchfacePaint(Color.YELLOW, style, smTextSize, lineWidth / 3f, true)
            palette[i][BATTERY_CRITICAL.ordinal] = watchfacePaint(Color.RED, style, smTextSize, lineWidth / 3f, true)

            palette[i][SECOND_HAND.ordinal] = watchfacePaint(Color.RED, style, smTextSize, lineWidth / 3f)
            palette[i][HAND_SHADOW.ordinal] = watchfacePaint(Color.BLACK, style, smTextSize, lineWidth / 8f)
            palette[i][TICK_SHADOW.ordinal] = watchfacePaint(Color.BLACK, style, smTextSize, lineWidth / 8f)
            palette[i][MINUTE_HAND.ordinal] = watchfacePaint(Color.WHITE, style, textSize, lineWidth)
            palette[i][HOUR_HAND.ordinal] = watchfacePaint(Color.WHITE, style, textSize, lineWidth * 1.5f)
            palette[i][ARC_SHADOW.ordinal] = watchfacePaint(Color.BLACK, style, smTextSize, lineWidth / 6f)
            palette[i][MONTHBOX_TEXT.ordinal] = watchfacePaint(Color.WHITE, style, smTextSize, lineWidth / 3f)
            palette[i][SMALL_LINES.ordinal] = watchfacePaint(Color.WHITE, style, smTextSize, lineWidth / 3f)
            palette[i][BIG_TEXT_AND_LINES.ordinal] = watchfacePaint(Color.WHITE, style, textSize, lineWidth)
            palette[i][BIG_SHADOW.ordinal] = watchfacePaint(Color.BLACK, style, textSize, lineWidth / 2f)
            palette[i][BLACK_FILL.ordinal] = watchfacePaint(Color.BLACK, style, textSize, lineWidth)
            palette[i][LOWBIT_CALENDAR_FILL.ordinal] = watchfacePaint(
                Color.WHITE,
                style,
                textSize,
                lineWidth
            ) // the docs claim we have access to other colors here, like CYAN, but that's not true at least on the Moto 360 Sport
            palette[i][MONTHBOX_SHADOW.ordinal] = watchfacePaint(Color.BLACK, style, smTextSize, lineWidth / 4f)

            // shadows are stroke, not fill, so we fix that here
            palette[i][HAND_SHADOW.ordinal]?.style = Paint.Style.STROKE
            palette[i][TICK_SHADOW.ordinal]?.style = Paint.Style.STROKE
            palette[i][ARC_SHADOW.ordinal]?.style = Paint.Style.STROKE
            palette[i][MONTHBOX_SHADOW.ordinal]?.style = Paint.Style.STROKE
            palette[i][BIG_SHADOW.ordinal]?.style = Paint.Style.STROKE

            // by default, text is centered, but some styles want it on the left
            // (these are the places where we'll eventually have to do more work for RTL languages)
            palette[i][MONTHBOX_TEXT.ordinal]?.textAlign = Paint.Align.LEFT
            palette[i][MONTHBOX_SHADOW.ordinal]?.textAlign = Paint.Align.LEFT
        }

        // In anti-burnin mode: we'll be drawing some "shadows" as
        // white outlines around black centers, "hollowing out" the hands as we're supposed to do.
        for (style in listOf(Style.AMBIENT, Style.AMBIENT_ANTI_BURNIN, Style.LOWBIT_ANTI_BURNIN)) {
            val i = style.ordinal

            palette[i][HAND_SHADOW.ordinal]?.color = Color.WHITE
            palette[i][HAND_SHADOW.ordinal]?.strokeWidth = lineWidth / 6f

            palette[i][MINUTE_HAND.ordinal]?.color = Color.BLACK
            palette[i][MINUTE_HAND.ordinal]?.style = Paint.Style.STROKE

            palette[i][HOUR_HAND.ordinal]?.color = Color.BLACK
            palette[i][HOUR_HAND.ordinal]?.style = Paint.Style.STROKE

            palette[i][ARC_SHADOW.ordinal]?.color = Color.WHITE
//            palette[i][MONTHBOX_SHADOW.ordinal]?.color = Color.WHITE
            palette[i][BIG_SHADOW.ordinal]?.color = Color.WHITE

//            palette[i][MONTHBOX_TEXT.ordinal]?.color = Color.BLACK
            palette[i][BIG_TEXT_AND_LINES.ordinal]?.color = Color.BLACK
            palette[i][BIG_SHADOW.ordinal]?.color = Color.WHITE
        }
    }

    /**
     * This will return a Paint of a given style and brushId, where those are the constants
     * defined in this file. Anything else will cause an exception.
     */
    operator fun get(style: Style, brushId: Brush): Paint =
        requireNotNull(palette[style.ordinal][brushId.ordinal]) {
            "undefined paintcan color, style($style), brushId($brushId)"
        }

    companion object : AnkoLogger {
        private fun colorFunc(argb: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeJoin = Paint.Join.BEVEL
            color = argb
            style = Paint.Style.FILL
        }

        private val colorFuncMemo = ::colorFunc.memoize()

        /**
         * This gets a Paint of a given color. If the same color is requested more than once,
         * the Paint will only be created once and is internally cached. We use this for the
         * calendar wedges, where the colors are coming from the user's calendar.
         *
         * This is *not* meant for use in low-bit mode, when anti-aliasing is forbidden.
         * In that mode, we behave completely differently, drawing thin arcs rather than
         * large wedges.
         */
        fun getCalendarPaint(argb: Int): Paint = colorFuncMemo(argb)

        /**
         * This gets a greyscale Paint based on the given color, otherwise behaving
         * the same as [getCalendarPaint].
         */
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
            val a =
                (argb and 0xff000000.toInt()) shr 24 // toInt because of a Kotlin bug: https://youtrack.jetbrains.com/issue/KT-4749
            val y = (argbToLuminance(argb) * 255f).toInt()
            return a shl 24 or (y shl 16) or (y shl 8) or y
        }

        /**
         * This generates all the Paint that we'll need for drawing the watchface. These are all cached.
         */
        private fun watchfacePaint(
            _argb: Int,
            _style: Style,
            _textSize: Float,
            _strokeWidth: Float,
            forceColorAmbient: Boolean = false
        ) =
        // underscores in the formal parameters to fix the variable shadowing in the apply block, below
            Paint(Paint.SUBPIXEL_TEXT_FLAG or Paint.HINTING_ON).apply {
                strokeCap = Paint.Cap.SQUARE
                strokeWidth = _strokeWidth
                textSize = _textSize
                style = Paint.Style.FILL
                textAlign = Paint.Align.CENTER
                isAntiAlias = when (_style) {
                    Style.NORMAL, Style.AMBIENT, Style.AMBIENT_ANTI_BURNIN -> true
                    else -> false // anti-aliasing is forbidden in the low-bit modes
                }

                // In specific cases, we're going to force colors to be colorful even in ambient mode (see below)
                val effectiveStyle = if (_style == Style.AMBIENT && forceColorAmbient) Style.NORMAL else _style

                color = when (effectiveStyle) {
                    Style.NORMAL -> _argb

                    Style.LOWBIT, Style.LOWBIT_ANTI_BURNIN -> when (_argb) {
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

                    Style.AMBIENT, Style.AMBIENT_ANTI_BURNIN -> argbToGreyARGB(_argb)

                    else -> {
                        error { "watchfacePaint: unknown style: $_style" }
                        0
                    }
                }
            }

        val COMPLICATION_BG_COLOR = watchfacePaint(0x80000000.toInt(), Style.NORMAL, 0f, 0f).color
        val COMPLICATION_FG_COLOR = watchfacePaint(Color.WHITE, Style.NORMAL, 0f, 0f).color
    }
}
