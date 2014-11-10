/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

/**
 * Created by dwallach on 8/25/14.
 */
public class WearSender implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, MessageApi.MessageListener {
    private static final String TAG = "WearSender";
    byte[] wireBytesToSend = null;

    private Context context;
    private GoogleApiClient googleApiClient;

    private void initGoogle() {
        if(googleApiClient == null) {
            readyToSend = false;
            googleApiClient = new GoogleApiClient.Builder(context).
                    addApi(Wearable.API).
                    addConnectionCallbacks(this).
                    addOnConnectionFailedListener(this).
                    build();
            googleApiClient.connect();
        }
    }

    public void sendAllToWatch() {
        try {
            ClockState clockState = ClockState.getSingleton();
            wireBytesToSend = clockState.getProtobuf();

            Log.v(TAG, "preparing event list for transmission, length(" + wireBytesToSend.length + " bytes)");

            /*
             * Useful source: http://toastdroid.com/2014/08/18/messageapi-simple-conversations-with-android-wear/
             * Major source: https://developer.android.com/google/auth/api-client.html
             */

            if(!isActiveConnection()) return;
            if(wireBytesToSend == null) return;
            if(wireBytesToSend.length == 0) return;

            Log.v(TAG, "ready to send request");

            /*
             * essential code borrowed from WearOngoingNotificationSample
             */
            if (googleApiClient.isConnected()) {
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        NodeApi.GetConnectedNodesResult nodes =
                                Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
                        int failures = 0;
                        for (Node node : nodes.getNodes()) {
                            Log.v(TAG, "Sending to node: " + node.getDisplayName());
                            MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(
                                    googleApiClient, node.getId(), Constants.WearDataSendPath, wireBytesToSend).await();
                            if (!result.getStatus().isSuccess()) {
                                Log.e(TAG, "ERROR: failed to send Message: " + result.getStatus());
                                failures++;
                            }
                            if(failures == 0) {
                                wireBytesToSend = null; // we're done with sending this message
                            }
                        }

                        return null;
                    }
                }.execute();
            }

        } catch (Throwable throwable) {
            Log.e(TAG, "couldn't manage to send to the watch; not a big deal");
        }
    }

    private String nodeId;

    private static WearSender singleton = null;
    public WearSender(Context context) {
        this.context = context;

        if(singleton == null)
            singleton = this;
        onCreate();
    }

    public static WearSender getSingleton() {
        return singleton;
    }

    private boolean isActiveConnection() {
        if(readyToSend) return true;
        Log.v(TAG, "connection inactive, retrying");
        initGoogle();
        return false;
    }

    protected void onCreate() {
        Log.v(TAG, "onCreate!");
        initGoogle();
    }

    private boolean readyToSend = false;
    @Override
    public void onConnected(Bundle connectionHint) {
        Log.v(TAG, "connected to Google API!");
        // Connected to Google Play services!
        // The good stuff goes here.
        readyToSend = true;

        Wearable.MessageApi.addListener(googleApiClient, this);

        sendAllToWatch();
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.
        Log.v(TAG, "suspended connection!");
        cleanup();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // This callback is important for handling errors that
        // may occur while attempting to connect with Google.
        //
        // More about this in the next section.

        Log.v(TAG, "lost connection!");
        cleanup();
    }

    private void cleanup() {
        try {
            readyToSend = false;
            if (googleApiClient != null) {
                Wearable.MessageApi.removeListener(googleApiClient, this);
                if (googleApiClient.isConnected())
                    googleApiClient.disconnect();
            }
        } finally {
            googleApiClient = null;
        }
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

}
