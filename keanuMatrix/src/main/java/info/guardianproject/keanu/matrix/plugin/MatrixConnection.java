package info.guardianproject.keanu.matrix.plugin;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import org.json.JSONArray;
import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.crypto.IncomingRoomKeyRequest;
import org.matrix.androidsdk.crypto.IncomingRoomKeyRequestCancellation;
import org.matrix.androidsdk.crypto.MXCrypto;
import org.matrix.androidsdk.crypto.MXEncryptedAttachments;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomMediaMessage;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.store.IMXStoreListener;
import org.matrix.androidsdk.db.MXMediaCache;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.IMXMediaUploadListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.LoginRestClient;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.crypto.EncryptedFileInfo;
import org.matrix.androidsdk.rest.model.login.AuthParams;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.LoginFlow;
import org.matrix.androidsdk.rest.model.login.RegistrationFlowResponse;
import org.matrix.androidsdk.rest.model.login.RegistrationParams;
import org.matrix.androidsdk.rest.model.message.FileMessage;
import org.matrix.androidsdk.util.JsonUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import info.guardianproject.iocipher.FileInputStream;
import info.guardianproject.keanu.core.model.ChatGroup;
import info.guardianproject.keanu.core.model.ChatGroupManager;
import info.guardianproject.keanu.core.model.ChatSession;
import info.guardianproject.keanu.core.model.ChatSessionManager;
import info.guardianproject.keanu.core.model.Contact;
import info.guardianproject.keanu.core.model.ContactListManager;
import info.guardianproject.keanu.core.model.ImConnection;
import info.guardianproject.keanu.core.model.ImEntity;
import info.guardianproject.keanu.core.model.ImErrorInfo;
import info.guardianproject.keanu.core.model.ImException;
import info.guardianproject.keanu.core.model.Message;
import info.guardianproject.keanu.core.model.Presence;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.service.IChatSession;
import info.guardianproject.keanu.core.util.SecureMediaStore;
import info.guardianproject.keanu.core.util.UploadProgressListener;

import static info.guardianproject.keanu.core.KeanuConstants.DEFAULT_AVATAR_HEIGHT;
import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;

public class MatrixConnection extends ImConnection {

    private MXSession mSession;
    private MXDataHandler mDataHandler;
    private LoginRestClient mLoginRestClient;

    protected KeanuMXFileStore mStore = null;
    private Credentials mCredentials = null;

    private HomeServerConnectionConfig mConfig;

    //Registration flows
    private RegistrationFlowResponse mRegistrationFlowResponse;
    private final Set<String> mSupportedStages = new HashSet<>();
    private final List<String> mRequiredStages = new ArrayList<>();
    private final List<String> mOptionalStages = new ArrayList<>();

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

    private Handler mResponseHandler = new Handler();
    private ThreadPoolExecutor mExecutor = null;

    public MatrixConnection (Context context)
    {
        super (context);

        mContactListManager = new MatrixContactListManager(context, this);
        mChatGroupManager = new MatrixChatGroupManager(this);
        mChatSessionManager = new MatrixChatSessionManager(this);

        mExecutor = new ThreadPoolExecutor(1, 1, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>());

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

        initMatrix();

    }

