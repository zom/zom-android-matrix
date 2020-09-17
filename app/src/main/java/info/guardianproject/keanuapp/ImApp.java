/*
 * Copyright (C) 2007-2008 Esmertec AG. Copyright (C) 2007-2008 The Android Open
 * Source Project
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
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import androidx.multidex.MultiDexApplication;
import androidx.appcompat.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;

import com.vanniktech.emoji.EmojiManager;
import com.vanniktech.emoji.google.GoogleEmojiProvider;

import net.sqlcipher.database.SQLiteDatabase;

import org.acra.ACRA;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.config.MailSenderConfigurationBuilder;
import org.acra.data.StringFormat;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import info.guardianproject.iocipher.VirtualFileSystem;
import info.guardianproject.keanu.core.Preferences;
import info.guardianproject.keanu.core.cacheword.CacheWordHandler;
import info.guardianproject.keanu.core.cacheword.ICacheWordSubscriber;
import info.guardianproject.keanu.core.cacheword.PRNGFixes;
import info.guardianproject.keanu.core.model.ImConnection;
import info.guardianproject.keanu.core.model.ImErrorInfo;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.provider.ImpsProvider;
import info.guardianproject.keanu.core.service.Broadcaster;
import info.guardianproject.keanu.core.service.IChatSession;
import info.guardianproject.keanu.core.service.IChatSessionManager;
import info.guardianproject.keanu.core.service.IConnectionCreationListener;
import info.guardianproject.keanu.core.service.IImConnection;
import info.guardianproject.keanu.core.service.ImServiceConstants;
import info.guardianproject.keanu.core.service.NetworkConnectivityReceiver;
import info.guardianproject.keanu.core.service.RemoteImService;
import info.guardianproject.keanu.core.service.StatusBarNotifier;
import info.guardianproject.keanu.core.service.adapters.ConnectionListenerAdapter;
import info.guardianproject.keanu.core.util.Debug;
import info.guardianproject.keanu.core.util.ImPluginHelper;
import info.guardianproject.keanu.core.util.Languages;

import static info.guardianproject.keanu.core.KeanuConstants.DEFAULT_DEVICE_NAME;
import static info.guardianproject.keanu.core.KeanuConstants.IMPS_CATEGORY;
import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;
import static info.guardianproject.keanu.core.KeanuConstants.NOTIFICATION_CHANNEL_ID_MESSAGE;
import static info.guardianproject.keanu.core.KeanuConstants.NOTIFICATION_CHANNEL_ID_SERVICE;
import static info.guardianproject.keanu.core.service.RemoteImService.getConnection;

public class ImApp extends MultiDexApplication implements ICacheWordSubscriber {


    public final static String URL_UPDATER = "https://gitlab.com/keanuapp/keanuapp-android/-/raw/master/appupdater.xml";

    public static ImApp sImApp;


    MyConnListener mConnectionListener;

    Broadcaster mBroadcaster;

    /**
     * A queue of messages that are waiting to be sent when service is
     * connected.
     */
    ArrayList<Message> mQueue = new ArrayList<Message>();

    /** A flag indicates that we have called tomServiceStarted start the service. */
