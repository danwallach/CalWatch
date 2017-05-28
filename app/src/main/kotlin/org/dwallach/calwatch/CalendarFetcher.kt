/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch

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
                      val contentUri: Uri = WearableCalendarContract.Instances.CONTENT_URI,
                      val authority: String = WearableCalendarContract.AUTHORITY): AnkoLogger {
    // this will fire when it's time to (re)load the calendar, launching an asynchronous
    // task to do all the dirty work and eventually update ClockState
    private val contextRef = WeakReference(initialContext)
    private var isReceiverRegistered: Boolean = false
    private val instanceID = instanceCounter++

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            verbose { "receiver: got intent message.  action(${intent.action}), data(${intent.data}), toString($intent)" }
            if (Intent.ACTION_PROVIDER_CHANGED == intent.action) {

                // Google's reference code also checks that the Uri matches intent.getData(), but the URI we're getting back via intent.getData() is:
                // content://com.google.android.wearable.provider.calendar
                //
                // versus the URL we're looking for in the first place:
                // content://com.google.android.wearable.provider.calendar/instances/when

                // Solution? Screw it. Whatever we get, we don't care, we'll reload the calendar.

                verbose("receiver: time to load new calendar data")
                rescan()
            }
        }
    }

    init {
        verbose { "here begins CalendarFetcher #$instanceID" }
        singletonFetcher?.kill() // clear out the old singleton fetcher, if it's there
        singletonFetcher = this // and now the newbie becomes the singleton

        // hook into watching the calendar (code borrowed from Google's calendar wear app)
        verbose("setting up intent receiver")
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
        verbose { "killing CalendarFetcher #$instanceID" }

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
        if(!isReceiverRegistered) {
            // this means that we're reusing a `killed` CalendarFetcher, which is bad, because it
            // won't be listening to the broadcasts any more. Log so we can discover it, but otherwise
            // continue running. At least it will update once an hour...
            error { "no receiver registered! (CalendarFetcher #$instanceID)" }
        }

        if(scanInProgress) {
            return
        } else {
            verbose { "rescan starting asynchronously (CalendarFetcher #$instanceID)" }
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
    private fun loadContent(context: Context): Pair<List<WireEvent>, Exception?> {
        // local state which we'll eventually return
        var cr = emptyList<WireEvent>()

        // first, get the list of calendars
        verbose { "loadContent: starting to load content (CalendarFetcher #$instanceID)" }

        TimeWrapper.update()
        val time = TimeWrapper.gmtTime
        val queryStartMillis = TimeWrapper.localFloorHour - TimeWrapper.gmtOffset
        val queryEndMillis = queryStartMillis + 24.hours

        try {
            verbose { "loadContent: Query times... Now: %s, QueryStart: %s, QueryEnd: %s"
                    .format(DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME),
                            DateUtils.formatDateTime(context, queryStartMillis, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE),
                            DateUtils.formatDateTime(context, queryEndMillis, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE)) }
        } catch (t: Throwable) {
            // sometimes the date formatting blows up... who knew? best to just ignore and move on
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
        try {
            val builder = contentUri.buildUpon()
            ContentUris.appendId(builder, queryStartMillis)
            ContentUris.appendId(builder, queryEndMillis)
            val iCursor = context.contentResolver.query(builder.build(),
                    instancesProjection, null, null, null)

            // if it's null, which shouldn't ever happen, then we at least won't gratuitously fail here
            if (iCursor != null) {
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
                            cr += WireEvent(startTime, endTime, displayColor)

                    } while (iCursor.moveToNext())
                    verbose {"loadContent: visible instances found: ${cr.size}" }
                }

                // lifecycle cleanliness: important to close down when we're done
                iCursor.close()
            }

            if (!cr.isEmpty()) {
                // Primary sort: color, so events from the same calendar will become consecutive wedges

                // Secondary sort: endTime, with objects ending earlier appearing first in the sort.
                //   (goal: first fill in the outer ring of the display with smaller wedges; the big
                //    ones will end late in the day, and will thus end up on the inside of the watchface)

                // Third-priority sort: startTime, with objects starting later (smaller) appearing first in the sort.

                return Pair(cr.sortedWith(
                        compareBy<WireEvent> { it.displayColor }
                                .thenBy { it.endTime }
                                .thenByDescending { it.startTime }),
                        null)
            } else {
                return Pair(emptyList(), null)
            }
        } catch (e: SecurityException) {
            // apparently we don't have permission for the calendar!
            error("security exception while reading calendar!", e)
            kill()
            ClockState.calendarPermission = false

            return Pair(emptyList(), e)
        }
    }

    /**
     * Starts an asynchronous task to load the calendar
     */
    private fun runAsyncLoader() {
        val context = getContext() ?: return
        // nothing to do without a context, so give up, surrender!

        if(this != singletonFetcher)
            warn { "not using the singleton fetcher! (CalendarFetcher me #$instanceID), singleton #${singletonFetcher?.instanceID})" }

        //
        // Why a wake-lock? In part, because the Google sample code does it this way, and in part
        // because it makes sense. We want this task to finish quickly. In practice, the "total
        // calendar computation" time reported below seems to be around a second or less -- running
        // once an hour -- so we're not killing the battery in any case.
        //
        val wakeLock = context.powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CalWatchWakeLock")
        wakeLock.acquire(10.seconds) // 10 seconds is more than we'll ever need

        verbose { "async: wake lock acquired (CalendarFetcher #$instanceID)" }

        //
        // This is the Anko library being awesome. Rather than using AsyncTask, which can get
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
        //
        var result: Pair<List<WireEvent>, Throwable?>
        doAsync {
            try {
                val startTime = SystemClock.elapsedRealtimeNanos()
                result = loadContent(context)
                val endTime = SystemClock.elapsedRealtimeNanos()
                info { "async: total calendar computation time: %.3f ms".format((endTime - startTime) / 1000000.0) }
            } catch (e: Throwable) {
                // pass the exception back to the UI thread; we'll log it there
                result = Pair(emptyList(), e)
            } finally {
                // no matter what, we want to release the wake lock
                wakeLock.release()
            }

            //
            // This will run after the above chunk of the async completes, which could be a while
            // if the watch goes into "doze" mode, but it will definitely happen when the screen is
            // being redrawn, which is what we care about. Pretty much all that's going on here are
            // the calls to ClockState, that make the new calendar state visible the next time
            // the screen is being redrawn.
            //
            uiThread {
                scanInProgress = false
                val (wireEventList, e) = result

                if (e != null) {
                    verbose( { "uiThread: failure in async computation (CalendarFetcher #$instanceID)" }, e)
                } else {
                    ClockState.setWireEventList(wireEventList)
                    ClockState.pingObservers()
                }

                verbose { "uiThread: complete (CalendarFetcher #$instanceID)" }
            }
        }
    }

    companion object {
        private var singletonFetcher: CalendarFetcher? = null
        private var scanInProgress: Boolean = false
        private var instanceCounter: Int = 0 // ID numbers for tracking / better logging

        fun requestRescan() {
            singletonFetcher?.rescan()
        }

        private fun calendarColorFix(calendarColor: Int, eventColor: Int) =
                if (eventColor != 0) eventColor
                else if (calendarColor != 0) calendarColor
                else Color.DKGRAY
    }
}


