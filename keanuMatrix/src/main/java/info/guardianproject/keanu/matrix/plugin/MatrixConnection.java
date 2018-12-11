package info.guardianproject.keanu.matrix.plugin;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomMediaMessage;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.store.IMXStoreListener;
import org.matrix.androidsdk.data.store.MXFileStore;
import org.matrix.androidsdk.data.store.MXMemoryStore;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.IMXMediaUploadListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.LoginRestClient;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.sync.DeviceInfo;
import org.matrix.androidsdk.util.ContentManager;
import org.w3c.dom.Text;

import java.io.InputStream;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import info.guardianproject.keanu.core.conversations.UploadProgressListener;
import info.guardianproject.keanu.core.model.ChatGroup;
import info.guardianproject.keanu.core.model.ChatGroupManager;
import info.guardianproject.keanu.core.model.ChatSession;
import info.guardianproject.keanu.core.model.ChatSessionManager;
import info.guardianproject.keanu.core.model.Contact;
import info.guardianproject.keanu.core.model.ContactListManager;
import info.guardianproject.keanu.core.model.ImConnection;
import info.guardianproject.keanu.core.model.ImException;
import info.guardianproject.keanu.core.model.Message;
import info.guardianproject.keanu.core.model.Presence;
import info.guardianproject.keanu.core.provider.Imps;

public class MatrixConnection extends ImConnection {

    private MXSession mSession;
    private MXDataHandler mDataHandler;

    protected KeanuMXFileStore mStore = null;
    private Credentials mCredentials = null;

    private HomeServerConnectionConfig mConfig;

    private long mProviderId = -1;
    private long mAccountId = -1;
    private Contact mUser = null;

    private String mDeviceName = null;
    private String mDeviceId = null;

    private HashMap<String,String> mSessionContext = new HashMap<>();
    private MatrixChatSessionManager mChatSessionManager;
    private MatrixContactListManager mContactListManager;
    private MatrixChatGroupManager mChatGroupManager;

    private final static String TAG = "MATRIX";
    private static final int THREAD_ID = 10001;

    private final static String HTTPS_PREPEND = "https://";

    private Handler mResponseHandler;

    public MatrixConnection (Context context)
    {
        super (context);

        mContactListManager = new MatrixContactListManager(context, this);
        mChatGroupManager = new MatrixChatGroupManager(this);
        mChatSessionManager = new MatrixChatSessionManager();

        mResponseHandler = new Handler();
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

        mDeviceName = providerSettings.getDeviceName();
        mDeviceId = providerSettings.getDeviceName(); //make them the same for now

        providerSettings.close();

    }

    private Contact makeUser(Imps.ProviderSettings.QueryMap providerSettings, ContentResolver contentResolver) {

        String nickname = Imps.Account.getNickname(contentResolver, mAccountId);
        String userName = Imps.Account.getUserName(contentResolver, mAccountId);
        String domain = providerSettings.getDomain();

        Contact contactUser = new Contact(new MatrixAddress('@' + userName + ':' + domain), nickname, Imps.Contacts.TYPE_NORMAL);
        return contactUser;
    }

    @Override
    public int getCapability() {
        return 0;
    }

    @Override
    public void loginAsync(long accountId, final String password, long providerId, boolean retry) {

        setState(LOGGING_IN, null);

        mProviderId = providerId;
        mAccountId = accountId;

        new Thread ()
        {
            public void run ()
            {
                loginAsync (password);
            }
        }.start();
    }

