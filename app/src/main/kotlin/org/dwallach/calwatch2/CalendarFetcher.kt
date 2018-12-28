/*
 * CalWatch
 * Copyright (C) 2014-2018 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch2

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.provider.CalendarContract
import android.support.wearable.provider.WearableCalendarContract
import android.text.format.DateUtils

import java.lang.ref.WeakReference
import kotlin.comparisons.compareBy
import kotlin.comparisons.thenBy
import kotlin.comparisons.thenByDescending

import org.jetbrains.anko.*

/**
 * This class handles all the dirty work of asynchronously loading calendar data from the calendar provider
 * and updating the state in ClockState. The constructor arguments, contentUri and authority, are used
 * to specify what database Uri we're querying and what "authority" we're using to set up the IntentFilter.
 * These are different on wear vs. mobile, so are specified in those subdirectories. Everything else
 * is portable.
 *
 * Internally, this class registers a BroadcastReceiver such that it will detect and deal with updates
 * to the calendar itself. External users can call CalendarFetcher.requestRescan(), which will cause
 * the most recently created CalendarFetcher to do the work. This is something worth doing at the top
 * of the hour, when it's time to update the calendar.
 */
class CalendarFetcher(initialContext: Context,
                      private val contentUri: Uri = WearableCalendarContract.Instances.CONTENT_URI,
                      private val authority: String = WearableCalendarContract.AUTHORITY): AnkoLogger {
    // this will fire when it's time to (re)load the calendar, launching an asynchronous
    // task to do all the dirty work and eventually update ClockState
    private val contextRef = WeakReference(initialContext)
    private var isReceiverRegistered: Boolean = false
    private val instanceID = instanceCounter++

    override fun toString() =
        "CalendarFetcher(contextRef(%s), authority($authority), contentUri($contentUri), isReceiverRegistered($isReceiverRegistered), instanceId($instanceID))"
                .format(if (contextRef.get() == null) "null" else "non-null")

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            info { "receiver: got intent message.  action(${intent.action}), data(${intent.data}), toString($intent)" }
            if (Intent.ACTION_PROVIDER_CHANGED == intent.action) {

                // Google's reference code also checks that the Uri matches intent.getData(), but the URI we're getting back via intent.getData() is:
                // content://com.google.android.wearable.provider.calendar
                //
                // versus the URL we're looking for in the first place:
                // content://com.google.android.wearable.provider.calendar/instances/when

                // Solution? Screw it. Whatever we get, we don't care, we'll reload the calendar.

                info("receiver: time to load new calendar data")
                rescan()
            } else {
                warn { "receiver: ignoring intent: action(${intent.action}), data(${intent.data}), toString($intent)" }
            }
        }
    }

    init {
        warn { "here begins CalendarFetcher #$instanceID" }
        singletonFetcher?.kill() // clear out the old singleton fetcher, if it's there
        singletonFetcher = this // and now the newbie becomes the singleton

        // hook into watching the calendar (code borrowed from Google's calendar wear app)
        info("setting up intent receiver")
        val filter = IntentFilter(Intent.ACTION_PROVIDER_CHANGED).apply {
            addDataScheme("content")
            addDataAuthority(authority, null)
        }
        initialContext.registerReceiver(broadcastReceiver, filter)
        isReceiverRegistered = true

        // kick off initial loading of calendar state
        rescan()
    }

    private fun getContext(): Context? {
        return contextRef.get()
    }

    /**
     * Call this when the CalendarFetcher is no longer going to be used. This will get rid
     * of broadcast receivers and other such things. Once you do this, the CalendarFetcher
     * cannot be used any more. Make a new one if you want to restart things later.
     */
    fun kill() {
        warn { "killing, state = " + currentState() }

        val context: Context? = getContext()

        if (isReceiverRegistered && context != null) {
            context.unregisterReceiver(broadcastReceiver)
        }

        isReceiverRegistered = false
    }

    /**
     * This will start asynchronously loading the calendar. The results will eventually arrive
     * in ClockState, and any Observers of ClockState will be notified.
     */
    private fun rescan() {
        if (!isReceiverRegistered) {
            // this means that we're reusing a `killed` CalendarFetcher, which is bad, because it
            // won't be listening to the broadcasts any more. Log so we can discover it, but otherwise
            // continue running. At least it will update once an hour...
            error { "no receiver registered! (CalendarFetcher #$instanceID)" }
        }

        if (scanInProgress) {
            warn { "scan in progress, not doing another scan! (CalendarFetcher #$instanceID)" }
            return
        } else {
            info { "rescan starting asynchronously (CalendarFetcher #$instanceID)" }
            scanInProgress = true
            runAsyncLoader()
        }
    }

    /**
     * Queries the calendar database with proper Android APIs (ugly stuff). Note that we're returning
     * two things: a list of events -- the thing we really want -- and an optional exception, which
     * would be accompanied by an emptyList if it were non-null. We're doing this, rather than throwing
     * an exception, because this result is meant to be saved across threads, which a thrown exception
     * can't do very well.
     */
    private fun loadContent(context: Context): List<CalendarEvent> {
        // local state which we'll eventually return
        val cr = mutableListOf<CalendarEvent>()

        // first, get the list of calendars
        info { "loadContent: starting to load content (CalendarFetcher #$instanceID)" }

        TimeWrapper.update()
        val time = TimeWrapper.gmtTime
        val queryStartMillis = TimeWrapper.localFloorHour - TimeWrapper.gmtOffset
        val queryEndMillis = queryStartMillis + 24.hours

        try {
            info {
                "loadContent: Query times... Now: %s, QueryStart: %s, QueryEnd: %s"
                        .format(DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME),
                                DateUtils.formatDateTime(context, queryStartMillis, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE),
                                DateUtils.formatDateTime(context, queryEndMillis, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE))
            }
        } catch (th: Throwable) {
            // sometimes the date formatting blows up... who knew? best to just ignore and move on
            warn("loadContent: date-formatter blew up while trying to log, ignoring", th)
        }

        // And now, the event instances

        val instancesProjection = arrayOf(
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.CALENDAR_COLOR,
                CalendarContract.Instances.EVENT_COLOR,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.VISIBLE)

        // Note: we used to use DISPLAY_COLOR, but that's now deprecated on Wear 2.0 because reasons.
        // https://issuetracker.google.com/issues/38476499

        // now, get the list of events
        val builder = contentUri.buildUpon()
        ContentUris.appendId(builder, queryStartMillis)
        ContentUris.appendId(builder, queryEndMillis)
        val iCursor = context.contentResolver.query(builder.build(),
                instancesProjection, null, null, null)

        // if it's null, which shouldn't ever happen, then we at least won't gratuitously fail here
        if (iCursor == null) {
            warn("Got null cursor, no events!")
        } else {
            if (iCursor.moveToFirst()) {
                do {
                    var i = 0

                    val startTime = iCursor.getLong(i++)
                    val endTime = iCursor.getLong(i++)
                    i++ // val eventID = iCursor.getLong(i++)
                    val displayColor = calendarColorFix(iCursor.getInt(i++), iCursor.getInt(i++))
                    val allDay = iCursor.getInt(i++) != 0
                    val visible = iCursor.getInt(i) != 0

                    if (visible && !allDay)
                        cr.add(CalendarEvent(startTime, endTime, displayColor))

                } while (iCursor.moveToNext())
                info { "loadContent: visible instances found: ${cr.size}" }
            }

            // lifecycle cleanliness: important to close down when we're done
            iCursor.close()
        }

        // Primary sort: color, so events from the same calendar will become consecutive wedges

        // Secondary sort: endTime, with objects ending earlier appearing first in the sort.
        //   (goal: first fill in the outer ring of the display with smaller wedges; the big
        //    ones will end late in the day, and will thus end up on the inside of the watchface)

        // Third-priority sort: startTime, with objects starting later (smaller) appearing first in the sort.
        val sorted =
                cr.sortedWith(
                        compareBy<CalendarEvent> { it.displayColor }
                                .thenBy { it.endTime }
                                .thenByDescending { it.startTime })

        return sorted
    }

    /**
     * Starts an asynchronous task to load the calendar
     */
    private fun runAsyncLoader() {
        val context = getContext()

        if (context == null) {
            error { "no context, cannot load calendar" }
            scanInProgress = false
            return
        }

        if (this != singletonFetcher)
            warn { "not using the singleton fetcher! (CalendarFetcher me #$instanceID), singleton #${singletonFetcher?.instanceID})" }


        // Historical note: we used to acquire a wake lock here, in part because that's what
        // some sample Android code did while accessing the calendar. With Wear 2, this seemed
        // to cause weird problems, but only after reboot. Experimentally removing the wake lock
        // code fixed the weird problems, and everything still works. So, no more wake lock!

        // The code below is the Anko library being awesome. Rather than using AsyncTask, which can get
        // messy, we instead say "this block should run asynchronously, and oh by the way, run this
        // other code on the UI thread once the asynchronous bit is done". Spectacular. The main async
        // block will be producing its result in the result variable, declared below, which is then
        // picked up by the UI thread part. Note that we don't want to throw an exception from the
        // async code. Instead, we catch anything that might have gone wrong and just pass the exception
        // back as part of the result.
        //
        // Mea culpa: I used to think one of the big problem with Google's Go language was that they
        // didn't embrace exceptions for error handling. Now I get it. Exceptions don't play nicely
        // with asynchronous/concurrent computations, which is pretty much the whole point of Go.
        // That said, I still think Go is broken without parametric polymorphism. The Cassowary solver
        // that we're using was written in Java before Java had generics and there were even comments
        // next to all of the hash tables and such as to what their generic types should have been,
        // but when I tried to make those into real parametric Java type declarations, some things
        // didn't match up quite right.
        doAsync({ th ->
            if (th is SecurityException)
                ClockState.calendarPermission = false

            error("Failure in asyncTask!", th)
            kill()
            scanInProgress = false
        }) {
            val startTime = SystemClock.elapsedRealtimeNanos()
            val result = loadContent(context)
            val endTime = SystemClock.elapsedRealtimeNanos()
            info { "async: total calendar computation time: %.3f ms".format((endTime - startTime) / 1000000.0) }

            //
            // This will run after the above chunk of the async completes, which could be a while
            // if the watch goes into "doze" mode, but it will definitely happen when the screen is
            // being redrawn, which is what we care about. Pretty much all that's going on here are
            // the calls to ClockState, that make the new calendar state visible the next time
            // the screen is being redrawn.
            //
            uiThread {
                scanInProgress = false
                ClockState.setWireEventList(result)
                Utilities.redrawEverything()
                info { "uiThread: complete (CalendarFetcher #$instanceID)" }
            }
        }
    }

    companion object: AnkoLogger {
        private var singletonFetcher: CalendarFetcher? = null
        @Volatile private var scanInProgress: Boolean = false
        private var instanceCounter: Int = 0 // ID numbers for tracking / better logging

        private fun currentState() =
                "singletonFetcher(%s), scanInProgress($scanInProgress), instanceCounter($instanceCounter)"
                        .format(singletonFetcher?.toString() ?: "null")

        fun requestRescan() {
            info { "requestRescan: " + currentState() }
            singletonFetcher?.rescan()
        }

        private fun calendarColorFix(calendarColor: Int, eventColor: Int) = when {
            eventColor != 0 -> eventColor
            calendarColor != 0 -> calendarColor
            else -> Color.DKGRAY
        }
    }
}


