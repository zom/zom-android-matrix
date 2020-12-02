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

package info.guardianproject.keanuapp.ui.conversation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.appcompat.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import org.apache.commons.io.IOUtils;
import org.ocpsoft.prettytime.PrettyTime;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import info.guardianproject.keanuapp.R;
import info.guardianproject.keanu.core.model.Presence;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.service.IChatSession;
import info.guardianproject.keanu.core.util.SecureMediaStore;
import info.guardianproject.keanu.core.util.SystemServices;
import info.guardianproject.keanuapp.ImApp;
import info.guardianproject.keanuapp.ui.BaseActivity;
import info.guardianproject.keanuapp.ui.camera.CameraActivity;
import info.guardianproject.keanuapp.ui.contacts.ContactsPickerActivity;
import info.guardianproject.keanuapp.ui.stories.StoryEditorActivity;
import info.guardianproject.keanuapp.ui.widgets.MediaInfo;
import info.guardianproject.keanuapp.ui.widgets.ShareRequest;

import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;
import static info.guardianproject.keanuapp.ui.conversation.StoryActivity.ARG_CONTRIBUTOR_MODE;

public class ConversationDetailActivity extends BaseActivity {

    private long mChatId = -1;
    private String mAddress = null;
    private String mNickname = null;

    protected ConversationView mConvoView = null;

    MediaRecorder mMediaRecorder = null;
    File mAudioFilePath = null;

    private ImApp mApp;

    //private AppBarLayout appBarLayout;
    private View mRootLayout;
    protected Toolbar mToolbar;

    private PrettyTime mPrettyTime;
    private View.OnClickListener backButtonHandler;

    private boolean mIsContributor = false;

