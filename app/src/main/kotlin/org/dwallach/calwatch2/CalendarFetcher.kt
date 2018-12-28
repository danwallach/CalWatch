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
import kotlinx.coroutines.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error
import org.jetbrains.anko.info
import org.jetbrains.anko.warn

import java.lang.ref.WeakReference
import kotlin.comparisons.compareBy
import kotlin.comparisons.thenBy
import kotlin.comparisons.thenByDescending


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
                      private val authority: String = WearableCalendarContract.AUTHORITY,
                      private val backupAuthority: String = CalendarContract.AUTHORITY): AnkoLogger {

    private val contextRef = WeakReference(initialContext)
    private var isReceiverRegistered: Boolean = false
    private val instanceID = ++instanceCounter

    override fun toString() =
        "CalendarFetcher(contextRef(%s), authority($authority), contentUri($contentUri), isReceiverRegistered($isReceiverRegistered), instanceId($instanceID))"
                .format(if (contextRef.get() == null) "null" else "non-null")

    private fun onReceiveBroadcastHandler(context: Context, intent: Intent, authority: String) {
        info { "broadcastReceiver: GOT INTENT MESSAGE! action(${intent.action}), data(${intent.data}), toString($intent), authority($authority)" }
        if (Intent.ACTION_PROVIDER_CHANGED == intent.action) {

            // Google's reference code also checks that the Uri matches intent.getData(), but the URI we're getting back via intent.getData() is:
            // content://com.google.android.wearable.provider.calendar
            //
            // versus the URL we're looking for in the first place:
            // content://com.google.android.wearable.provider.calendar/instances/when

            // Solution? Screw it. Whatever we get, we don't care, we'll reload the calendar.

            info("broadcastReceiver: time to load new calendar data")
            rescan(context)
        } else {
            warn { "broadcastReceiver: IGNORING INTENT: action(${intent.action}), data(${intent.data}), toString($intent), authority($authority)" }
        }
    }

    // Why do we have two separate BroadcastReceiver instances? In part, because we were mysteriously
    // not receiving these intents at one point and this was a form of paranoia. Also, it doesn't seem
    // to hurt anything. If, for whatever broken reason, both receivers get triggered, then the first
    // one will launch the background task to read the calendar, and the second will notice that the
    // first one is in progress and will become a no-op.

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onReceiveBroadcastHandler(context, intent, authority)
    }

    private val broadcastReceiverBackup = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onReceiveBroadcastHandler(context, intent, backupAuthority)
    }

    init {
        info { "HERE BEGINS CalendarFetcher #$instanceID" }
        singletonFetcher?.kill() ?: info { "No prior leftover singleton fetcher (CalendarFetcher #$instanceID)" }
        singletonFetcher = this // and now the newbie becomes the singleton

        // hook into watching the calendar (code borrowed from Google's calendar wear app)
        info { "setting up intent receiver (CalendarFetcher #$instanceID)" }
        val filter = IntentFilter(Intent.ACTION_PROVIDER_CHANGED).apply {
            addDataScheme("content")
            addDataAuthority(authority, null)
        }
        initialContext.registerReceiver(broadcastReceiver, filter)

        // desperate times call for desperate measures -- trying the "wrong" calendar contract
        val filterBackup = IntentFilter(Intent.ACTION_PROVIDER_CHANGED).apply {
            addDataScheme("content")
            addDataAuthority(backupAuthority, null)
        }
        initialContext.registerReceiver(broadcastReceiverBackup, filterBackup)


        isReceiverRegistered = true

        // kick off initial loading of calendar state
        rescan(initialContext)
    }

    private fun getContext(): Context? = contextRef.get()

    /**
     * Call this when the CalendarFetcher is no longer going to be used. This will get rid
     * of broadcast receivers and other such things. Once you do this, the CalendarFetcher
     * cannot be used any more. Make a new one if you want to restart things later.
     */
    fun kill() {
        warn { "killing, state = $currentState" }

        val context: Context? = getContext()

        if (isReceiverRegistered && context != null) {
            context.unregisterReceiver(broadcastReceiver)
            context.unregisterReceiver(broadcastReceiverBackup)
        }

        isReceiverRegistered = false
    }

    /**
     * This will start asynchronously loading the calendar. The results will eventually arrive
     * in ClockState, and any Observers of ClockState will be notified.
     */
    private fun rescan(context: Context? = null) {
        if (!isReceiverRegistered) {
            // this means that we're reusing a `killed` CalendarFetcher, which is bad, because it
            // won't be listening to the broadcasts any more. Log so we can discover it, but otherwise
            // continue running. At least it will update once an hour...
            error { "rescan: no receiver registered! (CalendarFetcher #$instanceID)" }
        }

        if (scanInProgress) {
            warn { "rescan: scan in progress, not doing another scan! (CalendarFetcher #$instanceID)" }
            return
        } else {
            info { "rescan: starting asynchronously (CalendarFetcher #$instanceID)" }
            scanInProgress = true
            runAsyncLoader(context ?: getContext())
        }
    }

    private fun calendarColorFix(calendarColor: Int, eventColor: Int) = when {
        eventColor != 0 -> eventColor
        calendarColor != 0 -> calendarColor
        else -> Color.DKGRAY
    }

    /**
     * Queries the calendar database with proper Android APIs (ugly stuff). Note that we're returning
     * two things: a list of events -- the thing we really want -- and an optional exception, which
     * would be accompanied by an emptyList if it were non-null. We're doing this, rather than throwing
     * an exception, because this result is meant to be saved across threads, which a thrown exception
     * can't do very well.
     */
    private fun loadContent(context: Context): List<CalendarEvent>? {
        val lFetchCounter = ++fetchCounter

        // local state which we'll eventually return
        val cr = mutableListOf<CalendarEvent>()

        // first, get the list of calendars
        info { "loadContent: starting to load content, fetchCounter($lFetchCounter) (CalendarFetcher #$instanceID)" }

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

        try {
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
        } catch (e: SecurityException) {
            error("unexpected security exception while reading calendar", e)
            kill()
            scanInProgress = false
            ClockState.calendarPermission = false
            return null
        }

        // Primary sort: color, so events from the same calendar will become consecutive wedges

        // Secondary sort: endTime, with objects ending earlier appearing first in the sort.
        //   (goal: first fill in the outer ring of the display with smaller wedges; the big
        //    ones will end late in the day, and will thus end up on the inside of the watchface)

        // Third-priority sort: startTime, with objects starting later (smaller) appearing first in the sort.
        return cr.sortedWith(
                compareBy<CalendarEvent> { it.displayColor }
                        .thenBy { it.endTime }
                        .thenByDescending { it.startTime })
    }

    /**
     * Starts an asynchronous task to load the calendar
     */
    private fun runAsyncLoader(context: Context?) {
        if (context == null) {
            error { "runAsyncLoader: no context, cannot load calendar" }
            scanInProgress = false
            return
        }

        if (this != singletonFetcher)
            warn { "runAsyncLoader: not using the singleton fetcher! (CalendarFetcher me #$instanceID), singleton #${singletonFetcher?.instanceID})" }


        // Behold! The power of Kotlin 1.3's coroutines, with bonus wildly incomplete documentation!
        // https://github.com/Kotlin/kotlinx.coroutines/blob/master/ui/coroutines-guide-ui.md
        // https://medium.com/@andrea.bresolin/playing-with-kotlin-in-android-coroutines-and-how-to-get-rid-of-the-callback-hell-a96e817c108b

        // In a gratuitously short nutshell:

        // There are two universes in Kotlin: the "regular" universe and the "suspend" universe.
        // (More formally, these are CoroutineScopes.)

        // In the "suspend" universe, functions and methods know how to suspend themselves and
        // save behind a continuation so they can resume later. The call to GlobalScope.launch
        // is the bridge from the "regular" universe to the "suspend" universe where we can call
        // things that might want to go in the background.

        // The call to withContext() creates a task that happily runs in the background, on
        // some other thread, that will eventually return a value that we'll read here, back
        // on the UI thread again! This is only possible when we're in the "suspend" universe,
        // but note that we don't have to declare any "suspend" functions nor do we have to
        // call await() for the results to come back. That's all handled by withContext().

        GlobalScope.launch {
            info { "runAsyncLoader: here we go! (CalendarFetcher #$instanceID)" }
            // Asynchronously fetches the content, then runs the rest of the block on the UI thread
            // once we get back.
            val eventList = withContext(Dispatchers.Default) {
                val startTimeNano = SystemClock.elapsedRealtimeNanos()
                val result = loadContent(context)
                val endTimeNano = SystemClock.elapsedRealtimeNanos()
                info { "runAsyncLoader: total calendar fetch time: %.3f ms".format((endTimeNano - startTimeNano) / 1000000.0) }
                result
            }

            if (eventList == null) {
                warn { "runAsyncLoader: No result, not updating any calendar state (CalendarFetcher #$instanceID)" }
                scanInProgress = false
            } else {
                info { "runAsyncLoader: success reading the calendar (CalendarFetcher #$instanceID)" }
                val layoutPair = withContext(Dispatchers.Default) {
                    val startTimeNano = SystemClock.elapsedRealtimeNanos()
                    val result = ClockStateHelper.clipToVisible(eventList)
                    val endTimeNano = SystemClock.elapsedRealtimeNanos()
                    info { "runAsyncLoader: total calendar layout time: %.3f ms".format((endTimeNano - startTimeNano) / 1000000.0) }
                    result
                }
                scanInProgress = false
                ClockState.setWireEventList(eventList, layoutPair)
                Utilities.redrawEverything()
            }
            info { "runAsyncLoader: background tasks complete (CalendarFetcher #$instanceID)" }
        }
        info { "runAsyncLoader: Heading back to uiThread async tasks running (CalendarFetcher #$instanceID)" }
    }

    companion object: AnkoLogger {
        // Declaring these volatile is probably overkill, but they may be read from different threads, so
        // we want changes to propagate immediately. scanInProgress is particularly important for this,
        // since it's how we prevent running multiple simultaneous queries to the calendar provider.
        @Volatile private var singletonFetcher: CalendarFetcher? = null
        @Volatile private var scanInProgress: Boolean = false
        @Volatile private var instanceCounter: Int = -1 // ID numbers for tracking / better logging
        @Volatile private var fetchCounter: Int = -1 // ID numbers for tracking / better logging

        private val currentState: String
            get() = "singletonFetcher(%s), scanInProgress($scanInProgress), instanceCounter($instanceCounter)"
                    .format(singletonFetcher?.toString() ?: "null")

        fun requestRescan() {
            info { "requestRescan: $currentState" }
            singletonFetcher?.rescan()
        }
    }
}
