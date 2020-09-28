package info.guardianproject.keanuapp.tasks;

import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import info.guardianproject.keanu.core.model.ImConnection;
import info.guardianproject.keanu.core.model.ImErrorInfo;
import info.guardianproject.keanu.core.model.Server;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.service.IChatSession;
import info.guardianproject.keanu.core.service.IChatSessionListener;
import info.guardianproject.keanu.core.service.IChatSessionManager;
import info.guardianproject.keanu.core.service.IContactList;
import info.guardianproject.keanu.core.service.IContactListManager;
import info.guardianproject.keanu.core.service.IImConnection;
import info.guardianproject.keanu.core.service.RemoteImService;
import info.guardianproject.keanu.core.util.DatabaseUtils;
import info.guardianproject.keanuapp.ImApp;
import info.guardianproject.keanuapp.R;
import info.guardianproject.keanuapp.ui.onboarding.OnboardingAccount;
import info.guardianproject.keanuapp.ui.onboarding.OnboardingListener;
import info.guardianproject.keanuapp.ui.onboarding.OnboardingManager;

import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;

/**
 * Created by n8fr8 on 5/1/17.
 */

public class MigrateAccountTask extends AsyncTask<Server, Void, OnboardingAccount> {

    Activity mContext;
    IImConnection mConn;
    String mDomain;
    long mAccountId;
    long mProviderId;
    ImApp mApp;
    IImConnection mNewConn;
    OnboardingAccount mNewAccount;

    MigrateAccountListener mListener;

    Handler mHandler = new Handler();

    ArrayList<String> mContacts;

    public MigrateAccountTask(Activity context, ImApp app, long providerId, long accountId, MigrateAccountListener listener)
    {
        mContext = context;
        mAccountId = accountId;
        mProviderId = providerId;
        mApp = app;

        mListener = listener;

        mConn = RemoteImService.getConnection(providerId, accountId);

        Cursor cursor = context.getContentResolver().query(Imps.ProviderSettings.CONTENT_URI, new String[]{Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE}, Imps.ProviderSettings.PROVIDER + "=?", new String[]{Long.toString(mProviderId)}, null);

        if (cursor == null)
            return;

        Imps.ProviderSettings.QueryMap providerSettings = new Imps.ProviderSettings.QueryMap(
                cursor, context.getContentResolver(), mProviderId, false, null);
        mDomain = providerSettings.getDomain();
        providerSettings.close();
        if (!cursor.isClosed())
            cursor.close();

        mContacts = new ArrayList<>();
    }

    @Override
    protected OnboardingAccount doInBackground(Server... newServers) {

        //get existing account username
        String nickname = Imps.Account.getNickname(mContext.getContentResolver(), mAccountId);
        String username = Imps.Account.getUserName(mContext.getContentResolver(), mAccountId);
        String password = Imps.Account.getPassword(mContext.getContentResolver(), mAccountId);

        //find or use provided new server/domain
        for (Server newServer : newServers) {

            if (mDomain.equals(newServer.domain))
                continue; //don't migrate to the same server... to to =

            //register account on new domain with same password
            //mNewAccount = registerNewAccount(nickname, username, password, newServer.domain, newServer.server);

            OnboardingManager om = new OnboardingManager(mContext, null);
            om.registerAccount(nickname, username, password, newServer.domain, newServer.server, newServer.port);

            String newAccountId = '@' + username + ':' + mNewAccount.domain;

            //send migration message to existing contacts and/or sessions
            try {

                boolean loggedInToOldAccount = mConn.getState() == ImConnection.LOGGED_IN;

                //login and set new default account
                SignInHelper signInHelper = new SignInHelper(mContext, mHandler);
                signInHelper.activateAccount(mNewAccount.providerId, mNewAccount.accountId);
                signInHelper.signIn(mNewAccount.password, mNewAccount.providerId, mNewAccount.accountId, true);

                mNewConn = RemoteImService.getConnection(mNewAccount.providerId, mNewAccount.accountId);

                while (mNewConn.getState() != ImConnection.LOGGED_IN) {
                    try {
                        Thread.sleep(500);
                    } catch (Exception e) {
                    }
                }

                String fingerprint = "";
                String inviteLink = OnboardingManager.generateInviteLink(newAccountId);

                String migrateMessage = mContext.getString(R.string.migrate_message) + ' ' + inviteLink;
                IChatSessionManager sessionMgr = mConn.getChatSessionManager();
                IContactListManager clManager = mConn.getContactListManager();
                List<IContactList> listOfLists = clManager.getContactLists();

                if (loggedInToOldAccount) {

                    for (IContactList contactList : listOfLists) {
                        String[] contacts = contactList.getContacts();

                        for (String contact : contacts) {
                            mContacts.add(contact);

                            IChatSession session = sessionMgr.getChatSession(contact);

                            if (session == null) {
                                sessionMgr.createChatSession(contact, true, new IChatSessionListener() {
                                    @Override
                                    public void onChatSessionCreated(IChatSession session) throws RemoteException {

                                        session.sendMessage(migrateMessage, false, false, false, null);

                                        //archive existing contact
                                        clManager.archiveContact(contact, session.isGroupChatSession() ? Imps.Contacts.TYPE_NORMAL : Imps.Contacts.TYPE_GROUP, true);
                                    }

                                    @Override
                                    public void onChatSessionCreateError(String name, ImErrorInfo error) throws RemoteException {

                                    }

                                    @Override
                                    public IBinder asBinder() {
                                        return null;
                                    }
                                });
                            }

                        }

                    }
                } else {
                    String[] offlineAddresses = clManager.getOfflineAddresses();

                    for (String address : offlineAddresses) {
                        mContacts.add(address);
                        clManager.archiveContact(address, Imps.Contacts.TYPE_NORMAL, true);
                    }
                }

                for (String contact : mContacts) {
                    addToContactList(mNewConn, contact, null, null);
                }

                if (loggedInToOldAccount) {
                    //archive existing info.guardianproject.keanu.core.conversations and contacts
                    List<IChatSession> listSession = mConn.getChatSessionManager().getActiveChatSessions();
                    for (IChatSession session : listSession) {
                        session.leave();
                    }
                    mConn.broadcastMigrationIdentity(newAccountId);
                }

                migrateAvatars(username, newAccountId);
                mApp.setDefaultAccount(mNewAccount.providerId, mNewAccount.accountId);

                //logout of existing account
                setKeepSignedIn(mAccountId, false);

                if (loggedInToOldAccount)
                    mConn.logout(true);

                return mNewAccount;

            } catch (Exception e) {
                Log.e(LOG_TAG, "error with migration", e);
            }
        }

        //failed
        return null;
    }

