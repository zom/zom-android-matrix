package info.guardianproject.keanuapp.ui.onboarding;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.ListPopupWindow;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;


import info.guardianproject.keanu.core.Preferences;
import info.guardianproject.keanu.core.model.Server;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.ui.RoundedAvatarDrawable;
import info.guardianproject.keanu.core.util.DatabaseUtils;
import info.guardianproject.keanu.core.util.SecureMediaStore;

import com.google.android.material.snackbar.Snackbar;
import com.theartofdev.edmodo.cropper.CropImageView;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import info.guardianproject.keanu.core.model.Server;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.ui.RoundedAvatarDrawable;
import info.guardianproject.keanu.core.util.DatabaseUtils;
import info.guardianproject.keanu.core.util.Languages;
import info.guardianproject.keanu.core.util.SecureMediaStore;
import info.guardianproject.keanuapp.ImApp;
import info.guardianproject.keanuapp.MainActivity;
import info.guardianproject.keanuapp.R;
import info.guardianproject.keanuapp.tasks.AddContactAsyncTask;
import info.guardianproject.keanuapp.tasks.SignInHelper;
import info.guardianproject.keanuapp.ui.BaseActivity;
import info.guardianproject.keanuapp.ui.legacy.SimpleAlertHandler;

import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;

public class OnboardingActivity extends BaseActivity implements OnboardingListener {

    private ViewFlipper mViewFlipper;
    //private View mSetupButton;
    private ImageView mImageAvatar;

    private EditText mSpinnerDomains;

    private OnboardingAccount mNewAccount;

    private SimpleAlertHandler mHandler;

    private static final String USERNAME_ONLY_ALPHANUM = "[^A-Za-z0-9]";

    private boolean mShowSplash = true;
    private ListPopupWindow mDomainList;

    private FindServerTask mCurrentFindServerTask;
    private boolean mLoggingIn = false;

    private Handler mOnboardingHandler = new Handler();

    private OnboardingManager mManager;

    private long mProviderId = -1;
    private long mAccountId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mShowSplash = getIntent().getBooleanExtra("showSplash",true);

        mProviderId = getIntent().getLongExtra("provider",-1L);
        mAccountId = getIntent().getLongExtra("account",-1L);

        setContentView(R.layout.awesome_onboarding);

        ActionBar ab = getSupportActionBar();

        if (ab != null) {
            if (mShowSplash) {
                ab.hide();
            }
            else {
                ab.setDisplayHomeAsUpEnabled(true);
                ab.setHomeButtonEnabled(true);
            }

            ab.setTitle("");
        }

        mHandler = new SimpleAlertHandler(this);

        View viewSplash = findViewById(R.id.flipViewMain);
        View viewRegister =  findViewById(R.id.flipViewRegister);
      //  View viewCreate = findViewById(R.id.flipViewCreateNew);
        View viewLogin = findViewById(R.id.flipViewLogin);
       // View viewInvite = findViewById(R.id.flipViewInviteFriends);
        View viewAdvanced  = findViewById(R.id.flipViewAdvanced);


        //   if (themeColorHeader != -1)
       //     viewInvite.setBackgroundColor(themeColorHeader);

        mViewFlipper = findViewById(R.id.viewFlipper1);

     //   mEditUsername = (EditText)viewCreate.findViewById(R.id.edtNewName);
        mSpinnerDomains = viewAdvanced.findViewById(R.id.spinnerDomains);
     //   ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
       //         android.R.layout.simple_dropdown_item_1line, OnboardingManager.getServers(this));
        // mSpinnerDomains.setAdapter(adapter);

