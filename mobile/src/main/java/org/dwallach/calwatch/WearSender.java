package org.dwallach.calwatch;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import org.dwallach.calwatch.proto.WireEvent;
import org.dwallach.calwatch.proto.WireUpdate;

import java.util.List;

/**
 * Created by dwallach on 8/25/14.
 */
public class WearSender implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "WearSender";
    byte[] wireBytesToSend = null;
    byte[] lastMessage = null;

    private Context context;
    private GoogleApiClient mGoogleApiClient;

    private void initGoogle() {
        if(mGoogleApiClient == null)
            mGoogleApiClient = new GoogleApiClient.Builder(context).
                    addApi(Wearable.API).
                    addConnectionCallbacks(this).
                    addOnConnectionFailedListener(this).
                    build();
        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }
        if (!mGoogleApiClient.isConnected()) {
            Log.e(TAG, "not connected to GoogleAPI?");
            readyToSend = false;
        } else {
            Log.e(TAG, "connected to GoogleAPI!");
            readyToSend = true;
        }
    }

    public void store(ClockFaceStub stub) {
        WireUpdate wireUpdate = new WireUpdate(null, false, stub.getShowSeconds(), stub.getFaceMode());
        wireBytesToSend = wireUpdate.toByteArray();

        Log.v(TAG, "preparing event list for transmission, length(" + wireBytesToSend.length + " bytes)");
    }

    public void store(List<WireEvent> wireEvents, ClockFaceStub stub) {
        WireUpdate wireUpdate = new WireUpdate(wireEvents, true, stub.getShowSeconds(), stub.getFaceMode());
        wireBytesToSend = wireUpdate.toByteArray();

        Log.v(TAG, "preparing event list for transmission, length(" + wireBytesToSend.length + " bytes)");
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

    /*
     * Useful source: http://toastdroid.com/2014/08/18/messageapi-simple-conversations-with-android-wear/
     * Major source: https://developer.android.com/google/auth/api-client.html
     */

    public void sendNow() {
        sendNow(false);
    }

    public void sendNow(boolean repeatDesired) {
        if(!isActiveConnection()) return;

        if(wireBytesToSend == null && repeatDesired)
            wireBytesToSend = lastMessage;

        if(wireBytesToSend == null) return;

        Log.v(TAG, "ready to send request");

        /*
         * essential code borrowed from WearOngoingNotificationSample
         */
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
                                mGoogleApiClient, node.getId(), Constants.WearDataSendPath, wireBytesToSend).await();
                        if (!result.getStatus().isSuccess()) {
                            Log.e(TAG, "ERROR: failed to send Message: " + result.getStatus());
                            failures++;
                        }
                        if(failures == 0) {
                            lastMessage = wireBytesToSend;
                            wireBytesToSend = null; // we're done with sending this message
                        }
                    }

                    return null;
                }
            }.execute();
        }
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
        Log.v(TAG, "onConnected!");
        // Connected to Google Play services!
        // The good stuff goes here.
        initGoogle();
        sendNow();
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.
        Log.v(TAG, "suspended connection!");
        readyToSend = false;
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // This callback is important for handling errors that
        // may occur while attempting to connect with Google.
        //
        // More about this in the next section.

        Log.v(TAG, "lost connection!");
        readyToSend = false;
        mGoogleApiClient.disconnect();
    }
}
