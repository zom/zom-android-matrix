/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package info.guardianproject.keanuapp;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import net.sqlcipher.DatabaseErrorHandler;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;

import info.guardianproject.keanu.core.Preferences;
import info.guardianproject.keanu.core.cacheword.CacheWordHandler;
import info.guardianproject.keanu.core.cacheword.ICacheWordSubscriber;
import info.guardianproject.keanu.core.provider.Imps;
//import info.guardianproject.keanu.core.provider.SQLCipherOpenHelper;

import info.guardianproject.keanu.core.provider.SQLCipherOpenHelper;
import info.guardianproject.keanu.core.util.ImPluginHelper;
import info.guardianproject.keanu.core.util.SecureMediaStore;
import info.guardianproject.keanuapp.tasks.SignInHelper;
import info.guardianproject.keanuapp.ui.LockScreenActivity;
import info.guardianproject.keanuapp.ui.contacts.AddContactActivity;
import info.guardianproject.keanuapp.ui.legacy.SimpleAlertHandler;
import info.guardianproject.keanuapp.ui.onboarding.OnboardingActivity;
import info.guardianproject.panic.Panic;
import info.guardianproject.panic.PanicResponder;

import java.io.File;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static info.guardianproject.keanu.core.KeanuConstants.IMPS_CATEGORY;
import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;
import static info.guardianproject.keanu.core.KeanuConstants.PREFERENCE_KEY_TEMP_PASS;


public class RouterActivity extends AppCompatActivity implements ICacheWordSubscriber {

    private static final String TAG = "RouterActivity";
    private Cursor mProviderCursor;
    private ImApp mApp;
    private SimpleAlertHandler mHandler;
    private SignInHelper mSignInHelper;

    private boolean mDoSignIn = false;

    public static final String ACTION_LOCK_APP = "actionLockApp";

    private static String EXTRA_DO_LOCK = "doLock";
    private static String EXTRA_DO_SIGNIN = "doSignIn";
    public static String EXTRA_ORIGINAL_INTENT = "originalIntent";

    private ProgressDialog dialog;

    static final String[] PROVIDER_PROJECTION = { Imps.Provider._ID, Imps.Provider.NAME,
                                                 Imps.Provider.FULLNAME, Imps.Provider.CATEGORY,
                                                 Imps.Provider.ACTIVE_ACCOUNT_ID,
                                                 Imps.Provider.ACTIVE_ACCOUNT_USERNAME,
                                                 Imps.Provider.ACTIVE_ACCOUNT_PW,
                                                 Imps.Provider.ACTIVE_ACCOUNT_LOCKED,
                                                 Imps.Provider.ACTIVE_ACCOUNT_KEEP_SIGNED_IN,
                                                 Imps.Provider.ACCOUNT_PRESENCE_STATUS,
                                                 Imps.Provider.ACCOUNT_CONNECTION_STATUS, };

    static final int PROVIDER_ID_COLUMN = 0;
    static final int PROVIDER_NAME_COLUMN = 1;
    static final int PROVIDER_FULLNAME_COLUMN = 2;
    static final int PROVIDER_CATEGORY_COLUMN = 3;
    static final int ACTIVE_ACCOUNT_ID_COLUMN = 4;
    static final int ACTIVE_ACCOUNT_USERNAME_COLUMN = 5;
    static final int ACTIVE_ACCOUNT_PW_COLUMN = 6;
    static final int ACTIVE_ACCOUNT_LOCKED = 7;
    static final int ACTIVE_ACCOUNT_KEEP_SIGNED_IN = 8;
    static final int ACCOUNT_PRESENCE_STATUS = 9;
    static final int ACCOUNT_CONNECTION_STATUS = 10;

    private CacheWordHandler mCacheWord = null;
    private boolean mDoLock;


    private final int REQUEST_LOCK_SCREEN = 9999;
    private final int REQUEST_HANDLE_LINK = REQUEST_LOCK_SCREEN+1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mApp = (ImApp)getApplication();

