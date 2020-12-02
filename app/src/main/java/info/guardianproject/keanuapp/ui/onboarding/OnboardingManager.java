package info.guardianproject.keanuapp.ui.onboarding;


import info.guardianproject.keanu.matrix.plugin.MatrixConnection;
import info.guardianproject.keanuapp.R;
import info.guardianproject.keanu.core.provider.Imps;
import org.json.JSONException;
import org.matrix.androidsdk.core.model.MatrixError;

import info.guardianproject.keanu.core.util.ImPluginHelper;
import info.guardianproject.keanu.core.util.LogCleaner;
import info.guardianproject.keanuapp.ImApp;
import info.guardianproject.keanuapp.nearby.NearbyAddContactActivity;
import info.guardianproject.keanuapp.ui.qr.QrScanActivity;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.UUID;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.Telephony;
import android.text.TextUtils;
import android.util.Log;

import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.rest.model.login.LocalizedFlowDataLoginTerms;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.util.ImPluginHelper;
import info.guardianproject.keanu.core.util.LogCleaner;
import info.guardianproject.keanu.matrix.plugin.MatrixConnection;
import info.guardianproject.keanu.matrix.plugin.RegistrationManager;
import info.guardianproject.keanuapp.ImApp;
import info.guardianproject.keanuapp.R;
import info.guardianproject.keanuapp.nearby.NearbyAddContactActivity;
import info.guardianproject.keanuapp.ui.qr.QrScanActivity;

import static info.guardianproject.keanu.core.KeanuConstants.DEFAULT_DEVICE_NAME;
import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;

public class OnboardingManager implements RegistrationManager.RegistrationListener {

    public final static int REQUEST_SCAN = 1111;
    public final static int REQUEST_CHOOSE_AVATAR = REQUEST_SCAN + 1;

    public static String BASE_INVITE_URL = "https://zom.im/i/#";

    public final static String DEFAULT_SCHEME = "matrix";

    private final WeakReference<Activity> mActivity;
    private final WeakReference<OnboardingListener> mListener;
    private final MatrixConnection mConn;
    private OnboardingAccount mAccountRegistering;

    public OnboardingManager(Activity activity, OnboardingListener listener) {
        mActivity = new WeakReference<>(activity);
        mListener = new WeakReference<>(listener);
        mConn = new MatrixConnection(activity);

    }

    public static void inviteSMSContact(Activity context, String message) {
        String defaultSmsPackageName = Telephony.Sms.getDefaultSmsPackage(context); // Need to change the build to API 19

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_TEXT, message);

        if (defaultSmsPackageName != null)// Can be null in case that there is no default, then the user would be able to choose
        // any app that support this intent.
        {
            sendIntent.setPackage(defaultSmsPackageName);
        }

