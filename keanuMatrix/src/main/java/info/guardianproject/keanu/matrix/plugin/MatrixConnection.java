package info.guardianproject.keanu.matrix.plugin;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.Handler;
import android.os.Process;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.crypto.IncomingRoomKeyRequest;
import org.matrix.androidsdk.crypto.IncomingRoomKeyRequestCancellation;
import org.matrix.androidsdk.crypto.MXCrypto;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.store.IMXStoreListener;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.LoginRestClient;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PowerLevels;
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
import org.matrix.androidsdk.rest.model.message.AudioMessage;
import org.matrix.androidsdk.rest.model.message.FileMessage;
import org.matrix.androidsdk.rest.model.message.ImageMessage;
import org.matrix.androidsdk.rest.model.message.VideoMessage;
import org.matrix.androidsdk.rest.model.search.SearchUsersResponse;
import org.matrix.androidsdk.util.JsonUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;
import info.guardianproject.iocipher.FileReader;
import info.guardianproject.keanu.core.Preferences;
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
import info.guardianproject.keanu.core.model.Invitation;
import info.guardianproject.keanu.core.model.Message;
import info.guardianproject.keanu.core.model.Presence;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.service.IChatSession;
import info.guardianproject.keanu.core.service.IContactListListener;
import info.guardianproject.keanu.core.service.adapters.ChatSessionAdapter;
import info.guardianproject.keanu.core.util.DatabaseUtils;
import info.guardianproject.keanu.core.util.Downloader;
import info.guardianproject.keanu.core.util.SecureMediaStore;
import info.guardianproject.keanu.matrix.R;

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
  //  private ThreadPoolExecutor mExecutor = null;
   // private ThreadPoolExecutor mExecutorGroups = null;
    private ExecutorService mExecutorGroups = null;
    private ExecutorService mExecutor = null;

    private final static int LARGE_GROUP_SIZE_THRESHOLD = 25;

    public MatrixConnection (Context context)
    {
        super (context);

        mContactListManager = new MatrixContactListManager(context, this);
        mChatGroupManager = new MatrixChatGroupManager(context, this);
        mChatSessionManager = new MatrixChatSessionManager(context, this);

        mExecutor = Executors.newCachedThreadPool();
        mExecutorGroups = Executors.newCachedThreadPool();

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

        initGroupLoader();
        initAvatarLoader();
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

        mExecutor.execute(() -> loginAsync (password));

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
        mConfig.forceUsageOfTlsVersions();

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
        mDataHandler.setLazyLoadingEnabled(true);

        mStore.setDataHandler(mDataHandler);

        mChatSessionManager.setDataHandler(mDataHandler);
        mChatGroupManager.setDataHandler(mDataHandler);

        mLoginRestClient = new LoginRestClient(mConfig);

    }

    private void loginAsync (String password)
    {
        android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

        String username = mUser.getAddress().getUser();
        setState(ImConnection.LOGGING_IN, null);

        if (password == null)
            password = Imps.Account.getPassword(mContext.getContentResolver(), mAccountId);

        final boolean enableEncryption = true;
        final String initialToken = Preferences.getValue(mUser.getAddress().getUser() + ".sync");

        File fileCredsJson = new File("/" + username + "/creds.json");

        if (fileCredsJson.exists()& fileCredsJson.length() > 0)
        {
            mCredentials = new Credentials();
            try {
                JsonParser parser = new JsonParser();

                String json = IOUtils.toString(new FileInputStream(fileCredsJson));
                JsonObject jCreds = parser.parse(json).getAsJsonObject();

                mCredentials = new Credentials();
                mCredentials.accessToken = jCreds.get("access_token").getAsString();
                mCredentials.deviceId = jCreds.get("device_id").getAsString();
                mCredentials.homeServer = jCreds.get("home_server").getAsString();
                if (jCreds.has("refresh_token"))
                    if (!jCreds.get("refresh_token").isJsonNull())
                        mCredentials.refreshToken = jCreds.get("refresh_token").getAsString();

                mCredentials.userId = jCreds.get("user_id").getAsString();

            } catch (Exception e) {
                debug("error reading from cred json",e);
                fileCredsJson.delete();
                loginAsync(password);
                return;
            }

            mConfig.setCredentials(mCredentials);

            initSession (enableEncryption, initialToken);
        }
        else {
            mLoginRestClient.loginWithUser(username, password, mDeviceName, mDeviceId, new SimpleApiCallback<Credentials>() {

                @Override
                public void onSuccess(Credentials credentials) {

                    setState(ImConnection.LOGGING_IN, null);

                    mCredentials = credentials;

                    try {
                        fileCredsJson.getParentFile().mkdirs();
                        String jCreds =mCredentials.toJson().toString();
                        info.guardianproject.iocipher.FileWriter writer = new info.guardianproject.iocipher.FileWriter(fileCredsJson);
                        writer.write(jCreds);
                        writer.flush();
                        writer.close();
                    } catch (IOException e) {
                        debug("error writing creds to file",e);
                    } catch (JSONException e) {
                        debug("error turning creds into json",e);
                    }

                    mCredentials.deviceId = mDeviceId;
                    mConfig.setCredentials(mCredentials);

                    initSession (enableEncryption, initialToken);

                }


                @Override
                public void onNetworkError(Exception e) {
                    super.onNetworkError(e);

                    debug("loginWithUser: OnNetworkError", e);

                    setState(ImConnection.SUSPENDED, null);

                    mResponseHandler.postDelayed(new Runnable() {
                        public void run() {
                            loginAsync(null);
                        }
                    }, 10000);

                }

                @Override
                public void onMatrixError(MatrixError e) {
                    super.onMatrixError(e);

                    debug("loginWithUser: onMatrixError: " + e.mErrorBodyAsString);

                }

                @Override
                public void onUnexpectedError(Exception e) {
                    super.onUnexpectedError(e);

                    debug("loginWithUser: onUnexpectedError", e);


                }
            });
        }
    }

    private void initSession (boolean enableEncryption, String initialToken)
    {
        mExecutor.execute(new Runnable() {
            public void run() {
                mSession = new MXSession.Builder(mConfig, mDataHandler, mContext.getApplicationContext())
                        .withFileEncryption(enableEncryption)
                        .build();

                mChatGroupManager.setSession(mSession);
                mChatSessionManager.setSession(mSession);

                mSession.startEventStream(initialToken);
                setState(LOGGED_IN, null);
                mSession.setIsOnline(true);

                mSession.enableCrypto(true, new ApiCallback<Void>() {
                    @Override
                    public void onNetworkError(Exception e) {
                        debug("getCrypto().start.onNetworkError", e);

                    }

                    @Override
                    public void onMatrixError(MatrixError matrixError) {
                        debug("getCrypto().start.onMatrixError", matrixError);

                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        debug("getCrypto().start.onUnexpectedError", e);

                    }

                    @Override
                    public void onSuccess(Void aVoid) {

                        debug("getCrypto().start.onSuccess");

                        mDataHandler.getCrypto().setWarnOnUnknownDevices(false);

                        mDataHandler.getMediaCache().clearShareDecryptedMediaCache();
                        mDataHandler.getMediaCache().clearTmpDecryptedMediaCache();

                    }
                });
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

            mSession.stopEventStream();
            mSession.isOnline();
            setState(ImConnection.DISCONNECTED, null);

            /**
            mSession.logout(mContext, new SimpleApiCallback<Void>() {
                @Override
                public void onSuccess(Void aVoid) {

                    setState(ImConnection.DISCONNECTED, null);

                }
            });**/
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
    public void sendMessageRead (String roomId, String msgId)
    {
        Event event = mStore.getEvent(msgId, roomId);
        Room room = mStore.getRoom(roomId);
        room.sendReadReceipt(event, new BasicApiCallback("sendReadReceipt"));
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

        ArrayList<String> result = null;

        List<MXDeviceInfo> devices = mDataHandler.getCrypto().getUserDevices(address);
        if (devices != null && devices.size() > 0)
        {
            result = new ArrayList<>();

            for (MXDeviceInfo device : devices)
            {
                String deviceInfo = device.displayName()
                        +'|' + device.deviceId
                        +'|' + device.fingerprint()
                        +'|' + device.isVerified();

                result.add(deviceInfo);

            }

        }


        return result;
    }

    @Override
    public void setDeviceVerified (String address, String device, boolean verified)
    {
        mDataHandler.getCrypto().setDeviceVerification(verified ? MXDeviceInfo.DEVICE_VERIFICATION_VERIFIED : MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED,
                device, address, new BasicApiCallback("setDeviceVerified"));
    }

    @Override
    public void broadcastMigrationIdentity(String newIdentity) {

    }

    @Override
    public void changeNickname(String nickname) {

        mDataHandler.getMyUser().updateDisplayName(nickname,new BasicApiCallback("changeNickname"));

    }

    private void loadStateAsync ()
    {

        mExecutorGroups.execute(() -> {

            Collection<Room> rooms = mStore.getRooms();

            for (Room room : rooms)
            {
                if (room.isMember()) {
                    addRoomContact(room);
                }

            }

            mContactListManager.loadContactListsAsync();

        });

    }

    protected ChatGroup addRoomContact (final Room room)
    {
        debug ("addRoomContact: " + room.getRoomId() + " - " + room.getRoomDisplayName(mContext) + " - " + room.getNumberOfMembers());

        MatrixAddress mAddr = new MatrixAddress(room.getRoomId());

        final ChatGroup group = mChatGroupManager.getChatGroup(mAddr);

        String subject = room.getRoomDisplayName(mContext);

        if (TextUtils.isEmpty(group.getName()))
            group.setName(subject);

        ChatSessionAdapter csa = mChatSessionManager.getChatSessionAdapter(room.getRoomId());
        if (csa == null) {

            mChatSessionManager.createChatSession(group, true);
            csa = mChatSessionManager.getChatSessionAdapter(room.getRoomId());

        }

        if (!subject.equals(mContext.getString(R.string.default_group_title)))
        {
            try {
                csa.setGroupChatSubject(subject);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        updateGroup(room, group);

        checkRoomEncryption(room);

       String downloadUrl = mSession.getContentManager().getDownloadableThumbnailUrl(room.getAvatarUrl(), DEFAULT_AVATAR_HEIGHT, DEFAULT_AVATAR_HEIGHT, "scale");

        if (!TextUtils.isEmpty(downloadUrl)) {
            if (!hasAvatar(room.getRoomId(), downloadUrl)) {
                downloadAvatar(room.getRoomId(), downloadUrl);
            }
        }

        group.setLoaded();

        return group;
    }

    protected void updateGroup (Room room)
    {
        updateGroup(room, null);
    }

    protected void updateGroup (Room room, ChatGroup group)
    {
        if (room.isInvited() || room.isMember()) {
            if (group == null) {
                group = mChatGroupManager.getChatGroup(new MatrixAddress(room.getRoomId()));
                if (TextUtils.isEmpty(group.getName()))
                    group.setName(room.getRoomDisplayName(mContext));
            }

            ChatSessionAdapter csa = mChatSessionManager.getChatSessionAdapter(room.getRoomId());
            if (csa != null)
                csa.presenceChanged(Presence.AVAILABLE);

            updateGroupMembers(room, group);



        }
    }

    protected void updateGroupMembers (final Room room, final ChatGroup group) {

        if (!lbqGroups.contains(group))
            lbqGroups.add(group);


    }

    protected void updateGroupMembersAsync (final Room room, final ChatGroup group)
    {
        android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST);

        room.getMembersAsync(new ApiCallback<List<RoomMember>>() {
            @Override
            public void onNetworkError(Exception e) {
                debug("Network error syncing active members", e);


            }

            @Override
            public void onMatrixError(MatrixError matrixError) {
                debug("Matrix error syncing active members", matrixError);


            }

            @Override
            public void onUnexpectedError(Exception e) {

                debug("Error syncing active members", e);


            }

            @Override
            public void onSuccess(final List<RoomMember> roomMembers) {

                mExecutorGroups.execute(new GroupMemberLoader(room, group, roomMembers));

            }
        });

    }

    class GroupMemberLoader implements Runnable {

        Room room;
        ChatGroup group;
        List<RoomMember> members;

        public GroupMemberLoader (Room room, ChatGroup group, List<RoomMember> members)
        {
            this.room = room;
            this.group = group;
            this.members = members;
        }

        public void run() {

            final PowerLevels powerLevels = room.getState().getPowerLevels();

            group.beginMemberUpdates();

            for (RoomMember member : members) {
                debug("RoomMember: " + room.getRoomId() + ": " + member.getName() + " (" + member.getUserId() + ")");

                Contact contact = mContactListManager.getContact(member.getUserId());

                if (contact == null) {
                    if (member.getName() != null)
                        contact = new Contact(new MatrixAddress(member.getUserId()), member.getName(), Imps.Contacts.TYPE_NORMAL);
                    else
                        contact = new Contact(new MatrixAddress(member.getUserId()));
                }

                //this is a one-to-one chat (2 people in a room), save the contact as a local one
                if (members.size() == 2) {
                    if (!member.getUserId().equals(mDataHandler.getUserId())) {
                        mContactListManager.saveContact(contact);
                    }
                }

                group.notifyMemberJoined(member.getUserId(), contact);

                if (powerLevels != null) {
                    if (powerLevels.getUserPowerLevel(member.getUserId()) > powerLevels.invite)
                        group.notifyMemberRoleUpdate(contact, "moderator", "owner");
                    else
                        group.notifyMemberRoleUpdate(contact, "member", "member");
                }

                if (members.size() < 20) {
                    String downloadUrl = mSession.getContentManager().getDownloadableThumbnailUrl(member.getUserId(), DEFAULT_AVATAR_HEIGHT, DEFAULT_AVATAR_HEIGHT, "scale");

                    if (!TextUtils.isEmpty(downloadUrl)) {
                        if (!hasAvatar(member.getUserId(), downloadUrl)) {
                            downloadAvatar(member.getUserId(), downloadUrl);
                        }
                    }
                }

            }

            group.endMemberUpdates();

        }
    };

    protected void checkRoomEncryption (Room room)
    {

        ChatSessionAdapter csa = mChatSessionManager.getChatSessionAdapter(room.getRoomId());
        if (csa != null) {
            if (mDataHandler != null && mDataHandler.getCrypto() != null) {
                boolean isEncrypted = mDataHandler.getCrypto().isRoomEncrypted(room.getRoomId());

                if (!csa.getUseEncryption() == isEncrypted)
                    csa.updateEncryptionState(isEncrypted);
            }
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

             handlePresence(event);

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
                mExecutor.execute(() -> handleIncomingMessage(event));
            }
            else if (event.getType().equals(Event.EVENT_TYPE_PRESENCE))
            {

                debug ("PRESENCE: from=" + event.getSender() + ": " + event.getContent());
                mExecutor.execute(() -> handlePresence(event));



            }
            else if (event.getType().equals(Event.EVENT_TYPE_RECEIPT))
            {
                debug ("RECEIPT: from=" + event.getSender() + ": " + event.getContent());

            }
            else if (event.getType().equals(Event.EVENT_TYPE_TYPING))
            {
                debug ("TYPING: from=" + event.getSender() + ": " + event.getContent());
                mExecutor.execute(() -> handleTyping(event));
            }
            else if (event.getType().equals(Event.EVENT_TYPE_FORWARDED_ROOM_KEY))
            {
                debug ("EVENT_TYPE_FORWARDED_ROOM_KEY: from=" + event.getSender() + ": " + event.getContent());

            }
            else if (event.getType().equals(Event.EVENT_TYPE_STICKER))
            {
                debug ("STICKER: from=" + event.getSender() + ": " + event.getContent());
                mExecutor.execute(() -> handleIncomingSticker(event));


            }
            else if (event.getType().equals(Event.EVENT_TYPE_STATE_ROOM_NAME)) {
                mExecutorGroups.execute(() -> {
                    ChatSessionAdapter csa = mChatSessionManager.getChatSessionAdapter(event.roomId);
                    if (csa != null) {
                        String newName = event.getContent().getAsJsonObject().get("name").getAsString();
                        try {
                            csa.setGroupChatSubject(newName);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }

                });
            }
            else if (event.getType().equals(Event.EVENT_TYPE_STATE_ROOM_AVATAR)) {
                mExecutorGroups.execute(() -> {
                    Room room = mStore.getRoom(event.roomId);
                    String downloadUrl = mSession.getContentManager().getDownloadableThumbnailUrl(room.getAvatarUrl(), DEFAULT_AVATAR_HEIGHT, DEFAULT_AVATAR_HEIGHT, "scale");

                    if (!TextUtils.isEmpty(downloadUrl)) {
                        if (!hasAvatar(room.getRoomId(), downloadUrl)) {
                            downloadAvatar(room.getRoomId(), downloadUrl);
                        }
                    }
                });
            }

            Preferences.setValue(mUser.getAddress().getUser() + ".sync",event.mToken);

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
                handleRoomInvite(room, event.sender);
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
            handleRoomInvite (room, s);


        }

        @Override
        public void onJoinRoom(String s) {
            debug ("onJoinRoom: " + s);

            Room room = mStore.getRoom(s);
            if (room != null)
                addRoomContact(room);

        }

        @Override
        public void onRoomFlush(String s) {
            debug ("onRoomFlush: " + s);
            Room room = mDataHandler.getRoom(s);
            if (room != null)
                updateGroup(room);
        }

        @Override
        public void onRoomInternalUpdate(String s) {
            debug ("onRoomInternalUpdate: " + s);
            Room room = mDataHandler.getRoom(s);
            if (room != null)
               updateGroup(room);
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
            Room room = mDataHandler.getRoom(s);
           // if (room != null)
            //    updateGroup(room);
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
            Room room = mStore.getRoom(s);

            if (room.isInvited())
            {
                onNewRoom(room.getRoomId());
            }

        }

        @Override
        public void onJoinGroup(String s) {

            mExecutorGroups.execute(new Runnable ()
            {
                public void run ()
                {
                    debug ("onNewGroupInvitation: " + s);
                    Room room = mStore.getRoom(s);
                    if (room != null)
                        addRoomContact(room);
                }
            });

        }

        @Override
        public void onLeaveGroup(String s) {

        }

        @Override
        public void onGroupProfileUpdate(String s) {
            Room room = mDataHandler.getRoom(s);
            if (room != null)
                updateGroup(room);
        }

        @Override
        public void onGroupRoomsListUpdate(String s) {

            debug ("onGroupRoomsListUpdate: " + s);
        }

        @Override
        public void onGroupUsersListUpdate(String s) {
            debug ("onGroupUsersListUpdate: " + s);

            Room room = mDataHandler.getRoom(s);
            if (room != null)
                updateGroup(room);
        }

        @Override
        public void onGroupInvitedUsersListUpdate(String s) {
            Room room = mDataHandler.getRoom(s);
            if (room != null)
                updateGroup(room);
        }


        @Override
        public void onAccountDataUpdated() {
            debug ("onAccountDataUpdated!");
        }


    };

    private void handlePresence (Event event)
    {

        User user = mStore.getUser(event.getSender());

        //not me!
        if (!user.user_id.equals(mDataHandler.getUserId())) {

            Contact contact = mContactListManager.getContact(user.user_id);

            if (contact != null) {

                boolean currentlyActive = false;
                int lastActiveAgo = -1;

                if (event.getContentAsJsonObject().has("currently_active"))
                    currentlyActive = event.getContentAsJsonObject().get("currently_active").getAsBoolean();

                if (event.getContentAsJsonObject().has("last_active_ago"))
                    lastActiveAgo = event.getContentAsJsonObject().get("last_active_ago").getAsInt();


                if (currentlyActive)
                    contact.setPresence(new Presence(Presence.AVAILABLE));
                else
                    contact.setPresence(new Presence(Presence.OFFLINE));

                Contact[] contacts = {contact};
                mContactListManager.notifyContactsPresenceUpdated(contacts);
            }

            String downloadUrl = mSession.getContentManager().getDownloadableThumbnailUrl(user.getAvatarUrl(), DEFAULT_AVATAR_HEIGHT, DEFAULT_AVATAR_HEIGHT, "scale");
            if (!TextUtils.isEmpty(downloadUrl)) {
                if (!hasAvatar(user.user_id, downloadUrl)) {
                    downloadAvatar(user.user_id, downloadUrl);
                }
            }
        }

    }

    private void handleTyping (Event event)
    {
        Contact contact = null;

        if (event.getContentAsJsonObject().has("user_ids")) {
            JsonArray userIds = event.getContentAsJsonObject().get("user_ids").getAsJsonArray();

            for (JsonElement element : userIds) {
                String userId = element.getAsString();
                if (!userId.equals(mSession.getMyUserId())) {

                    IChatSession csa = mChatSessionManager.getAdapter().getChatSession(event.roomId);
                    if (csa != null) {
                        try {
                            csa.setContactTyping(new Contact(new MatrixAddress(userId)), true);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }

                }
            }
        }

    }

    private void handleIncomingSticker (Event event)
    {
        /**
         *
         * {
         *   "age" : null,
         *   "content": {
         *     "body": "A hastily-rendered stick figure stands with arms in the air beneath three blue-and-white juggling balls apparently in motion. We cannot tell whether the figure is juggling competently or has simply thrown all three balls into the air and is awaiting the inevitable. The figure's mouth is formed into an enigmatic 'o'.",
         *     "info": {"h":200,"mimetype":"image/png","size":30170,"thumbnail_info":{"h":200,"mimetype":"image/png","size":30170,"w":88},"thumbnail_url":"mxc://matrix.org/mQEotjwsEKeZivqIfZjxNfgC","w":88},
         *     "url": "mxc://matrix.org/mQEotjwsEKeZivqIfZjxNfgC",
         *   },
         *   "eventId": "$154896906639558KPSVT:matrix.org",
         *   "originServerTs": 1548969066195,
         *   "roomId": "!LprBecrICHRvlyHpsO:matrix.org",
         *   "type": "m.sticker",
         *   "userId": "null",
         *   "sender": "@n8fr8:matrix.org",
         * }
         *
         *  Sent state : SENT
         */
        if (!TextUtils.isEmpty(event.getSender())) {

            debug("MESSAGE: room=" + event.roomId + " from=" + event.getSender() + " event=" + event.toString());

            String stickerAlt = null;
            String stickerUrl = null;
            String stickerType = "image/png";
            String timeStamp = null;

            if (event.getContent().getAsJsonObject().has("body"))
                stickerAlt = event.getContent().getAsJsonObject().get("body").getAsString();

            if (event.getContent().getAsJsonObject().has("url"))
                stickerUrl = event.getContent().getAsJsonObject().get("url").getAsString();
            else  if (event.getContent().getAsJsonObject().has("sticker_pack")) {
                stickerUrl = "asset://stickers/" + event.getContent().getAsJsonObject().get("sticker_pack").getAsString()
                        + "/" + event.getContent().getAsJsonObject().get("sticker_name").getAsString() + ".png";

            }

            if (!TextUtils.isEmpty(stickerUrl)) {

                Room room = mStore.getRoom(event.roomId);

                if (room != null && room.isMember()) {

                    MatrixAddress addrSender = new MatrixAddress(event.sender);
                    Uri uriSticker = Uri.parse(stickerUrl);

                    String localFolder = addrSender.getAddress();
                    String localFileName = new Date().getTime() + '.' + uriSticker.getPath();
                    String downloadableUrl = mSession.getContentManager().getDownloadableUrl(stickerUrl, false);

                    if (uriSticker.getScheme().equals("mxc")) {
                        try {
                            MatrixDownloader dl = new MatrixDownloader();
                            info.guardianproject.iocipher.File fileDownload = dl.openSecureStorageFile(localFolder, localFileName);
                            OutputStream storageStream = new info.guardianproject.iocipher.FileOutputStream(fileDownload);
                            boolean downloaded = dl.get(downloadableUrl, storageStream);
                            stickerAlt = SecureMediaStore.vfsUri(fileDownload.getAbsolutePath()).toString();

                        } catch (Exception e) {
                            debug("Error downloading file: " + downloadableUrl, e);
                        }
                    }
                    else if (uriSticker.getScheme().equals("asset"))
                    {
                        //what do we do?
                    }

                    if (!TextUtils.isEmpty(stickerAlt)) {
                        ChatSession session = mChatSessionManager.getSession(event.roomId);

                        if (session == null) {
                            ImEntity participant = mChatGroupManager.getChatGroup(new MatrixAddress(event.roomId));
                            session = mChatSessionManager.createChatSession(participant, false);
                        }

                        Message message = new Message(stickerAlt);
                        message.setID(event.eventId);
                        message.setFrom(addrSender);
                        message.setDateTime(new Date(event.originServerTs));//use "age"?
                        message.setContentType(stickerType);

                        if (mDataHandler.getCrypto().isRoomEncrypted(event.roomId)) {
                            message.setType(Imps.MessageType.INCOMING_ENCRYPTED);
                        } else
                            message.setType(Imps.MessageType.INCOMING);

                        session.onReceiveMessage(message, true);

                        IChatSession csa = mChatSessionManager.getAdapter().getChatSession(event.roomId);
                        if (csa != null) {
                            try {
                                csa.setContactTyping(new Contact(addrSender), false);
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

            debug("MESSAGE: room=" + event.roomId + " from=" + event.getSender() + " event=" + event.toString());

            String messageBody = null;

            if (event.getContent().getAsJsonObject().has("body"))
                messageBody = event.getContent().getAsJsonObject().get("body").getAsString();

            if (TextUtils.isEmpty(messageBody)) {
                debug("WARN: MESSAGE HAS NO BODY: " + event.toString());
                return;
            }

            Room room = mStore.getRoom(event.roomId);

            if (room != null && room.isMember()) {

                ChatSession session = mChatSessionManager.getSession(event.roomId);

                if (session == null) {
                    ImEntity participant = mChatGroupManager.getChatGroup(new MatrixAddress(event.roomId));
                    session = mChatSessionManager.createChatSession(participant, false);
                }

                ChatSessionAdapter csa = mChatSessionManager.getChatSessionAdapter(event.roomId);

                if (!csa.doesMessageExist(event.eventId)) {
                    MatrixAddress addrSender = new MatrixAddress(event.sender);

                    Message message = downloadMedia (event, messageBody, addrSender);

                    if (message == null) {
                        message = new Message(messageBody);
                        message.setID(event.eventId);
                        message.setFrom(addrSender);
                        message.setDateTime(new Date(event.getOriginServerTs()));
                    }

                    if (mDataHandler.getCrypto().isRoomEncrypted(event.roomId)) {
                        message.setType(Imps.MessageType.INCOMING_ENCRYPTED);
                    } else
                        message.setType(Imps.MessageType.INCOMING);

                    session.onReceiveMessage(message, (!addrSender.mAddress.equals(mUser.getAddress().getAddress())));

                    csa.setContactTyping(new Contact(addrSender), false);

                }
            }
        }

    }

    private Message downloadMedia (Event event, String localFileName, MatrixAddress addrSender)
    {
        String messageType = event.getContent().getAsJsonObject().get("msgtype").getAsString();
        String messageMimeType = null;

        Message message = null;

        if (messageType.equals("m.image")
                || messageType.equals("m.file")
                || messageType.equals("m.video")
                || messageType.equals("m.audio")) {

            String result = null;
            String mediaUrl = null;
            String thumbUrl = null;
            String localFolder = addrSender.getAddress();
            EncryptedFileInfo encryptedFileInfo = null;
            EncryptedFileInfo encryptedThumbInfo = null;

            if (messageType.equals("m.file")) {
                FileMessage fileMessage = JsonUtils.toFileMessage(event.getContent());
                messageMimeType = fileMessage.getMimeType();
                mediaUrl = fileMessage.getUrl();
                encryptedFileInfo = fileMessage.file;
            }
            else if (messageType.equals("m.image"))
            {
                ImageMessage fileMessage = JsonUtils.toImageMessage(event.getContent());
                messageMimeType = fileMessage.getMimeType();
                mediaUrl = fileMessage.getUrl();
                encryptedFileInfo = fileMessage.file;
            }
            else if (messageType.equals("m.audio"))
            {
                AudioMessage fileMessage = JsonUtils.toAudioMessage(event.getContent());
                messageMimeType = fileMessage.getMimeType();
                mediaUrl = fileMessage.getUrl();
                encryptedFileInfo = fileMessage.file;
            }
            else if (messageType.equals("m.video"))
            {
                VideoMessage fileMessage = JsonUtils.toVideoMessage(event.getContent());
                messageMimeType = fileMessage.getMimeType();
                mediaUrl = fileMessage.getUrl();
                encryptedFileInfo = fileMessage.file;
                thumbUrl = fileMessage.getThumbnailUrl();

                if (fileMessage.info != null)
                    encryptedThumbInfo = fileMessage.info.thumbnail_file;

            }


            MatrixDownloader dl = new MatrixDownloader();
            info.guardianproject.iocipher.File fileDownload = null;


            try {
                boolean isEncrypted = null != encryptedFileInfo;

                String downloadableUrl = mSession.getContentManager().getDownloadableUrl(mediaUrl, isEncrypted);

                if (isEncrypted)
                    fileDownload = dl.openSecureStorageFile(localFolder, localFileName + ".encrypted");
                else
                    fileDownload = dl.openSecureStorageFile(localFolder, localFileName);

                OutputStream storageStream = new info.guardianproject.iocipher.FileOutputStream(fileDownload);
                boolean downloaded = dl.get(downloadableUrl, storageStream);

                if (downloaded) {

                    if (isEncrypted) {
                        info.guardianproject.iocipher.File fileDownloadDecrypted = dl.openSecureStorageFile(localFolder, localFileName);
                        storageStream = new info.guardianproject.iocipher.FileOutputStream(fileDownloadDecrypted);
                        boolean success = MatrixDownloader.decryptAttachment(new FileInputStream(fileDownload), encryptedFileInfo, storageStream);
                        fileDownload.delete();
                        fileDownload = fileDownloadDecrypted;
                        result = SecureMediaStore.vfsUri(fileDownload.getAbsolutePath()).toString();
                    }
                    else
                    {
                        result = SecureMediaStore.vfsUri(fileDownload.getAbsolutePath()).toString();
                    }
                }

                try {
                    downloadableUrl = mSession.getContentManager().getDownloadableUrl(thumbUrl, isEncrypted);


                    info.guardianproject.iocipher.File fileDownloadThumb;

                    if (isEncrypted)
                        fileDownloadThumb = dl.openSecureStorageFile(localFolder, localFileName + ".thumb.encrypted");
                    else
                        fileDownloadThumb = dl.openSecureStorageFile(localFolder, localFileName + ".thumb.jpg");

                    storageStream = new info.guardianproject.iocipher.FileOutputStream(fileDownloadThumb);
                    downloaded = dl.get(downloadableUrl, storageStream);

                    if (downloaded) {

                        if (isEncrypted) {
                            storageStream = new info.guardianproject.iocipher.FileOutputStream(new File(fileDownload.getAbsolutePath() + ".thumb.jpg"));
                            boolean success = MatrixDownloader.decryptAttachment(new FileInputStream(fileDownloadThumb), encryptedThumbInfo, storageStream);
                            fileDownloadThumb.delete();
                        }
                    }

                } catch (Exception e) {
                    debug("Error downloading file: " + thumbUrl, e);
                }

                if (result != null) {
                    message = new Message(result);
                    message.setID(event.eventId);
                    message.setFrom(addrSender);
                    message.setDateTime(new Date(event.getOriginServerTs()));
                    message.setContentType(messageMimeType);
                }

            } catch (Exception e) {
                debug("Error downloading file: " + mediaUrl, e);
            }


        }

        return message;
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
                    listener.onRegistrationFailed("Network error occured: " + e.toString());

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
                       listener.onRegistrationFailed(e.toString());
                    }
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    debug ("register:onUnexpectedError",e);
                    listener.onRegistrationFailed(e.toString());
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

    private void downloadAvatar (final String address, final String url)
    {
        lbqAvatars.add(new Pair<>(address, url));
    }

    private void downloadAvatarAsync (final String address, final String url)
    {
        mExecutorGroups.execute(() -> {
            boolean hasAvatar = DatabaseUtils.doesAvatarHashExist(mContext.getContentResolver(),Imps.Avatars.CONTENT_URI,address,url);

            if (!hasAvatar) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try {
                    new Downloader().get(url, baos);

                    if (baos != null && baos.size() > 0)
                        setAvatar(address, baos.toByteArray(), url);

                } catch (Exception e) {
                    debug("downloadAvatar error",e);
                }
            }
        });


    }

    private boolean hasAvatar (String address, String downloadUrl)
    {
        return DatabaseUtils.doesAvatarHashExist(mContext.getContentResolver(),Imps.Avatars.CONTENT_URI,address,downloadUrl);
    }

    private void setAvatar(String address, byte[] avatarBytesCompressed, String avatarHash) {

        try {

            int rowsUpdated = DatabaseUtils.updateAvatarBlob(mContext.getContentResolver(), Imps.Avatars.CONTENT_URI, avatarBytesCompressed, address);

            if (rowsUpdated <= 0)
                DatabaseUtils.insertAvatarBlob(mContext.getContentResolver(), Imps.Avatars.CONTENT_URI, mProviderId, mAccountId, avatarBytesCompressed, avatarHash, address);

        } catch (Exception e) {
            Log.w(LOG_TAG, "error loading image bytes", e);
        }
    }

    @Override
    public void searchForUser (String search, IContactListListener listener)
    {
        mSession.searchUsers(search, 10, null, new ApiCallback<SearchUsersResponse>() {
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
            public void onSuccess(SearchUsersResponse searchUsersResponse) {

                if (searchUsersResponse != null && searchUsersResponse.results != null) {

                    Contact[] contacts = new Contact[searchUsersResponse.results.size()];
                    int i = 0;
                    for (User user : searchUsersResponse.results) {
                        contacts[i++] = new Contact(new MatrixAddress(user.user_id),user.displayname,Imps.Contacts.TYPE_NORMAL);
                    }

                    if (listener != null)
                    {
                        try {
                            listener.onContactsPresenceUpdate(contacts);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }


            }
        });
    }

    private void handleRoomInvite (Room room, String sender)
    {
        MatrixAddress addrRoom = new MatrixAddress(room.getRoomId());
        MatrixAddress addrSender = null;

        if (!TextUtils.isEmpty(sender))
            addrSender = new MatrixAddress(sender);

        if (room.isInvited())
        {
            Invitation invite = new Invitation(room.getRoomId(),addrRoom,addrSender,room.getRoomDisplayName(mContext));
            mChatGroupManager.notifyGroupInvitation(invite);

            ChatGroup participant = mChatGroupManager.getChatGroup(addrRoom);
            if (TextUtils.isEmpty(participant.getName()))
                participant.setName(room.getRoomDisplayName(mContext));

            participant.setJoined(false);
            ChatSession session = mChatSessionManager.createChatSession(participant, true);
            ChatSessionAdapter csa = mChatSessionManager.getChatSessionAdapter(room.getRoomId());
            csa.setLastMessage(mContext.getString(R.string.room_invited));

        }
        else if (room.isMember())
        {
            ChatSession session = mChatSessionManager.getSession(room.getRoomId());

            if (session == null) {
                ChatGroup participant =(ChatGroup) mChatGroupManager.getChatGroup(addrRoom);
                if (TextUtils.isEmpty(participant.getName()))
                    participant.setName(room.getRoomDisplayName(mContext));
                session = mChatSessionManager.createChatSession(participant, true);
            }
        }
    }

    LinkedBlockingQueue<ChatGroup> lbqGroups = new LinkedBlockingQueue<>();
    Timer mTimerGroupLoader;

    private void initGroupLoader () {
        mTimerGroupLoader = new Timer();

        mTimerGroupLoader.schedule(new TimerTask() {

            public void run() {

                while (lbqGroups.peek() != null) {
                    ChatGroup group = lbqGroups.poll();
                    Room room = mDataHandler.getRoom(group.getAddress().getAddress());

                    mExecutorGroups.execute(() -> updateGroupMembersAsync(room,group));
                }

            }

        }, 0, 10000);
    }

    LinkedBlockingQueue<Pair<String,String>> lbqAvatars = new LinkedBlockingQueue<>();
    Timer mTimerAvatarLoader;

    private void initAvatarLoader () {
        mTimerAvatarLoader = new Timer();

        mTimerAvatarLoader.schedule(new TimerTask() {

            public void run() {

                int maxLoad = 5;
                int i = 0;

                while (lbqAvatars.peek() != null && i++ < maxLoad) {
                    Pair<String,String> pair = lbqAvatars.poll();
                    downloadAvatarAsync(pair.first, pair.second);
                }

            }

        }, 0, 10000);
    }

}