        mHandler = new MyHandler(this);

        Intent intent = getIntent();

        mDoLock = ACTION_LOCK_APP.equals(intent.getAction());

        if (mDoLock) {
            shutdownAndLock(this);

            return;
        } else if (Panic.isTriggerIntent(intent)) {
            if (PanicResponder.receivedTriggerFromConnectedApp(this)) {
                if (Preferences.uninstallApp()) {
                    // lock and delete first for rapid response, then uninstall
                    shutdownAndLock(this);
                    PanicResponder.deleteAllAppData(this);
                    Intent uninstall = new Intent(Intent.ACTION_DELETE);
                    uninstall.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(uninstall);
                } else if (Preferences.clearAppData()) {
                    // lock first for rapid response, then delete
                    shutdownAndLock(this);
                    PanicResponder.deleteAllAppData(this);
                } else if (Preferences.lockApp()) {
                    shutdownAndLock(this);
                }
                // TODO add other responses here, paying attention to if/else order
            } else if (PanicResponder.shouldUseDefaultResponseToTrigger(this)) {
                if (Preferences.lockApp()) {
                    shutdownAndLock(this);
                }
            }
            // this Intent should not trigger any more processing
            finish();
            return;
        }

        mSignInHelper = new SignInHelper(this, mHandler);
        mDoSignIn = intent.getBooleanExtra(EXTRA_DO_SIGNIN, false);