        context.startActivity(sendIntent);
    }

    public static void inviteShare(Activity context, String message) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, message);
        intent.setType("text/plain");
        context.startActivity(intent);
    }

    public static void inviteNearby(Activity context, String message) {
        Intent intent = new Intent(context, NearbyAddContactActivity.class);
        intent.putExtra(Intent.EXTRA_TEXT, message);
        intent.setType("text/plain");
        context.startActivity(intent);
    }

    public static void inviteShareToPackage(Activity context, String message, String packageName) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, message);
        intent.setType("text/plain");
        intent.setPackage(packageName);
        context.startActivity(intent);
    }

    public static void inviteScan(Activity context, String message) {
        Intent intent = new Intent(context, QrScanActivity.class);
        intent.putExtra(Intent.EXTRA_TEXT, message);
        intent.setType("text/plain");
        context.startActivityForResult(intent, REQUEST_SCAN);

    }

    public static String generateInviteMessage(Context context, String nickname, String username) {
        try {
            return nickname + ' ' + context.getString(R.string.is_inviting_you) + " "
                    + generateInviteLink(username);
        } catch (Exception e) {
            Log.d(LOG_TAG, "error with link", e);
            return null;
        }
    }


    public static DecodedInviteLink decodeInviteLink (String link) throws UnsupportedEncodingException {

        DecodedInviteLink diLink = null;

        if (link.contains("/i/#")) {

            //this is an invite link

            //this is an invite link like this: https://zom.im/i/#@earthmouse:matrix.org

            if (link.contains("@")) {
                try {
                    String matrixContact = URLDecoder.decode(link.substring(link.lastIndexOf("@")),"UTF-8");

                    diLink = new DecodedInviteLink();
                    diLink.username = matrixContact;

                } catch (IllegalArgumentException iae) {
                    Log.e(LOG_TAG, "bad link decode", iae);
                }
            }
            else if (link.indexOf("!")!=-1) {

                try {
                    String matrixContact = URLDecoder.decode(link.substring(link.lastIndexOf("!")),"UTF-8");

                    diLink = new DecodedInviteLink();
                    diLink.username = matrixContact;

                } catch (IllegalArgumentException iae) {
                    Log.e(LOG_TAG, "bad link decode", iae);
                }
            }
            else if (link.indexOf("#")!=-1) {
                try {
                    String matrixContact = URLDecoder.decode(link.substring(link.lastIndexOf("#")),"UTF-8");

                    if (matrixContact.startsWith("##"))
                        matrixContact = matrixContact.substring(1);

                    diLink = new DecodedInviteLink();
                    diLink.username = matrixContact;

                } catch (IllegalArgumentException iae) {
                    Log.e(LOG_TAG, "bad link decode", iae);
                }
            }


            /*
            try {
                String out = new String(Base64.decode(code[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING));

                String[] partsTemp = out.split("\\?");

                if (partsTemp == null)
                {
                    partsTemp = new String[1];
                    partsTemp[0] = out;
                    diLink = new DecodedInviteLink();
                    diLink.username = out;
                }
                else {

                    diLink = new DecodedInviteLink();
                    diLink.username = partsTemp[0];

                    if (partsTemp.length > 1)
                    {
                        String[] parts = partsTemp[1].split("&");


                        for (String part : parts) {

                            String[] keyValue = part.split("=");

                            if (keyValue[0].equals("otr"))
                                diLink.fingerprint = keyValue[1];
                            else if (keyValue[0].equals("m"))
                                diLink.isMigration = true;
                            else if (keyValue[0].equals("nickname"))
                                diLink.nickname = keyValue[1];

;
                        }



                    }

                }

            }
            catch (IllegalArgumentException iae)
            {
             Log.e(LOG_TAG,"bad link decode",iae);
            }*/
        } else if (link.contains("matrix.to")) {

            //this is an invite link like this: https://matrix.to/#/@n8fr8:matrix.org
            try {
                String matrixContact = link.substring(link.lastIndexOf("@"));

                diLink = new DecodedInviteLink();
                diLink.username = matrixContact;

            } catch (IllegalArgumentException iae) {
                Log.e(LOG_TAG, "bad link decode", iae);
            }
        } else if (link.startsWith(DEFAULT_SCHEME)) {

            //this is an invite link like this: https://matrix.to/#/@n8fr8:matrix.org
            try {
                String matrixContact = link.substring(link.lastIndexOf("id=") + 3);
                diLink = new DecodedInviteLink();
                diLink.username = matrixContact;

            } catch (IllegalArgumentException iae) {
                Log.e(LOG_TAG, "bad link decode", iae);
            }
        }

        return diLink;

    }

    public static String generateMatrixLink(String username) {
        return DEFAULT_SCHEME + "://invite?id=" + username;
    }

    /*
    public static String generateInviteLink (Context context, String username) throws IOException
    {
        StringBuffer inviteUrl = new StringBuffer();
        inviteUrl.append(DEFAULT_SCHEME)
                .append("://").append("invite?");

        //StringBuffer code = new StringBuffer();
        inviteUrl.append("id=");
        inviteUrl.append(username);

        //code.append("?otr=").append(fingerprint);

        //if (nickname != null)
          //  code.append("&nickname=").append(nickname);

        //if (isMigrateLink)
          //  code.append("&m=1");

      //  inviteUrl.append(Base64.encodeToString(code.toString().getBytes(), Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING));
        return inviteUrl.toString();
    }*/

    public static String generateInviteLink(String username) {
        return BASE_INVITE_URL + username;
    }

    /*
    private final static String PASSWORD_LETTERS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789+@!#";
    private final static int PASSWORD_LENGTH = 12;

    public static String generatePassword()
    {
        // Pick from some letters that won't be easily mistaken for each
        // other. So, for example, omit o O and 0, 1 l and L.
        SecureRandom random = new SecureRandom();

        StringBuffer pw = new StringBuffer();
        for (int i=0; i<PASSWORD_LENGTH; i++)
        {
            int index = (int)(random.nextDouble()*PASSWORD_LETTERS.length());
            pw.append(PASSWORD_LETTERS.substring(index, index+1));
        }
        return pw.toString();
    }*/

    public void registerAccount(final String nickname, final String username,
                                final String password, final String domain,
                                final String server, final int port) {

        Activity activity = mActivity.get();
        if (activity == null) return;

        ImPluginHelper helper = ImPluginHelper.getInstance(activity);
        final ContentResolver cr = activity.getContentResolver();

        mAccountRegistering = new OnboardingAccount();
        mAccountRegistering.username = username;
        mAccountRegistering.domain = domain;
        mAccountRegistering.password = password;
        mAccountRegistering.nickname = nickname;
        mAccountRegistering.providerId = helper.createAdditionalProvider(helper.getProviderNames().get(0)); //xmpp FIXME
        mAccountRegistering.accountId = ImApp.insertOrUpdateAccount(cr, mAccountRegistering.providerId, -1, nickname, username, password);

        Cursor pCursor = cr.query(Imps.ProviderSettings.CONTENT_URI,
                new String[]{Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE},
                Imps.ProviderSettings.PROVIDER + "=?", new String[]{Long.toString(mAccountRegistering.providerId)},
                null);

        Imps.ProviderSettings.QueryMap settings = new Imps.ProviderSettings.QueryMap(
                pCursor, cr, mAccountRegistering.providerId, false /* don't keep updated */,
                null /* no handler */);

        settings.setRequireTls(true);
        settings.setTlsCertVerify(true);
        settings.setAllowPlainAuth(false);

        String newDeviceId = DEFAULT_DEVICE_NAME + "-"
                + UUID.randomUUID().toString().substring(0, 8);

        settings.setDeviceName(newDeviceId);

        try {
            settings.setDomain(domain);
            settings.setPort(port);
            settings.setServer(server); // If we have a host, then we should use it.
            settings.setDoDnsSrv(server == null);
            settings.requery();
            settings.close();

            if (Looper.myLooper() == null)
                Looper.prepare();

            mConn.initUser(mAccountRegistering.providerId, mAccountRegistering.accountId);
            mConn.register(username, password, this);
        }
        catch (Exception e) {
            LogCleaner.error(LOG_TAG, "error registering new account", e);
        }
    }

    public void continueRegister(String captchaResponse, boolean termsApproved) {
        mConn.continueRegister(captchaResponse, termsApproved);
    }

    public void addExistingAccount(String username, String domain, String password, long accountId, long providerId) {

        Activity activity = mActivity.get();
        if (activity == null) return;

        final OnboardingAccount result = new OnboardingAccount();

        int port = 5222;

        ContentResolver cr = activity.getContentResolver();
        ImPluginHelper helper = ImPluginHelper.getInstance(activity);

        if (providerId == -1)
            providerId = helper.createAdditionalProvider(helper.getProviderNames().get(0)); //xmpp FIXME

        accountId = ImApp.insertOrUpdateAccount(cr, providerId, accountId, username, username, password);

        if (accountId == -1)
            return;

        Uri accountUri = ContentUris.withAppendedId(Imps.Account.CONTENT_URI, accountId);

        Cursor pCursor = cr.query(Imps.ProviderSettings.CONTENT_URI, new String[]{Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE},
                Imps.ProviderSettings.PROVIDER + "=?", new String[]{Long.toString(providerId)}, null);

        Imps.ProviderSettings.QueryMap settings = new Imps.ProviderSettings.QueryMap(
                pCursor, cr, providerId, false /* don't keep updated */, null /* no handler */);

        // Should check to see if Orbot is installed and running.
        boolean doDnsSrvLookup = true;

        settings.setRequireTls(true);
        settings.setTlsCertVerify(true);
        settings.setAllowPlainAuth(false);

        settings.setDoDnsSrv(doDnsSrvLookup);

        String newDeviceId = DEFAULT_DEVICE_NAME + "-"
                + UUID.randomUUID().toString().substring(0, 8);

        settings.setDeviceName(newDeviceId);

        try {

            settings.setDomain(domain);
            settings.setPort(port);
            settings.requery();

            result.username = username;
            result.domain = domain;
            result.password = password;
            result.providerId = providerId;
            result.accountId = accountId;

            //now keep this account signed-in
            ContentValues values = new ContentValues();
            values.put(Imps.AccountColumns.KEEP_SIGNED_IN, 1);
            cr.update(accountUri, values, null, null);

            settings.requery();

            if (Looper.myLooper() == null)
                Looper.prepare();

            OnboardingListener listener = mListener.get();
            if (listener != null) listener.registrationSuccessful(result);

            /**
            mConn.initUser(providerId, accountId);

            mConn.checkAccount(accountId, password, providerId, new MatrixConnection.LoginListener() {
                @Override
                public void onLoginSuccess() {
                    OnboardingListener listener = mListener.get();
                    if (listener != null) listener.registrationSuccessful(result);
                }

                @Override
                public void onLoginFailed(String message) {
                    OnboardingListener listener = mListener.get();
                    if (listener != null) listener.registrationFailed(message);
                }
            });
            **/

            // settings closed in registerAccount
        } catch (Exception e) {
            LogCleaner.error(LOG_TAG, "error registering new account", e);

            OnboardingListener listener = mListener.get();
            if (listener != null) listener.registrationFailed(e.getMessage());
        }

        settings.close();
    }

    public static class DecodedInviteLink {
        public String username;
        public String fingerprint;
        public String nickname;
    }

    @Override
    public void onRegistrationSuccess(String warningMessage) {
        // Now keep this account signed-in.
        ContentValues values = new ContentValues();
        values.put(Imps.AccountColumns.KEEP_SIGNED_IN, 1);

        Activity activity = mActivity.get();
        if (activity != null) activity.getContentResolver().update(
                ContentUris.withAppendedId(Imps.Account.CONTENT_URI, mAccountRegistering.accountId),
                values, null, null);

        OnboardingListener listener = mListener.get();
        if (listener != null) listener.registrationSuccessful(mAccountRegistering);
    }

    @Override
    public void onRegistrationFailed(String message) {
        Activity activity = mActivity.get();
        if (activity != null) ImApp.deleteAccount(activity.getContentResolver(),
                mAccountRegistering.accountId, mAccountRegistering.providerId);

        mAccountRegistering = null;

        OnboardingListener listener = mListener.get();
        if (listener != null) listener.registrationFailed(message);
    }

    @Override
    public void onWaitingEmailValidation() {
        // We don't support this, currently, so should not happen.

        Activity activity = mActivity.get();
        if (activity == null) return;

        OnboardingListener listener = mListener.get();
        if (listener != null) listener.registrationFailed(activity.getString(R.string.account_setup_error_server));
    }

    @Override
    public void onIdentityServerMissing() {
        Activity activity = mActivity.get();
        if (activity == null) return;

        OnboardingListener listener = mListener.get();
        if (listener != null) listener.registrationFailed(activity.getString(R.string.account_setup_error_server));
    }

    @Override
    public void onWaitingCaptcha(String publicKey) {
        Activity activity = mActivity.get();
        if (activity == null) return;

        if (!TextUtils.isEmpty(publicKey)) {
            Intent intent = new Intent(activity, CaptchaActivity.class);
            intent.putExtra(CaptchaActivity.EXTRA_HOME_SERVER_URL, mConn.getHomeServer().toString());
            intent.putExtra(CaptchaActivity.EXTRA_SITE_KEY, publicKey);

            activity.startActivityForResult(intent, CaptchaActivity.REQUEST_CODE);
        }
        else {
            OnboardingListener listener = mListener.get();
            if (listener != null) listener.registrationFailed(activity.getString(R.string.account_setup_error_server));
        }
    }

    @Override
    public void onWaitingTerms(List<LocalizedFlowDataLoginTerms> localizedFlowDataLoginTerms) {
        Activity activity = mActivity.get();
        if (activity == null) return;

        if (!localizedFlowDataLoginTerms.isEmpty()) {
            Intent intent = new Intent(activity, TermsActivity.class);
            intent.putParcelableArrayListExtra(TermsActivity.EXTRA_TERMS, (ArrayList<? extends Parcelable>) localizedFlowDataLoginTerms);
            activity.startActivityForResult(intent, TermsActivity.REQUEST_CODE);
        }
        else {
            OnboardingListener listener = mListener.get();
            if (listener != null) listener.registrationFailed(activity.getString(R.string.account_setup_error_server));
        }
    }

    @Override
    public void onThreePidRequestFailed(String message) {
        OnboardingListener listener = mListener.get();
        if (listener != null) listener.registrationFailed(message);
    }

    @Override
    public void onResourceLimitExceeded(MatrixError e) {
        Activity activity = mActivity.get();
        if (activity != null) ImApp.deleteAccount(activity.getContentResolver(),
                mAccountRegistering.accountId, mAccountRegistering.providerId);

        mAccountRegistering = null;

        OnboardingListener listener = mListener.get();
        if (listener != null) listener.registrationFailed(e.getLocalizedMessage());
    }
}
