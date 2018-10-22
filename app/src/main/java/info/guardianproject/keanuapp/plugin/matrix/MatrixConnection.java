package info.guardianproject.keanuapp.plugin.matrix;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.store.MXFileStore;
import org.matrix.androidsdk.data.store.MXMemoryStore;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.LoginRestClient;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.login.Credentials;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import info.guardianproject.keanuapp.conversations.UploadProgressListener;
import info.guardianproject.keanuapp.model.Address;
import info.guardianproject.keanuapp.model.ChatGroup;
import info.guardianproject.keanuapp.model.ChatGroupManager;
import info.guardianproject.keanuapp.model.ChatSessionManager;
import info.guardianproject.keanuapp.model.Contact;
import info.guardianproject.keanuapp.model.ContactListManager;
import info.guardianproject.keanuapp.model.ImConnection;
import info.guardianproject.keanuapp.model.ImException;
import info.guardianproject.keanuapp.model.Presence;
import info.guardianproject.keanuapp.plugin.xmpp.XmppAddress;
import info.guardianproject.keanuapp.provider.Imps;

public class MatrixConnection extends ImConnection {

    private MXSession mSession;
    private MXDataHandler mDataHandler;

    private IMXStore mStore = null;
    private Credentials mCredentials = null;

    private HomeServerConnectionConfig mConfig;

    private long mProviderId = -1;
    private long mAccountId = -1;
    private Contact mUser = null;

    private HashMap<String,String> mSessionContext = new HashMap<>();
    private MatrixChatSessionManager mChatSessionManager = new MatrixChatSessionManager();
    private MatrixContactListManager mContactListManager = new MatrixContactListManager();
    private MatrixChatGroupManager mChatGroupManager = new MatrixChatGroupManager();

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
        mUserPresence = new Presence(Presence.AVAILABLE, null, Presence.CLIENT_TYPE_MOBILE);

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


        setState(LOGGING_IN, null);

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
                boolean enableEncryption = true;

                mCredentials = credentials;
                mConfig.setCredentials(mCredentials);

                mStore = new MXFileStore(mConfig,enableEncryption, mContext);
                mDataHandler = new MXDataHandler(mStore, mCredentials);
                mDataHandler.addListener(mEventListener);

                mSession = new MXSession.Builder(mConfig, mDataHandler, mContext.getApplicationContext())
                        .build();

                mSession.startEventStream(initialToken);
                mSession.enableCrypto(true, new ApiCallback<Void>() {
                    @Override
                    public void onNetworkError(Exception e) {
                        debug ("enableCrypto: onNetworkError",e);

                    }

                    @Override
                    public void onMatrixError(MatrixError matrixError) {
                        debug ("enableCrypto: onMatrixError: " + matrixError);

                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        debug ("enableCrypto: onUnexpectedError",e);

                    }

                    @Override
                    public void onSuccess(Void aVoid) {
                        debug ("enableCrypto: onSuccess");

                    }
                });