    private Handler mHandler = new Handler()
    {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            if (msg.what == 1) {
                if (!mConvoView.isGroupChat()) {
                    if (mConvoView.getRemotePresence() == Presence.AVAILABLE)
                    {
                        getSupportActionBar().setSubtitle(getString(R.string.presence_available));
                    }
                    else if (mConvoView.getLastSeen() != null) {
                        Date lastSeen = new Date();
                        if (mConvoView.getLastSeen().before(lastSeen))
                            lastSeen = mConvoView.getLastSeen();
                        getSupportActionBar().setSubtitle(mPrettyTime.format(lastSeen));
                    } else {
                        if (mConvoView.getRemotePresence() == Presence.AWAY)
                            getSupportActionBar().setSubtitle(getString(R.string.presence_away));
                        else if (mConvoView.getRemotePresence() == Presence.OFFLINE)
                            getSupportActionBar().setSubtitle(getString(R.string.presence_offline));
                        else if (mConvoView.getRemotePresence() == Presence.DO_NOT_DISTURB)
                            getSupportActionBar().setSubtitle(getString(R.string.presence_busy));
                        else
                            getSupportActionBar().setSubtitle(getString(R.string.presence_available));

                    }
                }
            }
        }
    };

    /**
     * Override to use another layout
     * @return Returns a layout file id.
     */
    protected int getLayoutFileId() {
        return R.layout.awesome_activity_detail;
    }

    protected ConversationView createConvoView() {
        return new ConversationView(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
       // getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        setContentView(getLayoutFileId());
        mApp = (ImApp)getApplication();
        mToolbar = (Toolbar) findViewById(R.id.toolbar);

        mConvoView = createConvoView();

      //  appBarLayout = (AppBarLayout)findViewById(R.id.appbar);
        mRootLayout = findViewById(R.id.main_content);

        mPrettyTime = new PrettyTime(getCurrentLocale());

        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        applyStyleForToolbar();

        collapseToolbar();

        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        );

    }

    public void updateLastSeen (Date lastSeen)
    {
       mHandler.sendEmptyMessage(1);
    }

    public void applyStyleForToolbar() {


        getSupportActionBar().setTitle(mConvoView.getTitle());

        /**
        if (!mConvoView.isGroupChat()) {
            if (mConvoView.getLastSeen() != null) {
                getSupportActionBar().setSubtitle(new PrettyTime().format(mConvoView.getLastSeen()));
            } else if (mConvoView.getRemotePresence() != -1) {
                if (mConvoView.getRemotePresence() == Presence.AWAY)
                    getSupportActionBar().setSubtitle(getString(R.string.presence_away));
                else if (mConvoView.getRemotePresence() == Presence.OFFLINE)
                    getSupportActionBar().setSubtitle(getString(R.string.presence_offline));
                else if (mConvoView.getRemotePresence() == Presence.DO_NOT_DISTURB)
                    getSupportActionBar().setSubtitle(getString(R.string.presence_busy));
                else
                    getSupportActionBar().setSubtitle(getString(R.string.presence_available));

            } else {
                getSupportActionBar().setSubtitle(mConvoView.getSubtitle());
            }
        }
        else
        {
            getSupportActionBar().setSubtitle("");

        }*/


        //not set color
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        int themeColorHeader = settings.getInt("themeColor",-1);
        int themeColorText = settings.getInt("themeColorText",-1);
        int themeColorBg = settings.getInt("themeColorBg",-1);

        if (themeColorHeader != -1) {

            if (themeColorText == -1)
                themeColorText = getContrastColor(themeColorHeader);

            if (Build.VERSION.SDK_INT >= 21) {
                getWindow().setNavigationBarColor(themeColorHeader);
                getWindow().setStatusBarColor(themeColorHeader);
                getWindow().setTitleColor(themeColorText);
            }

      //      appBarLayout.setBackgroundColor(themeColorHeader);
         //   collapsingToolbar.setBackgroundColor(themeColorHeader);
            mToolbar.setBackgroundColor(themeColorHeader);
            mToolbar.setTitleTextColor(themeColorText);

        }

        if (themeColorBg != -1)
        {
            if (mRootLayout != null)
                mRootLayout.setBackgroundColor(themeColorBg);

            View viewInput = findViewById(R.id.inputLayout);
            viewInput.setBackgroundColor(themeColorBg);

            if (themeColorText != -1) {
                mConvoView.mComposeMessage.setTextColor(themeColorText);
                mConvoView.mComposeMessage.setHintTextColor(themeColorText);
            }
        }

    }

    @SuppressLint("Range")
    public static int getContrastColor(int colorIn) {
         double y = (299 * Color.red(colorIn) + 587 * Color.green(colorIn) + 114 * Color.blue(colorIn)) / 1000;
        return y >= 128 ? Color.BLACK : Color.WHITE;
    }


    private void processIntent(Intent intent)
    {

        mApp = (ImApp)getApplication();

        if (intent != null) {
            mChatId = intent.getLongExtra("id", -1);

            mAddress = intent.getStringExtra("address");
            mNickname = intent.getStringExtra("nickname");

            if (mChatId != -1) {
                boolean bound = mConvoView.bindChat(mChatId, mAddress, mNickname);

                if (bound) {

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            applyStyleForToolbar();

                            if (intent.getBooleanExtra("isNew", false)) {
                                mConvoView.showGroupInfo(intent.getStringExtra("subject"));
                                intent.putExtra("isNew", false);

                            }

                        }
                    });

                } else
                    finish();
            } else {
                finish();
            }
        }

    }

    public void collapseToolbar(){

     //   appBarLayout.setExpanded(false);
    }

    public void expandToolbar(){

    //    appBarLayout.setExpanded(true);
    }

    @Override
    protected void onResume() {
        super.onResume();

        new AsyncTask<Void,Void,Void>() {

            @Override
            protected Void doInBackground(Void... voids) {
                processIntent(getIntent());

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);

                mConvoView.setSelected(true);

            }
        }.execute();



    }

    private void setLastRead ()
    {

        // Set last read date now!
        if (mChatId != -1) {
            ContentValues values = new ContentValues(1);
            values.put(Imps.Chats.LAST_READ_DATE, System.currentTimeMillis());
            getContentResolver().update(ContentUris.withAppendedId(Imps.Chats.CONTENT_URI, mChatId), values, null, null);
        }

    }

    @Override
    protected void onPause() {
        super.onPause();

        mConvoView.setSelected(false);

       // unregisterReceiver(receiver);

        // Set last read date now!
        if (mChatId != -1) {
            ContentValues values = new ContentValues(1);
            values.put(Imps.Chats.LAST_READ_DATE, System.currentTimeMillis());
            getContentResolver().update(ContentUris.withAppendedId(Imps.Chats.CONTENT_URI, mChatId), values, null, null);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);
        processIntent(intent);

        // Set last read date now!
        if (mChatId != -1) {
            ContentValues values = new ContentValues(1);
            values.put(Imps.Chats.LAST_READ_DATE, System.currentTimeMillis());
            getContentResolver().update(ContentUris.withAppendedId(Imps.Chats.CONTENT_URI, mChatId), values, null, null);
        }
    }

    @Override
    public void onBackPressed() {
        if (getBackButtonHandler() != null) {
            backButtonHandler.onClick(null);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (getBackButtonHandler() != null) {
                    backButtonHandler.onClick(null);
                    return true;
                }
                finish();
                return true;
            case R.id.menu_end_conversation:
                mConvoView.closeChatSession(true);
                finish();
                return true;
            case R.id.menu_group_info:
                mConvoView.showGroupInfo(null);
                return true;
            case R.id.menu_live_mode:
                startLiveMode(true);
                return true;
                /**
            case R.id.menu_start_live_view:
                startLiveMode(false);
                return true;**/
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.menu_conversation_detail_group, menu);
        return true;
    }

    void startLiveMode (boolean isContrib)
    {
        Intent intent = new Intent(this, StoryActivity.class);
        intent.putExtra("id", mChatId);
        intent.putExtra("address", mAddress);
        intent.putExtra(ARG_CONTRIBUTOR_MODE, isContrib);

        startActivity(intent);
    }


    void showAddContact ()
    {
        Intent intent = new Intent(this, ContactsPickerActivity.class);
        startActivityForResult(intent, REQUEST_PICK_CONTACTS);
    }

    void startImagePicker() {

        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE);

        if (permissionCheck ==PackageManager.PERMISSION_DENIED)
        {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {


                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                Snackbar.make(mConvoView.getHistoryView(), R.string.grant_perms, Snackbar.LENGTH_LONG).show();
            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_FILE);

            }
        }
        else {
            Intent intent = new Intent(ConversationDetailActivity.this, AddUpdateMediaActivity.class);
            intent.setType("image");
            startActivityForResult(intent,REQUEST_ADD_MEDIA);

            //startActivityForResult(getPickImageChooserIntent(), REQUEST_SEND_IMAGE);


        }

    }

    /**
     * Create a chooser intent to select the source to get image from.<br/>
     * The source can be camera's (ACTION_IMAGE_CAPTURE) or gallery's (ACTION_GET_CONTENT).<br/>
     * All possible sources are added to the intent chooser.
     */
    public Intent getPickImageChooserIntent() {


        List<Intent> allIntents = new ArrayList<>();
        PackageManager packageManager = getPackageManager();

        // collect all gallery intents
        Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
        galleryIntent.setType("*/*");

        String[] mimetypes = {"image/*", "video/*"};
        galleryIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimetypes);

        List<ResolveInfo> listGallery = packageManager.queryIntentActivities(galleryIntent, 0);
        for (ResolveInfo res : listGallery) {
            Intent intent = new Intent(galleryIntent);
            intent.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
            intent.setPackage(res.activityInfo.packageName);
            allIntents.add(intent);
        }

        // the main intent is the last in the list (fucking android) so pickup the useless one
        Intent mainIntent = allIntents.get(allIntents.size() - 1);
        for (Intent intent : allIntents) {
            if (intent.getComponent().getClassName().equals("com.android.documentsui.DocumentsActivity")) {
                mainIntent = intent;
                break;
            }
        }
        allIntents.remove(mainIntent);

        // Create a chooser from the main intent
        Intent chooserIntent = Intent.createChooser(mainIntent, getString(R.string.choose_photos));

        // Add all other intents
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, allIntents.toArray(new Parcelable[allIntents.size()]));

        return chooserIntent;
    }

    Uri mLastPhoto = null;
    private final static int MY_PERMISSIONS_REQUEST_AUDIO = 1;
    private final static int MY_PERMISSIONS_REQUEST_CAMERA = 2;
    private final static int MY_PERMISSIONS_REQUEST_FILE = 3;


    void startPhotoTaker() {
        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA);

        if (permissionCheck ==PackageManager.PERMISSION_DENIED)
        {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {


                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                Snackbar.make(mConvoView.getHistoryView(), R.string.grant_perms, Snackbar.LENGTH_LONG).show();
            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        MY_PERMISSIONS_REQUEST_CAMERA);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }
        else {

            Intent intent = new Intent(ConversationDetailActivity.this, AddUpdateMediaActivity.class);
            startActivityForResult(intent,REQUEST_ADD_MEDIA);

            /**
            Intent intent = new Intent(this, CameraActivity.class);

            intent.putExtra(CameraActivity.SETTING_ONE_AND_DONE,true);
            startActivityForResult(intent, ConversationDetailActivity.REQUEST_TAKE_PICTURE);
             **/

            /**
           if (Preferences.useProofMode())
           {

           }
           else {
               // create Intent to take a picture and return control to the calling application
               Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
               File photo = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "cs_" + new Date().getTime() + ".jpg");

               mLastPhoto = FileProvider.getUriForFile(this,
                       BuildConfig.APPLICATION_ID + ".provider",
                       photo);

               intent.putExtra(MediaStore.EXTRA_OUTPUT,
                       mLastPhoto);

               // start the image capture Intent
               startActivityForResult(intent, ConversationDetailActivity.REQUEST_TAKE_PICTURE);

           }**/

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }


    void startFilePicker(String mimeType) {

        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE);

        if (permissionCheck ==PackageManager.PERMISSION_DENIED)
        {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {


                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                Snackbar.make(mConvoView.getHistoryView(), R.string.grant_perms, Snackbar.LENGTH_LONG).show();
            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_FILE);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }
        else {

            // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file
            // browser.
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

            // Filter to only show results that can be "opened", such as a
            // file (as opposed to a list of contacts or timezones)
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            // Filter to show only images, using the image MIME data type.
            // If one wanted to search for ogg vorbis files, the type would be "audio/ogg".
            // To search for all documents available via installed storage providers,
            // it would be "*/*".
            intent.setType(mimeType);

            startActivityForResult(Intent.createChooser(intent, getString(R.string.invite_share)), REQUEST_SEND_FILE);
        }
    }

    void startStoryEditor ()
    {

        startActivityForResult(new Intent(this, StoryEditorActivity.class), REQUEST_CREATE_STORY);
    }

    private boolean isCallable(Intent intent) {
        List<ResolveInfo> list = getPackageManager().queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    public void handleSendDelete(Uri contentUri, String defaultType, boolean delete, boolean resizeImage, boolean importContent) {
        final Snackbar sb = Snackbar.make(mConvoView.getHistoryView(), R.string.upgrade_progress_action, Snackbar.LENGTH_INDEFINITE);
        sb.show();

        new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] objects) {

                handleSendDeleteAsync(mConvoView.getChatSession(),contentUri,defaultType,delete,resizeImage,importContent);
                sb.dismiss();

                return null;
            }

            @Override
            protected void onPostExecute(Object o) {
                super.onPostExecute(o);

                sb.dismiss();
            }
        }.execute();
    }

    public void handleSendDeleteAsync(IChatSession session, Uri contentUri, String defaultType, boolean delete, boolean resizeImage, boolean importContent) {
        try {

            // import
            SystemServices.FileInfo info = SystemServices.getFileInfoFromURI(this, contentUri);

            if (info.type == null)
                info.type = defaultType;

            String sessionId = mConvoView.getChatId()+"";

            Uri sendUri;

            if (resizeImage)
                sendUri = SecureMediaStore.resizeAndImportImage(this, sessionId, contentUri, info.type);
            else if (importContent) {

                if (contentUri.getScheme() == null || contentUri.getScheme().equals("assets"))
                    sendUri = SecureMediaStore.importContent(sessionId,info.name, info.stream);
                else if (contentUri.getScheme().startsWith("http"))
                {
                    sendUri = SecureMediaStore.importContent(sessionId,info.name, new URL(contentUri.toString()).openConnection().getInputStream());
                }
                else
                    sendUri = SecureMediaStore.importContent(sessionId,info.name, info.stream);

                if (info.type.startsWith("video"))
                {

                   Bitmap bitmap = null;
                   String videoPath = new SystemServices().getUriRealPath(this, contentUri);
                   if (!TextUtils.isEmpty(videoPath)) {
                       bitmap = ThumbnailUtils.createVideoThumbnail(videoPath, MediaStore.Images.Thumbnails.MINI_KIND);

                       if (bitmap != null){
                           String thumbPath = sendUri.getPath() + ".thumb.jpg";
                           info.guardianproject.iocipher.File fileThumb = new info.guardianproject.iocipher.File(thumbPath);
                           ByteArrayOutputStream baos = new ByteArrayOutputStream();
                           bitmap.compress(Bitmap.CompressFormat.JPEG,100,baos);
                           IOUtils.write(baos.toByteArray(),new info.guardianproject.iocipher.FileOutputStream(fileThumb));

                       }
                   }

                   if (bitmap == null)
                   {

                       long videoId = -1;
                       String lastPath = contentUri.getLastPathSegment();
                       try {
                           videoId = Long.parseLong(lastPath);
                       }
                       catch (Exception e)
                       {
                           String[] parts = lastPath.split(":");
                           if (parts.length > 1)
                               videoId = Long.parseLong(parts[1]);
                       }

                       if (videoId != -1)
                       {
                           bitmap = MediaStore.Video.Thumbnails.getThumbnail(
                                   getContentResolver(), videoId,
                                   MediaStore.Images.Thumbnails.MINI_KIND,
                                   (BitmapFactory.Options) null);

                           if (bitmap != null) {
                               String thumbPath = sendUri.getPath() + ".thumb.jpg";
                               info.guardianproject.iocipher.File fileThumb = new info.guardianproject.iocipher.File(thumbPath);
                               ByteArrayOutputStream baos = new ByteArrayOutputStream();
                               bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                               IOUtils.write(baos.toByteArray(),new info.guardianproject.iocipher.FileOutputStream(fileThumb));

                           }
                       }
                   }
                }
            }
            else
            {
                sendUri = contentUri;
                info.type = getContentResolver().getType(sendUri);
                if (info.type == null)
                    info.type = defaultType;
            }

            // send
            boolean sent = handleSendData(session, sendUri, info.type);
            if (!sent) {
                // not deleting if not sent
                return;
            }
            // auto delete
            if (delete) {
                boolean deleted = delete(contentUri);
                if (!deleted) {
                    throw new IOException("Error deleting " + contentUri);
                }
            }

            if (info.stream != null)
                info.stream.close();


        } catch (Exception e) {
            //  Toast.makeText(this, "Error sending file", Toast.LENGTH_LONG).show(); // TODO i18n
            Log.e(LOG_TAG, "error sending file", e);
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultIntent) {

        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_PICK_CONTACTS) {

                if (resultIntent == null)
                    return;

                ArrayList<String> invitees = new ArrayList<String>();

                String username = resultIntent.getStringExtra(ContactsPickerActivity.EXTRA_RESULT_USERNAME);

                if (username != null)
                    invitees.add(username);
                else
                    invitees = resultIntent.getStringArrayListExtra(ContactsPickerActivity.EXTRA_RESULT_USERNAMES);

                mConvoView.inviteContacts(invitees);

            }

            if (requestCode == REQUEST_SEND_IMAGE) {

                //Log.v("ImageSend","ImageSend_1");


                if (resultIntent == null)
                    return;

                /**
                Uri uri = resultIntent.getData() ;

                if( uri == null ) {
                    return ;
                }**/

                String[] mediaUris = resultIntent.getStringArrayExtra("resultUris");
                String[] mediaTypes = resultIntent.getStringArrayExtra("resultTypes");

                if (mediaUris == null || mediaUris.length == 0)
                    return;

                for (int i = 0; i < mediaUris.length; i++)
                {
                    ShareRequest request = new ShareRequest();
                    request.deleteFile = false;
                    request.resizeImage = true;
                    request.importContent = true;
                    request.media = Uri.parse(mediaUris[i]);
                    request.mimeType = mediaTypes[i];

                    if (TextUtils.isEmpty(request.mimeType)) {
                        // import
                        //Log.v("ImageSend","ImageSend_2");
                        SystemServices.FileInfo info = null;
                        try {
                            //Log.v("ImageSend","ImageSend_3");
                            info = SystemServices.getFileInfoFromURI(this, request.media);
                            request.mimeType = info.type;
                            info.stream.close();
                        } catch (Exception e) {

                        }

                    }

                    /**
                    if (request.mimeType.startsWith("image"))
                    {
                        try {
                            //Log.v("ImageSend","ImageSend_4");
                            mConvoView.setMediaDraft(request);
                        }
                        catch (Exception e){
                            Log.w(LOG_TAG,"error setting media draft",e);
                        }
                    }
                    else {**/
                        boolean deleteFile = false;
                        boolean resizeImage = false;
                        boolean importContent = true; //let's import it!
                        //Log.v("ImageSend","ImageSend_5");
                        handleSendDelete(request.media, request.mimeType, deleteFile, resizeImage, importContent);
                    //}
                }


            }
            else if (requestCode == REQUEST_SEND_FILE || requestCode == REQUEST_SEND_AUDIO) {


                if (resultIntent == null)
                    return;

                Uri uri = resultIntent.getData() ;

                if( uri == null ) {
                    return;
                }

                String defaultType = resultIntent.getType();

                boolean deleteFile = false;
                boolean resizeImage = false;
                boolean importContent = true; //let's import it!
                //Log.v("ImageSend","ImageSend_send file");

                new AsyncTask<Void,Void,Void>()
                {

                    @Override
                    protected Void doInBackground(Void... voids) {
                        handleSendDelete(uri, defaultType, deleteFile, resizeImage, importContent);
                        return null;
                    }
                }.execute();

            }
            else if (requestCode == REQUEST_ADD_MEDIA)
            {
                String[] mediaUris = resultIntent.getStringArrayExtra("resultUris");
                String[] mediaTypes = resultIntent.getStringArrayExtra("resultTypes");

                if (mediaUris != null) {

                    for (int i = 0; i < mediaUris.length; i++) {
                        boolean deleteFile = false;
                        boolean resizeImage = false;
                        boolean importContent = true; //let's import it!

                        if ((!TextUtils.isEmpty(mediaTypes[i]))
                                && mediaTypes[i].startsWith("video"))
                            importContent = false;
                        //Log.v("ImageSend","REQUEST_ADD_MEDIA");
                        handleSendDelete(Uri.parse(mediaUris[i]), mediaTypes[i], deleteFile, resizeImage, importContent);
                    }
                }
                else {
                    ArrayList<MediaInfo> list = (ArrayList<MediaInfo>) resultIntent.getSerializableExtra("listMediaInfo");

                    if (list != null)
                    {

                        for (MediaInfo mInfo : list) {
                            boolean deleteFile = false;
                            boolean resizeImage = false;
                            boolean importContent = true; //let's import it!

                            if ((!TextUtils.isEmpty(mInfo.mimeType))
                                    && mInfo.mimeType.startsWith("video"))
                                importContent = false;
                            //Log.v("ImageSend","REQUEST_ADD_MEDIA");
                            handleSendDelete(mInfo.uri, mInfo.mimeType, deleteFile, resizeImage, importContent);
                        }
                    }
                }


            }
            else if (requestCode == REQUEST_TAKE_PICTURE)
            {

                ShareRequest request = new ShareRequest();

                request.deleteFile = false;
                request.resizeImage = false;
                request.importContent = false;
                request.media = resultIntent.getData();
                request.mimeType = resultIntent.getType();

                if (request.mimeType.equals("image/jpeg")) {
                    try {
                        mConvoView.setMediaDraft(request);
                    } catch (Exception e) {
                        Log.w(LOG_TAG, "error setting media draft", e);
                    }
                }
                else {
                    sendShareRequest(request);
                }

            }
            else if (requestCode == REQUEST_CREATE_STORY)
            {
                ShareRequest request = new ShareRequest();
                request.deleteFile = false;
                request.resizeImage = false;
                request.importContent = false;
                request.media = resultIntent.getData();
                request.mimeType = resultIntent.getType();
                sendShareRequest(request);

            }
            else if (requestCode == REQUEST_IMAGE_VIEW)
            {
                if (resultIntent != null &&
                        resultIntent.hasExtra("resendImageUri"))
                {
                    ShareRequest request = new ShareRequest();
                    request.deleteFile = false;
                    request.resizeImage = false;
                    request.importContent = false;
                    request.media = Uri.parse(resultIntent.getStringExtra("resendImageUri"));
                    request.mimeType = resultIntent.getStringExtra("resendImageMimeType");

                    sendShareRequest(request);

                }

                mConvoView.requeryCursor();
            }



        }
    }

    public void sendShareRequest (ShareRequest sreq)
    {
        handleSendDelete(sreq.media,sreq.mimeType,sreq.deleteFile,sreq.resizeImage,sreq.importContent);
    }

    public boolean handleSendData(IChatSession session, Uri uri, String mimeType) {
        try {

            if (session != null) {

                String offerId = UUID.randomUUID().toString();
                return session.offerData(offerId, null, uri.toString(), mimeType );
            }

        } catch (RemoteException e) {
            Log.e(LOG_TAG,"error sending file",e);
        }
        return false; // was not sent
    }

    boolean mIsAudioRecording = false;

    public boolean isAudioRecording ()
    {
        return mIsAudioRecording;
    }

    public void startAudioRecording ()
    {
        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO);

        if (permissionCheck ==PackageManager.PERMISSION_DENIED)
        {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.RECORD_AUDIO)) {


                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                Snackbar.make(mConvoView.getHistoryView(), R.string.grant_perms, Snackbar.LENGTH_LONG).show();
            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_REQUEST_AUDIO);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }
        else {

            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am.getMode() == AudioManager.MODE_NORMAL) {

                mMediaRecorder = new MediaRecorder();

                String fileName = UUID.randomUUID().toString().substring(0, 8) + ".m4a";
                mAudioFilePath = new File(getFilesDir(), fileName);

                mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

                //maybe we can modify these in the future, or allow people to tweak them
                mMediaRecorder.setAudioChannels(1);
                mMediaRecorder.setAudioEncodingBitRate(22050);
                mMediaRecorder.setAudioSamplingRate(64000);

                mMediaRecorder.setOutputFile(mAudioFilePath.getAbsolutePath());

                try {
                    mIsAudioRecording = true;
                    mMediaRecorder.prepare();
                    mMediaRecorder.start();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "couldn't start audio", e);
                }
            }
        }
    }

    public int getAudioAmplitude ()
    {
        return mMediaRecorder.getMaxAmplitude();
    }

    public void stopAudioRecording (boolean send)
    {
        if (mMediaRecorder != null && mAudioFilePath != null && mIsAudioRecording) {

            try {

                mMediaRecorder.stop();

                mMediaRecorder.reset();
                mMediaRecorder.release();

                if (send) {
                    Uri uriAudio = Uri.fromFile(mAudioFilePath);
                    boolean deleteFile = true;
                    boolean resizeImage = false;
                    boolean importContent = true;
                    handleSendDelete(uriAudio, "audio/mp4", deleteFile, resizeImage, importContent);
                } else {
                    mAudioFilePath.delete();
                }
            }
            catch (IllegalStateException ise)
            {
                Log.w(LOG_TAG,"error stopping audio recording: " + ise);
            }
            catch (RuntimeException re) //stop can fail so we should catch this here
            {
                Log.w(LOG_TAG,"error stopping audio recording: " + re);
            }

            mIsAudioRecording = false;
        }

    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        if (mLastPhoto != null)
            savedInstanceState.putString("lastphoto", mLastPhoto.toString());

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

    private TextView getActionBarTextView(Toolbar toolbar) {
        TextView titleTextView = null;

        try {
            Field f = toolbar.getClass().getDeclaredField("mTitleTextView");
            f.setAccessible(true);
            titleTextView = (TextView) f.get(toolbar);
        } catch (NoSuchFieldException e) {
        } catch (IllegalAccessException e) {
        }
        return titleTextView;
    }


    @TargetApi(Build.VERSION_CODES.N)
    public Locale getCurrentLocale(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            return getResources().getConfiguration().getLocales().get(0);
        } else{
            //noinspection deprecation
            return getResources().getConfiguration().locale;
        }
    }

    public View.OnClickListener getBackButtonHandler() {
        return backButtonHandler;
    }

    public void setBackButtonHandler(View.OnClickListener backButtonHandler) {
        this.backButtonHandler = backButtonHandler;
    }

    public static final int REQUEST_PICK_CONTACTS = RESULT_FIRST_USER + 1;
    public static final int REQUEST_SEND_IMAGE = REQUEST_PICK_CONTACTS + 1;
    public static final int REQUEST_SEND_FILE = REQUEST_SEND_IMAGE + 1;
    public static final int REQUEST_SEND_AUDIO = REQUEST_SEND_FILE + 1;
    public static final int REQUEST_TAKE_PICTURE = REQUEST_SEND_AUDIO + 1;
    public static final int REQUEST_SETTINGS = REQUEST_TAKE_PICTURE + 1;
    public static final int REQUEST_TAKE_PICTURE_SECURE = REQUEST_SETTINGS + 1;
    public static final int REQUEST_ADD_CONTACT = REQUEST_TAKE_PICTURE_SECURE + 1;
    public static final int REQUEST_IMAGE_VIEW = REQUEST_ADD_CONTACT + 1;
    public static final int REQUEST_ADD_MEDIA = REQUEST_IMAGE_VIEW + 1;
    public static final int REQUEST_CREATE_STORY = REQUEST_ADD_MEDIA + 1;

}
