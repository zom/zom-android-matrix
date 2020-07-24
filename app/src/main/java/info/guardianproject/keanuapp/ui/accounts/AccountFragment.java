package info.guardianproject.keanuapp.ui.accounts;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.RemoteException;
import android.provider.MediaStore;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import info.guardianproject.keanu.core.model.ImConnection;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.service.IImConnection;
import info.guardianproject.keanu.core.service.RemoteImService;
import info.guardianproject.keanu.core.util.DatabaseUtils;
import info.guardianproject.keanu.core.util.SecureMediaStore;
import info.guardianproject.keanuapp.ImApp;
import info.guardianproject.keanuapp.R;
import info.guardianproject.keanuapp.tasks.SignInHelper;
import info.guardianproject.keanuapp.ui.contacts.DeviceDisplayActivity;

import static android.content.Context.CLIPBOARD_SERVICE;
import static info.guardianproject.keanu.core.KeanuConstants.DEFAULT_AVATAR_HEIGHT;
import static info.guardianproject.keanu.core.KeanuConstants.DEFAULT_AVATAR_WIDTH;
import static info.guardianproject.keanu.core.KeanuConstants.IMPS_CATEGORY;
import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;


public class AccountFragment extends Fragment {

    ImageView mIvAvatar;
    CropImageView mCropImageView;
    TextView mTvPassword, mTvNickname, mTvUsername;
    Handler mHandler = new Handler();
    ImageView ivScan;
    View mView;

    long mProviderId;
    long mAccountId;
    String mUserAddress;
    String mNickname;

    IImConnection mConn;


