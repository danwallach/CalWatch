package org.dwallach.calwatch;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

/**
 * We need a separate activity for the sole purpose of requesting permissions.
 */
public class PermissionActivity extends Activity {
    private static final String TAG = "PermissionActivity";
    private boolean firstTimeOnly = true;

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

        firstTimeOnly = getIntent().getExtras().getBoolean("firstTimeOnly");

        Log.v(TAG, "starting PermissionActivity");

        if(firstTimeOnly) {
            CalendarPermission.requestFirstTimeOnly(this);
        } else {
            CalendarPermission.request(this);
        }
        Log.v(TAG, "finishing PermissionActivity");
        this.finish(); // we're done, so this shuts everything down
    }

    /**
     * Call this to launch the wear permission dialog.
     */
    public static void kickStart(Context context, boolean firstTimeOnly) {
        Log.v(TAG, "kickStart");

        Intent activityIntent = new Intent(context, PermissionActivity.class);
        activityIntent.putExtra("firstTimeOnly", firstTimeOnly);
        context.startActivity(activityIntent);
    }
}
