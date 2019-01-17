package info.guardianproject.keanu.core.service;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.util.Debug;

import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;
import static info.guardianproject.keanu.core.KeanuConstants.PREFERENCE_KEY_TEMP_PASS;

public class GeneralJobIntentService extends JobIntentService {

    public static final int JOB_ID = 0x01;

    private boolean isBooted = false;

    public static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, GeneralJobIntentService.class, JOB_ID, work);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {

        Context context = getApplicationContext();

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))
            handleBoot(context,intent);
    }

    private void handleBoot (Context context, Intent intent)
    {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        boolean prefStartOnBoot = prefs.getBoolean("pref_start_on_boot", true);

        Debug.onServiceStart();
        if (prefStartOnBoot) {

            if (!isBooted) {
                if (Imps.isUnencrypted(context) || prefs.contains(PREFERENCE_KEY_TEMP_PASS)) {
                    Log.d(LOG_TAG, "autostart");

                    //show unlock notification
                    Log.d("Keanu","started from boot");

                    Intent serviceIntent = new Intent(context, RemoteImService.class);
                    //   serviceIntent.setComponent(ImServiceConstants.IM_SERVICE_COMPONENT);
                    serviceIntent.putExtra(ImServiceConstants.EXTRA_CHECK_AUTO_LOGIN, true);
                    ContextCompat.startForegroundService(context,serviceIntent);

                    Log.d(LOG_TAG, "autostart done");
                } else {
                    //show unlock notification
                    StatusBarNotifier sbn = new StatusBarNotifier(context);
                    sbn.notifyLocked();
                }

                isBooted = true;
            }
        }
    }
}