    private void loginAsync (String password)
    {

        TrafficStats.setThreadStatsTag(THREAD_ID);

        String username = mUser.getAddress().getUser();

        ContentResolver contentResolver = mContext.getContentResolver();

        if (password == null)
            password = Imps.Account.getPassword(contentResolver, mAccountId);

        Cursor cursor = contentResolver.query(Imps.ProviderSettings.CONTENT_URI, new String[]{Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE}, Imps.ProviderSettings.PROVIDER + "=?", new String[]{Long.toString(mProviderId)}, null);

        if (cursor == null)
            return; //not going to work

        Imps.ProviderSettings.QueryMap providerSettings = new Imps.ProviderSettings.QueryMap(
                cursor, contentResolver, mProviderId, false, null);

        String server = providerSettings.getServer();
        if (TextUtils.isEmpty(server))
            server = providerSettings.getDomain();

        providerSettings.close();
        if (!cursor.isClosed())
            cursor.close();

        mConfig = new HomeServerConnectionConfig.Builder()
                .withHomeServerUri(Uri.parse(HTTPS_PREPEND + server))
                .build();


        final String initialToken = "";
        final boolean enableEncryption = true;


        mCredentials = new Credentials();
        mCredentials.userId = mUser.getAddress().getAddress();
        mCredentials.homeServer = HTTPS_PREPEND + server;
        mCredentials.deviceId = mDeviceId;

        mConfig.setCredentials(mCredentials);

        mStore = new KeanuMXFileStore(mConfig,enableEncryption, mContext);

        mStore.addMXStoreListener(new IMXStoreListener() {
            @Override
            public void postProcess(String s) {
                debug ("MXSTORE: postProcess: " + s);


            }

            @Override
            public void onStoreReady(String s) {
                debug ("MXSTORE: onStoreReady: " + s);

            }

            @Override
            public void onStoreCorrupted(String s, String s1) {
                debug ("MXSTORE: onStoreCorrupted: " + s + " " + s1);
            }

            @Override
            public void onStoreOOM(String s, String s1) {
                debug ("MXSTORE: onStoreOOM: " + s + " " + s1);

            }

            @Override
            public void onReadReceiptsLoaded(String s) {
                debug ("MXSTORE: onReadReceiptsLoaded: " + s);

            }
        });
        mStore.open();

        mDataHandler = new MXDataHandler(mStore, mCredentials);
        mDataHandler.addListener(mEventListener);
        mStore.setDataHandler(mDataHandler);

        mChatSessionManager.setDataHandler(mDataHandler);
        mChatGroupManager.setDataHandler(mDataHandler);

        new LoginRestClient(mConfig).loginWithUser(username, password, mDeviceName, mDeviceId, new SimpleApiCallback<Credentials>()
        {

            @Override
            public void onSuccess(Credentials credentials) {

                mCredentials = credentials;
                mConfig.setCredentials(mCredentials);

                mResponseHandler.post(new Runnable ()
                {
                    public void run ()
                    {


                        mSession = new MXSession.Builder(mConfig, mDataHandler, mContext.getApplicationContext())
                                .withFileEncryption(enableEncryption)
                                .build();

                        mSession.enableCrypto(true, new ApiCallback<Void>() {
                            @Override
                            public void onNetworkError(Exception e) {
                                debug ("enableCrypto: onNetworkError",e);

                            }

                            @Override
                            public void onMatrixError(MatrixError matrixError) {
                                debug ("enableCrypto: onMatrixError", matrixError);

                            }

                            @Override
                            public void onUnexpectedError(Exception e) {
                                debug ("enableCrypto: onUnexpectedError",e);

                            }

                            @Override
                            public void onSuccess(Void aVoid) {
                                debug ("enableCrypto: onSuccess");
                                mSession.startEventStream(initialToken);
                                setState(LOGGED_IN, null);
                            }
                        });

                        mChatGroupManager.setSession(mSession);


                    }
                });
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


        setState(ImConnection.LOGGING_OUT, null);

        if (mSession.isAlive()) {
            mSession.logout(mContext, new SimpleApiCallback<Void>() {
                @Override
                public void onSuccess(Void aVoid) {

                    setState(ImConnection.DISCONNECTED, null);
                }
            });
        }
        else
        {
            setState(ImConnection.DISCONNECTED, null);
        }
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
        /**
        mDataHandler.getRoom(to).sendTypingNotification(isTyping, 3000, new ApiCallback<Void>() {
            @Override
            public void onNetworkError(Exception e) {
                debug ("sendTypingStatus:onNetworkError",e);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                debug ("sendTypingStatus:onMatrixError",e);

            }

            @Override
            public void onUnexpectedError(Exception e) {
                debug ("sendTypingStatus:onUnexpectedError",e);

            }

            @Override
            public void onSuccess(Void aVoid) {

                debug ("sendTypingStatus:onSuccess!");
            }
        });**/
    }

    @Override
    public List getFingerprints(String address) {
        return null;
    }

    @Override
    public void broadcastMigrationIdentity(String newIdentity) {

    }

    @Override
    public String sendMediaMessage(String roomId, String fileName, String mimeType, long fileSize, InputStream is, boolean doEncryption, UploadProgressListener listener) {


        final MXMediasCache mediasCache = mDataHandler.getMediasCache();

        String uploadId = roomId + ':' + fileName;

        mediasCache.uploadContent(is, fileName, mimeType, uploadId, new IMXMediaUploadListener() {
            @Override
            public void onUploadStart(String s) {

            }

            @Override
            public void onUploadProgress(String s, UploadStats uploadStats) {

            }

            @Override
            public void onUploadCancel(String s) {

            }

            @Override
            public void onUploadError(String s, int i, String s1) {

            }

            @Override
            public void onUploadComplete(final String uploadId, final String contentUri) {

            }
        });

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

                for (Room room : rooms)
                {
                    addRoomContact (room);
                }

                return null;
            }
        }.execute();
    }

    protected ChatGroup addRoomContact (final Room room)
    {
        debug ("addRoomContact: " + room.getRoomId() + " - " + room.getRoomDisplayName(mContext) + " - " + room.getNumberOfMembers());

        String subject = room.getRoomDisplayName(mContext);
        if (TextUtils.isEmpty(subject))
            subject = room.getRoomId();// room.getRoomDisplayName(mContext);

        MatrixAddress mAddr = new MatrixAddress(room.getRoomId());

        final ChatGroup group = mChatGroupManager.getChatGroup(mAddr, subject);

        room.getMembersAsync(new ApiCallback<List<RoomMember>>() {
            @Override
            public void onNetworkError(Exception e) {
                debug ("Network error syncing active members",e);
            }

            @Override
            public void onMatrixError(MatrixError matrixError) {
                debug ("Matrix error syncing active members",matrixError);
            }

            @Override
            public void onUnexpectedError(Exception e) {

                debug("Error syncing active members",e);
            }

            @Override
            public void onSuccess(List<RoomMember> roomMembers) {

                group.beginMemberUpdates();

                for (RoomMember member : roomMembers)
                {
                    debug ( "RoomMember: " + room.getRoomId() + ": " + member.getName() + " (" + member.getUserId() + ")");
                    Contact contact = mContactListManager.getContact(member.getUserId());

                    if (contact == null) {

                        contact = new Contact(new MatrixAddress(member.getUserId()), member.getName(), Imps.Contacts.TYPE_NORMAL);

                        //if this is a one-to-one room, add this person as a contact
                        if (roomMembers.size() == 2) {
                            try {
                                mContactListManager.doAddContactToListAsync(contact, null, true);
                            } catch (ImException e) {
                                Log.e(TAG, "Error adding contact to list", e);
                            }
                        }

                    }


                    if (group.getMember(member.getUserId())==null) {
                        group.notifyMemberJoined(member.getUserId(), contact);
                        group.notifyMemberRoleUpdate(contact, null, "member");
                    }

                }

                group.endMemberUpdates();

            }
        });


        return group;
    }

    protected void debug (String msg, MatrixError error)
    {
        Log.w(TAG, msg + ": " + error.errcode +  "=" + error.getMessage());

    }

    protected void debug (String msg)
    {
        Log.d(TAG, msg);
    }

    protected void debug (String msg, Exception e)
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

            boolean currentlyActive = false;
            int lastActiveAgo = -1;

            if (event.getContentAsJsonObject().has("currently_active"))
                currentlyActive = event.getContentAsJsonObject().get("currently_active").getAsBoolean();

            if (event.getContentAsJsonObject().has("last_active_ago"))
                lastActiveAgo = event.getContentAsJsonObject().get("last_active_ago").getAsInt();

        }