        mDomainList = new ListPopupWindow(this);
        mDomainList.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line, Server.getServersText(this)));
        mDomainList.setAnchorView(mSpinnerDomains);
        mDomainList.setWidth(600);
        mDomainList.setHeight(400);

        mDomainList.setModal(false);
        mDomainList.setOnItemClickListener((parent, view, position, id) -> {
            mSpinnerDomains.setText(Server.getServersText(OnboardingActivity.this)[position]);
            mDomainList.dismiss();
        });

        mSpinnerDomains.setText(Server.getServersText(OnboardingActivity.this)[0]);


        mSpinnerDomains.setOnClickListener(v -> mDomainList.show());
        mSpinnerDomains.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus)
                mDomainList.show();
        });

        mImageAvatar = findViewById(R.id.imageAvatar);
        mImageAvatar.setOnClickListener(view -> startAvatarTaker());

        setAnimLeft();

        ImageView imageLogo = viewSplash.findViewById(R.id.imageLogo);
        imageLogo.setOnClickListener(v -> {
            setAnimLeft();
            showOnboarding();
        });

        View btnStartOnboardingNext = viewSplash.findViewById(R.id.nextButton);
        btnStartOnboardingNext.setOnClickListener(v -> {
            setAnimLeft();
            showOnboarding();
        });

        View btnShowCreate = viewRegister.findViewById(R.id.btnShowRegister);
        btnShowCreate.setOnClickListener(v -> {
            setAnimLeft();
            showSetupScreen();
        });

        View btnShowLogin = viewRegister.findViewById(R.id.btnShowLogin);
        btnShowLogin.setOnClickListener(v -> {
            setAnimLeft();
            showLoginScreen();
        });

        /*
        View btnShowAdvanced = viewCreate.findViewById(R.id.btnAdvanced);
        btnShowAdvanced.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                setAnimLeft();
                showAdvancedScreen();
            }

        });*/

        // set up language chooser button
        View languageButton = viewSplash.findViewById(R.id.languageButton);
        languageButton.setOnClickListener(v -> {
            final Activity activity = OnboardingActivity.this;
            final Languages languages = Languages.get(activity);
            final ArrayAdapter<String> languagesAdapter = new ArrayAdapter<>(activity,
                    android.R.layout.simple_list_item_single_choice, languages.getAllNames());
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setIcon(R.drawable.ic_settings_language);
            builder.setTitle(R.string.KEY_PREF_LANGUAGE_TITLE);
            builder.setAdapter(languagesAdapter, (dialog, position) -> {
                String[] languageCodes = languages.getSupportedLocales();
                ImApp.resetLanguage(activity, languageCodes[position]);
                dialog.dismiss();
            });
            builder.show();
        });

        /*
        mEditUsername.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_NULL || actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    Handler threadHandler = new Handler();
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0, new ResultReceiver(
                            threadHandler) {
                        @Override
                        protected void onReceiveResult(int resultCode, Bundle resultData) {
                            super.onReceiveResult(resultCode, resultData);

                            mNickname = mEditUsername.getText().toString();

                            if (mNickname.length() > 0) {
                                startAccountSetup();
                            }


                        }
                    });
                    return true;
                }

                return false;
            }
        });*/

        View btnCreateAdvanced = viewAdvanced.findViewById(R.id.btnNewRegister);
        btnCreateAdvanced.setOnClickListener(v -> {
            View viewEdit = findViewById(R.id.edtNameAdvanced);
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);

            if (imm != null) {
                imm.hideSoftInputFromWindow(viewEdit.getWindowToken(), 0);
            }

            startAdvancedSetup();
        });

        /*
        View btnInviteSms = viewInvite.findViewById(R.id.btnInviteSMS);
        btnInviteSms.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                doInviteSMS();

            }

        });

        View btnInviteShare = viewInvite.findViewById(R.id.btnInviteShare);
        btnInviteShare.setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View v) {
               
                doInviteShare();
                
            }
            
        });

        View btnInviteQR = viewInvite.findViewById(R.id.btnInviteScan);
        btnInviteQR.setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View v) {
               
                doInviteScan();
                
            }
            
        });*/


        View btnSignIn = viewLogin.findViewById(R.id.btnSignIn);
        btnSignIn.setOnClickListener(v -> doExistingAccountRegister());

        if (!mShowSplash)
        {
            setAnimLeft();
            showOnboarding();
        }

        if (mAccountId != -1 && mProviderId != -1)
            showLoginScreen();
    }

    private void setAnimLeft ()
    {
        Animation animIn = AnimationUtils.loadAnimation(this, R.anim.push_left_in);
        Animation animOut = AnimationUtils.loadAnimation(this, R.anim.push_left_out);
        mViewFlipper.setInAnimation(animIn);
        mViewFlipper.setOutAnimation(animOut);
    }
    
    private void setAnimRight ()
    {
        Animation animIn = AnimationUtils.loadAnimation(OnboardingActivity.this, R.anim.push_right_in);
        Animation animOut = AnimationUtils.loadAnimation(OnboardingActivity.this, R.anim.push_right_out);
        mViewFlipper.setInAnimation(animIn);
        mViewFlipper.setOutAnimation(animOut);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.menu_onboarding, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == android.R.id.home) {
            showPrevious();

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {

        if (mDomainList != null && mDomainList.isShowing())
            mDomainList.dismiss();
        else
            showPrevious();
    }


    // Back button should bring us to the previous screen, unless we're on the first screen
    private void showPrevious()
    {
        setAnimRight();

        ActionBar ab = getSupportActionBar();
        if (ab != null) ab.setTitle("");

        if (mCurrentFindServerTask != null) {
            mCurrentFindServerTask.cancel(true);
        }

        if (mViewFlipper.getCurrentView().getId() == R.id.flipViewMain)
        {
            finish();
        }
        else if (mViewFlipper.getCurrentView().getId() == R.id.flipViewRegister)
        {
            if (mShowSplash) {
                showSplashScreen();
            }
            else {
                finish();
            }
        }
        /*
        else if (mViewFlipper.getCurrentView().getId() == R.id.flipViewCreateNew)
        {
            showOnboarding();
        }*/
        else if (mViewFlipper.getCurrentView().getId() == R.id.flipViewLogin)
        {
            showOnboarding();
        }
        else if (mViewFlipper.getCurrentView().getId() == R.id.flipViewAdvanced)
        {
            showOnboarding();
        }
    }

    private void showSplashScreen ()
    {
        setAnimRight();

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.hide();
            ab.setTitle("");
        }

        mViewFlipper.setDisplayedChild(0);
    }

    private void showOnboarding ()
    {

        mViewFlipper.setDisplayedChild(1);

    }


    private void showSetupScreen ()
    {
        mViewFlipper.setDisplayedChild(3);

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.show();
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setHomeButtonEnabled(true);
        }
    }

    private void showLoginScreen ()
    {
        mViewFlipper.setDisplayedChild(2);
        findViewById(R.id.progressExistingUser).setVisibility(View.GONE);
        findViewById(R.id.progressExistingImage).setVisibility(View.GONE);

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.show();
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setHomeButtonEnabled(true);
        }
    }

    private void startAdvancedSetup() {
        String nickname = ((EditText) findViewById(R.id.edtNameAdvanced)).getText().toString();
        String username = nickname.replaceAll(USERNAME_ONLY_ALPHANUM, "").toLowerCase();

        if (TextUtils.isEmpty(username)) {
            //if there are no alphanum then just use a series of numbers with the app name
            username = getString(R.string.app_name) + "=" + (int) (Math.random() * 1000000f);
        }

        String domain = ((EditText) findViewById(R.id.spinnerDomains)).getText().toString();

        String password = ((EditText) findViewById(R.id.edtNewPass)).getText().toString();
        String passwordConfirm = ((EditText) findViewById(R.id.edtNewPassConfirm)).getText().toString();

        if (!TextUtils.isEmpty(password)) {
            if (!TextUtils.isEmpty(passwordConfirm)) {
                if (password.equals(passwordConfirm)) {
                    mViewFlipper.setDisplayedChild(4);

                    if (mCurrentFindServerTask != null)
                        mCurrentFindServerTask.cancel(true);

                    mCurrentFindServerTask = new FindServerTask();
                    mCurrentFindServerTask.execute(nickname, username, domain, password);
                }
                else {
                    Toast.makeText(this, R.string.lock_screen_passphrases_not_matching, Toast.LENGTH_LONG).show();

                    findViewById(R.id.edtNewPassConfirm).setBackgroundColor(getResources().getColor(R.color.holo_red_dark));
                }
            }
            else {
                findViewById(R.id.edtNewPassConfirm).setBackgroundColor(getResources().getColor(R.color.holo_red_dark));
                Toast.makeText(this, "The confirm password field cannot be blank.", Toast.LENGTH_LONG).show();
            }
        }
        else {
            findViewById(R.id.edtNewPass).setBackgroundColor(getResources().getColor(R.color.holo_red_dark));
            Toast.makeText(this, "The password field cannot be blank.", Toast.LENGTH_LONG).show();
        }
    }

    private class FindServerTask extends AsyncTask<String, Void, OnboardingAccount> {
        @Override
        protected OnboardingAccount doInBackground(String... setupValues) {
            try {
                Server[] servers = Server.getServers(OnboardingActivity.this);

                Server myServer = new Server();
                String password = null;

                if (setupValues.length > 2)
                    myServer.domain = setupValues[2]; //User can specify the domain they want to be on for a new account.

                if (setupValues.length > 3)
                    password = setupValues[3];

                if (setupValues.length > 4)
                    myServer.server = setupValues[4];

                if (myServer.domain == null && servers != null)
                {
                    myServer = servers[0];
                }

                String nickname = setupValues[0];
                String username = setupValues[1];

                if (mManager == null) mManager = new OnboardingManager(OnboardingActivity.this, OnboardingActivity.this);

                mManager.registerAccount(nickname, username, password, myServer.domain, myServer.domain, myServer.port);

                return null;
            }
            catch (Exception e) {
                Log.e(LOG_TAG, "auto onboarding fail", e);
                return null;
            }
        }

        @Override
        protected void onCancelled(OnboardingAccount onboardingAccount) {
            super.onCancelled(onboardingAccount);

            startAdvancedSetup ();
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();

            startAdvancedSetup ();
        }

        @Override
        protected void onPostExecute(OnboardingAccount account) {
        }
    }

      /*
    private void showInviteScreen ()
    {
        mViewFlipper.setDisplayedChild(5);

        getSupportActionBar().setTitle("");
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        getSupportActionBar().setHomeButtonEnabled(false);
        TextView tv = (TextView)findViewById(R.id.statusInviteFriends);
        tv.setText(R.string.invite_friends);

        mItemSkip.setVisible(true);
        mItemSkip.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {

                showMainScreen();
                mItemSkip.setVisible(false);
                return false;
            }
        });
    }

    private void doInviteSMS()
    {
        String inviteString = OnboardingManager.generateInviteMessage(this, mNickname,mUsername, mFingerprint);
        OnboardingManager.inviteSMSContact(this, null, inviteString);
    }

    private void doInviteShare()
    {

        String inviteString = OnboardingManager.generateInviteMessage(this, mNickname,mUsername, mFingerprint);
        OnboardingManager.inviteShare(this, inviteString);
    }
 
    private void doInviteScan ()
    {
        String inviteString;
        try {
            inviteString = OnboardingManager.generateInviteLink(this, mUsername, mFingerprint, mNickname);
            OnboardingManager.inviteScan(this, inviteString);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
    }*/

    private void showMainScreen(boolean isNewAccount)
    {
        finish();

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("firstTime", isNewAccount);
        startActivity(intent);
    }

    private synchronized void doExistingAccountRegister() {
        String username = ((TextView) findViewById(R.id.edtName)).getText().toString();
        String password = ((TextView) findViewById(R.id.edtPass)).getText().toString();
        String server = ((TextView) findViewById(R.id.edtServer)).getText().toString();

        if (!mLoggingIn) {
            mLoggingIn = true;

            findViewById(R.id.progressExistingUser).setVisibility(View.VISIBLE);
            findViewById(R.id.progressExistingImage).setVisibility(View.VISIBLE);

            hideKeyboard();

            if (mManager == null) mManager = new OnboardingManager(this, this);

            mManager.addExistingAccount(username, server, password, mAccountId, mProviderId);
        }
    }

    private void hideKeyboard() {
        // Check if no view has focus:
        View view = this.getCurrentFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        if (view != null && imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void showErrorMessage(String message) {
        Snackbar.make(mViewFlipper, message, Snackbar.LENGTH_LONG).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);


        ImApp mApp = (ImApp)getApplication();
        mApp.initAccountInfo();

        if (resultCode == RESULT_OK) {
            if (requestCode == OnboardingManager.REQUEST_SCAN) {

                ArrayList<String> resultScans = data.getStringArrayListExtra("result");

                if (resultScans != null && resultScans.size() > 0)
                {
                    for (String resultScan : resultScans)
                    {
                        try {
                            //parse each string and if they are for a new user then add the user
                            OnboardingManager.DecodedInviteLink diLink = OnboardingManager.decodeInviteLink(resultScan);

                            new AddContactAsyncTask(mNewAccount.providerId, mNewAccount.accountId).execute(diLink.username, diLink.fingerprint, diLink.nickname);

                            //if they are for a group chat, then add the group.
                        }
                        catch (Exception e) {
                            Log.w(LOG_TAG, "error parsing QR invite link", e);
                        }
                    }

                    showMainScreen (false);
                }
            }
            else if (requestCode == OnboardingManager.REQUEST_CHOOSE_AVATAR)
            {
                Uri imageUri = getPickImageResultUri(data);

                if (imageUri == null)
                    return;

                mCropImageView = new CropImageView(OnboardingActivity.this);// (CropImageView)view.findViewById(R.id.CropImageView);
             //   mCropImageView.setAspectRatio(1, 1);
             //   mCropImageView.setFixedAspectRatio(true);
             //   mCropImageView.setCropShape(CropImageView.CropShape.OVAL);
              //  mCropImageView.setGuidelines(1);

                try {
                    Bitmap bmpThumbnail = SecureMediaStore.getThumbnailFile(OnboardingActivity.this, imageUri, 512);
                    mCropImageView.setImageBitmap(bmpThumbnail);

                    // Use the Builder class for convenient dialog construction
                    androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(OnboardingActivity.this);
                    builder.setView(mCropImageView)
                            .setPositiveButton(R.string.ok, (dialog, id) -> {
                                setAvatar(mCropImageView.getCroppedImage(), mNewAccount);
                                showMainScreen(false);

                                delete(mOutputFileUri);
                            })
                            .setNegativeButton(R.string.cancel, (dialog, id) -> {
                                // User cancelled the dialog.
                                delete(mOutputFileUri);
                            });

                    // Create the AlertDialog object and return it
                    androidx.appcompat.app.AlertDialog dialog = builder.create();
                    dialog.show();
                }
                catch (IOException ioe) {
                    Log.e(LOG_TAG, "couldn't load avatar", ioe);
                }
            }
            else if (requestCode == CaptchaActivity.REQUEST_CODE)
            {
                if (mManager != null) mManager.continueRegister(data.getStringExtra(CaptchaActivity.EXTRA_RESPONSE), false);
            }
            else if (requestCode == TermsActivity.REQUEST_CODE)
            {
                if (mManager != null) mManager.continueRegister(null, true);
            }
        }
    }

    private void setAvatar(Bitmap bmp, OnboardingAccount account) {

        RoundedAvatarDrawable avatar = new RoundedAvatarDrawable(bmp);
        mImageAvatar.setImageDrawable(avatar);

        try {

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, stream);

            byte[] avatarBytesCompressed = stream.toByteArray();
            String avatarHash = "nohash";

            DatabaseUtils.insertAvatarBlob(getContentResolver(), Imps.Avatars.CONTENT_URI, account.providerId, account.accountId, avatarBytesCompressed, avatarHash, account.username + '@' + account.domain);
        } catch (Exception e) {
            Log.w(LOG_TAG, "error loading image bytes", e);
        }
    }

    CropImageView mCropImageView;

    /**
     * Create a chooser intent to select the source to get image from.<br/>
     * The source can be camera's (ACTION_IMAGE_CAPTURE) or gallery's (ACTION_GET_CONTENT).<br/>
     * All possible sources are added to the intent chooser.
     */
    public Intent getPickImageChooserIntent() {

        // Determine Uri of camera image to save.
        Uri outputFileUri = getCaptureImageOutputUri();

        List<Intent> allIntents = new ArrayList<>();
        PackageManager packageManager = getPackageManager();

        // collect all camera intents
        Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        List<ResolveInfo> listCam = packageManager.queryIntentActivities(captureIntent, 0);
        for (ResolveInfo res : listCam) {
            Intent intent = new Intent(captureIntent);
            intent.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
            intent.setPackage(res.activityInfo.packageName);
            if (outputFileUri != null) {
                intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
            }
            allIntents.add(intent);
        }

        // collect all gallery intents
        Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
        galleryIntent.setType("image/*");
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
            ComponentName componentName = intent.getComponent();

            if (componentName != null && componentName.getClassName().equals("com.android.documentsui.DocumentsActivity")) {
                mainIntent = intent;
                break;
            }
        }
        allIntents.remove(mainIntent);

        // Create a chooser from the main intent
        Intent chooserIntent = Intent.createChooser(mainIntent, getString(R.string.choose_photos));

        // Add all other intents
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, allIntents.toArray(new Parcelable[0]));

        return chooserIntent;
    }

    Uri mOutputFileUri = null;

    /**
     * Get URI to image received from capture by camera.
     */
    private synchronized Uri getCaptureImageOutputUri() {

        if (mOutputFileUri == null) {
            File photo = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "kavatar.jpg");
            mOutputFileUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".provider",
                    photo);

        }

        return mOutputFileUri;
    }


    private void delete(Uri uri) {
        String scheme = uri.getScheme();

        if (scheme != null) {
            if (scheme.equals("content")) {
                getContentResolver().delete(uri, null, null);
                return;
            }

            if (scheme.equals("file")) {
                File file = new File(uri.toString().substring(5));

                if (file.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        }
    }


    /**
     * Get the URI of the selected image from {@link #getPickImageChooserIntent()}.<br/>
     * Will return the correct URI for camera and gallery image.
     *
     * @param data the returned data of the activity result
     */
    public Uri getPickImageResultUri(Intent data) {
        if (data != null && data.getData() != null) {
            String action = data.getAction();
            boolean isCamera = action != null && action.equals(MediaStore.ACTION_IMAGE_CAPTURE);
            return isCamera ? getCaptureImageOutputUri() : data.getData();
        }

        return getCaptureImageOutputUri();
    }

    private final static int MY_PERMISSIONS_REQUEST_CAMERA = 1;

    void startAvatarTaker() {
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
                Snackbar.make(mViewFlipper, R.string.grant_perms, Snackbar.LENGTH_LONG).show();
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
            startActivityForResult(getPickImageChooserIntent(), OnboardingManager.REQUEST_CHOOSE_AVATAR);
        }
    }

    @Override
    public void registrationSuccessful(final OnboardingAccount account) {
        mNewAccount = account;

        ImApp app = (ImApp) getApplication();
        app.setDefaultAccount(account.providerId, account.accountId, account.username, account.nickname);

        SignInHelper signInHelper = new SignInHelper(this, mHandler);
        signInHelper.activateAccount(account.providerId, account.accountId);
        signInHelper.signIn(account.password, account.providerId, account.accountId, true);

        app.setDefaultAccount(account.providerId, account.accountId, account.username, account.nickname);

        showMainScreen(!mLoggingIn);

        mLoggingIn = false;
    }

    @Override
    public void registrationFailed(final String err) {
        if (mLoggingIn) {
            showErrorMessage(getString(R.string.invalid_password));

            findViewById(R.id.progressExistingUser).setVisibility(View.GONE);
            findViewById(R.id.progressExistingImage).setVisibility(View.GONE);

            mLoggingIn = false;
        }
        else {
            mOnboardingHandler.post(() -> {
                showSetupScreen();

                String text = getString(R.string.account_setup_error_server) + ": " + err;
                ((TextView) findViewById(R.id.statusError)).setText(text);
            });
        }
    }
}