    private final static String DEFAULT_PASSWORD_TEXT = "*************";

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment GalleryFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static AccountFragment newInstance(String param1, String param2) {
        AccountFragment fragment = new AccountFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    public AccountFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        mView = inflater.inflate(R.layout.awesome_fragment_account, container, false);

        mTvNickname = (TextView) mView.findViewById(R.id.tvNickname);

        mTvUsername = (TextView) mView.findViewById(R.id.edtName);
        mTvUsername.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText(getActivity().getString(R.string.app_name), mUserAddress);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getActivity(), R.string.action_copied, Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        mTvPassword = (TextView) mView.findViewById(R.id.edtPass);
        mTvPassword.setText(DEFAULT_PASSWORD_TEXT);

        ImageView btnShowPassword = mView.findViewById(R.id.btnShowPass);
        btnShowPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mTvPassword.getText().toString().equals(DEFAULT_PASSWORD_TEXT)) {
                    mTvPassword.setText(getAccountPassword(mProviderId));
                    btnShowPassword.setImageResource(R.drawable.eye_slash_icon);
                }
                else {
                    mTvPassword.setText(DEFAULT_PASSWORD_TEXT);
                    btnShowPassword.setImageResource(R.drawable.ic_visibility_black_24dp);
                }
            }
        });

        View btnEditAccountNickname = mView.findViewById(R.id.edit_account_nickname);
        btnEditAccountNickname.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showChangeNickname();
            }
        });

        View btnEditAccountPassword = mView.findViewById(R.id.edit_account_password);
        btnEditAccountPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showChangePassword();
            }
        });


        mIvAvatar = (ImageView) mView.findViewById(R.id.imageAvatar);
        mIvAvatar.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {

                startAvatarTaker();

            }
        });


        mView.findViewById(R.id.btnViewDevices).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewDevicesClicked();
            }
        });

        updateInfo();

        return mView;
    }

    private void updateInfo ()
    {
        ImApp mApp = ((ImApp) getActivity().getApplication());

        if (mApp.getDefaultUsername() == null)
            return;

        mProviderId = mApp.getDefaultProviderId();
        mAccountId = mApp.getDefaultAccountId();
        mUserAddress = mApp.getDefaultUsername().trim();
        mNickname = Imps.Account.getNickname(getContext().getContentResolver(), mAccountId);

        mConn = RemoteImService.getConnection(mProviderId, mAccountId);

        if (mTvUsername != null) {
            mTvUsername.setText(mUserAddress);
            mTvNickname.setText(mNickname);

            try {

                Drawable avatar = DatabaseUtils.getAvatarFromAddress(mUserAddress, DEFAULT_AVATAR_WIDTH, DEFAULT_AVATAR_HEIGHT, false);

                if (avatar != null)
                    mIvAvatar.setImageDrawable(avatar);
            } catch (Exception e) {
                Log.w(LOG_TAG, "error getting avatar", e);
            }
        }
    }


    private void showChangeNickname ()
    {
        AlertDialog.Builder alert = new AlertDialog.Builder(getContext());

        // Set an EditText view to get user input
        final EditText input = new EditText(getContext());
        input.setText(mNickname);
        alert.setView(input);

        alert.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String newNickname = input.getText().toString();

                //update just the nickname
                ImApp.insertOrUpdateAccount(getContext().getContentResolver(), mProviderId, mAccountId, newNickname, null, null);

                if (mConn != null) {
                    try {
                        mConn.changeNickname(newNickname);
                    } catch (RemoteException e) {
                    }
                }

                mTvNickname.setText(newNickname);
                // Do something with value!
            }
        });

        alert.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Canceled.
            }
        });

        alert.show();
    }

    private void showChangePassword ()
    {
        AlertDialog.Builder alert = new AlertDialog.Builder(getContext());
        alert.setTitle(R.string.lock_screen_create_passphrase);

        // Set an EditText view to get user input
        final EditText input = new EditText(getContext());
      //  input.setText(getAccountPassword(mProviderId));
        alert.setView(input);

        alert.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String newPassword = input.getText().toString();

                if (!TextUtils.isEmpty(newPassword)) {

                    new ChangePasswordTask().execute(getAccountPassword(mProviderId),newPassword);
                }
            }
        });

        alert.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Canceled.
            }
        });

        alert.show();
    }

    private boolean checkConnection() {
        try {
            IImConnection conn = RemoteImService.getConnection(mProviderId, mAccountId);

            if (conn.getState() == ImConnection.DISCONNECTED)
                return false;

            return true;
        } catch (Exception e) {
            return false;
        }

    }

    private String getAccountPassword(long providerId) {

        String result = "";

        Cursor c = getActivity().getContentResolver().query(Imps.Provider.CONTENT_URI_WITH_ACCOUNT,
                new String[]{Imps.Provider.ACTIVE_ACCOUNT_PW}, Imps.Provider.CATEGORY + "=? AND providers." + Imps.Provider._ID + "=?" /* selection */,
                new String[]{IMPS_CATEGORY, providerId + ""} /* selection args */,
                Imps.Provider.DEFAULT_SORT_ORDER);

        if (c != null) {
            c.moveToFirst();
            result = c.getString(0);
            c.close();
        }

        return result;

    }

    private String updatePassword(long providerId) {

        String result = "";

        Cursor c = getActivity().getContentResolver().query(Imps.Provider.CONTENT_URI_WITH_ACCOUNT,
                new String[]{Imps.Provider.ACTIVE_ACCOUNT_PW}, Imps.Provider.CATEGORY + "=? AND providers." + Imps.Provider._ID + "=?" /* selection */,
                new String[]{IMPS_CATEGORY, providerId + ""} /* selection args */,
                Imps.Provider.DEFAULT_SORT_ORDER);

        if (c != null) {
            c.moveToFirst();
            result = c.getString(0);
            c.close();
        }

        return result;

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && requestCode == 200) {

            Uri imageUri = getPickImageResultUri(data);

            if (imageUri == null)
                return;

            mCropImageView = new CropImageView(getActivity());// (CropImageView)view.findViewById(R.id.CropImageView);
   //         mCropImageView.setAspectRatio(1, 1);
    //        mCropImageView.setFixedAspectRatio(true);
    //        mCropImageView.setCropShape(CropImageView.CropShape.OVAL);

            //  mCropImageView.setGuidelines(1);

            try {
                Bitmap bmpThumbnail = SecureMediaStore.getThumbnailFile(getActivity(), imageUri, 512);
                mCropImageView.setImageBitmap(bmpThumbnail);

                // Use the Builder class for convenient dialog construction
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setView(mCropImageView)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                setAvatar(mCropImageView.getCroppedImage());
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // User cancelled the dialog
                            }
                        });
                // Create the AlertDialog object and return it
                AlertDialog dialog = builder.create();
                dialog.show();
                ;
            } catch (IOException ioe) {
                Log.e(LOG_TAG, "couldn't load avatar", ioe);
            }

        }


    }

    private void setAvatar(Bitmap bmp) {

        BitmapDrawable avatar = new BitmapDrawable(bmp);
        mIvAvatar.setImageDrawable(avatar);

        final ImApp app = ((ImApp) getActivity().getApplication());

        try {

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, stream);

            byte[] avatarBytesCompressed = stream.toByteArray();
            String avatarHash = "nohash";
            int rowsUpdated = DatabaseUtils.updateAvatarBlob(getActivity().getContentResolver(), Imps.Avatars.CONTENT_URI, avatarBytesCompressed, mUserAddress);

            if (rowsUpdated <= 0)
                DatabaseUtils.insertAvatarBlob(getActivity().getContentResolver(), Imps.Avatars.CONTENT_URI, mProviderId, mAccountId, avatarBytesCompressed, avatarHash, mUserAddress);
            if (mConn != null) {
                try {
                    //this will also trigger an update of the avatar
                    mConn.changeNickname(mTvNickname.getText().toString());
                } catch (RemoteException e) {
                }
            }

        } catch (Exception e) {
            Log.w(LOG_TAG, "error loading image bytes", e);
        }
    }

    /**
     * Create a chooser intent to select the source to get image from.<br/>
     * The source can be camera's (ACTION_IMAGE_CAPTURE) or gallery's (ACTION_GET_CONTENT).<br/>
     * All possible sources are added to the intent chooser.
     */
    public Intent getPickImageChooserIntent() {

        // Determine Uri of camera image to save.
        Uri outputFileUri = getCaptureImageOutputUri();

        List<Intent> allIntents = new ArrayList<>();
        PackageManager packageManager = getActivity().getPackageManager();

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

    /**
     * Get URI to image received from capture by camera.
     */
    private Uri getCaptureImageOutputUri() {
        Uri outputFileUri = null;
        File getImage = getActivity().getExternalCacheDir();
        if (getImage != null) {
            getImage = (new File(getImage.getPath(), "pickImageResult.jpg"));
            outputFileUri = FileProvider.getUriForFile(getActivity(),
                    getContext().getPackageName() + ".provider",
                    getImage);
        }
        return outputFileUri;
    }


    /**
     * Get the URI of the selected image from {@link #getPickImageChooserIntent()}.<br/>
     * Will return the correct URI for camera and gallery image.
     *
     * @param data the returned data of the activity result
     */
    public Uri getPickImageResultUri(Intent data) {
        boolean isCamera = true;
        if (data != null) {

            if (data.getData() == null)
                return getCaptureImageOutputUri();
            else {
                String action = data.getAction();
                isCamera = action != null && action.equals(MediaStore.ACTION_IMAGE_CAPTURE);
                return isCamera ? getCaptureImageOutputUri() : data.getData();
            }

        }
        else
            return getCaptureImageOutputUri();
    }

    @Override
    public void setUserVisibleHint(boolean visible)
    {
        super.setUserVisibleHint(visible);
        if (visible && isResumed())
        {
            //Only manually call onResume if fragment is already visible
            //Otherwise allow natural fragment lifecycle to call onResume
            updateInfo();

        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!getUserVisibleHint())
        {
            return;
        }

        updateInfo ();
    }


    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    private String prettyPrintFingerprint(String fingerprint) {
        StringBuffer spacedFingerprint = new StringBuffer();

        int blockLength = 4;

        for (int i = 0; i + blockLength <= fingerprint.length(); i += blockLength) {
            spacedFingerprint.append(fingerprint.subSequence(i, i + blockLength));
            spacedFingerprint.append(' ');
        }

        return spacedFingerprint.toString();
    }

    void signIn () {
        // The toggle is enabled
        SignInHelper helper = new SignInHelper(getActivity(), mHandler, new SignInHelper.SignInListener() {
            @Override
            public void connectedToService() {


            }

            @Override
            public void stateChanged(int state, long accountId) {



            }
        });

        helper.signIn(getAccountPassword(mProviderId), mProviderId, mAccountId,true);

        //keep signed in please!
        ContentValues values = new ContentValues();
        values.put(Imps.AccountColumns.KEEP_SIGNED_IN, 1);
        getActivity().getContentResolver().update(ContentUris.withAppendedId(Imps.Account.CONTENT_URI, mAccountId), values, null, null);
    }

    void signOut() {
        //if you are signing out, then we will deactive "auto" sign in
        ContentValues values = new ContentValues();
        values.put(Imps.AccountColumns.KEEP_SIGNED_IN, 0);
        getActivity().getContentResolver().update(ContentUris.withAppendedId(Imps.Account.CONTENT_URI, mAccountId), values, null, null);
        signOut(mProviderId, mAccountId);
        ;
    }

    void signOut(long providerId, long accountId) {

        try {

            IImConnection conn = RemoteImService.getConnection(mProviderId, mAccountId);
            if (conn != null) {
                conn.logout(false);
            } else {
                // Normally, we can always get the connection when user chose to
                // sign out. However, if the application crash unexpectedly, the
                // status will never be updated. Clear the status in this case
                // to make it recoverable from the crash.
                ContentValues values = new ContentValues(2);
                values.put(Imps.AccountStatusColumns.PRESENCE_STATUS, Imps.CommonPresenceColumns.OFFLINE);
                values.put(Imps.AccountStatusColumns.CONNECTION_STATUS, Imps.ConnectionStatus.OFFLINE);
                String where = Imps.AccountStatusColumns.ACCOUNT + "=?";
                getActivity().getContentResolver().update(Imps.AccountStatus.CONTENT_URI, values, where,
                        new String[]{Long.toString(accountId)});
            }
        } catch (RemoteException ex) {
            Log.e(LOG_TAG, "signout: caught ", ex);
        } finally {

        }

    }

    private final static int MY_PERMISSIONS_REQUEST_CAMERA = 1;

    void startAvatarTaker() {
        int permissionCheck = ContextCompat.checkSelfPermission(getActivity(),
                Manifest.permission.CAMERA);

        if (permissionCheck ==PackageManager.PERMISSION_DENIED)
        {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(),
                    Manifest.permission.CAMERA)) {

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                Snackbar.make(mView, R.string.grant_perms, Snackbar.LENGTH_LONG).show();
            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(getActivity(),
                        new String[]{Manifest.permission.CAMERA},
                        MY_PERMISSIONS_REQUEST_CAMERA);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }
        else {

            startActivityForResult(getPickImageChooserIntent(), 200);
        }
    }

    private class ChangePasswordTask extends AsyncTask<String, Void, Boolean> {

        String newPassword = null;

        @Override
        protected Boolean doInBackground(String... setupValues) {
            try {

                String oldPassword = setupValues[0];
                newPassword = setupValues[1];

                /**
                if (!oldPassword.equals(newPassword)) {
                    boolean result = OnboardingManager.changePassword(getActivity(), mProviderId, mAccountId, oldPassword, newPassword);
                    return result;
                }
                else
                    return false;
                 **/
                return true;//just change locally
            }
            catch (Exception e)
            {
                Log.e(LOG_TAG, "auto onboarding fail", e);
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean passwordChanged) {

            if (passwordChanged) {
                //update just the nickname
                ImApp.insertOrUpdateAccount(getContext().getContentResolver(), mProviderId, mAccountId, "", "", newPassword);
                mTvPassword.setText(DEFAULT_PASSWORD_TEXT);
            }

        }
    }

    public void viewDevicesClicked ()
    {
        Intent intent = new Intent(getActivity(),DeviceDisplayActivity.class);
        intent.putExtra("nickname",mNickname);
        intent.putExtra("address",mUserAddress);
        intent.putExtra("provider",mProviderId);
        intent.putExtra("account",mAccountId);

        startActivity(intent);

    }


}
