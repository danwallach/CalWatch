package org.dwallach.complications

import android.support.v4.content.ContextCompat
import org.dwallach.R
import org.jetbrains.anko.*

class ConfigActivityUI: AnkoComponent<AnalogComplicationConfigActivity> {

    // Useful help:
    // http://macoscope.com/blog/kotlin-anko-layouts/

    override fun createView(ui: AnkoContext<AnalogComplicationConfigActivity>) = with(ui) {
        val previewSize = ctx.resources.getDimension(R.dimen.analog_complication_settings_preview_size).toInt()
        // note: we could complicate this by getting the pixel density, dividing, then converting back
        // to dp, but it's unclear if this is necessary.
        // https://stackoverflow.com/questions/11121028/load-dimension-value-from-res-values-dimension-xml-from-source-code

        frameLayout {
            lparams(width = matchParent, height = matchParent)
            backgroundColor = ContextCompat.getColor(ctx, R.color.dark_grey)
            horizontalPadding = dip(24)

            listView {
                // first, the complications and such
                frameLayout {
                    lparams(width = previewSize, height = previewSize)

                    imageView(R.)

                }

            }
        }
    }
}