                setState(LOGGED_IN, null);

            }

            @Override
            public void onNetworkError(Exception e) {
                super.onNetworkError(e);

                Log.w(TAG,"OnNetworkError",e);

                setState(SUSPENDED, null);

            }

            @Override
            public void onMatrixError(MatrixError e) {
                super.onMatrixError(e);

                Log.w(TAG,"onMatrixError: " + e.mErrorBodyAsString);

                setState(SUSPENDED, null);

            }

            @Override
            public void onUnexpectedError(Exception e) {
                super.onUnexpectedError(e);

                Log.w(TAG,"onUnexpectedError",e);


                setState(SUSPENDED, null);

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

    private void loadStateAsync ()
    {
        new AsyncTask<Void, Void, String>()
        {
            @Override
            protected String doInBackground(Void... voids) {

                Collection<Room> rooms = mStore.getRooms();

                for (final Room room : rooms)
                {
                    debug ("Room: " + room.getRoomId() + " - " + room.getTopic() + " - " + room.getNumberOfMembers());

                    String subject = room.getTopic();
                    if (TextUtils.isEmpty(subject))
                        subject = room.getRoomDisplayName(mContext);

                    final ChatGroup group = new ChatGroup(new MatrixAddress(room.getRoomId()),subject,mChatGroupManager);

                    room.getMembersAsync(new ApiCallback<List<RoomMember>>() {
                        @Override
                        public void onNetworkError(Exception e) {
                            debug ("Network error syncing active members",e);
                        }

                        @Override
                        public void onMatrixError(MatrixError matrixError) {
                            debug ("Matrix error syncing active members: " + matrixError);
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            debug("Error syncing active members",e);
                        }

                        @Override
                        public void onSuccess(List<RoomMember> roomMembers) {

                            for (RoomMember member : roomMembers)
                            {
                                debug ( room.getRoomId() + ": " + member.getName() + " (" + member.getUserId() + ")");
                                Contact contact = new Contact (new MatrixAddress(member.getUserId()),member.getName(), Imps.Contacts.TYPE_NORMAL);
                                group.notifyMemberJoined(member.getUserId(),contact);
                            }
                        }
                    });
                }

                /**
                Collection<RoomSummary> listRoomSummaries = mSession.getDataHandler().getSummaries(false);

                for (RoomSummary summary : listRoomSummaries)
                {
                    debug ("Room: " + summary.getRoomId() + " - " + summary.getRoomTopic());
                }

                List<String> roomIds = mDataHandler.getDirectChatRoomIdsList();

                for (String roomId : roomIds)
                {
                    final Room room = mDataHandler.getRoom(roomId);
                    debug ("room: " + room.getRoomId() + " - " + room.getTopic() + " - " + room.getNumberOfMembers() + " members");

                    room.getActiveMembersAsync(new ApiCallback<List<RoomMember>>() {
                        @Override
                        public void onNetworkError(Exception e) {
                            debug ("Network error syncing active members",e);
                        }

                        @Override
                        public void onMatrixError(MatrixError matrixError) {
                            debug ("Matrix error syncing active members: " + matrixError);
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            debug("Error syncing active members",e);
                        }

                        @Override
                        public void onSuccess(List<RoomMember> roomMembers) {

                            for (RoomMember member : roomMembers)
                            {
                                debug ( room.getRoomId() + ": " + member.getName() + " (" + member.getUserId() + ")");
                            }
                        }
                    });
                }**/

                return null;
            }
        }.execute();
    }

    private void debug (String msg)
    {
        Log.d(TAG, msg);
    }

    private void debug (String msg, Exception e)
    {
        Log.e(TAG, msg, e);
    }

    IMXEventListener mEventListener = new IMXEventListener() {
        @Override
        public void onStoreReady() {
            debug ("onStoreReady!");
        }

        @Override
        public void onPresenceUpdate(Event event, User user) {
            debug ("onPresenceUpdate : " + user.user_id + ": event=" + event.toString());
        }

        @Override
        public void onAccountInfoUpdate(MyUser myUser) {

        }

        @Override
        public void onIgnoredUsersListUpdate() {

        }

        @Override
        public void onDirectMessageChatRoomsListUpdate() {
            debug("onDirectMessageChatRoomsListUpdate");
        }

        @Override
        public void onLiveEvent(Event event, RoomState roomState) {
            debug ("live: " + event);

        }

        @Override
        public void onLiveEventsChunkProcessed(String s, String s1) {

        }

        @Override
        public void onBingEvent(Event event, RoomState roomState, BingRule bingRule) {
            debug ("bing: " + event.toString());
        }

        @Override
        public void onEventSentStateUpdated(Event event) {

        }

        @Override
        public void onEventSent(Event event, String s) {

        }

        @Override
        public void onEventDecrypted(Event event) {

        }

        @Override
        public void onBingRulesUpdate() {

        }

        @Override
        public void onInitialSyncComplete(String s) {
            debug ("onInitialSyncComplete: " + s);

            loadStateAsync ();

        }

        @Override
        public void onSyncError(MatrixError matrixError) {
            debug ("onSyncError: " + matrixError);
        }

        @Override
        public void onCryptoSyncComplete() {
            debug ("onCryptoSyncComplete");

        }

        @Override
        public void onNewRoom(String s) {

        }

        @Override
        public void onJoinRoom(String s) {

        }

        @Override
        public void onRoomFlush(String s) {

        }

        @Override
        public void onRoomInternalUpdate(String s) {

        }

        @Override
        public void onNotificationCountUpdate(String s) {

        }

        @Override
        public void onLeaveRoom(String s) {

        }

        @Override
        public void onRoomKick(String s) {

        }

        @Override
        public void onReceiptEvent(String s, List<String> list) {

        }

        @Override
        public void onRoomTagEvent(String s) {

        }

        @Override
        public void onReadMarkerEvent(String s) {

        }

        @Override
        public void onToDeviceEvent(Event event) {

        }

        @Override
        public void onNewGroupInvitation(String s) {

        }

        @Override
        public void onJoinGroup(String s) {

        }

        @Override
        public void onLeaveGroup(String s) {

        }

        @Override
        public void onGroupProfileUpdate(String s) {

        }

        @Override
        public void onGroupRoomsListUpdate(String s) {

            debug ("onGroupRoomsListUpdate: " + s);
        }

        @Override
        public void onGroupUsersListUpdate(String s) {
            debug ("onGroupUsersListUpdate: " + s);

        }

        @Override
        public void onGroupInvitedUsersListUpdate(String s) {

        }
    };
}
