package org.dwallach.calwatch;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;

public class WearActivity extends Activity {
    private static final String TAG = "WearActivity";

    private static WearActivity singletonActivity = null;

    public static WearActivity getSingletonActivity() {
        return singletonActivity;
    }

    private MyViewAnim view;

    public MyViewAnim getViewAnim() { return view; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "starting onCreate");

        singletonActivity = this;

        // start the calendar service, if it's not already running
        if(WearReceiverService.getSingleton() == null) {
            Intent serviceIntent = new Intent(this, WearReceiverService.class);
            startService(serviceIntent);
        }

        // bring up the UI
        setContentView(R.layout.activity_wear);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                view = (MyViewAnim) stub.findViewById(R.id.surfaceView);

                Log.v(TAG, "starting data API receiver");
            }
        });
    }
}
