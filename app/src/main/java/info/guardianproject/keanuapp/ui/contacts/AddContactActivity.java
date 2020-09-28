/*
 * Copyright (C) 2008 Esmertec AG. Copyright (C) 2008 The Android Open Source
 * Project
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

package info.guardianproject.keanuapp.ui.contacts;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Telephony;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.widget.ListPopupWindow;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import info.guardianproject.keanu.core.model.Contact;
import info.guardianproject.keanu.core.model.ImConnection;
import info.guardianproject.keanu.core.model.ImErrorInfo;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.service.IContactList;
import info.guardianproject.keanu.core.service.IContactListListener;
import info.guardianproject.keanu.core.service.IImConnection;
import info.guardianproject.keanu.core.service.RemoteImService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import info.guardianproject.keanu.matrix.plugin.MatrixAddress;
import info.guardianproject.keanuapp.MainActivity;
import info.guardianproject.keanuapp.R;
import info.guardianproject.keanuapp.ImApp;
import info.guardianproject.keanuapp.tasks.AddContactAsyncTask;
import info.guardianproject.keanuapp.ui.BaseActivity;
import info.guardianproject.keanuapp.ui.accounts.AccountListItem;
import info.guardianproject.keanuapp.ui.legacy.SimpleAlertHandler;
import info.guardianproject.keanuapp.ui.onboarding.OnboardingManager;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;


public class AddContactActivity extends BaseActivity {
    private static final String TAG = "AddContactActivity";

    private static final String[] CONTACT_LIST_PROJECTION = { Imps.ContactList._ID,
                                                             Imps.ContactList.NAME, };
    private static final int CONTACT_LIST_NAME_COLUMN = 1;

    private EditText mNewAddress;
    //private Spinner mListSpinner;
  //  Button mInviteButton;
    ImApp mApp;
    SimpleAlertHandler mHandler;

    private boolean mAddLocalContact = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        super.onCreate(savedInstanceState);

        setTitle("");

        mApp = (ImApp)getApplication();

        long providerId = getIntent().getLongExtra(ContactsPickerActivity.EXTRA_RESULT_PROVIDER,mApp.getDefaultProviderId());
        long accountId = getIntent().getLongExtra(ContactsPickerActivity.EXTRA_RESULT_ACCOUNT,mApp.getDefaultAccountId());

        mConn = RemoteImService.getConnection(providerId, accountId);
        mHandler = new SimpleAlertHandler(this);

        setContentView(R.layout.add_contact_activity);

        TextView label = (TextView) findViewById(R.id.input_contact_label);
        label.setText(getString(R.string.enter_your_friends_account, mApp.getDefaultUsername()));

        mNewAddress = (EditText) findViewById(R.id.email);
        mNewAddress.addTextChangedListener(mTextWatcher);

        mNewAddress.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {

                    addContact();
                }
                return false;
            }
        });


        setupActions ();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Intent intent = getIntent();
        mAddLocalContact = intent.getBooleanExtra("addLocalContact",true);

        String scheme = intent.getScheme();

        if (TextUtils.equals(scheme, "matrix"))
        {
            addContactFromUri(intent.getData());
        }


        if (intent.hasExtra("username"))
        {
            String newContact = intent.getStringExtra("username");
            mNewAddress.setText(newContact);

        }

    }

    private boolean isPackageInstalled(String packagename, PackageManager packageManager) {
        try {
            packageManager.getPackageInfo(packagename, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();


    }

    private String getInviteMessage() {
        ImApp app = ((ImApp)getApplication());

        String nickname = app.getDefaultNickname();
        if (nickname == null)
            nickname = new MatrixAddress(app.getDefaultUsername()).getUser();

        return OnboardingManager.generateInviteMessage(AddContactActivity.this, nickname, app.getDefaultUsername());
    }

    private void setupActions ()
    {
        PackageManager pm = getPackageManager();

        View btnAddFriend = findViewById(R.id.btnAddFriend);
        btnAddFriend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mNewAddress.getText() != null && mNewAddress.getText().length() > 0) {
                    addContact();
                }
            }
        });

        // WeChat installed?
        ImageButton btnInviteWeChat = findViewById(R.id.btnInviteWeChat);
        final String packageNameWeChat = "com.tencent.mm";
        if (isPackageInstalled(packageNameWeChat, pm)) {
            try {
                Drawable icon = getPackageManager().getApplicationIcon(packageNameWeChat);
                if (icon != null) {
                    btnInviteWeChat.setImageDrawable(icon);
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            btnInviteWeChat.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    OnboardingManager.inviteShareToPackage(AddContactActivity.this, getInviteMessage(), packageNameWeChat);
                }

            });
        } else {
            btnInviteWeChat.setVisibility(View.GONE);
        }

        // WhatsApp installed?
        ImageButton btnInviteWhatsApp = findViewById(R.id.btnInviteWhatsApp);
        final String packageNameWhatsApp = "com.whatsapp";
        if (isPackageInstalled(packageNameWhatsApp, pm)) {
            try {
                Drawable icon = getPackageManager().getApplicationIcon(packageNameWhatsApp);
                if (icon != null) {
                    btnInviteWhatsApp.setImageDrawable(icon);
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            btnInviteWhatsApp.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    OnboardingManager.inviteShareToPackage(AddContactActivity.this, getInviteMessage(), packageNameWhatsApp);
                }

            });
        } else {
            btnInviteWhatsApp.setVisibility(View.GONE);
        }

        // Populate SMS option
        View btnInviteSms = findViewById(R.id.btnInviteSMS);
        if (pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                String defaultSmsPackageName = Telephony.Sms.getDefaultSmsPackage(this);
                if (defaultSmsPackageName != null) {
                    try {
                        Drawable icon = getPackageManager().getApplicationIcon(defaultSmsPackageName);
                        if (icon != null) {
                            ((ImageButton) btnInviteSms).setImageDrawable(icon);
                        }
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            btnInviteSms.setVisibility(View.GONE);
        }

        btnInviteSms.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                OnboardingManager.inviteSMSContact(AddContactActivity.this, null);
            }

        });

        View btnInviteShare = findViewById(R.id.btnInviteShare);
        btnInviteShare.setOnClickListener(new View.OnClickListener()
        {

            @Override
            public void onClick(View v) {
                OnboardingManager.inviteShare(AddContactActivity.this, getInviteMessage());
            }

        });

        View btnInviteQR = findViewById(R.id.btnInviteScan);
        btnInviteQR.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                if (hasCameraPermission()) {
                    ImApp app = ((ImApp) getApplication());

                    String xmppLink = OnboardingManager.generateMatrixLink(app.getDefaultUsername());
                    OnboardingManager.inviteScan(AddContactActivity.this, xmppLink);
                }
            }

        });

        View btnInviteNearby = findViewById(R.id.btnInviteNearby);
        btnInviteNearby.setOnClickListener(new View.OnClickListener()
        {

            @Override
            public void onClick(View v) {
                ImApp app = ((ImApp) getApplication());
                String xmppLink = OnboardingManager.generateMatrixLink(app.getDefaultUsername());
                OnboardingManager.inviteNearby(AddContactActivity.this, xmppLink);
            }

        });
    }

    private final static int MY_PERMISSIONS_REQUEST_CAMERA = 1;

    boolean hasCameraPermission () {
        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA);

        if (permissionCheck == PackageManager.PERMISSION_DENIED)
        {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    MY_PERMISSIONS_REQUEST_CAMERA);

            return false;
        }
        else {

            return true;
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        
    }



    public class ProviderListItemFactory implements LayoutInflater.Factory {
        @Override
        public View onCreateView(String name, Context context, AttributeSet attrs) {
            if (name != null && name.equals(AccountListItem.class.getName())) {
            //    return new ProviderListItem(context, AddContactActivity.this, null);
                return new AccountListItem(context, attrs);
            }
            return null;
        }

    }

    private int searchInitListPos(Cursor c, String listName) {
        if (TextUtils.isEmpty(listName)) {
            return 0;
        }
        c.moveToPosition(-1);
        while (c.moveToNext()) {
            if (listName.equals(c.getString(CONTACT_LIST_NAME_COLUMN))) {
                return c.getPosition();
            }
        }
        return 0;
    }

    private String getDomain (long providerId)
    {
        //mDefaultDomain = Imps.ProviderSettings.getStringValue(getContentResolver(), mProviderId,
          //      ImpsConfigNames.DEFAULT_DOMAIN);
        ContentResolver cr = getContentResolver();
        Cursor pCursor = cr.query(Imps.ProviderSettings.CONTENT_URI, new String[]{Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE}, Imps.ProviderSettings.PROVIDER + "=?", new String[]{Long.toString(providerId)}, null);

        Imps.ProviderSettings.QueryMap settings = new Imps.ProviderSettings.QueryMap(
                pCursor, cr, providerId, false /* don't keep updated */, null /* no handler */);

        String domain = settings.getDomain();//get domain of current user

        settings.close();
        pCursor.close();

        return domain;
    }

    boolean foundOne = false;

    private synchronized void addContact() {

        if (foundOne)
            return;

        String[] recipients = mNewAddress.getText().toString().split(",");

        String addAddress = null;

        for (String address : recipients) {

            addAddress = address;

            if (addAddress.trim().length() > 0) {
                if (!address.startsWith("@"))
                    addAddress = '@' + address + ':' + getString(R.string.default_server);

                if (mAddLocalContact) {
                    new AddContactAsyncTask(mApp.getDefaultProviderId(), mApp.getDefaultAccountId()).execute(addAddress);
                }

                foundOne = true;
            }
        }

        if (foundOne) {
            Intent intent = new Intent();
            intent.putExtra(ContactsPickerActivity.EXTRA_RESULT_USERNAME, addAddress);
            intent.putExtra(ContactsPickerActivity.EXTRA_RESULT_PROVIDER, mApp.getDefaultProviderId());
            intent.putExtra(ContactsPickerActivity.EXTRA_RESULT_ACCOUNT, mApp.getDefaultAccountId());

            setResult(RESULT_OK, intent);

        }

        finish();




    }



    /**
    private String getSelectedListName() {
        Cursor c = (Cursor) mListSpinner.getSelectedItem();
        return (c == null) ? null : c.getString(CONTACT_LIST_NAME_COLUMN);
    }*/

    private View.OnClickListener mButtonHandler = new View.OnClickListener() {
        public void onClick(View v) {
            mApp.callWhenServiceConnected(mHandler, new Runnable() {
                public void run() {
                    addContact();
                }
            });
        }
    };


    private View.OnClickListener mScanHandler = new View.OnClickListener() {
        public void onClick(View v) {
         //   new IntentIntegrator(AddContactActivity.this).initiateScan();

        }
    };

    ListPopupWindow mDomainList;
    ArrayList<String> sortedSuggestions;

    private synchronized void showUserSuggestions (final Contact[] contacts)
    {
        if (contacts == null || contacts.length == 0)
        {
            if (mDomainList != null && mDomainList.isShowing())
                mDomainList.dismiss();
            return;
        }

        if (mDomainList == null) {
            mDomainList = new ListPopupWindow(this);
            mDomainList.setAnchorView(mNewAddress);
            mDomainList.setWidth(ListPopupWindow.WRAP_CONTENT);
            mDomainList.setHeight(ListPopupWindow.WRAP_CONTENT);
            mDomainList.setDropDownGravity(CENTER_HORIZONTAL);

            mDomainList.setModal(false);

            mDomainList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    mDomainList.dismiss();
                    mNewAddress.setText(sortedSuggestions.get(position));
                    mNewAddress.setSelection(mNewAddress.length());
                }
            });
        }

        HashMap<String, String> suggestions = new HashMap<>();
        for (Contact contact : contacts) {

            if (!mNewAddress.getText().toString().equals(contact.getAddress().getUser()))
                suggestions.put(contact.getAddress().getAddress(), contact.getAddress().getUser());
        }

        if (suggestions.size() > 0) {

            sortedSuggestions = new ArrayList<>(suggestions.values());

            mDomainList.setAdapter(new ArrayAdapter(
                    this,
                    android.R.layout.simple_dropdown_item_1line, sortedSuggestions));

            mDomainList.show();


        }
        else if (mDomainList != null && mDomainList.isShowing())
        {
            mDomainList.dismiss();
        }
    }

    private IImConnection mConn;

    private TextWatcher mTextWatcher = new TextWatcher() {
        public void afterTextChanged(Editable s) {

            try {

                if (mDomainList != null)
                    mDomainList.dismiss();

                String searchString = mNewAddress.getText().toString();
                if (searchString.length() > 1) {

                    if (mConn == null)
                        mConn = checkConnection();

                    if (mConn != null && mConn.getState() == ImConnection.LOGGED_IN) {

                        /**
                         //disable user search for now
                        mConn.searchForUser(searchString, new IContactListListener() {
                            @Override
                            public void onContactChange(int type, IContactList list, Contact contact) throws RemoteException {

                            }

                            @Override
                            public void onAllContactListsLoaded() throws RemoteException {

                            }

                            @Override
                            public void onContactsPresenceUpdate(Contact[] contacts) throws RemoteException {

                                if (contacts != null && contacts.length > 0) {

                                    showUserSuggestions(contacts);
                                }
                            }

                            @Override
                            public void onContactError(int errorType, ImErrorInfo error, String listName, Contact contact) throws RemoteException {

                            }

                            @Override
                            public IBinder asBinder() {
                                return null;
                            }
                        });**/
                    }
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // noop


        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // noop




        }
    };

    private static void log(String msg) {
        Log.d(LOG_TAG, "<AddContactActivity> " + msg);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultIntent) {
        super.onActivityResult(requestCode, resultCode, resultIntent);

        if (resultCode == RESULT_OK) {
            if (requestCode == OnboardingManager.REQUEST_SCAN) {

                ArrayList<String> resultScans = resultIntent.getStringArrayListExtra("result");
                for (String resultScan : resultScans) {
                    Log.v("ScannerDon", "Result--" + resultScan);

                    try {

                        if (resultScan.startsWith("zom://"))
                        {
                            List<String> params = Uri.parse(resultScan).getQueryParameters("id");

                            if (params.size() > 0) {
                                String address = params.get(0);

                                if (mAddLocalContact)
                                    new AddContactAsyncTask(mApp.getDefaultProviderId(), mApp.getDefaultAccountId()).execute(address, null);

                                Intent intent = new Intent();
                                intent.putExtra(ContactsPickerActivity.EXTRA_RESULT_USERNAME, address);
                                intent.putExtra(ContactsPickerActivity.EXTRA_RESULT_PROVIDER, mApp.getDefaultProviderId());
                                intent.putExtra(ContactsPickerActivity.EXTRA_RESULT_ACCOUNT, mApp.getDefaultAccountId());
                                Log.v("ScannerDon", "Result 1--" + address);
                                setResult(RESULT_OK, intent);
                            }
                        } else {
                            //parse each string and if they are for a new user then add the user
                            OnboardingManager.DecodedInviteLink diLink = OnboardingManager.decodeInviteLink(resultScan);

                            if (diLink.username.startsWith("@")) {
                                if (mAddLocalContact)
                                    new AddContactAsyncTask(mApp.getDefaultProviderId(), mApp.getDefaultAccountId()).execute(diLink.username, diLink.fingerprint, diLink.nickname);

                                Intent intent=new Intent();
                                intent.putExtra(ContactsPickerActivity.EXTRA_RESULT_USERNAME, diLink.username);
                                intent.putExtra(ContactsPickerActivity.EXTRA_RESULT_PROVIDER, mApp.getDefaultProviderId());
                                intent.putExtra(ContactsPickerActivity.EXTRA_RESULT_ACCOUNT, mApp.getDefaultAccountId());

                                setResult(RESULT_OK, intent);
                            }
                            else if (diLink.username.startsWith("!")||diLink.username.startsWith("#")) {
                                Intent intentAdd = new Intent(this, MainActivity.class);
                                intentAdd.setAction("join");
                                intentAdd.putExtra("group", diLink.username);
                                startActivity(intentAdd);
                                finish();
                            }

                            Intent intent = new Intent();
                            intent.putExtra(ContactsPickerActivity.EXTRA_RESULT_USERNAME, diLink.username);
                            intent.putExtra(ContactsPickerActivity.EXTRA_RESULT_PROVIDER, mApp.getDefaultProviderId());
                            intent.putExtra(ContactsPickerActivity.EXTRA_RESULT_ACCOUNT, mApp.getDefaultAccountId());

                            setResult(RESULT_OK, intent);
                        }

                        //if they are for a group chat, then add the group
                    } catch (Exception e) {
                        Log.v("ScannerDon", "Result 3--" + e.getMessage());
                        Log.w(LOG_TAG, "error parsing QR invite link", e);
                    }
                }
            }

            finish();

        }


    }

    /**
     * Implement {@code xmpp:} URI parsing according to the RFC: http://tools.ietf.org/html/rfc5122
     * @param uri the URI to be parsed
     */
    private void addContactFromUri(Uri uri) {
        Log.i(TAG, "addContactFromUri: " + uri + "  scheme: " + uri.getScheme());

        mNewAddress.setText(uri.getQueryParameter("id"));

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;

        }
        return super.onOptionsItemSelected(item);
    }


    private IImConnection checkConnection() {
        try {

            long providerId = getIntent().getLongExtra(ContactsPickerActivity.EXTRA_RESULT_PROVIDER,mApp.getDefaultProviderId());
            long accountId = getIntent().getLongExtra(ContactsPickerActivity.EXTRA_RESULT_ACCOUNT,mApp.getDefaultAccountId());


            if (providerId != -1 && accountId != -1) {
                IImConnection conn = RemoteImService.getConnection(providerId, accountId);

               return conn;
            }


        } catch (Exception e) {
        }

        return null;


    }
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
                                                         Imps.Provider.ACCOUNT_CONNECTION_STATUS
                                                         
                                                        };

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
}
