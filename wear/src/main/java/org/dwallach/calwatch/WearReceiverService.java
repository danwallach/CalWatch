/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * This class pairs up with WearSender
 * Created by dwallach on 8/25/14.
 *
 */
public class WearReceiverService extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private final static String TAG = "WearReceiverService";

    // private List<EventWrapper> eventList = null;
    // private int maxLevel = 0;
    // private int faceMode = ClockFace.FACE_TOOL;
    private GoogleApiClient googleApiClient = null;
    private static WearReceiverService singleton;

    public WearReceiverService() {
        super();
        Log.v(TAG, "starting listening service");
        singleton = this;
    }

    public static WearReceiverService getSingleton() { return singleton; }

    private void newEventBytes(byte[] eventBytes) {
        ClockState clockState = ClockState.getSingleton();
        if(clockState == null) {
            Log.e(TAG, "whoa, no ClockState yet?!");
            return;
        }

        // once we are able to load something, whether from the saved preferences file or from the
        // phone, we'll declare ourselves happy and won't bug the user any more
        WearNotificationHelper.seenPhone(this);

        clockState.setProtobuf(eventBytes);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "service starting!");

        // why is this necessary?
        initGoogle();

        // this also seems a reasonable place to set up the battery monitor

        BatteryWrapper.init(this);

        // and to load any saved data while we're waiting on the phone to give us fresh data

        ClockState clockState = ClockState.getSingleton();
        if(clockState == null) {
            Log.e(TAG, "whoa, no ClockState yet?!");
        } else {
            if(clockState.getWireInitialized()) {
                Log.v(TAG, "clock state already initialized, no need to go to saved prefs");
            } else {
                loadPreferences();
            }
        }

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    //
    // Official documentation: https://developer.android.com/training/wearables/data-layer/events.html
    // Very, very helpful: http://www.doubleencore.com/2014/07/create-custom-ongoing-notification-android-wear/
    //

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.v(TAG, "message received!");
        initGoogle();
        if (messageEvent.getPath().equals(Constants.WearDataSendPath)) {
            byte[] messageData = messageEvent.getData();
            if(messageData.length == 0) {
                Log.e(TAG, "zero-length message received from phone; asking for a retry");
                pingPhone(); // something went awfully wrong here
            } else {
                Log.v(TAG, "message length: " + messageData.length + " bytes");
                newEventBytes(messageData);
                savePreferences(messageData); // save this for subsequent restarts
            }
        } else {
            Log.v(TAG, "received message on unexpected path: " + messageEvent.getPath());
        }
    }

    private void initGoogle() {
        if(googleApiClient == null) {
            Log.v(TAG, "Trying to connect to GoogleApi");
            googleApiClient = new GoogleApiClient.Builder(this).
                    addApi(Wearable.API).
                    addConnectionCallbacks(this).
                    addOnConnectionFailedListener(this).
                    build();
            googleApiClient.connect();
        }
    }

        @Override
    public void onCreate() {
        super.onCreate();

        Log.v(TAG, "onCreate!");
        initGoogle();
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.v(TAG, "Connected to Google Api Service");

        pingPhone();
    }


    @Override
    public void onConnectionSuspended(int cause) {
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.
        Log.v(TAG, "suspended connection!");
        if(googleApiClient != null && googleApiClient.isConnected())
            googleApiClient.disconnect();
        googleApiClient = null;
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // This callback is important for handling errors that
        // may occur while attempting to connect with Google.
        //
        // More about this in the next section.

        Log.v(TAG, "lost connection!");
        if(googleApiClient != null && googleApiClient.isConnected())
            googleApiClient.disconnect();
        googleApiClient = null;
    }

    public void onPeerConnected(Node peer) {
        Log.v(TAG, "phone is connected!, "+peer.getDisplayName());

        pingPhone();
    }

    public void onPeerDisconnected(Node peer) {
        Log.v(TAG, "phone is disconnected!, " + peer.getDisplayName());
    }

    public void pingPhone() {
        Log.v(TAG, "pinging phone for data");

        if (googleApiClient != null && googleApiClient.isConnected()) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        NodeApi.GetConnectedNodesResult nodes =
                                Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
                        int failures = 0;

                        byte[] versionStringBytes = VersionWrapper.getVersionString().getBytes();

                        // TODO: test weird cases when we have one watch associated with >1 phone
                        // (is that possible?) or one phone associated with more than one phone
                        for (Node node : nodes.getNodes()) {
                            Log.v(TAG, "Sending to node: " + node.getDisplayName());
                            MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(
                                    googleApiClient, node.getId(), Constants.WearDataReturnPath, versionStringBytes).await();
                            if (!result.getStatus().isSuccess()) {
                                Log.e(TAG, "ERROR: failed to send Message: " + result.getStatus());
                                failures++;
                            }
                            if (failures == 0) {
                                Log.v(TAG, "ping delivered!");
                            }
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "unexpected failure in pingPhone()", t);
                    } finally {
                        return null;
                    }
                }
            }.execute();
        } else {
            Log.e(TAG, "pingPhone: No GoogleAPI?!");
        }
    }

    /**
     * Take a serialized state update and commit it to stable storage.
     * @param eventBytes protobuf-serialed WireUpdate (typically received from the phone)
     */
    public void savePreferences(byte[] eventBytes) {
        // if there's not enough state there to be real, then don't save it
        if(eventBytes == null || eventBytes.length < 1)
            return;

        Log.v(TAG, "savePreferences: " + eventBytes.length + " bytes");
        SharedPreferences prefs = getSharedPreferences("org.dwallach.calwatch.prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        // Kinda sad that we need to base64-encode our state before we save it, but the SharedPreferences
        // interface doesn't allow for arbitrary arrays of bytes. So yeah, base64-encoded,
        // protobuf-encoded, WireUpdate structure, which itself has a handful of ints and a
        // variable-length array of WireEvents, which are themselves just a bunch of long's.
        editor.putString("savedState", Base64.encodeToString(eventBytes, Base64.DEFAULT));

        if(!editor.commit())
            Log.v(TAG, "savePreferences commit failed ?!");
    }

    /**
     * load saved state, if it's present, and use it to initialize the watchface.
     */
    public void loadPreferences() {
        Log.v(TAG, "loadPreferences");

        SharedPreferences prefs = getSharedPreferences("org.dwallach.calwatch.prefs", MODE_PRIVATE);
        String savedState = prefs.getString("savedState", "");

        if(savedState != null && savedState.length() > 0) {
            try {
                byte[] eventBytes = Base64.decode(savedState, Base64.DEFAULT);
                newEventBytes(eventBytes);

            } catch (IllegalArgumentException e) {
                Log.e(TAG, "failed to decode base64 saved state: " + e.toString());
                return;
            }
        }
    }


    public static void kickStart(Context context) {
        // start the calendar service, if it's not already running
        if(getSingleton() == null) {
            Intent serviceIntent = new Intent(context, WearReceiverService.class);
            context.startService(serviceIntent);
        }

    }
}
