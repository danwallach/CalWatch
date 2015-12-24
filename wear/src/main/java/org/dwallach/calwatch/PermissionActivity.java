package org.dwallach.calwatch;

import android.app.Activity;
import android.support.annotation.NonNull;

/**
 * We need a separate activity for the sole purpose of requesting permissions.
 */
public class PermissionActivity extends Activity {
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        CalendarPermission.handleResult(requestCode, permissions, grantResults);
    }
}
