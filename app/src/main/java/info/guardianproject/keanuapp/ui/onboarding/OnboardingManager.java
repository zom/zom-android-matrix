package info.guardianproject.keanuapp.ui.onboarding;

import info.guardianproject.keanu.matrix.plugin.MatrixConnection;
import info.guardianproject.keanuapp.R;
import info.guardianproject.keanu.core.provider.Imps;
import org.json.JSONException;
import org.matrix.androidsdk.rest.model.MatrixError;

import info.guardianproject.keanu.core.util.ImPluginHelper;
import info.guardianproject.keanu.core.util.LogCleaner;
import info.guardianproject.keanuapp.ImApp;
import info.guardianproject.keanuapp.nearby.NearbyAddContactActivity;
import info.guardianproject.keanuapp.ui.qr.QrScanActivity;

import java.io.IOException;
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
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import android.util.Base64;
import android.util.Log;

import static info.guardianproject.keanu.core.KeanuConstants.DEFAULT_DEVICE_NAME;
import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;

public class OnboardingManager {

    public final static int REQUEST_SCAN = 1111;
    public final static int REQUEST_CHOOSE_AVATAR = REQUEST_SCAN+1;

    public static String BASE_INVITE_URL = "https://keanu.im/i/#";

    public final static String DEFAULT_SCHEME = "matrix";

    public static void setBaseInviteUrl (String baseInvite)
    {
        BASE_INVITE_URL = baseInvite;
    }

    public static void inviteSMSContact (Activity context, String phoneNumber, String message)
    {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) // At least KitKat
        {
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
        else // For early versions, do what worked for you before.
        {
            Intent smsIntent = new Intent(Intent.ACTION_VIEW);
            smsIntent.setType("vnd.android-dir/mms-sms");

            if (phoneNumber != null)
            smsIntent.putExtra("address",phoneNumber);
            smsIntent.putExtra("sms_body",message);

            context.startActivity(smsIntent);
        }
    }
    
