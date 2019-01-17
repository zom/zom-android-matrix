package info.guardianproject.keanu.core.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;


/**
 * Automatically initiate the service and connect when the network comes on,
 * including on boot.
 */
public class BootCompletedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
           if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
               GeneralJobIntentService.enqueueWork(context, intent);
           }
    }



}
