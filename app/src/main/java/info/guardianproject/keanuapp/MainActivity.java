/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.guardianproject.keanuapp;

import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.StrictMode;
import android.preference.PreferenceManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.core.view.MenuItemCompat;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.javiersantos.appupdater.AppUpdater;
import com.github.javiersantos.appupdater.enums.Display;
import com.github.javiersantos.appupdater.enums.UpdateFrom;

import info.guardianproject.iocipher.VirtualFileSystem;
import info.guardianproject.keanu.core.Preferences;
import info.guardianproject.keanu.core.model.ImConnection;
import info.guardianproject.keanu.core.model.ImErrorInfo;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.service.HeartbeatService;
import info.guardianproject.keanu.core.service.IChatSession;
import info.guardianproject.keanu.core.service.IChatSessionListener;
import info.guardianproject.keanu.core.service.IChatSessionManager;
import info.guardianproject.keanu.core.service.IImConnection;
import info.guardianproject.keanu.core.service.ImServiceConstants;
import info.guardianproject.keanu.core.service.NetworkConnectivityReceiver;
import info.guardianproject.keanu.core.service.RemoteImService;
import info.guardianproject.keanu.core.util.AssetUtil;
import info.guardianproject.keanu.core.util.Debug;
import info.guardianproject.keanu.core.util.SecureMediaStore;
import info.guardianproject.keanu.core.util.SystemServices;
import info.guardianproject.keanu.matrix.plugin.PopupAlertManager;
import info.guardianproject.keanuapp.tasks.AddContactAsyncTask;
import info.guardianproject.keanuapp.ui.BaseActivity;
import info.guardianproject.keanuapp.ui.LockScreenActivity;
import info.guardianproject.keanuapp.ui.MoreFragment;
import info.guardianproject.keanuapp.ui.accounts.AccountFragment;
import info.guardianproject.keanuapp.ui.accounts.AccountsActivity;
import info.guardianproject.keanuapp.ui.camera.CameraActivity;
import info.guardianproject.keanuapp.ui.contacts.AddContactActivity;
import info.guardianproject.keanuapp.ui.contacts.ContactsListFragment;
import info.guardianproject.keanuapp.ui.contacts.ContactsPickerActivity;
import info.guardianproject.keanuapp.ui.conversation.ConversationDetailActivity;
import info.guardianproject.keanuapp.ui.conversation.ConversationListFragment;
import info.guardianproject.keanuapp.ui.conversation.StoryActivity;
import info.guardianproject.keanuapp.ui.legacy.SettingActivity;
import info.guardianproject.keanuapp.ui.onboarding.OnboardingManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static info.guardianproject.keanu.core.KeanuConstants.IMPS_CATEGORY;
import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;
import static info.guardianproject.keanu.core.KeanuConstants.PREFERENCE_KEY_TEMP_PASS;

/**
 * TODO
 */
public class MainActivity extends BaseActivity {

    private ViewPager mViewPager;
    private TabLayout mTabLayout;
    private FloatingActionButton mFab;
    private Toolbar mToolbar;

    private ImApp mApp;

    public final static int REQUEST_ADD_CONTACT = 9999;
    public final static int REQUEST_CHOOSE_CONTACT = REQUEST_ADD_CONTACT+1;
    public final static int REQUEST_CHANGE_SETTINGS = REQUEST_CHOOSE_CONTACT+1;

    private ConversationListFragment mConversationList;
    private ContactsListFragment mContactList;
    private MoreFragment mMoreFragment;
    private AccountFragment mAccountFragment;
    private static final String SELECTED_ITEM_POSITION = "ItemPosition";
    private int mPosition;
    private LinearLayout mBannerLayout;
    private TextView mBannerText;
    private ImageView mBannerClose;

    private static final String[] PROVIDER_PROJECTION = {
            Imps.Provider._ID,
            Imps.Provider.NAME,
            Imps.Provider.FULLNAME,
            Imps.Provider.CATEGORY,
            Imps.Provider.ACTIVE_ACCOUNT_ID,
            Imps.Provider.ACTIVE_ACCOUNT_USERNAME,
            Imps.Provider.ACTIVE_ACCOUNT_PW,
            Imps.Provider.ACTIVE_ACCOUNT_LOCKED,
            Imps.Provider.ACTIVE_ACCOUNT_KEEP_SIGNED_IN,
            Imps.Provider.ACCOUNT_PRESENCE_STATUS,
            Imps.Provider.ACCOUNT_CONNECTION_STATUS,
            Imps.Provider.ACTIVE_ACCOUNT_NICKNAME


    };
    private static final int EVENT_NETWORK_STATE_CHANGED = 201;
    private ServiceHandler mServiceHandler;
    private NetworkConnectivityReceiver mNetworkConnectivityListener;


