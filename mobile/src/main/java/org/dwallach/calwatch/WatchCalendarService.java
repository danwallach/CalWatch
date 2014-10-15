package org.dwallach.calwatch;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.util.Observable;
import java.util.Observer;

public class WatchCalendarService extends Service implements MessageApi.MessageListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, Observer {
    private static final String TAG = "WatchCalendarService";

    private static WatchCalendarService singletonService;
    private WearSender wearSender;
    private CalendarFetcher calendarFetcher;

    private GoogleApiClient mGoogleApiClient;

    private ClockState clockState;
    private int mGoogleAPIFailureRetryCounter = 0;

    private ClockState getClockState() {
        // more on the design of this particular contraption in the comments in PhoneActivity
        if(clockState == null) {
            clockState = ClockState.getSingleton();
            clockState.addObserver(this);
        }
        return clockState;
    }


    private void initGoogle() {
        if(mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this).
                    addApi(Wearable.API).
                    addConnectionCallbacks(this).
                    addOnConnectionFailedListener(this).
                    build();
            mGoogleApiClient.connect();
        }
    }


    public WatchCalendarService() {
        super();

        Log.v(TAG, "starting calendar fetcher");
        if (singletonService != null) {
            Log.v(TAG, "whoa, multiple services!");
            if (calendarFetcher != null)
                calendarFetcher.haltUpdates();
        }

        singletonService = this;

        calendarFetcher = new CalendarFetcher(); // automatically allocates a thread and runs

        calendarFetcher.addObserver(new Observer() {
            @Override
            public void update(Observable observable, Object data) {
                Log.v(TAG, "New calendar state to send to watch!");

                // the following line is important: this is where we bridge the output of the phone-side
                // calendar fetcher (running as a separate thread inside the phone-side Service)
                // into the ClockState central repo (shared by many things). This change will
                // later on trigger a callback to the update method (below), which will
                // then decide it's time to send everything to the watch

                getClockState().setEventList(calendarFetcher.getContent().getWrappedEvents());
                // sendAllToWatch();
            }
        });
    }

    public static WatchCalendarService getSingletonService() {
        return singletonService;
    }

    // this is called when there's something new from the calendar DB; we'll be running
    // on the calendar's thread, not the UI thread. It's also useful to call from elsewhere
    // when we want to push data to the watch.
    public void sendAllToWatch() {
        if (wearSender == null) {
            Log.v(TAG, "no wear sender?!");
            return;
        }

        // and now, send on to the wear device
        wearSender.sendAllToWatch();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.v(TAG, "service starting!");

        BatteryWrapper.init(this);
        getClockState();

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "service created!");
        initGoogle();

        getClockState();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "service destroyed!");

        clockState.deleteObserver(this);
        clockState = null;
    }

    //
    // Official documentation: https://developer.android.com/training/wearables/data-layer/events.html
    // Very, very helpful: http://www.doubleencore.com/2014/07/create-custom-ongoing-notification-android-wear/
    //

    public void onMessageReceived(MessageEvent messageEvent) {
        Log.v(TAG, "message received!");

        if (messageEvent.getPath().equals(Constants.WearDataReturnPath)) {
            // the watch says "hi"; make sure we send it stuff

            sendAllToWatch(); // send the calendar and whatever else we have
        } else {
            Log.v(TAG, "received message on unexpected path: " + messageEvent.getPath());
        }
    }

    public void onConnected(Bundle connectionHint) {
        Log.v(TAG, "Google API connected!");

        try {
            Wearable.MessageApi.addListener(mGoogleApiClient, this);
            wearSender = new WearSender(this);
        } catch (NullPointerException e) {

//            Totally rare exception that we're now trying to deal with:
//
//            java.lang.NullPointerException
//            at com.google.android.gms.wearable.internal.ag.a(Unknown Source)
//            at com.google.android.gms.wearable.internal.ag.addListener(Unknown Source)
//            at org.dwallach.calwatch.WatchCalendarService.onConnected(WatchCalendarService.java:151)
//            at com.google.android.gms.common.internal.d.a(Unknown Source)
//            at com.google.android.gms.common.api.a.bn(Unknown Source)
//            at com.google.android.gms.common.api.a.d(Unknown Source)
//            at com.google.android.gms.common.api.a$2.onConnected(Unknown Source)
//            at com.google.android.gms.common.internal.d.a(Unknown Source)
//            at com.google.android.gms.common.internal.d.y(Unknown Source)
//            at com.google.android.gms.common.internal.c$g.a(Unknown Source)
//            at com.google.android.gms.common.internal.c$g.d(Unknown Source)
//            at com.google.android.gms.common.internal.c$b.bN(Unknown Source)
//            at com.google.android.gms.common.internal.c$a.handleMessage(Unknown Source)
//            at android.os.Handler.dispatchMessage(Handler.java:102)
//            at android.os.Looper.loop(Looper.java:136)
//            at android.app.ActivityThread.main(ActivityThread.java:5001)
//            at java.lang.reflect.Method.invokeNative(Native Method)
//            at java.lang.reflect.Method.invoke(Method.java:515)
//            at com.android.internal.os.ZygoteInit$MethodAndArgsCaller.run(ZygoteInit.java:785)
//            at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:601)
//            at de.robv.android.xposed.XposedBridge.main(XposedBridge.java:132)
//            at dalvik.system.NativeStart.main(Native Method)

            mGoogleAPIFailureRetryCounter++;
            Log.e(TAG, "Rare onConnected NullPointer failure! Disconnecting and retrying.", e);

            // we'll retry this a few times, but after that something is deeply broken and
            // it's time to just blow up in a user-visible way and hope we get a useful
            // stack trace
            if(mGoogleAPIFailureRetryCounter > 10) {
                throw e;
            } else {
                wearSender = null;
                onConnectionFailed(null);
                initGoogle();
            }
        }
    }

    public void onConnectionSuspended(int cause) {
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.
        try {
            Log.v(TAG, "suspended connection!");
            Wearable.MessageApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        } catch (Throwable t) {
            Log.e(TAG, "failure trying to clean up; ignored.", t);
        } finally {
            mGoogleApiClient = null;
        }
    }

    public void onConnectionFailed(ConnectionResult result) {
        // This callback is important for handling errors that
        // may occur while attempting to connect with Google.
        //
        // More about this in the next section.

        Log.v(TAG, "lost connection!");
        try {
            Wearable.MessageApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        } catch (Throwable t) {
            Log.e(TAG, "failure trying to clean up; ignored.", t);
        } finally {
            mGoogleApiClient = null;
        }
    }

    public void onPeerConnected(Node peer) {
        Log.v(TAG, "phone is connected!, " + peer.getDisplayName());
    }

    public void onPeerDisconnected(Node peer) {
        Log.v(TAG, "phone is disconnected!, " + peer.getDisplayName());
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.e(TAG, "onBind: we should support this");
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void update(Observable observable, Object data) {
        // somebody updated something in the clock state (new events, new display options, etc.)
        Log.v(TAG, "internal clock state changed: time to send all to the watch");
        sendAllToWatch();
    }

    public static void kickStart(Context ctx) {
        // start the calendar service, if it's not already running
        WatchCalendarService watchCalendarService = WatchCalendarService.getSingletonService();

        if(watchCalendarService == null) {
            Log.v(TAG, "launching watch calendar service");
            Intent serviceIntent = new Intent(ctx, WatchCalendarService.class);
            ctx.startService(serviceIntent);
        }

    }
}