//    private boolean mServiceStarted;
    private Context mApplicationContext;

    private CacheWordHandler mCacheWord;

    public static Executor sThreadPoolExecutor = null;

    private SharedPreferences settings = null;

    private boolean mThemeDark = false;

    private boolean mNeedsAccountUpgrade = false;

    public static final int EVENT_SERVICE_CONNECTED = 100;
    public static final int EVENT_CONNECTION_CREATED = 150;
    public static final int EVENT_CONNECTION_LOGGING_IN = 200;
    public static final int EVENT_CONNECTION_LOGGED_IN = 201;
    public static final int EVENT_CONNECTION_LOGGING_OUT = 202;
    public static final int EVENT_CONNECTION_DISCONNECTED = 203;
    public static final int EVENT_CONNECTION_SUSPENDED = 204;
    public static final int EVENT_USER_PRESENCE_UPDATED = 300;
    public static final int EVENT_UPDATE_USER_PRESENCE_ERROR = 301;


    private static final String[] ACCOUNT_PROJECTION = { Imps.Account._ID, Imps.Account.PROVIDER,
                                                        Imps.Account.NAME, Imps.Account.USERNAME,
                                                        Imps.Account.PASSWORD, };

    static final void log(String log) {
        Log.d(LOG_TAG, log);
    }


    public void initChannel(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel nc = new NotificationChannel(NOTIFICATION_CHANNEL_ID_SERVICE, getString(R.string.app_name), NotificationManager.IMPORTANCE_MIN);
            nc.setShowBadge(false);
            nm.createNotificationChannel(nc);

            nc = new NotificationChannel(NOTIFICATION_CHANNEL_ID_MESSAGE, getString(R.string.notifications), NotificationManager.IMPORTANCE_HIGH);
            nc.setLightColor(0xff990000);
            nc.enableLights(true);
            nc.enableVibration(false);
            nc.setShowBadge(true);
            nc.setSound(null, null);
            nm.createNotificationChannel(nc);


        }
    }


    protected void attachBaseContext(Context base) {
                super.attachBaseContext(base);
        // The following line triggers the initialization of ACRA

        CoreConfigurationBuilder builder = new CoreConfigurationBuilder(this);
        builder.setBuildConfigClass(BuildConfig.class).setReportFormat(StringFormat.KEY_VALUE_LIST);
        builder.getPluginConfigurationBuilder(MailSenderConfigurationBuilder.class).setEnabled(true).setMailTo("support@zom.im");
        builder.getPluginConfigurationBuilder(DialogConfigurationBuilder.class).setEnabled(true);
        ACRA.init(this, builder);

            }

    @Override
    public ContentResolver getContentResolver() {
        if (mApplicationContext == null || mApplicationContext == this) {
            return super.getContentResolver();
        }

        return mApplicationContext.getContentResolver();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Imps.appId = BuildConfig.APPLICATION_ID;//"info.guardianproject.keanuapp";

        StatusBarNotifier.defaultMainClass = "info.guardianproject.keanuapp.MainActivity";

        Preferences.setup(this);
        initChannel();

        Languages.setup(MainActivity.class, R.string.use_system_default);
        Languages.setLanguage(this, Preferences.getLanguage(), false);

        sImApp = this;

        settings = PreferenceManager.getDefaultSharedPreferences(this);

        Debug.onAppStart();

        PRNGFixes.apply(); //Google's fix for SecureRandom bug: http://android-developers.blogspot.com/2013/08/some-securerandom-thoughts.html

        EmojiManager.install(new GoogleEmojiProvider());

        // load these libs up front to shorten the delay after typing the passphrase
        SQLiteDatabase.loadLibs(getApplicationContext());
        VirtualFileSystem.get().isMounted();

       // mConnections = new HashMap<Long, IImConnection>();
        mApplicationContext = this;

        //initTrustManager();

        mBroadcaster = new Broadcaster();

        setAppTheme(null,null);

        // ChatSecure-Push needs to do initial setup as soon as Cacheword is ready
        mCacheWord = new CacheWordHandler(this, this);
        mCacheWord.connectToService();

        if (sThreadPoolExecutor == null) {
            int corePoolSize = 10;
            int maximumPoolSize = 20;
            int keepAliveTime = 5;
            BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(maximumPoolSize);
            sThreadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.MINUTES, workQueue);
        }

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(new NetworkConnectivityReceiver(), intentFilter);

        if (BuildConfig.DEBUG) {
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
        }


    }

    public boolean isThemeDark ()
    {
        return mThemeDark;
    }
    
    public void setAppTheme (Activity activity)
    {
        setAppTheme(activity, null);
    }

    public void setAppTheme (Activity activity, Toolbar toolbar)
    {

        mThemeDark = settings.getBoolean("themeDark", false);

        if (mThemeDark)
        {
            setTheme(R.style.AppThemeDark);


            if (activity != null)
            {
                activity.setTheme(R.style.AppThemeDark);
                
            }      
      
        }
        else
        {
            setTheme(R.style.AppTheme);


            if (activity != null)
            {
                activity.setTheme(R.style.AppTheme);
               
            }
            
            
        }

        Configuration config = getResources().getConfiguration();
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());

        if (RemoteImService.mImService != null)
        {
            boolean debugOn = settings.getBoolean("prefDebug", false);
            try {
                RemoteImService.mImService.enableDebugLogging(debugOn);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public static void resetLanguage(Activity activity, String language) {
        if (!TextUtils.equals(language, Preferences.getLanguage())) {
            /* Set the preference after setting the locale in case something goes
             * wrong. If setting the locale causes an Exception, it should not be set in
             * the preferences, otherwise this will be stuck in a crash loop. */
            Languages.setLanguage(activity, language, true);
            Preferences.setLanguage(language);
            Languages.forceChangeLanguage(activity);


        }
    }

    public void startImServiceIfNeed() {
        startImServiceIfNeed(false);
    }

    public void startImServiceIfNeed(boolean isBoot) {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG))
            log("start ImService");

        Intent serviceIntent = new Intent(getApplicationContext(), RemoteImService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            mApplicationContext.startForegroundService(serviceIntent);
        else
            mApplicationContext.startService(serviceIntent);

        mConnectionListener = new MyConnListener(new Handler());

        for (Message msg : mQueue) {
            msg.sendToTarget();
        }
        mQueue.clear();

        Message msg = Message.obtain(null, EVENT_SERVICE_CONNECTED);
        mBroadcaster.broadcast(msg);

   //     mApplicationContext
     //           .bindService(serviceIntent, mImServiceConn, Context.BIND_AUTO_CREATE|Context.BIND_IMPORTANT);

    }

    public boolean hasActiveConnections ()
    {
        try {
            return !RemoteImService.mImService.getActiveConnections().isEmpty();
        }
        catch (RemoteException re)
        {
            return false;
        }

    }

    public void stopImServiceIfInactive() {

        //todo we don't wnat to do this right now
        /**
        if (!hasActiveConnections()) {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG))
                log("stop ImService because there's no active connections");

            forceStopImService();

        }*/
    }


    public void forceStopImService() {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG))
            log("stop ImService");

        try {
            if (RemoteImService.mImService != null)
                RemoteImService.mImService.shutdownAndLock();
        }
        catch (RemoteException re)
        {

        }


        Intent serviceIntent = new Intent(this, RemoteImService.class);
        serviceIntent.putExtra(ImServiceConstants.EXTRA_CHECK_AUTO_LOGIN, true);
        mApplicationContext.stopService(serviceIntent);


    }

    /**
    private ServiceConnection mImServiceConn = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG))
                log("service connected");

            mImService = IRemoteImService.Stub.asInterface(service);

            synchronized (mQueue) {
                for (Message msg : mQueue) {
                    msg.sendToTarget();
                }
                mQueue.clear();
            }
            Message msg = Message.obtain(null, EVENT_SERVICE_CONNECTED);
            mBroadcaster.broadcast(msg);


            if (mKillServerOnStart)
            {
                forceStopImService();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG))
                log("service disconnected");

           // mConnections.clear();
            mImService = null;
        }
    };**/

    public boolean serviceConnected() {
        return RemoteImService.mImService != null;
    }

    public static long insertOrUpdateAccount(ContentResolver cr, long providerId, long accountId, String nickname, String username,
            String pw) {

        String selection = Imps.Account.PROVIDER + "=? AND (" + Imps.Account._ID + "=?" + " OR " + Imps.Account.USERNAME + "=?)";
        String[] selectionArgs = { Long.toString(providerId), Long.toString(accountId), username };

        Cursor c = cr.query(Imps.Account.CONTENT_URI, ACCOUNT_PROJECTION, selection, selectionArgs,
                null);
        if (c != null && c.moveToFirst()) {
            long id = c.getLong(c.getColumnIndexOrThrow(Imps.Account._ID));

            ContentValues values = new ContentValues(4);
            values.put(Imps.Account.PROVIDER, providerId);

            if (!TextUtils.isEmpty(nickname))
                values.put(Imps.Account.NAME, nickname);

            if (!TextUtils.isEmpty(username))
                values.put(Imps.Account.USERNAME, username);

            if (!TextUtils.isEmpty(pw))
                values.put(Imps.Account.PASSWORD, pw);

            Uri accountUri = ContentUris.withAppendedId(Imps.Account.CONTENT_URI, id);
            cr.update(accountUri, values, null, null);

            c.close();

            return id;
        } else {
            ContentValues values = new ContentValues(4);
            values.put(Imps.Account.PROVIDER, providerId);
            values.put(Imps.Account.NAME, nickname);
            values.put(Imps.Account.USERNAME, username);
            values.put(Imps.Account.PASSWORD, pw);

            if (pw != null && pw.length() > 0) {
                values.put(Imps.Account.KEEP_SIGNED_IN, true);
            }

            Uri result = cr.insert(Imps.Account.CONTENT_URI, values);
            if (c != null)
                c.close();
            return ContentUris.parseId(result);
        }
    }



    public Collection<IImConnection> getActiveConnections() {

        try {
            return RemoteImService.mImService.getActiveConnections();
        }
        catch (RemoteException re)
        {
            return null;
        }
    }

    public void callWhenServiceConnected(Handler target, Runnable callback) {
        Message msg = Message.obtain(target, callback);
        if (serviceConnected() && msg != null) {
            msg.sendToTarget();
        } else {
            startImServiceIfNeed();
            synchronized (mQueue) {
                mQueue.add(msg);
            }
        }
    }

    public static void refreshAccount (ContentResolver resolver, long accountId, long providerId)
    {

        IImConnection conn = getConnection(providerId, accountId);

        try {
            conn.logout(true);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        String newDeviceId =  DEFAULT_DEVICE_NAME + "-"
                + UUID.randomUUID().toString().substring(0, 8);

        Cursor cursor = resolver.query(Imps.ProviderSettings.CONTENT_URI, new String[]{Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE}, Imps.ProviderSettings.PROVIDER + "=?", new String[]{Long.toString(providerId)}, null);

        Imps.ProviderSettings.QueryMap providerSettings = new Imps.ProviderSettings.QueryMap(
                cursor, resolver, providerId, false, null);

        providerSettings.setDeviceName(newDeviceId);
        providerSettings.close();
        cursor.close();

    }

    public static void deleteAccount (ContentResolver resolver, long accountId, long providerId)
    {

        IImConnection conn = getConnection(providerId, accountId);

        Uri accountUri = ContentUris.withAppendedId(Imps.Account.CONTENT_URI, accountId);
        resolver.delete(accountUri, null, null);

        Uri providerUri = ContentUris.withAppendedId(Imps.Provider.CONTENT_URI, providerId);
        resolver.delete(providerUri, null, null);

        Uri.Builder builder = Imps.Contacts.CONTENT_URI_CONTACTS_BY.buildUpon();
        ContentUris.appendId(builder, providerId);
        ContentUris.appendId(builder, accountId);
        resolver.delete(builder.build(), null, null);



    }

    public void registerForBroadcastEvent(int what, Handler target) {
        mBroadcaster.request(what, target, what);
    }

    public void unregisterForBroadcastEvent(int what, Handler target) {
        mBroadcaster.cancelRequest(what, target, what);
    }

    public void registerForConnEvents(Handler handler) {
        mBroadcaster.request(EVENT_CONNECTION_CREATED, handler, EVENT_CONNECTION_CREATED);
        mBroadcaster.request(EVENT_CONNECTION_LOGGING_IN, handler, EVENT_CONNECTION_LOGGING_IN);
        mBroadcaster.request(EVENT_CONNECTION_LOGGED_IN, handler, EVENT_CONNECTION_LOGGED_IN);
        mBroadcaster.request(EVENT_CONNECTION_LOGGING_OUT, handler, EVENT_CONNECTION_LOGGING_OUT);
        mBroadcaster.request(EVENT_CONNECTION_SUSPENDED, handler, EVENT_CONNECTION_SUSPENDED);
        mBroadcaster.request(EVENT_CONNECTION_DISCONNECTED, handler, EVENT_CONNECTION_DISCONNECTED);
        mBroadcaster.request(EVENT_USER_PRESENCE_UPDATED, handler, EVENT_USER_PRESENCE_UPDATED);
        mBroadcaster.request(EVENT_UPDATE_USER_PRESENCE_ERROR, handler,
                EVENT_UPDATE_USER_PRESENCE_ERROR);
    }

    public void unregisterForConnEvents(Handler handler) {
        mBroadcaster.cancelRequest(EVENT_CONNECTION_CREATED, handler, EVENT_CONNECTION_CREATED);
        mBroadcaster.cancelRequest(EVENT_CONNECTION_LOGGING_IN, handler,
                EVENT_CONNECTION_LOGGING_IN);
        mBroadcaster.cancelRequest(EVENT_CONNECTION_LOGGED_IN, handler, EVENT_CONNECTION_LOGGED_IN);
        mBroadcaster.cancelRequest(EVENT_CONNECTION_LOGGING_OUT, handler,
                EVENT_CONNECTION_LOGGING_OUT);
        mBroadcaster.cancelRequest(EVENT_CONNECTION_SUSPENDED, handler, EVENT_CONNECTION_SUSPENDED);
        mBroadcaster.cancelRequest(EVENT_CONNECTION_DISCONNECTED, handler,
                EVENT_CONNECTION_DISCONNECTED);
        mBroadcaster.cancelRequest(EVENT_USER_PRESENCE_UPDATED, handler,
                EVENT_USER_PRESENCE_UPDATED);
        mBroadcaster.cancelRequest(EVENT_UPDATE_USER_PRESENCE_ERROR, handler,
                EVENT_UPDATE_USER_PRESENCE_ERROR);
    }

    void broadcastConnEvent(int what, long providerId, ImErrorInfo error) {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            log("broadcasting connection event " + what + ", provider id " + providerId);
        }
        Message msg = Message.obtain(null, what, (int) (providerId >> 32),
                (int) providerId, error);
        mBroadcaster.broadcast(msg);
    }

    public void dismissChatNotification(long providerId, String username) {
        if (RemoteImService.mImService != null) {
            try {
                RemoteImService.mImService.dismissChatNotification(providerId, username);
            } catch (RemoteException e) {
            }
        }
    }

    public void dismissNotification(long providerId) {
        if (RemoteImService.mImService != null) {
            try {
                RemoteImService.mImService.dismissNotifications(providerId);
            } catch (RemoteException e) {
            }
        }
    }

    /**
    private void fetchActiveConnections() {
        if (mImService != null)
        {
            try {
                // register the listener before fetch so that we won't miss any connection.
                mImService.addConnectionCreatedListener(mConnCreationListener);
                synchronized (mConnections) {
                    for (IBinder binder : (List<IBinder>) mImService.getActiveConnections()) {
                        IImConnection conn = IImConnection.Stub.asInterface(binder);
                        long providerId = conn.getProviderId();
                        if (!mConnections.containsKey(providerId)) {
                            mConnections.put(providerId, conn);
                            conn.registerConnectionListener(mConnectionListener);
                     }
                    }
                }
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "fetching active connections", e);
            }
        }
    }*/

    private final IConnectionCreationListener mConnCreationListener = new IConnectionCreationListener.Stub() {
        public void onConnectionCreated(IImConnection conn) throws RemoteException {
            long providerId = conn.getProviderId();
             conn.registerConnectionListener(mConnectionListener);

            /**
            synchronized (mConnections) {
                if (!mConnections.containsKey(providerId)) {
                    mConnections.put(providerId, conn);
                    conn.registerConnectionListener(mConnectionListener);
                }
            }*/
            broadcastConnEvent(EVENT_CONNECTION_CREATED, providerId, null);
        }
    };

    private final class MyConnListener extends ConnectionListenerAdapter {
        public MyConnListener(Handler handler) {
            super(handler);
        }

        @Override
        public void onConnectionStateChange(IImConnection conn, int state, ImErrorInfo error) {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
                log("onConnectionStateChange(" + state + ", " + error + ")");
            }

            try {

                //fetchActiveConnections();

                int what = -1;
                long providerId = conn.getProviderId();
                switch (state) {
                case ImConnection.LOGGED_IN:
                    what = EVENT_CONNECTION_LOGGED_IN;
                    break;

                case ImConnection.LOGGING_IN:
                    what = EVENT_CONNECTION_LOGGING_IN;
                    break;

                case ImConnection.LOGGING_OUT:
                    // NOTE: if this logic is changed, the logic in ImConnectionAdapter.ConnectionAdapterListener must be changed to match
                    what = EVENT_CONNECTION_LOGGING_OUT;

                    break;

                case ImConnection.DISCONNECTED:
                    // NOTE: if this logic is changed, the logic in ImConnectionAdapter.ConnectionAdapterListener must be changed to match
                    what = EVENT_CONNECTION_DISCONNECTED;
               //     mConnections.remove(providerId);
                    // stop the service if there isn't an active connection anymore.
                 //   stopImServiceIfInactive();

                    break;

                case ImConnection.SUSPENDED:
                    what = EVENT_CONNECTION_SUSPENDED;
                    break;
                }
                if (what != -1) {
                    broadcastConnEvent(what, providerId, error);
                }
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "onConnectionStateChange", e);
            }
        }

        @Override
        public void onUpdateSelfPresenceError(IImConnection connection, ImErrorInfo error) {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
                log("onUpdateUserPresenceError(" + error + ")");
            }
            try {
                long providerId = connection.getProviderId();
                broadcastConnEvent(EVENT_UPDATE_USER_PRESENCE_ERROR, providerId, error);
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "onUpdateUserPresenceError", e);
            }
        }

        @Override
        public void onSelfPresenceUpdated(IImConnection connection) {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG))
                log("onUserPresenceUpdated");

            try {
                long providerId = connection.getProviderId();
                broadcastConnEvent(EVENT_USER_PRESENCE_UPDATED, providerId, null);
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "onUserPresenceUpdated", e);
            }
        }
    }

    public IChatSession getChatSession(long providerId, long accountId, String remoteAddress) {

        IImConnection conn = getConnection(providerId,accountId);

        IChatSessionManager chatSessionManager = null;
        if (conn != null) {
            try {
                chatSessionManager = conn.getChatSessionManager();
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "error in getting ChatSessionManager", e);
            }
        }

        if (chatSessionManager != null) {
            try {
                return chatSessionManager.getChatSession(remoteAddress);
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "error in getting ChatSession", e);
            }
        }

        return null;
    }

    public void maybeInit(Activity activity) {
        startImServiceIfNeed();
        setAppTheme(activity,null);
        ImPluginHelper.getInstance(this).loadAvailablePlugins();
    }

    public boolean setDefaultAccount (long providerId, long accountId, String username, String nickname)
    {
        mDefaultNickname = nickname;
        mDefaultUsername = username;
        setDefaultAccount (providerId, accountId);
        return true;
    }

    public boolean setDefaultAccount (long providerId, long accountId)
    {
        mDefaultProviderId = providerId;
        mDefaultAccountId = accountId;
        settings.edit().putLong("defaultAccountId",mDefaultAccountId).commit();

        final Uri uri = Imps.Provider.CONTENT_URI_WITH_ACCOUNT;
        String[] PROVIDER_PROJECTION = {
                Imps.Provider._ID,
                Imps.Provider.ACTIVE_ACCOUNT_ID,
                Imps.Provider.ACTIVE_ACCOUNT_USERNAME,
                Imps.Provider.ACTIVE_ACCOUNT_NICKNAME

        };

        final Cursor cursorProviders = getContentResolver().query(uri, PROVIDER_PROJECTION,
                Imps.Provider.ACTIVE_ACCOUNT_ID + "=" + accountId
                        + " AND " + Imps.Provider.CATEGORY + "=?"
                        + " AND " + Imps.Provider.ACTIVE_ACCOUNT_USERNAME + " NOT NULL" /* selection */,
                new String[]{IMPS_CATEGORY} /* selection args */,
                Imps.Provider.DEFAULT_SORT_ORDER);

        if (cursorProviders != null && cursorProviders.getCount() > 0) {
            cursorProviders.moveToFirst();
            mDefaultUsername = cursorProviders.getString(2);
            mDefaultNickname = cursorProviders.getString(3);

            Cursor pCursor = getContentResolver().query(Imps.ProviderSettings.CONTENT_URI, new String[]{Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE}, Imps.ProviderSettings.PROVIDER + "=?", new String[]{Long.toString(mDefaultProviderId)}, null);

            Imps.ProviderSettings.QueryMap settingsProvider = new Imps.ProviderSettings.QueryMap(
                    pCursor, getContentResolver(), mDefaultProviderId, false /* don't keep updated */, null /* no handler */);

            mDefaultUsername = '@' + mDefaultUsername + ':' + settingsProvider.getDomain();

            settingsProvider.close();
            cursorProviders.close();

            return true;
        }


        if (cursorProviders != null)
            cursorProviders.close();

        return false;
    }

    public boolean initAccountInfo ()
    {

        long lastAccountId = settings.getLong("defaultAccountId",-1);

        if (mDefaultProviderId == -1 || mDefaultAccountId == -1) {

            final Uri uri = Imps.Provider.CONTENT_URI_WITH_ACCOUNT;
            String[] PROVIDER_PROJECTION = {
                    Imps.Provider._ID,
                    Imps.Provider.ACTIVE_ACCOUNT_ID,
                    Imps.Provider.ACTIVE_ACCOUNT_USERNAME,
                    Imps.Provider.ACTIVE_ACCOUNT_NICKNAME,
                    Imps.Provider.ACTIVE_ACCOUNT_KEEP_SIGNED_IN
            };

            final Cursor cursorProviders = getContentResolver().query(uri, PROVIDER_PROJECTION,
                    Imps.Provider.CATEGORY + "=?" + " AND " + Imps.Provider.ACTIVE_ACCOUNT_USERNAME + " NOT NULL" /* selection */,
                    new String[]{IMPS_CATEGORY} /* selection args */,
                    Imps.Provider.DEFAULT_SORT_ORDER);

            if (cursorProviders != null && cursorProviders.getCount() > 0) {
                while(cursorProviders.moveToNext()) {

                    long providerId = cursorProviders.getLong(0);
                    long accountId = cursorProviders.getLong(1);
                    String username = cursorProviders.getString(2);
                    String nickname = cursorProviders.getString(3);
                    boolean keepSignedIn = cursorProviders.getInt(4) != 0;

                    Cursor pCursor = getContentResolver().query(Imps.ProviderSettings.CONTENT_URI, new String[]{Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE}, Imps.ProviderSettings.PROVIDER + "=?", new String[]{Long.toString(providerId)}, null);

                    Imps.ProviderSettings.QueryMap settings = new Imps.ProviderSettings.QueryMap(
                            pCursor, getContentResolver(), providerId, false /* don't keep updated */, null /* no handler */);

                    username = '@' + username + ':' + settings.getDomain();

                    settings.close();

                    if (lastAccountId == -1 && keepSignedIn)
                    {
                        mDefaultProviderId = providerId;
                        mDefaultAccountId = accountId;
                        mDefaultUsername = username;
                        mDefaultNickname = nickname;
                    }
                    else if (lastAccountId == accountId)
                    {
                        mDefaultProviderId = providerId;
                        mDefaultAccountId = accountId;
                        mDefaultUsername = username;
                        mDefaultNickname = nickname;
                    }
                    else if (mDefaultProviderId == -1)
                    {
                        mDefaultProviderId = providerId;
                        mDefaultAccountId = accountId;
                        mDefaultUsername = username;
                        mDefaultNickname = nickname;
                    }

                }
            }

            if (cursorProviders != null)
                cursorProviders.close();
        }

        return true;

    }

    public boolean checkUpgrade ()
    {

        final Uri uri = Imps.Provider.CONTENT_URI_WITH_ACCOUNT;
        String[] PROVIDER_PROJECTION = {
                Imps.Provider._ID,
                Imps.Provider.ACTIVE_ACCOUNT_ID,
                Imps.Provider.ACTIVE_ACCOUNT_USERNAME,
                Imps.Provider.ACTIVE_ACCOUNT_NICKNAME,
                Imps.Provider.ACTIVE_ACCOUNT_KEEP_SIGNED_IN
        };

        final Cursor cursorProviders = getContentResolver().query(uri, PROVIDER_PROJECTION,
                Imps.Provider.CATEGORY + "=?" + " AND " + Imps.Provider.ACTIVE_ACCOUNT_USERNAME + " NOT NULL" /* selection */,
                new String[]{IMPS_CATEGORY} /* selection args */,
                Imps.Provider.DEFAULT_SORT_ORDER);

        if (cursorProviders != null && cursorProviders.getCount() > 0) {
            while(cursorProviders.moveToNext()) {

                long providerId = cursorProviders.getLong(0);
                long accountId = cursorProviders.getLong(1);
                String username = cursorProviders.getString(2);
                String nickname = cursorProviders.getString(3);
                boolean keepSignedIn = cursorProviders.getInt(4) != 0;

                Cursor pCursor = getContentResolver().query(Imps.ProviderSettings.CONTENT_URI, new String[]{Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE}, Imps.ProviderSettings.PROVIDER + "=?", new String[]{Long.toString(providerId)}, null);

                Imps.ProviderSettings.QueryMap settings = new Imps.ProviderSettings.QueryMap(
                        pCursor, getContentResolver(), providerId, false /* don't keep updated */, null /* no handler */);

                username = username + '@' + settings.getDomain();

                if ((!mNeedsAccountUpgrade)
                        && settings.getDomain().equalsIgnoreCase("dukgo.com") && keepSignedIn)
                {
                    mNeedsAccountUpgrade = true;
                }

                settings.close();

            }
        }

        if (cursorProviders != null)
            cursorProviders.close();


        return true;

    }

    private long mDefaultProviderId = -1;
    private long mDefaultAccountId = -1;
    private String mDefaultUsername = null;
    private String mDefaultNickname = null;

    public String getDefaultUsername ()
    {
        return mDefaultUsername;
    }

    public String getDefaultNickname ()
    {
        return mDefaultNickname;
    }


    public long getDefaultProviderId ()
    {
        return mDefaultProviderId;
    }

    public long getDefaultAccountId ()
    {
        return mDefaultAccountId;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        Languages.setLanguage(this, Preferences.getLanguage(),true);

    }



    @Override
    public void onCacheWordUninitialized() {
        // unused
    }

    @Override
    public void onCacheWordLocked() {
        // unused
    }

    @Override
    public void onCacheWordOpened() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(LOG_TAG, "Awaiting ImpsProvider ready");
                // Wait for ImpsProvider to initialize : it listens to onCacheWordOpened as well...
                ImpsProvider.awaitDataReady();

                Log.d(LOG_TAG, "ImpsProvider ready");
                // setupChatSecurePush will disconnect the CacheWordHandler when it's done
            }
        }).start();
    }

    public boolean needsAccountUpgrade ()
    {
        return mNeedsAccountUpgrade;
    }

    public void notifyAccountUpgrade ()
    {
        StatusBarNotifier notifier = new StatusBarNotifier(this);

    }

    /**
    public boolean doUpgrade (Activity activity, MigrateAccountTask.MigrateAccountListener listener)
    {

        boolean result = false;

        long lastAccountId = settings.getLong("defaultAccountId",-1);

        final Uri uri = Imps.Provider.CONTENT_URI_WITH_ACCOUNT;
        String[] PROVIDER_PROJECTION = {
                Imps.Provider._ID,
                Imps.Provider.ACTIVE_ACCOUNT_ID,
                Imps.Provider.ACTIVE_ACCOUNT_USERNAME,
                Imps.Provider.ACTIVE_ACCOUNT_NICKNAME,
                Imps.Provider.ACTIVE_ACCOUNT_KEEP_SIGNED_IN
        };

        final Cursor cursorProviders = getContentResolver().query(uri, PROVIDER_PROJECTION,
                Imps.Provider.CATEGORY + "=?" + " AND " + Imps.Provider.ACTIVE_ACCOUNT_USERNAME + " NOT NULL" ,
                new String[]{IMPS_CATEGORY} ,
                Imps.Provider.DEFAULT_SORT_ORDER);

        if (cursorProviders != null && cursorProviders.getCount() > 0) {
            while(cursorProviders.moveToNext()) {

                long providerId = cursorProviders.getLong(0);
                long accountId = cursorProviders.getLong(1);
                boolean keepSignedIn = cursorProviders.getInt(4) != 0;

                if (keepSignedIn)
                    new MigrateAccountTask(activity, this, providerId, accountId, listener).execute(Server.getServers(this));

            }
        }

        if (cursorProviders != null)
            cursorProviders.close();

        mNeedsAccountUpgrade = false;

        return result;

    }**/

    private void showFileSizes (File fileDir)
    {
        File list[] = fileDir.listFiles();

        for (File file : list)
        {
            if (file.isDirectory()) {

                Log.d("KeanuFiles",file.getAbsolutePath() + "/");
                showFileSizes(file);
            }
            else
            {
                Log.d("KeanuFiles",file.getAbsolutePath() + " (" + file.length()/1000000 + ")");
            }
        }
    }

}