    @Override
    public void onCreate(Bundle savedInstanceState) {


        if (Debug.DEBUG_ENABLED) {
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());

            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .penaltyLog()
                    .penaltyFlashScreen()
                    .penaltyDeath()
                    .detectCustomSlowCalls()
                    .detectNetwork()
                    .build());
        }

        super.onCreate(savedInstanceState);


        if (Preferences.doBlockScreenshots()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE);
        }

        setContentView(R.layout.awesome_activity_main);

        mApp = (ImApp)getApplication();

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        mViewPager = (ViewPager) findViewById(R.id.viewpager);
        mTabLayout = (TabLayout) findViewById(R.id.tabs);
        mBannerLayout = (LinearLayout) findViewById(R.id.bannerLayout);
        mBannerText = (TextView) findViewById(R.id.bannerText);
        mBannerClose = (ImageView) findViewById(R.id.bannerClose);
        mBannerClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mBannerLayout.setVisibility(View.GONE);
            }
        });

        setSupportActionBar(mToolbar);

        final ActionBar ab = getSupportActionBar();

        mConversationList = new ConversationListFragment();
        mContactList = new ContactsListFragment();
        mMoreFragment = new MoreFragment();
        mAccountFragment = new AccountFragment();

        Adapter adapter = new Adapter(getSupportFragmentManager());
        adapter.addFragment(mConversationList, getString(R.string.title_chats), R.drawable.ic_message_white_36dp);
        adapter.addFragment(mContactList, getString(R.string.contacts), R.drawable.ic_people_white_36dp);
        adapter.addFragment(mMoreFragment, getString(R.string.title_more), R.drawable.ic_more_horiz_white_36dp);
        adapter.addFragment(mAccountFragment, getString(R.string.title_me), R.drawable.ic_face_white_24dp);

        mViewPager.setAdapter(adapter);

        mViewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(mTabLayout));

        TabLayout.Tab tab = mTabLayout.newTab();
        tab.setIcon(R.drawable.ic_discuss);
        tab.setTag(getString(R.string.chats));
        tab.setContentDescription(R.string.chats);
        mTabLayout.addTab(tab);

        tab = mTabLayout.newTab();
        tab.setIcon(R.drawable.ic_people_white_36dp);
        tab.setTag(getString(R.string.contacts));
        tab.setContentDescription(R.string.contacts);

        mTabLayout.addTab(tab);

        tab = mTabLayout.newTab();
        tab.setIcon(R.drawable.ic_explore_white_24dp);
        tab.setTag(getString(R.string.title_more));
        tab.setContentDescription(R.string.title_more);

        mTabLayout.addTab(tab);

        tab = mTabLayout.newTab();
        tab.setIcon(R.drawable.ic_face_white_24dp);
        tab.setTag(getString(R.string.title_me));
        tab.setContentDescription(R.string.title_me);

        mTabLayout.addTab(tab);

        mTabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {

                mViewPager.setCurrentItem(tab.getPosition());

                setToolbarTitle(tab.getPosition());
                applyStyleColors ();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                setToolbarTitle(tab.getPosition());
                applyStyleColors ();
            }
        });

        mFab = (FloatingActionButton) findViewById(R.id.fab);
        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                int tabIdx = mViewPager.getCurrentItem();

                if (tabIdx == 0) {

                    if (mContactList.getContactCount() > 0) {
                        Intent intent = new Intent(MainActivity.this, ContactsPickerActivity.class);
                        startActivityForResult(intent, REQUEST_CHOOSE_CONTACT);
                    }
                    else
                    {
                        inviteContact();
                    }

                } else if (tabIdx == 1) {
                    inviteContact();
                } else if (tabIdx == 2) {
                    startPhotoTaker();
                }

            }
        });

        setToolbarTitle(0);

        mServiceHandler = new ServiceHandler();

        installRingtones ();

        applyStyle();
