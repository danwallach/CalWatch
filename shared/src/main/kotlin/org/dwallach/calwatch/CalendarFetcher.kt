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
import android.net.Uri
import android.os.*
import android.provider.CalendarContract
import android.text.format.DateUtils
import android.util.Log

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
 */
class CalendarFetcher(initialContext: Context, val contentUri: Uri, val authority: String) {
    // this will fire when it's time to (re)load the calendar, launching an asynchronous
    // task to do all the dirty work and eventually update ClockState
    private val contextRef = WeakReference(initialContext)
    private val loaderHandler = MyHandler(this)
    private var isReceiverRegistered: Boolean = false

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.v(TAG, "receiver: got intent message.  action(${intent.action}), data(${intent.data}), toString(${intent.toString()})")
            if (Intent.ACTION_PROVIDER_CHANGED == intent.action) {

                // Google's reference code also checks that the Uri matches intent.getData(), but the URI we're getting back via intent.getData() is:
                // content://com.google.android.wearable.provider.calendar
                //
                // versus the URL we're looking for in the first place:
                // content://com.google.android.wearable.provider.calendar/instances/when

                // Solution? Screw it. Whatever we get, we don't care, we'll reload the calendar.

                Log.v(TAG, "receiver: time to load new calendar data")
                rescan()
            }
        }
    }

    init {
        singletonFetcher = this

        // hook into watching the calendar (code borrowed from Google's calendar wear app)
        Log.v(TAG, "setting up intent receiver")
        val filter = IntentFilter(Intent.ACTION_PROVIDER_CHANGED).apply {
            addDataScheme("content")
            addDataAuthority(authority, null)
        }
        initialContext.registerReceiver(broadcastReceiver, filter)
        isReceiverRegistered = true

        // kick off initial loading of calendar state
        rescan()
    }

    fun getContext(): Context? {
        return contextRef.get()
    }

    /**
     * Call this when the CalendarFetcher is no longer going to be used. This will get rid
     * of broadcast receivers and other such things. Once you do this, the CalendarFetcher
     * cannot be used any more. Make a new one if you want to restart things later.
     */
    fun kill() {
        Log.v(TAG, "kill")

        val context: Context? = getContext()

        if (isReceiverRegistered && context != null) {
            context.unregisterReceiver(broadcastReceiver)
        }

        loaderHandler.removeMessages(MyHandler.MSG_LOAD_CAL)

        isReceiverRegistered = false
        scanInProgress = false
    }

    private var scanInProgress: Boolean = false
    /**
     * This will start asynchronously loading the calendar. The results will eventually arrive
     * in ClockState, and any Observers of ClockState will be notified.
     */
    fun rescan() {
        if(!isReceiverRegistered) {
            // this means that we're reusing a `killed` CalendarFetcher, which is bad, because it
            // won't be listening to the broadcasts any more. Log so we can discover it, but otherwise
            // continue running. At least it will update once an hour...
            Log.e(TAG, "no receiver registered!")
        }

        if(scanInProgress) {
//            Log.v(TAG, "rescan already in progress, redundant rescan request ignored")
            return
        } else {
            Log.v(TAG, "rescan starting asynchronously")
            scanInProgress = true
            loaderHandler.sendEmptyMessage(MyHandler.MSG_LOAD_CAL)
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
        Log.v(TAG, "loadContent: starting to load content")

        TimeWrapper.update()
        val time = TimeWrapper.gmtTime
        val queryStartMillis = TimeWrapper.localFloorHour - TimeWrapper.gmtOffset
        val queryEndMillis = queryStartMillis + 86400000 // 24 hours later

        try {
            Log.v(TAG, "loadContent: Query times... Now: %s, QueryStart: %s, QueryEnd: %s"
                    .format(DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME),
                            DateUtils.formatDateTime(context, queryStartMillis, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE),
                            DateUtils.formatDateTime(context, queryEndMillis, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE)))
        } catch (t: Throwable) {
            // sometimes the date formatting blows up... who knew? best to just ignore and move on
        }

        // And now, the event instances

        val instancesProjection = arrayOf(
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.DISPLAY_COLOR,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.VISIBLE)

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
                        i++ // long eventID = iCursor.getLong(i++);
                        val displayColor = iCursor.getInt(i++)
                        val allDay = iCursor.getInt(i++) != 0
                        val visible = iCursor.getInt(i) != 0

                        if (visible && !allDay)
                            cr += WireEvent(startTime, endTime, displayColor)

                    } while (iCursor.moveToNext())
                    Log.v(TAG, "loadContent: visible instances found: ${cr.size}")
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
                        null);
            } else {
                return Pair(emptyList(), null)
            }
        } catch (e: SecurityException) {
            // apparently we don't have permission for the calendar!
            Log.e(TAG, "security exception while reading calendar!", e)
            kill()
            ClockState.calendarPermission = false

            return Pair(emptyList(), e)
        }
    }

    /**
     * Asynchronous task to load the calendar instances.
     */
    class CalLoaderTask internal constructor(private val fetcher: CalendarFetcher) : AsyncTask<Context, Void, Pair<List<WireEvent>,Exception?>>() {
        private lateinit var wakeLock: PowerManager.WakeLock

        override fun doInBackground(vararg contexts: Context): Pair<List<WireEvent>,Exception?> {
            if(contexts.size == 0) {
                Log.e(TAG, "doInBackground: context required!")
                return Pair(emptyList(), null)
            }

            //
            // Design note: we could have done this with fetcher.getContext(), but that has the
            // potential to return null, which we don't want. The way we're doing it, by passing
            // the non-null context as an argument to the background task, we'll never be here
            // unless we have a valid context for doing the necessary calculations.
            //
            // See MyHandler.handleMessage for the null-handling logic.
            //
            val context: Context = contexts[0]

            //
            // Why a wake-lock? In part, because the Google sample code does it this way, and in part
            // because it makes sense. We want this task to finish quickly. In practice, the "total
            // calendar computation" number, reported below seems to be around a second or less -- running
            // once an hour -- so we're not killing the battery in any case.
            //
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CalWatchWakeLock")
            wakeLock.acquire()

            Log.v(TAG, "doInBackground: wake lock acquired")

            try {
                val startTime = SystemClock.elapsedRealtimeNanos()
                val result = fetcher.loadContent(context)
                val endTime = SystemClock.elapsedRealtimeNanos()
                Log.i(TAG, "doInBackground: total calendar computation time: %.3f ms".format((endTime - startTime) / 1000000.0))
                return result
            } catch (e: Exception) {
                Log.e(TAG, "doInBackground: unexpected failure setting wire event list from calendar", e)
                return Pair(emptyList(), e);
            } finally {
                // no matter what, we want to release the wake lock
                wakeLock.release()
            }
        }

        override fun onPostExecute(results: Pair<List<WireEvent>,Exception?>) {
            //
            // this method gets called if the task completes; we'll be back running on the main UI
            // thread, so we can notify observers that there's new data available.
            //
            fetcher.scanInProgress = false
            if(results.second != null) {
                Log.v(TAG, "onPostException: failure in background computation", results.second)
            } else {
                //
                // This is the place where side-effects from the background computation happen.
                // Any subsequent redraw will use the new calendar wedges.
                //
                ClockState.setWireEventList(results.first)
                ClockState.pingObservers()
            }

            Log.v(TAG, "onPostExecute: complete")
        }

        override fun onCancelled() {
            //
            // this method gets called if the task is cancelled, which we never actually do (easier to
            // just let the task complete, since it doesn't take *that* long to execute)
            //
            wakeLock.release()
            fetcher.scanInProgress = false

            Log.v(TAG, "onCancelled")
        }
    }

    class MyHandler(private val fetcher: CalendarFetcher) : Handler() {
        private lateinit var loaderTask: AsyncTask<Context, Void, Pair<List<WireEvent>,Exception?>>

        override fun handleMessage(message: Message) {
            val context: Context? = fetcher.getContext()
            if (context == null) {
                Log.e(TAG, "handleMessage: no available context, nothing to do")
                return
            }

            when (message.what) {
                MSG_LOAD_CAL -> {
                    Log.v(TAG, "handleMessage: launching calendar loader task")

                    loaderTask = CalLoaderTask(fetcher)
                    loaderTask.execute(context)
                }
                else -> Log.e(TAG, "handleMessage: unexpected message: ${message.toString()}")
            }
        }

        companion object {
            const val MSG_LOAD_CAL = 1
        }
    }

    companion object {
        private const val TAG = "CalendarFetcher"
        private var singletonFetcher: CalendarFetcher? = null

        fun requestRescan() {
//            Log.i(TAG, "requestRescan")
            singletonFetcher?.rescan()
        }
    }
}
