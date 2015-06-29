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
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * Created by dwallach on 8/25/14.
 */
public class WearSender implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "WearSender";
    byte[] wireBytesToSend = null;

    // yes, we're hanging onto a context here, as part the phone side of sending state to the watch,
    // and yes, hanging onto contexts is frowned upon. However, this context is only held alive
    // by virtue of the instance of WearSender, which is in turn only held alive by virtue of the
    // WatchCalendarService. And that thing can and will be summarily killed by the system at any
    // time, so this context won't leak.
    private Context context;
    private GoogleApiClient googleApiClient;

    private void initGoogle() {
        if(googleApiClient == null) {
            Log.v(TAG, "initializing GoogleApiClient");
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

//            if(!isActiveConnection()) return;
            if(wireBytesToSend == null) return;
            if(wireBytesToSend.length == 0) return;

            Log.v(TAG, "ready to send request");

            /*
             * essential code borrowed from WearOngoingNotificationSample
             */
            if (googleApiClient.isConnected()) {
                PutDataMapRequest putDataMapReq = PutDataMapRequest.create(Constants.SettingsPath);
                putDataMapReq.getDataMap().putByteArray(Constants.SettingsPath, wireBytesToSend);
                PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
                PendingResult<DataApi.DataItemResult> pendingResult =
                        Wearable.DataApi.putDataItem(googleApiClient, putDataReq);

                // this callback isn't strictly necessary for correctness, but it will be
                // exceptionally useful for log debugging
                pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(final DataApi.DataItemResult result) {
                        if(result.getStatus().isSuccess()) {
                            Log.d(TAG, "Data item set: " + result.getDataItem().getUri());
                        } else {
                            Log.e(TAG, "Data item failed? " + result.getStatus().toString());
                        }
                    }
                });

            }

        } catch (Throwable throwable) {
            Log.e(TAG, "couldn't manage to send to the watch; not a big deal", throwable);
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

        // shouldn't ever happen, but might explain the weird null pointer exceptions that
        // rarely show up in the logs
        if(googleApiClient == null) {
            Log.e(TAG, "unexpected null googleApiClient");
            closeGoogle();
            initGoogle();
            return;
        }

        sendAllToWatch();
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.
        Log.v(TAG, "suspended connection!");
        closeGoogle();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // This callback is important for handling errors that
        // may occur while attempting to connect with Google.
        //
        // More about this in the next section.

        Log.v(TAG, "lost connection!");
        closeGoogle();
    }

    private void closeGoogle() {
        try {
            Log.v(TAG, "cleaning up Google API");
            readyToSend = false;
            if (googleApiClient != null) {
                if (googleApiClient.isConnected())
                    googleApiClient.disconnect();
            }
        } finally {
            googleApiClient = null;
        }
    }
}
