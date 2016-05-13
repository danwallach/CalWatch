/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.drawable.AnimationDrawable
import android.os.Build
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.CardView
import android.view.Menu
import android.view.MenuItem
import android.view.View

import java.util.Observable
import java.util.Observer

import kotlinx.android.synthetic.main.activity_phone_intl.*
import org.jetbrains.anko.*

class PhoneActivity : Activity(), Observer, AnkoLogger {
    private var disableUICallbacks = false

    private fun select(card: CardView, button: View, selected: Boolean) {
        val bgColor = ContextCompat.getColor(this, if (selected) R.color.calWatchDark else R.color.calWatchPrimary)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // TODO: do something useful with the button, not just the card -- tinting?
            card.setCardBackgroundColor(bgColor)
        } else {
            // On Android 4.4, setting the Card background color does nothing, and there's no paint filling either,
            // so we'll fall back to monkeying around with the button itself
            button.backgroundColor = bgColor
        }
    }

    /**
     * This method loads the UI state with the given mode (ClockState.FACE_NUMBERS, etc.) and other
     * features. Call this whenever necessary.
     */
    private fun setFaceModeUI(mode: Int, showSecondsP: Boolean, showDayDateP: Boolean, showStepCounterP: Boolean) {
        verbose { "setFaceModeUI: mode($mode), showSecondsP($showSecondsP), showDayDateP($showDayDateP), showStepCounterP($showStepCounterP)" }
        if (!uiButtonsReady()) {
            verbose("trying to set UI mode without buttons active yet")
            return
        }

        disableUICallbacks = true

        try {
            // while we're here, we'll also put the proper day/date into the relevant button
            val dayOfWeek = TimeWrapper.localDayOfWeek()
            val monthDay = TimeWrapper.localMonthDay()
            dayDateButton.text = "$monthDay\n$dayOfWeek"

            when(ClockState.faceMode) {
                ClockState.FACE_NUMBERS -> {
                    select(toolCard, toolButton, false)
                    select(numbersCard, numbersButton, true)
                    select(liteCard, liteButton, false)
                }
                ClockState.FACE_LITE -> {
                    select(toolCard, toolButton, false)
                    select(numbersCard, numbersButton, false)
                    select(liteCard, liteButton, true)
                }
                ClockState.FACE_TOOL -> {
                    select(toolCard, toolButton, true)
                    select(numbersCard, numbersButton, false)
                    select(liteCard, liteButton, false)
                }
            }

            select(secondsImageCard, secondsImageButton, ClockState.showSeconds)
            select(stepCountImageCard, stepCountImageButton, ClockState.showStepCounter)
            select(dayDateCard, dayDateButton, ClockState.showDayDate)

        } catch (throwable: Throwable) {
            // probably a called-from-wrong-thread-exception, we'll just ignore it
            verbose("ignoring exception while updating button state")
        }

        disableUICallbacks = false
    }

    private fun uiButtonsReady() :Boolean =
            toolButton != null && numbersButton != null && liteButton != null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        verbose("Create!")

        setContentView(R.layout.activity_phone_intl)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        //
        // We're using an animated button for letting the user select the seconds hand; the blurb
        // below is necessary to start the animation.
        //
        // http://developer.android.com/guide/topics/graphics/drawable-animation.html
        //

        val secondsAnim = secondsImageButton.drawable as AnimationDrawable

        if(hasFocus)
            secondsAnim.start()
        else
            secondsAnim.stop()
    }

    override fun onPause() {
        super.onPause()
        verbose("Pause!")

        surfaceView.pause(this)

        ClockState.deleteObserver(this)
    }

    private fun setupClickListener(clickMe: View?, logMe: String, callMe: () -> Unit) {
        clickMe?.setOnClickListener {
            verbose(logMe)
            callMe()

            ClockState.pingObservers() // side-effect: will call back to PhoneActivity to update the UI and save state
            surfaceView.invalidate() // redraws the clock
        }
    }

    override fun onStart() {
        super.onStart()
        verbose("Start!")

        if(!uiButtonsReady()) {
            error("UI buttons aren't ready yet")
        } else {
            setupClickListener(liteButton, "lite-mode selected") {
                ClockState.faceMode = ClockState.FACE_LITE
            }

            setupClickListener(toolButton, "tool-mode selected") {
                ClockState.faceMode = ClockState.FACE_TOOL
            }

            setupClickListener(numbersButton, "numbers-mode selected") {
                ClockState.faceMode = ClockState.FACE_NUMBERS
            }

            // Each of these handles the big image buttons that we want to also cause
            // their switches to toggle.

            setupClickListener(secondsImageButton, "seconds toggle") {
                ClockState.showSeconds = !ClockState.showSeconds
            }

            setupClickListener(dayDateButton, "dayDate toggle") {
                ClockState.showDayDate = !ClockState.showDayDate
            }

            setupClickListener(stepCountImageButton, "stepCount toggle") {
                ClockState.showStepCounter = !ClockState.showStepCounter
            }
        }

        WatchCalendarService.kickStart(this)  // bring it up, if it's not already up
        PreferencesHelper.loadPreferences(this)
        CalendarPermission.init(this)
        GoogleApiWrapper.startConnection(this) { verbose { "GoogleApi ready" } }

        surfaceView.init(this)
        surfaceView.initCalendarFetcher(this)

        onResume()
        verbose("activity setup complete")
    }

    override fun onResume() {
        super.onResume()
        verbose("Resume!")

        GoogleApiWrapper.startConnection(this) { verbose { "GoogleApi ready" } }
        ClockState.addObserver(this)
        surfaceView.resume(this)
        surfaceView.invalidate()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        //        getMenuInflater().inflate(R.menu.phone, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return super.onOptionsItemSelected(item)
    }

    override fun update(observable: Observable?, data: Any?) {
        verbose("Noticed a change in the clock state; saving preferences")

        setFaceModeUI(ClockState.faceMode, ClockState.showSeconds, ClockState.showDayDate, ClockState.showStepCounter)
        PreferencesHelper.savePreferences(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        verbose("onRequestPermissionsResult")

        CalendarPermission.handleResult(requestCode, permissions, grantResults)
        surfaceView.initCalendarFetcher(this)

        verbose("finishing PermissionActivity")
    }

    companion object: AnkoLogger {
        /**
         * This will be called when the user clicks on the watchface, presumably because they want
         * us to request calendar permissions.
         */
        fun watchfaceClick(view: MyViewAnim) {
            verbose("Watchface clicked!")

            val activity = view.toActivity()

            if (!ClockState.calendarPermission) {
                verbose("Requesting permissions")
                CalendarPermission.request(activity)
                view.initCalendarFetcher(activity)
            } else {
                verbose("Permissions already granted.")
            }
        }
    }
}

/**
 * Helper extension function to convert from a view to its surrounding activity, if that activity exists.
 */
fun View.toActivity(): Activity {
    // See: http://stackoverflow.com/questions/8276634/android-get-hosting-activity-from-a-view
    var context = this.context
    while(context is ContextWrapper) {
        if(context is Activity) {
            return context
        } else {
            context = context.baseContext
        }
    }
    throw NoSuchFieldError("no activity found for this view") // shouldn't ever happen
}