    @Override
    public void networkTypeChanged() {
        super.networkTypeChanged();

        if (mSession != null)
            mSession.setIsOnline(true);
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

    private void initMatrix ()
    {
        TrafficStats.setThreadStatsTag(THREAD_ID);

        Cursor cursor = mContext.getContentResolver().query(Imps.ProviderSettings.CONTENT_URI, new String[]{Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE}, Imps.ProviderSettings.PROVIDER + "=?", new String[]{Long.toString(mProviderId)}, null);

        if (cursor == null)
            return; //not going to work

        Imps.ProviderSettings.QueryMap providerSettings = new Imps.ProviderSettings.QueryMap(
                cursor, mContext.getContentResolver(), mProviderId, false, null);

        String server = providerSettings.getServer();
        if (TextUtils.isEmpty(server))
            server = providerSettings.getDomain();

        providerSettings.close();
        if (!cursor.isClosed())
            cursor.close();

        mConfig = new HomeServerConnectionConfig.Builder()
                .withHomeServerUri(Uri.parse(HTTPS_PREPEND + server))
                .build();


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


        mLoginRestClient = new LoginRestClient(mConfig);
    }

    private void loginAsync (String password)
    {

        initMatrix();

        String username = mUser.getAddress().getUser();
        setState(ImConnection.LOGGING_IN, null);

        if (password == null)
            password = Imps.Account.getPassword(mContext.getContentResolver(), mAccountId);

        final boolean enableEncryption = true;
        final String initialToken = "";

        mLoginRestClient.loginWithUser(username, password, mDeviceName, mDeviceId, new SimpleApiCallback<Credentials>()
        {

            @Override
            public void onSuccess(Credentials credentials) {

                mCredentials = credentials;
                mConfig.setCredentials(mCredentials);
                setState(ImConnection.LOGGING_IN, null);

                mResponseHandler.post(new Runnable ()
                {
                    public void run ()
                    {

                        mSession = new MXSession.Builder(mConfig, mDataHandler, mContext.getApplicationContext())
                                .withFileEncryption(enableEncryption)
                                .build();
                        mSession.enableCryptoWhenStarting();

                        mChatGroupManager.setSession(mSession);
                        mChatSessionManager.setSession(mSession);

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
                                mSession.setIsOnline(true);
                                mDataHandler.getCrypto().start(false, new BasicApiCallback("getCrypto().start"));
                                mSession.setDeviceName(mDeviceId,mDeviceName,new BasicApiCallback("setDeviceName()"));

                                mDataHandler.getCrypto().setWarnOnUnknownDevices(false);

                            }
                        });



                    }
                });
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

        setState(ImConnection.LOGGED_IN, null);
        if (mSession != null)
            mSession.setIsOnline(true);
    }

    @Override
    public void logoutAsync() {


        logout();
    }

    @Override
    public void logout() {


        setState(ImConnection.LOGGING_OUT, null);

        if (mSession.isAlive()) {
            mSession.setIsOnline(false);
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
      //  if (mSession != null)
        //    mSession.setIsOnline(false);
    }

    @Override
    protected void setState(int state, ImErrorInfo error) {
        super.setState(state, error);

        if (mSession != null)
            mSession.setIsOnline(state != ImConnection.SUSPENDED);
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
        });
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

        /**
        Room room = mDataHandler.getRoom(roomId);

        RoomMediaMessage msg = new RoomMediaMessage();

        room.sendMediaMessage(msg, 320, 320, new RoomMediaMessage.EventCreationListener() {
            @Override
            public void onEventCreated(RoomMediaMessage roomMediaMessage) {

            }

            @Override
            public void onEventCreationFailed(RoomMediaMessage roomMediaMessage, String s) {

            }

            @Override
            public void onEncryptionFailed(RoomMediaMessage roomMediaMessage) {

            }
        });**/

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

                mContactListManager.loadContactListsAsync();

                Collection<Room> rooms = mStore.getRooms();

                for (Room room : rooms)
                {
                    ChatGroup group = addRoomContact (room);

                    mChatSessionManager.createChatSession(group,false);

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

        if (!mChatGroupManager.hasChatGroup(room.getRoomId()))
        {

        }

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
              //  group.clearMembers(true);

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


                    group.notifyMemberJoined(member.getUserId(), contact);
                    group.notifyMemberRoleUpdate(contact, "moderator", "owner");

                }

                group.endMemberUpdates();

            }
        });

        checkRoomEncryption(room);

        return group;
    }

    protected void checkRoomEncryption (Room room)
    {

        ChatSession session = mChatSessionManager.getSession(room.getRoomId());

        if (session != null) {
            boolean isEncrypted = mDataHandler.getCrypto().isRoomEncrypted(room.getRoomId());
            session.setUseEncryption(isEncrypted);
        }
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

            if (!mSession.getMediaCache().isAvatarThumbnailCached(user.getAvatarUrl(),DEFAULT_AVATAR_HEIGHT)) {
                ImageView iv = new ImageView(mContext);
                mSession.getMediaCache().loadAvatarThumbnail(mConfig, iv, user.getAvatarUrl(), DEFAULT_AVATAR_HEIGHT);
            }

            Contact contact = mContactListManager.getContact(user.user_id);

            if (contact == null) {

                contact = new Contact(new MatrixAddress(event.getSender()), user.displayname, Imps.Contacts.TYPE_NORMAL);
                try {
                    mContactListManager.doAddContactToListAsync(contact, null, false);
                } catch (ImException e) {
                    e.printStackTrace();
                }

            }

            if (currentlyActive)
                contact.setPresence(new Presence(Presence.AVAILABLE));
            else
                contact.setPresence(new Presence(Presence.OFFLINE));

            Contact[] contacts = {contact};
            mContactListManager.notifyContactsPresenceUpdated(contacts);
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
        public void onLiveEvent(final Event event, RoomState roomState) {
            debug ("onLiveEvent:type=" + event.getType());

            if (event.getType().equals(Event.EVENT_TYPE_MESSAGE))
            {
                mExecutor.execute(new Runnable ()
                {
                    public void run ()
                    {
                        handleIncomingMessage(event);
                    }
                });
            }
            else if (event.getType().equals(Event.EVENT_TYPE_PRESENCE))
            {

                debug ("PRESENCE: from=" + event.getSender() + ": " + event.getContent());
                mExecutor.execute(new Runnable ()
                {
                    public void run ()
                    {
                        handlePresence(event);
                    }
                });



            }
            else if (event.getType().equals(Event.EVENT_TYPE_RECEIPT))
            {
                debug ("RECEIPT: from=" + event.getSender() + ": " + event.getContent());

            }
            else if (event.getType().equals(Event.EVENT_TYPE_TYPING))
            {
                debug ("TYPING: from=" + event.getSender() + ": " + event.getContent());
                mExecutor.execute(new Runnable ()
                {
                    public void run ()
                    {
                        handleTyping(event);
                    }
                });
            }


        }

        @Override
        public void onLiveEventsChunkProcessed(String s, String s1) {
            debug ("onLiveEventsChunkProcessed: " + s + ":" + s1);

        }

        @Override
        public void onBingEvent(Event event, RoomState roomState, BingRule bingRule) {
            debug ("bing: " + event.toString());

            Room room = mStore.getRoom(roomState.roomId);
            if (room.isInvited())
            {
                onNewRoom(roomState.roomId);
            }

            if (!TextUtils.isEmpty(event.type)) {
                if (event.type.equals("m.room.encrypted")) {
                    mSession.getCrypto().reRequestRoomKeyForEvent(event);
                }
            }

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
            if (null != mSession.getCrypto()) {
                mSession.getCrypto().addRoomKeysRequestListener(new MXCrypto.IRoomKeysRequestListener() {
                    @Override
                    public void onRoomKeyRequest(IncomingRoomKeyRequest request) {

                        debug ("onRoomKeyRequest: " + request);
                        downloadKeys(request.mUserId,request.mDeviceId);

                    }

                    @Override
                    public void onRoomKeyRequestCancellation(IncomingRoomKeyRequestCancellation request) {
                     //  KeyRequestHandler.getSharedInstance().handleKeyRequestCancellation(request);
                        debug ("onRoomKeyRequestCancellation: " + request);

                    }
                });
            }

            loadStateAsync();
        }

        private void downloadKeys (String user, String device)
        {
            mSession.getCrypto().getDeviceList().downloadKeys(Arrays.asList(user), false, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
                @Override
                public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> devicesMap) {
                    final MXDeviceInfo deviceInfo = devicesMap.getObject(device, user);

                    if (null == deviceInfo) {
                        org.matrix.androidsdk.util.Log.e(LOG_TAG, "## displayKeyShareDialog() : No details found for device " + user + ":" + device);
                      //  onDisplayKeyShareDialogClose(false, false);
                        return;
                    }

                    if (deviceInfo.isUnknown()) {
                        mSession.getCrypto()
                                .setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED, device, user, new SimpleApiCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void info) {
                                      //  displayKeyShareDialog(session, deviceInfo, true);
                                    }
                                });
                    } else {
                     //   displayKeyShareDialog(session, deviceInfo, false);
                    }
                }

                private void onError(String errorMessage) {
                    org.matrix.androidsdk.util.Log.e(LOG_TAG, "## displayKeyShareDialog : downloadKeys failed " + errorMessage);
                 //   onDisplayKeyShareDialogClose(false, false);
                }

                @Override
                public void onNetworkError(Exception e) {
                    onError(e.getMessage());
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    onError(e.getMessage());
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    onError(e.getMessage());
                }
            });
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

            final Room room = mStore.getRoom(s);
            if (room.isInvited())
                room.join(new ApiCallback<Void>() {
                    @Override
                    public void onNetworkError(Exception e) {

                    }

                    @Override
                    public void onMatrixError(MatrixError matrixError) {

                    }

                    @Override
                    public void onUnexpectedError(Exception e) {

                    }

                    @Override
                    public void onSuccess(Void aVoid) {
                        ChatGroup group = addRoomContact(room);

                        mChatSessionManager.createChatSession(group,true);
                    }
                });


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
        public void onReceiptEvent(String roomId, List<String> list) {
            debug ("onReceiptEvent: " + roomId);

            Room room = mStore.getRoom(roomId);
            ChatSession session = mChatSessionManager.getSession(roomId);

            if (session != null) {

                for (String userId : list) {
                    //userId who got the room receipt
                    ReceiptData data = mStore.getReceipt(roomId, userId);
                    session.onMessageReceipt(data.eventId);

                }
            }
        }

        @Override
        public void onRoomTagEvent(String s) {
            debug ("onRoomTagEvent: " + s);

        }

        @Override
        public void onReadMarkerEvent(String s) {
            debug ("onReadMarkerEvent: " + s);

        }

        @Override
        public void onToDeviceEvent(Event event) {
            debug ("onToDeviceEvent: " + event);

        }

        @Override
        public void onNewGroupInvitation(final String s) {

            mResponseHandler.post (new Runnable ()
            {
                public void run ()
                {
                    debug ("onNewGroupInvitation: " + s);
                    mSession.joinRoom(s, new BasicApiCallback("onNewGroupInvitation"));
                    Room room = mStore.getRoom(s);
                    addRoomContact(room);
                }
            });

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

    private void handlePresence (Event event)
    {
        Contact contact = mContactListManager.getContact(event.getSender());

        if (contact == null) {

            User user = mStore.getUser(event.getSender());
            contact = new Contact(new MatrixAddress(event.getSender()), user.displayname, Imps.Contacts.TYPE_NORMAL);
            try {
                mContactListManager.doAddContactToListAsync(contact, null, false);
            } catch (ImException e) {
                e.printStackTrace();
            }

        }

        User user = mStore.getUser(event.getSender());
        if (user.isActive())
            contact.setPresence(new Presence(Presence.AVAILABLE));
        else
            contact.setPresence(new Presence(Presence.OFFLINE));
        Contact[] contacts = {contact};
        mContactListManager.notifyContactsPresenceUpdated(contacts);
    }

    private void handleTyping (Event event)
    {
        Contact contact = null;

        if (event.getContentAsJsonObject().has("user_ids")) {
            JsonArray userIds = event.getContentAsJsonObject().get("user_ids").getAsJsonArray();

            for (JsonElement element : userIds) {
                String userId = element.getAsString();
                if (!userId.equals(mSession.getMyUserId())) {
                    contact = mContactListManager.getContact(userId);
                    if (contact != null) {

                        /**
                        if (contact.getPresence() == null || (!contact.getPresence().isOnline())) {
                            contact.setPresence(new Presence(Presence.AVAILABLE));
                            Contact[] contacts = {contact};
                            mContactListManager.notifyContactsPresenceUpdated(contacts);
                        }**/

                        IChatSession csa = mChatSessionManager.getAdapter().getChatSession(event.roomId);
                        if (csa != null) {
                            try {
                                csa.setContactTyping(contact, true);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }

    }

    private void handleIncomingMessage (Event event)
    {
        if (!TextUtils.isEmpty(event.getSender())) {

            String messageBody = event.getContent().getAsJsonObject().get("body").getAsString();
            String messageType = event.getContent().getAsJsonObject().get("msgtype").getAsString();
            String messageMimeType = null;

            debug("MESSAGE: from=" + event.getSender() + " message=" + messageBody);

            User user = mStore.getUser(event.getSender());
            Room room = mStore.getRoom(event.roomId);

            Contact contact = mContactListManager.getContact(event.sender);

            if (contact == null) {

                contact = new Contact(new MatrixAddress(event.getSender()), user.displayname, Imps.Contacts.TYPE_NORMAL);
                try {
                    mContactListManager.doAddContactToListAsync(contact, null, false);
                } catch (ImException e) {
                    e.printStackTrace();
                }

            }

            if (messageType.equals("m.image")
                    || messageType.equals("m.file")
                    || messageType.equals("m.video")
                    || messageType.equals("m.audio"))
            {
                FileMessage fileMessage = JsonUtils.toFileMessage(event.getContent());

                String mediaUrl = fileMessage.getUrl();
                messageMimeType = fileMessage.getMimeType();

                String localFolder = contact.getAddress().getAddress();
                String localFileName = new Date().getTime() + '.' + messageBody;
                EncryptedFileInfo encryptedFileInfo = fileMessage.file;

                String downloadableUrl = mSession.getContentManager().getDownloadableUrl(mediaUrl, null != encryptedFileInfo);

                try {
                    MatrixDownloader dl = new MatrixDownloader();
                    info.guardianproject.iocipher.File fileDownload = dl.openSecureStorageFile( localFolder, localFileName);
                    OutputStream storageStream = new info.guardianproject.iocipher.FileOutputStream(fileDownload);
                    boolean downloaded = dl.get(downloadableUrl, storageStream);

                    if (encryptedFileInfo != null) {
                        InputStream decryptedIs = MXEncryptedAttachments.decryptAttachment(new FileInputStream(fileDownload), encryptedFileInfo);
                        info.guardianproject.iocipher.File fileDownloadDecrypted = dl.openSecureStorageFile( localFolder, localFileName);
                        SecureMediaStore.copyToVfs(decryptedIs, fileDownloadDecrypted.getAbsolutePath());
                        fileDownload.delete();
                        fileDownload = fileDownloadDecrypted;
                    }

                    messageBody = SecureMediaStore.vfsUri(fileDownload.getAbsolutePath()).toString();

                } catch (Exception e) {
                    debug ("Error downloading file: " + downloadableUrl,e);
                }


            }

            String subject = room.getRoomDisplayName(mContext);

            ImEntity participant = null;

            String userId = event.roomId;
            if (room.getNumberOfMembers() == 2) {
                userId = event.getSender();
                participant = contact;
            }
            else {
                participant = mChatGroupManager.getChatGroup(new MatrixAddress(event.roomId), subject);
                if (participant == null)
                    participant = addRoomContact(mStore.getRoom(event.roomId));
            }


            ChatSession session = mChatSessionManager.getSession(userId);

            if (session == null)
            {
                session = mChatSessionManager.createChatSession(participant,false);
            }

            Message message = new Message(messageBody);
            message.setID(event.eventId);
            message.setFrom(contact.getAddress());
            message.setDateTime(new Date());//use "age"?
            message.setContentType(messageMimeType);

            if (mDataHandler.getCrypto().isRoomEncrypted(event.roomId)) {
                message.setType(Imps.MessageType.INCOMING_ENCRYPTED);
            }
            else
                message.setType(Imps.MessageType.INCOMING);

            session.onReceiveMessage(message, true);

            IChatSession csa = mChatSessionManager.getAdapter().getChatSession(event.roomId);
            if (csa != null) {
                try {
                    csa.setContactTyping(contact, false);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            room.sendReadReceipt(event, new BasicApiCallback("sendReadReceipt"));
        }

    }
    /**
     * Send a registration request with the given parameters
     *
     * @param context
     * @param listener
     */
    public void register(final Context context, String username, String password, final RegistrationListener listener) {


        if (mLoginRestClient != null) {
            final RegistrationParams params = new RegistrationParams();
            params.username = username;
            params.password = password;
            params.initial_device_display_name = mDeviceName;
            params.x_show_msisdn = false;
            params.bind_email = false;
            params.bind_msisdn = false;

            AuthParams authParams = new AuthParams(LoginRestClient.LOGIN_FLOW_TYPE_DUMMY);

            params.auth = authParams;

            mLoginRestClient.register(params, new ApiCallback<Credentials>() {
                @Override
                public void onSuccess(Credentials credentials) {

                    mCredentials = credentials;
                    mConfig.setCredentials(credentials);

                    listener.onRegistrationSuccess();
                }


                @Override
                public void onNetworkError(Exception e) {
                    debug ("register:onNetworkError",e);

                }

                @Override
                public void onMatrixError(MatrixError e) {
                    if (TextUtils.equals(e.errcode, MatrixError.USER_IN_USE)) {
                        // user name is already taken, the registration process stops here (new user name should be provided)
                        // ex: {"errcode":"M_USER_IN_USE","error":"User ID already taken."}
                        org.matrix.androidsdk.util.Log.d(LOG_TAG, "User name is used");
                        listener.onRegistrationFailed(MatrixError.USER_IN_USE);
                    } else if (TextUtils.equals(e.errcode, MatrixError.UNAUTHORIZED)) {
                        // happens while polling email validation, do nothing
                    } else if (null != e.mStatus && e.mStatus == 401) {

                        try {
                            RegistrationFlowResponse registrationFlowResponse = JsonUtils.toRegistrationFlowResponse(e.mErrorBodyAsString);
                            setRegistrationFlowResponse(registrationFlowResponse);

                        } catch (Exception castExcept) {
                            org.matrix.androidsdk.util.Log.e(LOG_TAG, "JsonUtils.toRegistrationFlowResponse " + castExcept.getLocalizedMessage(), castExcept);
                        }

                        listener.onRegistrationFailed("ERROR_MISSING_STAGE");
                    } else if (TextUtils.equals(e.errcode, MatrixError.RESOURCE_LIMIT_EXCEEDED)) {
                        listener.onResourceLimitExceeded(e);
                    } else {
                       listener.onRegistrationFailed("");
                    }
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    debug ("register:onUnexpectedError",e);

                }
            });
        }
    }

    public interface RegistrationListener {
        void onRegistrationSuccess();

        void onRegistrationFailed(String message);

        void onResourceLimitExceeded(MatrixError e);
    }

    /**
     * Set the flow stages for the current home server
     *
     * @param registrationFlowResponse
     */
    private void setRegistrationFlowResponse(final RegistrationFlowResponse registrationFlowResponse) {
        if (registrationFlowResponse != null) {
            mRegistrationFlowResponse = registrationFlowResponse;
            analyzeRegistrationStages(mRegistrationFlowResponse);
        }
    }

    /**
     * Check if the given stage is supported by the current home server
     *
     * @param stage
     * @return true if supported
     */
    public boolean supportStage(final String stage) {
        return mSupportedStages.contains(stage);
    }

    /**
     * Analyze the flows stages
     *
     * @param newFlowResponse
     */
    private void analyzeRegistrationStages(final RegistrationFlowResponse newFlowResponse) {
        mSupportedStages.clear();
        mRequiredStages.clear();
        mOptionalStages.clear();

        boolean canCaptchaBeMissing = false;
        boolean canTermsBeMissing = false;
        boolean canPhoneBeMissing = false;
        boolean canEmailBeMissing = false;

        // Add all supported stage and check if some stage can be missing
        for (LoginFlow loginFlow : newFlowResponse.flows) {
            mSupportedStages.addAll(loginFlow.stages);

            if (!loginFlow.stages.contains(LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA)) {
                canCaptchaBeMissing = true;
            }

            /**
            if (!loginFlow.stages.contains(LoginRestClient.LOGIN_FLOW_TYPE_TERMS)) {
                canTermsBeMissing = true;
            }**/

            if (!loginFlow.stages.contains(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN)) {
                canPhoneBeMissing = true;
            }

            if (!loginFlow.stages.contains(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY)) {
                canEmailBeMissing = true;
            }
        }

        if (supportStage(LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA)) {
            if (canCaptchaBeMissing) {
                mOptionalStages.add(LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA);
            } else {
                mRequiredStages.add(LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA);
            }
        }

        /**
        if (supportStage(LoginRestClient.LOGIN_FLOW_TYPE_TERMS)) {
            if (canTermsBeMissing) {
                mOptionalStages.add(LoginRestClient.LOGIN_FLOW_TYPE_TERMS);
            } else {
                mRequiredStages.add(LoginRestClient.LOGIN_FLOW_TYPE_TERMS);
            }
        }**/

        if (supportStage(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY)) {
            if (canEmailBeMissing) {
                mOptionalStages.add(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY);
            } else {
                mRequiredStages.add(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY);
            }
        }

        if (supportStage(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN)) {
            if (canPhoneBeMissing) {
                mOptionalStages.add(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN);
            } else {
                mRequiredStages.add(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN);
            }
        }
    }
}
