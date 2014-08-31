package org.dwallach.calwatch;

import android.content.Intent;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.Observable;
import java.util.Observer;

public class WatchCalendarService extends WearableListenerService {
    private final String TAG = "WatchCalendarService";

    private static WatchCalendarService singletonService;
    private WearSender wearSender;
    private ClockFaceStub clockFaceStub;
    private CalendarFetcher calendarFetcher;

    public WatchCalendarService() {
        super();

        Log.v(TAG, "starting calendar fetcher");
        if(singletonService != null) {
            Log.v(TAG, "whoa, multiple services!");
            if(calendarFetcher != null)
                calendarFetcher.haltUpdates();
        }

        singletonService = this;

        // we'd much rather use the *service* than the *activity* here, but that seems
        // not work, for unknown reasons
        PhoneActivity phoneActivity = PhoneActivity.getSingletonActivity();
        if(phoneActivity != null) {
            wearSender = new WearSender(phoneActivity);
            clockFaceStub = phoneActivity.getClockFace();
        } else {
            Log.v(TAG, "no clockface yet, hmm");
            clockFaceStub = new ClockFaceStub();
        }

        calendarFetcher = new CalendarFetcher(); // automatically allocates a thread and runs

        calendarFetcher.addObserver(new Observer() {
                                        @Override
                                        public void update(Observable observable, Object data) {
                                            sendAllToWatch();
                                        }
                                    });
    }

    public ClockFaceStub getClockFace() {
        return clockFaceStub;
    }

    public WearSender getWearSender() {
        return wearSender;
    }

    public static WatchCalendarService getSingletonService() {
        return singletonService;
    }

    // this is called when there's something new from the calendar DB; we'll be running
    // on the calendar's thread, not the UI thread. It's also useful to call from elsewhere
    // when we want to push data to the watch.
    public void sendAllToWatch() {
        if(wearSender == null) {
            Log.v(TAG, "no wear sender?!");
            return;
        }

        // first, send the events to the local instance on the phone
        ClockFaceStub clockFaceStub = getClockFace();
        if(clockFaceStub == null)
            Log.v(TAG, "nowhere to send updated calendar events, hmm");
        else
            clockFaceStub.setEventList(calendarFetcher.getContent().getWrappedEvents());

        // and now, send on to the wear device
        wearSender.store(calendarFetcher.getContent().getWireEvents(), clockFaceStub);
        wearSender.sendNow();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "service starting!");
        // handleCommand(intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        Log.v(TAG, "service created!");
    }

    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        Log.v(TAG, "service destroyed!");
    }

    //
    // Official documentation: https://developer.android.com/training/wearables/data-layer/events.html
    // Very, very helpful: http://www.doubleencore.com/2014/07/create-custom-ongoing-notification-android-wear/
    //

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.v(TAG, "message received!");

        if (messageEvent.getPath().equals(Constants.WearDataReturnPath)) {
            // the watch says "hi"; make sure we send it stuff

            sendAllToWatch(); // send the calendar and whatever else we have
        } else {
            Log.v(TAG, "received message on unexpected path: " + messageEvent.getPath());
        }
    }

    /*
    @Override
    public void onConnected(Bundle connectionHint) {
        Log.v(TAG, "onConnected!");
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.
        Log.v(TAG, "suspended connection!");
        mGoogleApiClient.disconnect();
        mGoogleApiClient = null;
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // This callback is important for handling errors that
        // may occur while attempting to connect with Google.
        //
        // More about this in the next section.

        Log.v(TAG, "lost connection!");
        mGoogleApiClient.disconnect();
        mGoogleApiClient = null;
    }
    */

    public void onPeerConnected(Node peer) {
        Log.v(TAG, "phone is connected!, "+peer.getDisplayName());
    }

    public void onPeerDisconnected(Node peer) {
        Log.v(TAG, "phone is disconnected!, "+peer.getDisplayName());
    }


}
