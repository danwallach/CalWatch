package org.dwallach.calwatch;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;

/**
 * We need a separate activity for the sole purpose of requesting permissions.
 */
public class PermissionActivity extends Activity {
    private static final String TAG = "PermissionActivity";

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        CalendarPermission.handleResult(requestCode, permissions, grantResults);
        CalWatchFaceService.Engine engine = CalWatchFaceService.getEngine();
        if(engine != null) {
            engine.calendarPermissionUpdate();
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        Log.v(TAG, "starting PermissionActivity");
        CalendarPermission.request(this);
        Log.v(TAG, "finishing PermissionActivity");
        this.finish(); // we're done, so this shuts everything down
    }

    /**
     * Call this to launch the wear permission dialog.
     */
    public static void kickStart(Context context) {
        Log.v(TAG, "kickStart");

        Intent activityIntent = new Intent(context, PermissionActivity.class);
        context.startActivity(activityIntent);
    }
}