        AlarmManager alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);

        mCacheWord = new CacheWordHandler(this, (ICacheWordSubscriber)this);
        mCacheWord.connectToService();

        // if we have an incoming contact, send it to the right place
        String scheme = intent.getScheme();
        if(TextUtils.equals(scheme, "keanu"))
        {
            intent.setClass(this, AddContactActivity.class);
            startActivity(intent);
            finish();
            return;
        }

    }


    @SuppressWarnings("deprecation")
    private boolean cursorUnlocked() {
        try {
            Uri uri = Imps.Provider.CONTENT_URI_WITH_ACCOUNT;

            Builder builder = uri.buildUpon();
            /**
            if (pKey != null)
                builder.appendQueryParameter(ImApp.CACHEWORD_PASSWORD_KEY, pKey);
            if (!allowCreate)
                builder = builder.appendQueryParameter(ImApp.NO_CREATE_KEY, "1");
             */
            uri = builder.build();

            mProviderCursor = managedQuery(uri,
                    PROVIDER_PROJECTION, Imps.Provider.CATEGORY + "=?" /* selection */,
                    new String[] { IMPS_CATEGORY } /* selection args */,
                    Imps.Provider.DEFAULT_SORT_ORDER);

            if (mProviderCursor != null)
            {
                ImPluginHelper.getInstance(this).loadAvailablePlugins();

                mProviderCursor.moveToFirst();

                return true;
            }
            else
            {
                return false;
            }

        } catch (Exception e) {
            // Only complain if we thought this password should succeed

                Log.e(LOG_TAG, e.getMessage(), e);

                Toast.makeText(this, getString(info.guardianproject.keanu.core.R.string.error_welcome_database), Toast.LENGTH_LONG).show();
                finish();


            // needs to be unlocked
            return false;
        }
    }

    @Override
    protected void onPause() {
        if (mHandler != null)
            mHandler.unregisterForBroadcastEvents();

        super.onPause();
        if (mCacheWord != null)
            mCacheWord.detach();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCacheWord != null)
            mCacheWord.disconnectFromService();

        if (dialog != null)
            dialog.dismiss();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mCacheWord != null)
            mCacheWord.reattach();
    }

    private void doOnResume() {
        mHandler.registerForBroadcastEvents();

        int countAvailable = accountsAvailable();

        Intent intent = getIntent();
        if (intent != null && intent.getAction() != null && !intent.getAction().equals(Intent.ACTION_MAIN)) {

            Intent imUrlIntent = new Intent(this, ImUrlActivity.class);
            String action = intent.getAction();
            imUrlIntent.setAction(action);
            imUrlIntent.setType(intent.getType());

            if (intent.getExtras() != null)
                imUrlIntent.putExtras(intent.getExtras());

            Uri sharedData = intent.getData();
            if (sharedData != null) {
                List<ResolveInfo> resInfoList = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);

                for (ResolveInfo resolveInfo : resInfoList) {
                    String packageName = resolveInfo.activityInfo.packageName;
                    grantUriPermission(packageName, sharedData, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }

                imUrlIntent.setData(sharedData);
            }
            else
            {
                if (intent.getClipData() != null) {
                    ClipData mClipData = intent.getClipData();
                    ArrayList<Uri> mArrayUri = new ArrayList<Uri>();
                    for (int i = 0; i < mClipData.getItemCount(); i++) {

                        ClipData.Item item = mClipData.getItemAt(i);
                        Uri uri = item.getUri();

                        List<ResolveInfo> resInfoList = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);

                        for (ResolveInfo resolveInfo : resInfoList) {
                            String packageName = resolveInfo.activityInfo.packageName;
                            if (uri != null)
                                grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        }

                    }
                }

            }


                startActivityForResult(imUrlIntent, REQUEST_HANDLE_LINK);
                setIntent(null);
        }
        else if (countAvailable > 0)
        {
            if (mDoSignIn && mProviderCursor.moveToFirst()) {
                do {
                    if (!mProviderCursor.isNull(ACTIVE_ACCOUNT_ID_COLUMN)) {
                        int state = mProviderCursor.getInt(ACCOUNT_CONNECTION_STATUS);
                        long accountId = mProviderCursor.getLong(ACTIVE_ACCOUNT_ID_COLUMN);
                        if (mProviderCursor.getInt(ACTIVE_ACCOUNT_KEEP_SIGNED_IN) != 0) {
                            signIn(accountId);
                        }
                    }
                } while (mProviderCursor.moveToNext());
            }
            showMain();
        }
        else
        {
            showOnboarding();


        }

    }

    private void signIn(long accountId) {
        if (accountId == 0) {
            Log.w(TAG, "signIn: account id is 0, bail");
            return;
        }

        boolean isAccountEditable = mProviderCursor.getInt(ACTIVE_ACCOUNT_LOCKED) == 0;
        if (isAccountEditable && mProviderCursor.isNull(ACTIVE_ACCOUNT_PW_COLUMN)) {
            // no password, edit the account
            //if (Log.isLoggable(TAG, Log.d))
              //  Log.i(TAG, "no pw for account " + accountId);
            Intent intent = getEditAccountIntent();
            startActivity(intent);
            finish();
            return;
        }

        long providerId = mProviderCursor.getLong(PROVIDER_ID_COLUMN);
        String password = mProviderCursor.getString(ACTIVE_ACCOUNT_PW_COLUMN);
        boolean isActive = false; // TODO(miron)
        mSignInHelper.signIn(password, providerId, accountId, isActive);
    }

    private boolean isSignedIn(Cursor cursor) {
        int connectionStatus = cursor.getInt(ACCOUNT_CONNECTION_STATUS);

        return connectionStatus == Imps.ConnectionStatus.ONLINE;
    }

    private int accountsAvailable() {
        if (mProviderCursor == null || mProviderCursor.isClosed() || !mProviderCursor.moveToFirst()) {
            return 0;
        }
        int count = 0;
        do {
            if (!mProviderCursor.isNull(ACTIVE_ACCOUNT_PW_COLUMN) &&
                    !mProviderCursor.isNull(ACTIVE_ACCOUNT_ID_COLUMN)) {
                count++;
            }
        } while (mProviderCursor.moveToNext());

        return count;
    }

    Intent getEditAccountIntent() {
        Intent intent = new Intent(Intent.ACTION_EDIT, ContentUris.withAppendedId(
                Imps.Account.CONTENT_URI, mProviderCursor.getLong(ACTIVE_ACCOUNT_ID_COLUMN)));
        intent.putExtra("isSignedIn", isSignedIn(mProviderCursor));
        intent.addCategory(getProviderCategory(mProviderCursor));
        return intent;
    }


    private String getProviderCategory(Cursor cursor) {
        return cursor.getString(PROVIDER_CATEGORY_COLUMN);
    }

    private final static class MyHandler extends SimpleAlertHandler {

        public MyHandler(Activity activity) {
            super(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == ImApp.EVENT_CONNECTION_DISCONNECTED) {
                promptDisconnectedEvent(msg);
            }
            super.handleMessage(msg);
        }
    }

    @Override
    public void onCacheWordUninitialized() {
        Log.d(LOG_TAG,"cache word uninit");

        initTempPassphrase();
        showOnboarding();

    }

    void initTempPassphrase () {
        
        //set temporary passphrase        
        try {
            String tempPassphrase = UUID.randomUUID().toString();
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
            settings.edit().putString(PREFERENCE_KEY_TEMP_PASS, tempPassphrase).apply();
            mCacheWord.setPassphrase(tempPassphrase.toCharArray());
                
           
        } catch (GeneralSecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }


    }

    void showMain () {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    void openChat(String username) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("username",username);
        startActivity(intent);
        finish();
    }

    void showOnboarding () {
        
        //now show onboarding UI
        Intent intent = new Intent(this, OnboardingActivity.class);
        Intent returnIntent = getIntent();
        returnIntent.putExtra(EXTRA_DO_SIGNIN, mDoSignIn);
        intent.putExtra(EXTRA_ORIGINAL_INTENT, returnIntent);
        startActivity(intent);
        finish();
    }

    
    void showLockScreen() {
        Intent intent = new Intent(this, LockScreenActivity.class);
        Intent returnIntent = getIntent();
        returnIntent.putExtra(EXTRA_DO_SIGNIN, mDoSignIn);
        intent.putExtra(EXTRA_ORIGINAL_INTENT, returnIntent);
        startActivityForResult(intent, REQUEST_LOCK_SCREEN);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_LOCK_SCREEN) {
                showMain();
                return;
            } else if (requestCode == REQUEST_HANDLE_LINK) {
                if (data.hasExtra("newcontact")) {
                    String username = data.getStringExtra("newcontact");
                    openChat(username);
                }
            }
        }

        setIntent(null);
        finish();
    }

    @Override
    public void onCacheWordLocked() {
        if (mDoLock) {
            Log.d(LOG_TAG, "info.guardianproject.keanu.core.cacheword lock requested but already locked");

        } else {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

            if (settings.contains(PREFERENCE_KEY_TEMP_PASS))
            {
                try {
                    mCacheWord.setPassphrase(settings.getString(PREFERENCE_KEY_TEMP_PASS, null).toCharArray());


                } catch (GeneralSecurityException e) {
                    
                    Log.d(LOG_TAG, "couldn't open info.guardianproject.keanu.core.cacheword with temp password",e);
                    showLockScreen();
                }
            }
            else
            {
                showLockScreen();
            }
        }
    }

    @Override
    public void onCacheWordOpened() {
       // mCacheWord.setTimeout(0);
       byte[] encryptionKey = mCacheWord.getEncryptionKey();
       openEncryptedStores(encryptionKey);

        mApp.maybeInit(this);


        /**
        if (!mDoLock) {

            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {

                    doOnResume();
                }
            },500);

        }**/


    }

    public void shutdownAndLock(Context context) {

        mApp.forceStopImService();

        finish();

    }

    private boolean openEncryptedStores(byte[] key) {

        SecureMediaStore.init(this, key);

        if (cursorUnlocked()) {

            doOnResume();

            return true;
        } else {
            return false;
        }
    }




}