        @Override
        public void onAccountInfoUpdate(MyUser myUser) {
            debug ("onAccountInfoUpdate: " + myUser);

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
            debug ("onLiveEvent:type=" + event.getType());


            if (event.getType().equals(Event.EVENT_TYPE_MESSAGE))
            {
                if (!TextUtils.isEmpty(event.getSender())) {

                    String messageBody = event.getContent().getAsJsonObject().get("body").getAsString();
                    String messageType = event.getContent().getAsJsonObject().get("msgtype").getAsString();

                    debug("MESSAGE: from=" + event.getSender() + " message=" + messageBody);

                    User user = mStore.getUser(event.getSender());
//                    String deviceId = event.getContent().getAsJsonObject().get("deviceId").getAsString();
                    Room room = mStore.getRoom(event.roomId);
                    addRoomContact(room);

                    Contact contact = mContactListManager.getContact(event.sender);

                    if (contact == null) {

                        contact = new Contact(new MatrixAddress(event.getSender()), user.displayname, Imps.Contacts.TYPE_NORMAL);
                        try {
                            mContactListManager.doAddContactToListAsync(contact, null, false);
                        } catch (ImException e) {
                            e.printStackTrace();
                        }

                    }

                    if (contact != null){
                        //now pass the incoming message somewhere!

                        ChatSession session = mChatSessionManager.getSession(room.getRoomId());

                        if (session == null)
                        {
                           session = mChatSessionManager.createChatSession(contact,false);
                        }

                        Message message = new Message(messageBody);
                        message.setID(event.eventId);
                        message.setFrom(contact.getAddress());
                        message.setDateTime(new Date());//use "age"?

                        if (mDataHandler.getCrypto().isRoomEncrypted(room.getRoomId()))
                            message.setType(Imps.MessageType.INCOMING_ENCRYPTED);
                        else
                            message.setType(Imps.MessageType.INCOMING);

                        session.onReceiveMessage(message, true);
                    }
                }


            }
            else if (event.getType().equals(Event.EVENT_TYPE_PRESENCE))
            {

                debug ("PRESENCE: from=" + event.getSender() + ": " + event.getContent());

            }
            else if (event.getType().equals(Event.EVENT_TYPE_RECEIPT))
            {
                debug ("RECEIPT: from=" + event.getSender() + ": " + event.getContent());

            }

        }

