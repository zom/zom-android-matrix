package info.guardianproject.keanuapp.plugin.matrix;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import org.jxmpp.jid.impl.JidCreate;
import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.store.MXMemoryStore;
import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.network.NetworkConnectivityReceiver;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.LoginRestClient;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.sync.SyncResponse;
import org.matrix.androidsdk.sync.EventsThreadListener;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import info.guardianproject.keanuapp.conversations.UploadProgressListener;
import info.guardianproject.keanuapp.model.ChatGroupManager;
import info.guardianproject.keanuapp.model.ChatSessionManager;
import info.guardianproject.keanuapp.model.Contact;
import info.guardianproject.keanuapp.model.ContactListManager;
import info.guardianproject.keanuapp.model.ImConnection;
import info.guardianproject.keanuapp.model.ImException;
import info.guardianproject.keanuapp.model.Presence;
import info.guardianproject.keanuapp.plugin.xmpp.XmppAddress;
import info.guardianproject.keanuapp.provider.Imps;

import static org.spongycastle.cms.RecipientId.password;

public class MatrixConnection extends ImConnection {

    private MXSession mSession;
    private MXDataHandler mDataHandler;

    private IMXStore mStore = null;
    private Credentials mCredentials = null;

    private HomeServerConnectionConfig mConfig;

    private long mProviderId = -1;
    private long mAccountId = -1;
    private Contact mUser = null;

    private final static String TAG = "MATRIX";

    public MatrixConnection (Context context)
    {
        super (context);
    }

    @Override
    public Contact getLoginUser() {
        return mUser;
    }

    @Override
    public int[] getSupportedPresenceStatus() {
        return new int[0];
    }

    @Override
    public void initUser(long providerId, long accountId) throws ImException {

        ContentResolver contentResolver = mContext.getContentResolver();

        Cursor cursor = contentResolver.query(Imps.ProviderSettings.CONTENT_URI, new String[]{Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE}, Imps.ProviderSettings.PROVIDER + "=?", new String[]{Long.toString(providerId)}, null);

        if (cursor == null)
            throw new ImException("unable to query settings");

        Imps.ProviderSettings.QueryMap providerSettings = new Imps.ProviderSettings.QueryMap(
                cursor, contentResolver, providerId, false, null);

        mProviderId = providerId;
        mAccountId = accountId;
        mUser = makeUser(providerSettings, contentResolver);

        providerSettings.close();

        mStore = new MXMemoryStore();
    }

    private synchronized Contact makeUser(Imps.ProviderSettings.QueryMap providerSettings, ContentResolver contentResolver) {

        Contact contactUser = null;

        String nickname = Imps.Account.getNickname(contentResolver, mAccountId);
        String userName = Imps.Account.getUserName(contentResolver, mAccountId);
        String domain = providerSettings.getDomain();
        String xmppName = userName + '@' + domain + '/' + providerSettings.getXmppResource();
        contactUser = new Contact(new XmppAddress(xmppName), nickname, Imps.Contacts.TYPE_NORMAL);
        return contactUser;
    }

    @Override
    public int getCapability() {
        return 0;
    }

    @Override
    public void loginAsync(long accountId, String password, long providerId, boolean retry) {

        String username = mUser.getAddress().getUser();
        String server = "https://matrix.org";

        ContentResolver contentResolver = mContext.getContentResolver();

        if (password == null)
            password = Imps.Account.getPassword(contentResolver, mAccountId);

        mConfig = new HomeServerConnectionConfig.Builder()
                .withHomeServerUri(Uri.parse(server))
                .build();

        new LoginRestClient(mConfig).loginWithUser(username, password, new SimpleApiCallback<Credentials>()
        {

            @Override
            public void onSuccess(Credentials credentials) {

                String initialToken = "";
                mCredentials = credentials;
                mStore = new MXMemoryStore(mCredentials,mContext);
                mDataHandler = new MXDataHandler(mStore, credentials);

                mSession = new MXSession.Builder(mConfig, mDataHandler, mContext.getApplicationContext())
                        .build();

                mSession.startEventStream(initialToken);
            }

            @Override
            public void onNetworkError(Exception e) {
                super.onNetworkError(e);

                Log.w(TAG,"OnNetworkError",e);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                super.onMatrixError(e);

                Log.w(TAG,"onMatrixError: " + e.mErrorBodyAsString);

            }

            @Override
            public void onUnexpectedError(Exception e) {
                super.onUnexpectedError(e);

                Log.w(TAG,"onUnexpectedError",e);

            }
        });
    }

    @Override
    public void reestablishSessionAsync(Map<String, String> sessionContext) {

    }

    @Override
    public void logoutAsync() {
        logout();
    }

    @Override
    public void logout() {
        mSession.logout(mContext, new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void aVoid) {

            }
        });
    }

    @Override
    public void suspend() {

    }

    private HashMap<String,String> mSessionContext = new HashMap<>();
    private MatrixChatSessionManager mChatSessionManager = new MatrixChatSessionManager();
    private MatrixContactListManager mContactListManager = new MatrixContactListManager();
    private MatrixChatGroupManager mChatGroupManager = new MatrixChatGroupManager();

    @Override
    public Map<String, String> getSessionContext() {
        return mSessionContext;
    }

    @Override
    public ChatSessionManager getChatSessionManager() {
        return mChatSessionManager;
    }

    @Override
    public ContactListManager getContactListManager() {
        return mContactListManager;
    }

    @Override
    public ChatGroupManager getChatGroupManager() {
        return mChatGroupManager;
    }

    @Override
    public boolean isUsingTor() {
        return false;
    }

    @Override
    protected void doUpdateUserPresenceAsync(Presence presence) {

    }

    @Override
    public void sendHeartbeat(long heartbeatInterval) {

    }

    @Override
    public void setProxy(String type, String host, int port) {

    }

    @Override
    public void sendTypingStatus(String to, boolean isTyping) {

    }

    @Override
    public List getFingerprints(String address) {
        return null;
    }

    @Override
    public void broadcastMigrationIdentity(String newIdentity) {

    }

    @Override
    public String publishFile(String fileName, String mimeType, long fileSize, InputStream is, boolean doEncryption, UploadProgressListener listener) {
        return null;
    }

    @Override
    public void changeNickname(String nickname) {

    }
}
