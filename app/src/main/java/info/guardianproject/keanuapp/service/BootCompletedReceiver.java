package info.guardianproject.keanuapp.service;

import java.security.GeneralSecurityException;
import java.util.Date;

import info.guardianproject.keanuapp.ImApp;
import info.guardianproject.keanuapp.provider.Imps;
import info.guardianproject.keanuapp.service.ImServiceConstants;
import info.guardianproject.keanuapp.service.StatusBarNotifier;
import info.guardianproject.keanuapp.util.Debug;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;


/**
 * Automatically initiate the service and connect when the network comes on,
 * including on boot.
 */
public class BootCompletedReceiver extends BroadcastReceiver {

    @Override
    public synchronized void onReceive(Context context, Intent intent) {
           if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
               GeneralJobIntentService.enqueueWork(context, intent);
           }
    }



}