        @Override
        public void onLiveEventsChunkProcessed(String s, String s1) {
            debug ("onLiveEventsChunkProcessed: " + s + ":" + s1);

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
            debug ("onEventDecrypted: " + event);
        }

        @Override
        public void onBingRulesUpdate() {

        }

        @Override
        public void onInitialSyncComplete(String s) {
            debug ("onInitialSyncComplete: " + s);
            loadStateAsync();
        }

        @Override
        public void onSyncError(MatrixError matrixError) {

            debug ("onSyncError",matrixError);
        }

        @Override
        public void onCryptoSyncComplete() {
            debug ("onCryptoSyncComplete");

        }

        @Override
        public void onNewRoom(String s) {
            debug ("onNewRoom: " + s);

            Room room = mStore.getRoom(s);
            addRoomContact(room);

        }

        @Override
        public void onJoinRoom(String s) {
            debug ("onJoinRoom: " + s);

            Room room = mStore.getRoom(s);
            addRoomContact(room);

        }

        @Override
        public void onRoomFlush(String s) {
            debug ("onRoomFlush: " + s);

        }

        @Override
        public void onRoomInternalUpdate(String s) {
            debug ("onRoomInternalUpdate: " + s);

        }

        @Override
        public void onNotificationCountUpdate(String s) {
            debug ("onNotificationCountUpdate: " + s);

        }

        @Override
        public void onLeaveRoom(String s) {
            debug ("onLeaveRoom: " + s);

        }

        @Override
        public void onRoomKick(String s) {

        }

        @Override
        public void onReceiptEvent(String s, List<String> list) {
            debug ("onReceiptEvent: " + s);

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

            debug ("onNewGroupInvitation: " + s);
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
            loadStateAsync();
        }

        @Override
        public void onGroupInvitedUsersListUpdate(String s) {

        }


        @Override
        public void onAccountDataUpdated() {
            debug ("onAccountDataUpdated!");

        }


    };


}