    public static void inviteShare (Activity context, String message)
    {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, message);
        intent.setType("text/plain");
        context.startActivity(intent);
    }

    public static void inviteNearby(Activity context, String message)
    {
        Intent intent = new Intent(context, NearbyAddContactActivity.class);
        intent.putExtra(Intent.EXTRA_TEXT, message);
        intent.setType("text/plain");
        context.startActivity(intent);
    }

    public static void inviteShareToPackage (Activity context, String message, String packageName)
    {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, message);
        intent.setType("text/plain");
        intent.setPackage(packageName);
        context.startActivity(intent);
    }

    public static void inviteScan (Activity context, String message)
    {
        Intent intent = new Intent(context, QrScanActivity.class);
        intent.putExtra(Intent.EXTRA_TEXT,message);
        intent.setType("text/plain");
        context.startActivityForResult(intent, REQUEST_SCAN);

    }
    
    public static String generateInviteMessage (Context context, String nickname, String username, String fingerprint)
    {
        try
        {
            StringBuffer resp = new StringBuffer();

            resp.append(nickname)
                    .append(' ')
                    .append(context.getString(R.string.is_inviting_you))
                    .append(" ")
                    .append(generateInviteLink(context,username,fingerprint,nickname));
            
            return resp.toString();
        } catch (Exception e)
        { 
            Log.d(LOG_TAG,"error with link",e);
            return null;
        }
    }

    public static DecodedInviteLink decodeInviteLink (String link)
    {
        DecodedInviteLink diLink = null;

        if (link.contains("/i/#")){

            //this is an invite link

            //this is an invite link like this: https://zom.im/i/#@earthmouse:matrix.org
            try {
                String matrixContact = link.substring(link.lastIndexOf("@"));

                diLink = new DecodedInviteLink();
                diLink.username = matrixContact;

            }
            catch (IllegalArgumentException iae)
            {
                Log.e(LOG_TAG,"bad link decode",iae);
            }

            /**
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
            }**/
        }
        else if (link.contains("matrix.to")){

            //this is an invite link like this: https://matrix.to/#/@n8fr8:matrix.org
            try {
                String matrixContact = link.substring(link.lastIndexOf("@"));

                diLink = new DecodedInviteLink();
                diLink.username = matrixContact;

            }
            catch (IllegalArgumentException iae)
            {
                Log.e(LOG_TAG,"bad link decode",iae);
            }
        }
        else if (link.startsWith(DEFAULT_SCHEME)){

            //this is an invite link like this: https://matrix.to/#/@n8fr8:matrix.org
            try {
                String matrixContact = link.substring(link.lastIndexOf("id=")+3);
                diLink = new DecodedInviteLink();
                diLink.username = matrixContact;

            }
            catch (IllegalArgumentException iae)
            {
                Log.e(LOG_TAG,"bad link decode",iae);
            }
        }

        return diLink;

    }

    public static String generateMatrixLink (String username, String fingerprint) throws IOException
    {
        StringBuffer inviteUrl = new StringBuffer();
        inviteUrl.append(DEFAULT_SCHEME);
        inviteUrl.append("://invite?id=");
        inviteUrl.append(username);

        return inviteUrl.toString();
    }

    public static String generateInviteLink (Context context, String username, String fingerprint, String nickname) throws IOException
    {
        return generateInviteLink(context, username);
    }

    /**
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
    }**/

    public static String generateInviteLink(Context context, String username) throws IOException
    {
        StringBuffer inviteUrl = new StringBuffer();
        inviteUrl.append(BASE_INVITE_URL);
        inviteUrl.append(username);
       // inviteUrl.append(Base64.encodeToString(code.toString().getBytes(), Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING));
        return inviteUrl.toString();
    }

    /**
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
    }**/



    public static boolean changeLocalPassword (Activity context, long providerId, long accountId, String password)
    {
        try {
            final ContentResolver cr = context.getContentResolver();
            ImPluginHelper helper = ImPluginHelper.getInstance(context);
            ImApp.insertOrUpdateAccount(cr, providerId, accountId, null, null, password);

            /**
            XmppConnection xmppConn = new XmppConnection(context);
            xmppConn.initUser(providerId, accountId);
            boolean success = xmppConn.changeServerPassword(providerId, accountId, oldPassword, newPassword);
            **/
            return false;

            //return success;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public static void registerAccount (final Context context, final String nickname, final String username, final String password, final String domain, final String server, final int port, final OnboardingListener oListener) throws JSONException {



        final ContentResolver cr = context.getContentResolver();
        ImPluginHelper helper = ImPluginHelper.getInstance(context);

        final long providerId = helper.createAdditionalProvider(helper.getProviderNames().get(0)); //xmpp FIXME
        final long accountId = ImApp.insertOrUpdateAccount(cr, providerId, -1, nickname, username, password);

        final Uri accountUri = ContentUris.withAppendedId(Imps.Account.CONTENT_URI, accountId);

        Cursor pCursor = cr.query(Imps.ProviderSettings.CONTENT_URI, new String[]{Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE}, Imps.ProviderSettings.PROVIDER + "=?", new String[]{Long.toString(providerId)}, null);

        Imps.ProviderSettings.QueryMap settings = new Imps.ProviderSettings.QueryMap(
                pCursor, cr, providerId, false /* don't keep updated */, null /* no handler */);

        settings.setRequireTls(true);
        settings.setTlsCertVerify(true);
        settings.setAllowPlainAuth(false);

        String newDeviceId =  DEFAULT_DEVICE_NAME + "-"
                + UUID.randomUUID().toString().substring(0, 8);

        settings.setDeviceName(newDeviceId);

        try
        {
            settings.setDomain(domain);
            settings.setPort(port);

            if (server != null) {
                settings.setServer(server); //if we have a host, then we should use it
                settings.setDoDnsSrv(false);

            }
            else
            {
                settings.setServer(null);
                settings.setDoDnsSrv(true);

            }

            settings.requery();
            settings.close();

            if (Looper.myLooper() == null)
                Looper.prepare();

            MatrixConnection conn = new MatrixConnection(context);
            conn.initUser(providerId, accountId);

            final OnboardingAccount result = new OnboardingAccount();
            result.username = username;
            result.domain = domain;
            result.password = password;
            result.providerId = providerId;
            result.accountId = accountId;
            result.nickname = nickname;

            conn.register(context, username, password, new MatrixConnection.RegistrationListener() {
                @Override
                public void onRegistrationSuccess() {

                    //now keep this account signed-in
                    ContentValues values = new ContentValues();
                    values.put(Imps.AccountColumns.KEEP_SIGNED_IN, 1);
                    cr.update(accountUri, values, null, null);

                    if (oListener != null)
                        oListener.registrationSuccessful(result);
                }

                @Override
                public void onRegistrationFailed(String message) {
                    ImApp.deleteAccount(context.getContentResolver(),accountId, providerId);

                    if (oListener != null)
                        oListener.registrationFailed(message);
                }

                @Override
                public void onResourceLimitExceeded(MatrixError e) {
                    ImApp.deleteAccount(context.getContentResolver(),accountId, providerId);

                }
            });


        } catch (Exception e) {
            LogCleaner.error(LOG_TAG, "error registering new account", e);


        }



    }



    public static void addExistingAccount (Activity context, Handler handler, String username, String domain, String password, OnboardingListener onboardingListener) {

        final OnboardingAccount result = new OnboardingAccount();


        int port = 5222;

        ContentResolver cr = context.getContentResolver();
        ImPluginHelper helper = ImPluginHelper.getInstance(context);

        long providerId = helper.createAdditionalProvider(helper.getProviderNames().get(0)); //xmpp FIXME

        long accountId = ImApp.insertOrUpdateAccount(cr, providerId, -1, username, username, password);

        if (accountId == -1)
            return;

        Uri accountUri = ContentUris.withAppendedId(Imps.Account.CONTENT_URI, accountId);

        Cursor pCursor = cr.query(Imps.ProviderSettings.CONTENT_URI, new String[]{Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE}, Imps.ProviderSettings.PROVIDER + "=?", new String[]{Long.toString(providerId)}, null);

        Imps.ProviderSettings.QueryMap settings = new Imps.ProviderSettings.QueryMap(
                pCursor, cr, providerId, false /* don't keep updated */, null /* no handler */);

        //should check to see if Orbot is installed and running
        boolean doDnsSrvLookup = true;

        settings.setRequireTls(true);
        settings.setTlsCertVerify(true);
        settings.setAllowPlainAuth(false);

        settings.setDoDnsSrv(doDnsSrvLookup);

        String newDeviceId =  DEFAULT_DEVICE_NAME + "-"
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

            final MatrixConnection conn = new MatrixConnection(context);
            conn.initUser(providerId, accountId);

            conn.checkAccount(accountId, password, providerId, new MatrixConnection.LoginListener() {
                @Override
                public void onLoginSuccess() {

                    onboardingListener.registrationSuccessful(result);

                }

                @Override
                public void onLoginFailed(String message) {
                    onboardingListener.registrationFailed(message);
                }
            });


            // settings closed in registerAccount
        } catch (Exception e) {
            LogCleaner.error(LOG_TAG, "error registering new account", e);

            onboardingListener.registrationFailed(e.getMessage());
        }


        settings.close();

    }


    public static class DecodedInviteLink {
        public String username;
        public boolean isMigration = false;
        public String fingerprint;
        public String nickname;
    }

}