    @Override
    protected void onPostExecute(OnboardingAccount account) {
        super.onPostExecute(account);


        if (account == null)
        {
            if (mListener != null)
                mListener.migrateFailed(mProviderId,mAccountId);
        }
        else
        {
            if (mListener != null)
                mListener.migrateComplete(account);
        }

    }


    private int addToContactList (IImConnection conn, String address, String otrFingperint, String nickname)
    {
        int res = -1;

        try {

            IContactList list = getContactList(conn);

            if (list != null) {

                res = list.addContact(address, nickname);
                if (res != ImErrorInfo.NO_ERROR) {

                    //what to do here?
                }


                //Contact contact = new Contact(new XmppAddress(address),address);
                //IContactListManager contactListMgr = conn.getContactListManager();
                //contactListMgr.approveSubscription(contact);
            }

        } catch (RemoteException re) {
            Log.e(LOG_TAG, "error adding contact", re);
        }

        return res;
    }

    private IContactList getContactList(IImConnection conn) {
        if (conn == null) {
            return null;
        }

        try {
            IContactListManager contactListMgr = conn.getContactListManager();

            // Use the default list
            List<IBinder> lists = contactListMgr.getContactLists();
            for (IBinder binder : lists) {
                IContactList list = IContactList.Stub.asInterface(binder);
                if (list.isDefault()) {
                    return list;
                }
            }

            // No default list, use the first one as default list
            if (!lists.isEmpty()) {
                return IContactList.Stub.asInterface(lists.get(0));
            }

            return null;

        } catch (RemoteException e) {
            // If the service has died, there is no list for now.
            return null;
        }
    }

    public interface MigrateAccountListener {

        public void migrateComplete(OnboardingAccount account);

        public void migrateFailed(long providerId, long accountId);
    }

    private void setKeepSignedIn(final long accountId, boolean signin) {
        Uri mAccountUri = ContentUris.withAppendedId(Imps.Account.CONTENT_URI, accountId);
        ContentValues values = new ContentValues();
        values.put(Imps.Account.KEEP_SIGNED_IN, signin);
        mContext.getContentResolver().update(mAccountUri, values, null, null);
    }

    private void migrateAvatars (String oldUsername, String newUsername)
    {

        try {

            //first copy the old avatar over to the new account
            byte[] oldAvatar = DatabaseUtils.getAvatarBytesFromAddress(oldUsername);
            if (oldAvatar != null)
            {
                setAvatar(newUsername, oldAvatar);
            }

            //now change the older avatar, so the vcard gets reloaded
            Bitmap bitmap = BitmapFactory.decodeStream(mContext.getAssets().open("stickers/olo and shimi/4greeting.png"));
            setAvatar(oldUsername, bitmap);
        }
        catch (Exception ioe)
        {
            ioe.printStackTrace();
        }
    }

    private void setAvatar(String address, Bitmap bmp) {

        try {

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, stream);

            byte[] avatarBytesCompressed = stream.toByteArray();
            String avatarHash = "nohash";
            DatabaseUtils.insertAvatarBlob(mContext.getContentResolver(), Imps.Avatars.CONTENT_URI, mProviderId, mAccountId, avatarBytesCompressed, avatarHash, address);
        } catch (Exception e) {
            Log.w(LOG_TAG, "error loading image bytes", e);
        }
    }

    private void setAvatar(String address, byte[] avatarBytesCompressed) {

        try {
            String avatarHash = "nohash";
            DatabaseUtils.insertAvatarBlob(mContext.getContentResolver(), Imps.Avatars.CONTENT_URI, mProviderId, mAccountId, avatarBytesCompressed, avatarHash, address);
        } catch (Exception e) {
            Log.w(LOG_TAG, "error loading image bytes", e);
        }
    }
}
