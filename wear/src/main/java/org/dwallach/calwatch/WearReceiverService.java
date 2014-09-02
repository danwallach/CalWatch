package org.dwallach.calwatch;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import org.dwallach.calwatch.proto.WireEvent;
import org.dwallach.calwatch.proto.WireUpdate;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import com.squareup.wire.Wire;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * This class pairs up with WearSender
 * Created by dwallach on 8/25/14.
 *
 */
public class WearReceiverService extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private final static String TAG = "WearReceiverService";

    // private List<EventWrapper> eventList = null;
    // private int maxLevel = 0;
    // private boolean showSeconds = true;
    // private int faceMode = ClockFace.FACE_TOOL;
    private GoogleApiClient mGoogleApiClient = null;
    private static WearReceiverService singleton;

    public WearReceiverService() {
        super();
        Log.v(TAG, "starting listening service");
        singleton = this;
    }

    public static WearReceiverService getSingleton() { return singleton; }

    private void newEventBytes(byte[] eventBytes) {
        ClockState clockState = ClockState.getSingleton();
        clockState.setProtobuf(eventBytes);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "service starting!");
        // handleCommand(intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.

        // DONE!: send message to wake up phone and update us here on the watch
        // Note that we're doing is here and not in onCreate(). This seems
        // to get called later on, and suggests that we'll be ready to receive
        // a message in return from the phone, assuming it's alive and kicking.

        pingPhone();

        // this also seems a reasonable place to set up the battery monitor

        BatteryMonitor.init(this);

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
            newEventBytes(messageEvent.getData());
        } else {
            Log.v(TAG, "received message on unexpected path: " + messageEvent.getPath());
        }
    }

    private void initGoogle() {
        Log.v(TAG, "Trying to connect to GoogleApi");
        if(mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this).
                    addApi(Wearable.API).
                    addConnectionCallbacks(this).
                    addOnConnectionFailedListener(this).
                    build();
            mGoogleApiClient.connect();
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

    public void onPeerConnected(Node peer) {
        Log.v(TAG, "phone is connected!, "+peer.getDisplayName());

        pingPhone();
    }

    public void onPeerDisconnected(Node peer) {
        Log.v(TAG, "phone is disconnected!, " + peer.getDisplayName());
    }

    public void pingPhone() {
        Log.v(TAG, "pinging phone for data");

        if (mGoogleApiClient.isConnected()) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    NodeApi.GetConnectedNodesResult nodes =
                            Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
                    int failures = 0;
                    for (Node node : nodes.getNodes()) {
                        Log.v(TAG, "Sending to node: " + node.getDisplayName());
                        MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(
                                mGoogleApiClient, node.getId(), Constants.WearDataReturnPath, null).await();
                        if (!result.getStatus().isSuccess()) {
                            Log.e(TAG, "ERROR: failed to send Message: " + result.getStatus());
                            failures++;
                        }
                        if (failures == 0) {
                            Log.v(TAG, "ping delivered!");
                        }
                    }

                    return null;
                }
            }.execute();
        } else {
            Log.e(TAG, "pingPhone: No GoogleAPI?!");
        }
    }
}