//Code Added for Test Ticket(Network connection Banner)
        mNetworkConnectivityListener = new NetworkConnectivityReceiver();
        NetworkConnectivityReceiver.registerHandler(mServiceHandler, EVENT_NETWORK_STATE_CHANGED);
        mNetworkConnectivityListener.startListening(this);


    }
    private final class ServiceHandler extends Handler {
        public ServiceHandler() {
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_NETWORK_STATE_CHANGED:
                    // Log.d(TAG, "network");
                    if(mNetworkConnectivityListener.getState() == NetworkConnectivityReceiver.State.NOT_CONNECTED){


                        //don't wnat this to happen to often
                        //checkForUpdates();
//Code Added for Test Ticket(Network connection Banner)
                        String serverName = getServerName();
                        if (!TextUtils.isEmpty(serverName)) {
                            mBannerLayout.setVisibility(View.VISIBLE);
                            mBannerText.setText(String.format(getString(R.string.error_server_down), serverName));
                        }
                    }else{
                        mBannerLayout.setVisibility(View.GONE);
                    }
                    break;

                default:
            }
        }
    }

    //Code Added for Test Ticket(Network connection Banner)
    private String getServerName(){
        String server_name = "";
        ContentResolver cr = getContentResolver();
        //Fetching provider ID
        Cursor cursor = cr.query(Imps.Provider.CONTENT_URI_WITH_ACCOUNT, PROVIDER_PROJECTION, Imps.Provider.CATEGORY + "=?" + " AND " + Imps.Provider.ACTIVE_ACCOUNT_USERNAME + " NOT NULL" /* selection */,
                new String[] { IMPS_CATEGORY } /* selection args */,
                Imps.Provider.DEFAULT_SORT_ORDER);
        if (cursor != null) {
            cursor.moveToFirst();
            int mProviderId = cursor.getInt(cursor.getColumnIndexOrThrow(
                    Imps.Provider._ID));
            cursor.close();
            //Fetching provider account detail using provider id
            Cursor pCursor = cr.query(Imps.ProviderSettings.CONTENT_URI, new String[]{Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE}, Imps.ProviderSettings.PROVIDER + "=?", new String[]{Long.toString(mProviderId)}, null);
            Imps.ProviderSettings.QueryMap settings = new Imps.ProviderSettings.QueryMap(pCursor, cr,
                    mProviderId, false /* keep updated */, null /* no handler */);
            if (TextUtils.isEmpty(settings.getServer())) {
                server_name = settings.getDomain();
            } else {
                server_name = settings.getServer();
            }

            settings.close();
            return server_name;
        }

        return null;

    }


    private void installRingtones ()
    {
        AssetUtil.installRingtone(getApplicationContext(),R.raw.bell,getString(R.string.zom_bell));
        AssetUtil.installRingtone(getApplicationContext(),R.raw.chant,getString(R.string.zom_chant));
        AssetUtil.installRingtone(getApplicationContext(),R.raw.yak,getString(R.string.zom_yak));
        AssetUtil.installRingtone(getApplicationContext(),R.raw.dranyen,getString(R.string.zom_dranyen));

    }

    private void setToolbarTitle(int tabPosition)
    {
        StringBuffer sb = new StringBuffer();
        sb.append(getString(R.string.app_name));
        sb.append(" | ");

        switch (tabPosition) {
            case 0:

                if (mConversationList.getArchiveFilter())
                    sb.append(getString(R.string.action_archive));
                else
                    sb.append(getString(R.string.chats));

                break;
            case 1:

                if ((mContactList.getCurrentType() & Imps.Contacts.TYPE_FLAG_HIDDEN) != 0)
                    sb.append(getString(R.string.action_archive));
                else
                    sb.append(getString(R.string.friends));

                break;
            case 2:
                sb.append(getString(R.string.title_more));
                break;
            case 3:
                sb.append(getString(R.string.me_title));
                mAccountFragment.setUserVisibleHint(true);
                break;
        }

        mToolbar.setTitle(sb.toString());

        if (mFab != null) {
           // mFab.setVisibility(View.VISIBLE);

            if (tabPosition == 1) {
                mFab.setImageResource(R.drawable.ic_person_add_white_36dp);
            } else if (tabPosition == 2) {
            //    mFab.setImageResource(R.drawable.ic_photo_camera_white_36dp);
             //   mFab.setVisibility(View.GONE);

            } else if (tabPosition == 3) {
               // mFab.setVisibility(View.GONE);
            } else {
                mFab.setImageResource(R.drawable.ic_add_white_24dp);
            }
        }

    }

    public void inviteContact ()
    {
        Intent i = new Intent(MainActivity.this, AddContactActivity.class);
        startActivityForResult(i, MainActivity.REQUEST_ADD_CONTACT);
    }


    @Override
    public void onResume() {
        super.onResume();

        /**
        if (getIntent() != null) {
            Uri uriRef = ActivityCompat.getReferrer(this);
            ComponentName aCalling = getCallingActivity();

            Log.d("Main", "Calling Referrer: " + uriRef);
            Log.d("Main", "Calling Activity: " + aCalling);

            StatusBarNotifier sb = new StatusBarNotifier(this);
            sb.notifyError("System", "calling: " + aCalling + " & " + uriRef);
        }**/

        applyStyleColors ();

        //if VFS is not mounted, then send to WelcomeActivity
        if (!VirtualFileSystem.get().isMounted()) {
            finish();
            startActivity(new Intent(this, RouterActivity.class));
        } else {
            ImApp app = (ImApp) getApplication();
            mApp.maybeInit(this);
            mApp.initAccountInfo();
        }


        handleIntent(getIntent());

        PopupAlertManager.INSTANCE.onNewActivityDisplayed(this);

        checkForUpdates();

    }

    private Snackbar mSbStatus;

    private boolean checkConnection() {
        try {

            if (mSbStatus != null && mSbStatus.isShown())
                mSbStatus.dismiss();

            if (!isNetworkAvailable())
            {
                mSbStatus = Snackbar.make(mViewPager, R.string.status_no_internet, Snackbar.LENGTH_INDEFINITE);
                mSbStatus.show();

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        checkConnection();
                    }
                }, 5000); //Timer is in ms here.

                return false;
            }

            if (mApp.getDefaultProviderId() != -1) {
                final IImConnection conn = RemoteImService.getConnection(mApp.getDefaultProviderId(), mApp.getDefaultAccountId());
                final int connState = conn.getState();

                if (connState == ImConnection.DISCONNECTED) {

                    mSbStatus = Snackbar.make(mViewPager, R.string.error_suspended_connection, Snackbar.LENGTH_INDEFINITE);
                    mSbStatus.setAction(getString(R.string.connect), new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            mSbStatus.dismiss();
                            Intent i = new Intent(MainActivity.this, AccountsActivity.class);
                            startActivity(i);
                        }
                    });
                    mSbStatus.show();

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            checkConnection();
                        }
                    }, 5000); //Timer is in ms here.

                    return false;
                }
                else if (connState == ImConnection.LOGGED_IN)
                {

                }
                else if (connState == ImConnection.LOGGING_IN)
                {


                }
                else if (connState == ImConnection.LOGGING_OUT)
                {

                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }

    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent (Intent intent)
    {


        if (intent != null)
        {
            Uri data = intent.getData();
            String type = intent.getType();
          if (data != null && Imps.Chats.CONTENT_ITEM_TYPE.equals(type)) {

                long chatId = ContentUris.parseId(data);
                Intent intentChat = new Intent(this, ConversationDetailActivity.class);
                intentChat.putExtra("id", chatId);
                startActivity(intentChat);
            }
            else if (Imps.Contacts.CONTENT_ITEM_TYPE.equals(type))
            {
                long providerId = intent.getLongExtra(ImServiceConstants.EXTRA_INTENT_PROVIDER_ID,mApp.getDefaultProviderId());
                long accountId = intent.getLongExtra(ImServiceConstants.EXTRA_INTENT_ACCOUNT_ID,mApp.getDefaultAccountId());
                String username = intent.getStringExtra(ImServiceConstants.EXTRA_INTENT_FROM_ADDRESS);
                startChat(providerId, accountId, username,  true);
            }
          else if (Imps.Invitation.CONTENT_ITEM_TYPE.equals(type))
          {
              long providerId = intent.getLongExtra(ImServiceConstants.EXTRA_INTENT_PROVIDER_ID,mApp.getDefaultProviderId());
              long accountId = intent.getLongExtra(ImServiceConstants.EXTRA_INTENT_ACCOUNT_ID,mApp.getDefaultAccountId());
              String username = intent.getStringExtra(ImServiceConstants.EXTRA_INTENT_FROM_ADDRESS);

              long chatId = intent.getLongExtra(ImServiceConstants.EXTRA_INTENT_CHAT_ID,-1);
              if (chatId != -1) {
                  Intent intentChat = new Intent(this, ConversationDetailActivity.class);
                  intentChat.putExtra("id", chatId);
                  startActivity(intentChat);
              }
          }
            else if (intent.hasExtra("username"))
            {
                //launch a new chat based on the intent value
                startChat(mApp.getDefaultProviderId(), mApp.getDefaultAccountId(), intent.getStringExtra("username"),  true);
            }
          else if (intent.hasExtra("group"))
          {
              IImConnection conn = RemoteImService.getConnection(mApp.getDefaultProviderId(), mApp.getDefaultAccountId());
              joinGroupChat(intent.getStringExtra("group"),conn);
          }
            else if (intent.hasExtra(ContactsPickerActivity.EXTRA_RESULT_USERNAME))
          {
              String username = intent.getStringExtra(ContactsPickerActivity.EXTRA_RESULT_USERNAME);
              long providerId = intent.getLongExtra(ContactsPickerActivity.EXTRA_RESULT_PROVIDER,mApp.getDefaultProviderId());
              long accountId = intent.getLongExtra(ContactsPickerActivity.EXTRA_RESULT_ACCOUNT,mApp.getDefaultAccountId());

              startChat(providerId, accountId, username, true);

          }
            else if (intent.getBooleanExtra("firstTime",false))
          {
              inviteContact ();

          }

            setIntent(null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {

            if (requestCode == REQUEST_CHANGE_SETTINGS)
            {
                finish();
                startActivity(new Intent(this, MainActivity.class));
            }
            else if (requestCode == REQUEST_ADD_CONTACT)
            {

                String username = data.getStringExtra(ContactsPickerActivity.EXTRA_RESULT_USERNAME);

                if (username != null) {
                    long providerId = data.getLongExtra(ContactsPickerActivity.EXTRA_RESULT_PROVIDER, -1);
                    long accountId = data.getLongExtra(ContactsPickerActivity.EXTRA_RESULT_ACCOUNT,-1);

                    startChat(providerId, accountId, username,  false);
                }

            }
            else if (requestCode == REQUEST_CHOOSE_CONTACT)
            {
                String username = data.getStringExtra(ContactsPickerActivity.EXTRA_RESULT_USERNAME);

                if (username != null) {
                    long providerId = data.getLongExtra(ContactsPickerActivity.EXTRA_RESULT_PROVIDER, -1);
                    long accountId = data.getLongExtra(ContactsPickerActivity.EXTRA_RESULT_ACCOUNT, -1);

                    startChat(providerId, accountId, username, true);
                }
                else {

                    ArrayList<String> users = data.getStringArrayListExtra(ContactsPickerActivity.EXTRA_RESULT_USERNAMES);
                    if (users != null)
                    {
                        //start group and do invite here
                        startGroupChat(users);
                    }

                }
            }
            else if (requestCode == ConversationDetailActivity.REQUEST_TAKE_PICTURE)
            {
                try {
                    if (mLastPhoto != null)
                        importPhoto();
                }
                catch (Exception e)
                {
                    Log.w(LOG_TAG, "error importing photo",e);

                }
            }
            else if (requestCode == OnboardingManager.REQUEST_SCAN) {

                ArrayList<String> resultScans = data.getStringArrayListExtra("result");
                for (String resultScan : resultScans)
                {

                    try {

                        String address = null;
                        Uri uriScan = Uri.parse(resultScan);

                        if (!TextUtils.isEmpty(uriScan.getQueryParameter("id")))
                        {
                            List<String> lAddr = uriScan.getQueryParameters("id");

                            if (lAddr != null && lAddr.size() > 0)
                                new AddContactAsyncTask(mApp.getDefaultProviderId(), mApp.getDefaultAccountId()).execute(lAddr.get(0), null);

                        }
                        else {
                            //parse each string and if they are for a new user then add the user
                            OnboardingManager.DecodedInviteLink diLink = OnboardingManager.decodeInviteLink(resultScan);

                            if (diLink != null && TextUtils.isEmpty(diLink.username))
                                new AddContactAsyncTask(mApp.getDefaultProviderId(), mApp.getDefaultAccountId()).execute(diLink.username, diLink.fingerprint, diLink.nickname);

                        }

                        if (address != null)
                            startChat(mApp.getDefaultProviderId(), mApp.getDefaultAccountId(), address, true);

                        //if they are for a group chat, then add the group
                    }
                    catch (Exception e)
                    {
                        Log.w(LOG_TAG, "error parsing QR invite link", e);
                    }
                }
            }
        }
    }

    private void startGroupChat (ArrayList<String> invitees)
    {

        IImConnection conn = RemoteImService.getConnection(mApp.getDefaultProviderId(),mApp.getDefaultAccountId());
        startGroupChat(null, invitees, conn, true, true, false);


    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        if (mLastPhoto != null) {
            savedInstanceState.putString("lastphoto", mLastPhoto.toString());
        }


    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // Restore UI state from the savedInstanceState.
        // This bundle has also been passed to onCreate.

       String lastPhotoPath =  savedInstanceState.getString("lastphoto");
        if (lastPhotoPath != null)
            mLastPhoto = Uri.parse(lastPhotoPath);
    }

    private void importPhoto () throws FileNotFoundException, UnsupportedEncodingException {

        // import
        SystemServices.FileInfo info = SystemServices.getFileInfoFromURI(this, mLastPhoto);
        String sessionId = "self";
        String offerId = UUID.randomUUID().toString();

        try {
            Uri vfsUri = SecureMediaStore.resizeAndImportImage(this, sessionId, mLastPhoto, info.type);

            delete(mLastPhoto);

            //adds in an empty message, so it can exist in the gallery and be forwarded
            Imps.insertMessageInDb(
                    getContentResolver(), false, new Date().getTime(), true, null, vfsUri.toString(),
                    System.currentTimeMillis(), Imps.MessageType.OUTGOING_ENCRYPTED,
                    0, offerId, info.type, null);

            mLastPhoto = null;
        }
        catch (IOException ioe)
        {
            Log.e(LOG_TAG,"error importing photo",ioe);
        }

    }

    private boolean delete(Uri uri) {
        if (uri.getScheme().equals("content")) {
            int deleted = getContentResolver().delete(uri,null,null);
            return deleted == 1;
        }
        if (uri.getScheme().equals("file")) {
            File file = new File(uri.toString().substring(5));
            return file.delete();
        }
        return false;
    }


    private SearchView mSearchView;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        mSearchView = (SearchView) MenuItemCompat.getActionView(menu.findItem(R.id.menu_search));

        if (mSearchView != null )
        {
            mSearchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
            mSearchView.setIconifiedByDefault(false);

            SearchView.OnQueryTextListener queryTextListener = new SearchView.OnQueryTextListener()
            {
                public boolean onQueryTextChange(String query)
                {
                    if (mTabLayout.getSelectedTabPosition() == 0)
                        mConversationList.doSearch(query);
                    else if (mTabLayout.getSelectedTabPosition() == 1)
                        mContactList.doSearch(query);

                    return true;
                }

                public boolean onQueryTextSubmit(String query)
                {
                    if (mTabLayout.getSelectedTabPosition() == 0)
                        mConversationList.doSearch(query);
                    else if (mTabLayout.getSelectedTabPosition() == 1)
                        mContactList.doSearch(query);

                    return true;
                }
            };

            mSearchView.setOnQueryTextListener(queryTextListener);

            mSearchView.setOnCloseListener(new SearchView.OnCloseListener() {
                @Override
                public boolean onClose() {
                    mConversationList.doSearch(null);
                    return false;
                }
            });
        }

        MenuItem mItem = menu.findItem(R.id.menu_lock_reset);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        if (!settings.contains(PREFERENCE_KEY_TEMP_PASS))
            mItem.setVisible(true);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                //mDrawerLayout.openDrawer(GravityCompat.START);
                return true;

            case R.id.menu_settings:
                Intent sintent = new Intent(this, SettingActivity.class);
                startActivityForResult(sintent,  REQUEST_CHANGE_SETTINGS);
                return true;

            case R.id.menu_list_normal:
                clearFilters();
                Log.v("Filter","Normal");
                return true;

            case R.id.menu_list_archive:
                Log.v("Filter","Archive");
                enableArchiveFilter();
                return true;

            case R.id.menu_lock:
                handleLock();
                return true;

            case R.id.menu_new_account:
                Intent i = new Intent(MainActivity.this, AccountsActivity.class);
                startActivity(i);
                return true;

            case R.id.menu_lock_reset:
                resetPassphrase();
                return true;

            case R.id.menu_exit:
                handleExit();
                return true;


        }
        return super.onOptionsItemSelected(item);
    }

    private void clearFilters ()
    {

        if (mTabLayout.getSelectedTabPosition() == 0) {
            mConversationList.setArchiveFilter(false);
            Log.v("Filter","clear_1");
        }else {
            Log.v("Filter","clear_2");
            mContactList.setArchiveFilter(false);
        }

        setToolbarTitle(mTabLayout.getSelectedTabPosition());

    }

    private void enableArchiveFilter ()
    {

        if (mTabLayout.getSelectedTabPosition() == 0)
        {
            mConversationList.setArchiveFilter(true);
            Log.v("Filter","clear_11");
        }else {
            Log.v("Filter","clear_22");
            mContactList.setArchiveFilter(true);
        }

        setToolbarTitle(mTabLayout.getSelectedTabPosition());

    }

    public void resetPassphrase ()
    {
        /**
        Intent intent = new Intent(this, LockScreenActivity.class);
        intent.setAction(LockScreenActivity.ACTION_RESET_PASSPHRASE);
        startActivity(intent);**/

        //need to setup new user passphrase
        Intent intent = new Intent(this, LockScreenActivity.class);
        intent.setAction(LockScreenActivity.ACTION_CHANGE_PASSPHRASE);
        startActivity(intent);
    }


    public void handleLock ()
    {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        if (settings.contains(PREFERENCE_KEY_TEMP_PASS))
        {
            //need to setup new user passphrase
            Intent intent = new Intent(this, LockScreenActivity.class);
            intent.setAction(LockScreenActivity.ACTION_CHANGE_PASSPHRASE);
            startActivity(intent);
        }
        else {

            //time to do the lock
            Intent intent = new Intent(this, RouterActivity.class);
            intent.setAction(RouterActivity.ACTION_LOCK_APP);
            startActivity(intent);
            finish();
        }
    }

    public void handleExit ()
    {
        stopService(new Intent(this,RemoteImService.class));
        stopService(new Intent(this, RemoteImService.class));
        finish();
    }

    static class Adapter extends FragmentPagerAdapter {
        private final List<Fragment> mFragments = new ArrayList<>();
        private final List<String> mFragmentTitles = new ArrayList<>();
        private final List<Integer> mFragmentIcons = new ArrayList<>();

        public Adapter(FragmentManager fm) {
            super(fm);
        }

        public void addFragment(Fragment fragment, String title, int icon) {
            mFragments.add(fragment);
            mFragmentTitles.add(title);
            mFragmentIcons.add(icon);
        }

        @Override
        public Fragment getItem(int position) {
            return mFragments.get(position);
        }

        @Override
        public int getCount() {
            return mFragments.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
        return mFragmentTitles.get(position);
        }



    }

    public void startChat (long providerId, long accountId, String username, final boolean openChat)
    {

        if (!checkConnection())
            return;

        ArrayList<String> invitees = new ArrayList<>();
        invitees.add(username);

        IImConnection conn = RemoteImService.getConnection(providerId, accountId);

        if (conn != null)
            startGroupChat(null, invitees, conn, true, true, false);
    }

    public void startGroupChat ()
    {
        IImConnection conn = RemoteImService.getConnection(mApp.getDefaultProviderId(), mApp.getDefaultAccountId());
        startGroupChat(null, null, conn, true, true, false);
    }

    private IImConnection mLastConnGroup = null;
    private long mRequestedChatId = -1;

    public void startGroupChat (String roomSubject, final ArrayList<String> invitees, IImConnection conn, boolean isEncrypted, boolean isPrivate, boolean isSession)
    {
        mLastConnGroup = conn;

        try {
            /**
            if (TextUtils.isEmpty(roomSubject))
            {
                roomSubject = getString(R.string.new_group_title);
            }**/

            IChatSessionManager manager = mLastConnGroup.getChatSessionManager();

            String[] aInvitees = null;

            if (invitees != null)
                aInvitees = invitees.toArray(new String[invitees.size()]);

            mSbStatus = Snackbar.make(mViewPager, R.string.connecting_to_group_chat_, Snackbar.LENGTH_INDEFINITE);
            mSbStatus.show();

            manager.createMultiUserChatSession(roomSubject, null, true, aInvitees, isEncrypted, isPrivate, new IChatSessionListener() {

                @Override
                public IBinder asBinder() {
                    return null;
                }

                @Override
                public void onChatSessionCreated(final IChatSession session) throws RemoteException {

                    mSbStatus.dismiss();

                    session.setLastMessage(" ");
                    Intent intent = new Intent(MainActivity.this,  isSession ? StoryActivity.class : ConversationDetailActivity.class);
                    intent.putExtra("id", session.getId());
                    intent.putExtra("firsttime",true);

                    boolean isEmptyGroup = invitees == null || invitees.size() == 0;
                    intent.putExtra("isNew", isEmptyGroup);
                    intent.putExtra("subject", roomSubject);
                    intent.putExtra("nickname", roomSubject);

                    if (isSession)
                        intent.putExtra(StoryActivity.ARG_CONTRIBUTOR_MODE,true);

                    startActivity(intent);
                }

                @Override
                public void onChatSessionCreateError(String name, ImErrorInfo error) throws RemoteException {

                    mSbStatus.dismiss();

                    String errorMessage = getString(R.string.error);
                    if (error != null)
                        errorMessage = error.getDescription();

                    mSbStatus = Snackbar.make(mViewPager, errorMessage, Snackbar.LENGTH_LONG);
                    mSbStatus.show();
                }
            });


        } catch (RemoteException e) {
           e.printStackTrace();
        }
    }

    public void joinGroupChat (String roomAddress, IImConnection conn)
    {
        mLastConnGroup = conn;

        try {

            IChatSessionManager manager = mLastConnGroup.getChatSessionManager();

            //if we aren't already in a chat, then join it
            if (manager.getChatSession(roomAddress)==null) {

                mSbStatus = Snackbar.make(mViewPager, R.string.connecting_to_group_chat_, Snackbar.LENGTH_INDEFINITE);
                mSbStatus.show();

                manager.joinMultiUserChatSession(roomAddress, new IChatSessionListener() {

                    @Override
                    public IBinder asBinder() {
                        return null;
                    }

                    @Override
                    public void onChatSessionCreated(final IChatSession session) throws RemoteException {

                        mSbStatus.dismiss();

                        session.setLastMessage(" ");
                        Intent intent = new Intent(MainActivity.this, ConversationDetailActivity.class);
                        intent.putExtra("id", session.getId());
                        intent.putExtra("firsttime", true);
                        intent.putExtra("isNew", false);

                        startActivity(intent);
                    }

                    @Override
                    public void onChatSessionCreateError(String name, ImErrorInfo error) throws RemoteException {

                        mSbStatus.dismiss();

                        String errorMessage = getString(R.string.error);
                        if (error != null)
                            errorMessage = error.getDescription();

                        mSbStatus = Snackbar.make(mViewPager, errorMessage, Snackbar.LENGTH_LONG);
                        mSbStatus.show();
                    }
                });
            }


        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void showChat (long chatId)
    {
        Intent intent = new Intent(this, ConversationDetailActivity.class);
        intent.putExtra("id",chatId);
        startActivity(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mNetworkConnectivityListener != null)
            mNetworkConnectivityListener.stopListening();

    }

    private void checkForUpdates() {
        // Remove this for store builds!

        try {

            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;


            //if this is a full release, without -beta -rc etc, then check the appupdater!
            if (version.indexOf("-") == -1 && (!TextUtils.isEmpty(ImApp.URL_UPDATER))) {

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                long timeNow = new Date().getTime();
                long timeSinceLastCheck = prefs.getLong("updatetime", -1);

                //only check for updates once per day
                if (timeSinceLastCheck == -1 || (timeNow - timeSinceLastCheck) > 86400) {

                    AppUpdater appUpdater = new AppUpdater(this);
                    appUpdater.setDisplay(Display.SNACKBAR);

                    if (hasGooglePlay())
                        appUpdater.setUpdateFrom(UpdateFrom.GOOGLE_PLAY);
                    else {
                        appUpdater.setUpdateFrom(UpdateFrom.XML);
                        appUpdater.setUpdateXML(ImApp.URL_UPDATER);
                    }

                    appUpdater.start();

                    prefs.edit().putLong("updatetime", timeNow).commit();
                }
            }
        } catch (Exception e) {
            Log.d("AppUpdater", "error checking app updates", e);
        }

    }

    boolean hasGooglePlay() {
        try {
            getApplication().getPackageManager().getPackageInfo("com.android.vending", 0);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        return true;


    }


    Uri mLastPhoto = null;

    void startPhotoTaker() {

        /**
        // create Intent to take a picture and return control to the calling application
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photo = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),  "cs_" + new Date().getTime() + ".jpg");
        mLastPhoto = Uri.fromFile(photo);
        intent.putExtra(MediaStore.EXTRA_OUTPUT,
                mLastPhoto);

        // start the image capture Intent
        startActivityForResult(intent, ConversationDetailActivity.REQUEST_TAKE_PICTURE);
         **/
        Intent intent = new Intent(this, CameraActivity.class);
        startActivityForResult(intent, ConversationDetailActivity.REQUEST_TAKE_PICTURE);

    }

    /**
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setContentView(R.layout.awesome_activity_main);

    }*/

    public void applyStyle() {

        //first set font
        checkCustomFont();

        /**Typeface typeface = CustomTypefaceManager.getCurrentTypeface(this);

        if (typeface != null) {
            for (int i = 0; i < mToolbar.getChildCount(); i++) {
                View view = mToolbar.getChildAt(i);
                if (view instanceof TextView) {
                    TextView tv = (TextView) view;

                    tv.setTypeface(typeface);
                    break;
                }
            }
        }**/

        applyStyleColors ();
    }

    private void applyStyleColors ()
    {
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        //not set color

        int themeColorHeader = settings.getInt("themeColor",-1);
        int themeColorText = settings.getInt("themeColorText",-1);
        int themeColorBg = settings.getInt("themeColorBg",-1);

        if (themeColorHeader != -1) {

            if (themeColorText == -1)
                themeColorText = getContrastColor(themeColorHeader);

            if (Build.VERSION.SDK_INT >= 21) {
                getWindow().setNavigationBarColor(themeColorHeader);
                getWindow().setStatusBarColor(themeColorHeader);
                getWindow().setTitleColor(getContrastColor(themeColorHeader));
            }

            mToolbar.setBackgroundColor(themeColorHeader);
            mToolbar.setTitleTextColor(getContrastColor(themeColorHeader));

            mTabLayout.setBackgroundColor(themeColorHeader);
            mTabLayout.setTabTextColors(themeColorText, themeColorText);

            mFab.setBackgroundColor(themeColorHeader);

        }

        if (themeColorBg != -1)
        {
            if (mConversationList != null && mConversationList.getView() != null)
                mConversationList.getView().setBackgroundColor(themeColorBg);

            if (mContactList != null &&  mContactList.getView() != null)
                mContactList.getView().setBackgroundColor(themeColorBg);

            if (mMoreFragment != null && mMoreFragment.getView() != null)
                mMoreFragment.getView().setBackgroundColor(themeColorBg);

            if (mAccountFragment != null && mAccountFragment.getView() != null)
                mAccountFragment.getView().setBackgroundColor(themeColorBg);


        }

    }

    public static int getContrastColor(int colorIn) {
        double y = (299 * Color.red(colorIn) + 587 * Color.green(colorIn) + 114 * Color.blue(colorIn)) / 1000;
        return y >= 128 ? Color.BLACK : Color.WHITE;
    }

    private void checkCustomFont ()
    {

        /**
        if (Preferences.isLanguageTibetan())
        {
        //    CustomTypefaceManager.loadFromAssets(this,true);

        }
        else
        {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            List<InputMethodInfo> mInputMethodProperties = imm.getEnabledInputMethodList();

            final int N = mInputMethodProperties.size();
            boolean loadTibetan = false;
            for (int i = 0; i < N; i++) {

                InputMethodInfo imi = mInputMethodProperties.get(i);

                //imi contains the information about the keyboard you are using
                if (imi.getPackageName().equals("org.ironrabbit.bhoboard")) {
                    //                    CustomTypefaceManager.loadFromKeyboard(this);
                    loadTibetan = true;

                    break;
                }

            }

//            CustomTypefaceManager.loadFromAssets(this, loadTibetan);
        }
         **/

    }

    /**
    private void requestChangeBatteryOptimizations ()
    {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.battery_opt_title)
                    .setMessage(R.string.battery_opt_message)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton) {


                            Intent myIntent = new Intent();
                            myIntent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                            startActivity(myIntent);

                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton) {


                            dialog.dismiss();
                        }
                    })
                    .create();
            dialog.show();
        }

    }**/


}
