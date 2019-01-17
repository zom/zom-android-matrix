package info.guardianproject.keanu.matrix.plugin;

import android.content.ClipData;
import android.content.Context;
import android.net.Uri;
import android.opengl.Matrix;
import android.text.TextUtils;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.crypto.MXCryptoError;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomMediaMessage;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import info.guardianproject.keanu.core.model.ChatGroup;
import info.guardianproject.keanu.core.model.ChatSession;
import info.guardianproject.keanu.core.model.ChatSessionListener;
import info.guardianproject.keanu.core.model.ChatSessionManager;
import info.guardianproject.keanu.core.model.Contact;
import info.guardianproject.keanu.core.model.ImEntity;
import info.guardianproject.keanu.core.model.Message;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.service.adapters.ChatSessionAdapter;
import info.guardianproject.keanu.core.util.UploadProgressListener;

import static info.guardianproject.keanu.core.service.RemoteImService.debug;
import static org.matrix.androidsdk.crypto.CryptoConstantsKt.MXCRYPTO_ALGORITHM_MEGOLM;

public class MatrixChatSessionManager extends ChatSessionManager {

    private MXDataHandler mDataHandler;
    private MXSession mSession;
    private HashMap<String,Room> mRoomMap;
    private MatrixConnection mConn;
    private Context mContext;

    private final static String MESSAGE_TEXT_PLAIN = "text/plain";

    public MatrixChatSessionManager (Context context, MatrixConnection conn) {
        super();
        mContext = context;
        mConn = conn;
    }

    public void setDataHandler (MXDataHandler dataHandler)
    {
        mDataHandler = dataHandler;
        mRoomMap = new HashMap<>();
    }

    public void setSession (MXSession session)
    {
        mSession = session;
    }


    public ChatSession getSession (String address)
    {
        ChatSessionAdapter adapter = mSessions.get(address);
        if (adapter != null)
            return adapter.getChatSession();
        else
            return null;
    }

    @Override
    public synchronized ChatSession createChatSession(final ImEntity participant, boolean isNewSession) {
        ChatSession session = super.createChatSession(participant, isNewSession);

        Room room =  mRoomMap.get(participant.getAddress().getAddress());
        if (room == null) {

            if (participant instanceof ChatGroup) {
                room = mDataHandler.getRoom(session.getParticipant().getAddress().getAddress());

                if (room.getNumberOfMembers() == 2)
                {
                    final Room thisRoom = room;

                    room.getMembersAsync(new ApiCallback<List<RoomMember>>() {
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
                        public void onSuccess(List<RoomMember> roomMembers) {
                            ChatSessionAdapter adapter = mSessions.get(participant.getAddress().getAddress());
                            for (RoomMember member : roomMembers)
                            {
                                mRoomMap.put(member.getUserId(),thisRoom);
                                mSessions.put(member.getUserId(),adapter);
                            }

                        }
                    });
                }

            } else if (participant instanceof Contact) {

                User user = mDataHandler.getUser(participant.getAddress().getAddress());
                if (user != null) {
                    room = findRoom(participant.getAddress().getAddress());

                    if (room == null)
                        createOneToOneRoom(session.getParticipant().getAddress().getAddress());
                }
                else
                    return null;
            }

            if (room != null) {

                mConn.checkRoomEncryption(room);

                mRoomMap.put(participant.getAddress().getAddress(), room);
            }
        }

        return session;
    }

    private Room getRoom (ChatSession session)
    {
        String userId = session.getParticipant().getAddress().getAddress();
        Room room = mRoomMap.get(userId);

        if (room == null)
        {
            if (userId.startsWith("@"))
            {
                createOneToOneRoom(userId);
            }
            else {
                room = mDataHandler.getRoom(userId);
            }

            if (room != null)
                mRoomMap.put(userId,room);
            else
            {
                //can't send, no room!
                return null;
            }
        }

        if (session.useEncryption())
        {
            boolean isEncrypted = mDataHandler.getCrypto().isRoomEncrypted(room.getRoomId());
            if (!isEncrypted)
            {
                if (!room.isEncrypted())
                    room.enableEncryptionWithAlgorithm(MXCRYPTO_ALGORITHM_MEGOLM,new BasicApiCallback("enableEncryptionWithAlgorithm"));
            }
        }

        return room;
    }

    @Override
    public void sendMessageAsync(final ChatSession session, final Message message, final ChatSessionListener listener) {

        Room room = getRoom(session);
        if (room == null)
            return;

        if (TextUtils.isEmpty(message.getContentType())||message.getContentType().equals(MESSAGE_TEXT_PLAIN)) {
            room.sendTextMessage(message.getBody(), message.getBody(), MESSAGE_TEXT_PLAIN, new RoomMediaMessage.EventCreationListener() {

                @Override
                public void onEventCreated(final RoomMediaMessage roomMediaMessage) {
                    debug("sendMessageAsync:onEventCreated: " + roomMediaMessage);

                    roomMediaMessage.setEventSendingCallback(new ApiCallback<Void>() {
                        @Override
                        public void onNetworkError(Exception e) {
                            debug("onNetworkError: sending message", e);
                            message.setType(Imps.MessageType.QUEUED);


                            if (listener != null)
                                listener.onMessageSendFail(message);
                        }

                        @Override
                        public void onMatrixError(MatrixError matrixError) {
                            debug("onMatrixError: sending message: " + matrixError);
                            message.setType(Imps.MessageType.QUEUED);

                            if (matrixError instanceof MXCryptoError) {
                                MXCryptoError mxCryptoError = (MXCryptoError) matrixError;

                                if (matrixError.errcode.equals(mxCryptoError.UNKNOWN_DEVICES_CODE)) {

                                    //TODO this just auto "knowns" all, which isn't good. we need to warn the user
                                    MXUsersDevicesMap devices = (MXUsersDevicesMap) mxCryptoError.mExceptionData;
                                    acceptUnknownDevices(devices);

                                    //now resend!
                                    sendMessageAsync(session, message, listener);
                                }
                            }

                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            debug("onUnexpectedError: sending message", e);
                            message.setType(Imps.MessageType.QUEUED);


                            if (listener != null)
                                listener.onMessageSendFail(message);
                        }

                        @Override
                        public void onSuccess(Void aVoid) {

                            debug("onSuccess: message sent: " + roomMediaMessage.getEvent().getMatrixId());

                            if (mDataHandler.getCrypto().isRoomEncrypted(room.getRoomId()))
                                message.setType(Imps.MessageType.OUTGOING_ENCRYPTED);
                            else
                                message.setType(Imps.MessageType.OUTGOING);

                            if (listener != null)
                                listener.onMessageSendSuccess(message, roomMediaMessage.getEvent().eventId);
                        }
                    });
                }

                @Override
                public void onEventCreationFailed(RoomMediaMessage roomMediaMessage, String s) {
                    debug("sendMessageAsync:onEventCreationFailed: " + s + ";" + roomMediaMessage);

                    if (listener != null)
                        listener.onMessageSendFail(message);

                }

                @Override
                public void onEncryptionFailed(RoomMediaMessage roomMediaMessage) {
                    debug("sendMessageAsync:onEncryptionFailed: " + roomMediaMessage);


                    if (listener != null)
                        listener.onMessageSendFail(message);

                }
            });
        }
        else
        {
            sendMediaMessage(session, message, listener);
        }
    }

    public void sendMediaMessage(final ChatSession session, final Message message, final ChatSessionListener listener) {

        Room room = getRoom(session);
        if (room == null)
            return;

        Uri uriMedia = Uri.parse(message.getBody());
        String mimeType = message.getContentType();

        ClipData.Item clipItemData = new ClipData.Item(uriMedia);

        RoomMediaMessage msg = new RoomMediaMessage(clipItemData, mimeType);
        msg.setMessageType("m.file");

        KeanuRoomMediaMessagesSender sender = new KeanuRoomMediaMessagesSender(mContext,mDataHandler,room);
        sender.send(msg, new RoomMediaMessage.EventCreationListener() {

            @Override
            public void onEventCreated(final RoomMediaMessage roomMediaMessage) {
                debug("sendMessageAsync:onEventCreated: " + roomMediaMessage);

                roomMediaMessage.setEventSendingCallback(new ApiCallback<Void>() {
                    @Override
                    public void onNetworkError(Exception e) {
                        debug("onNetworkError: sending message", e);
                        message.setType(Imps.MessageType.QUEUED);


                        if (listener != null)
                            listener.onMessageSendFail(message);
                    }

                    @Override
                    public void onMatrixError(MatrixError matrixError) {
                        debug("onMatrixError: sending message: " + matrixError);
                        message.setType(Imps.MessageType.QUEUED);

                        if (matrixError instanceof MXCryptoError) {
                            MXCryptoError mxCryptoError = (MXCryptoError) matrixError;

                            if (matrixError.errcode.equals(mxCryptoError.UNKNOWN_DEVICES_CODE)) {

                                //TODO this just auto "knowns" all, which isn't good. we need to warn the user
                                MXUsersDevicesMap devices = (MXUsersDevicesMap) mxCryptoError.mExceptionData;
                                acceptUnknownDevices(devices);

                                //now resend!
                                sendMessageAsync(session, message, listener);
                            }
                        }

                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        debug("onUnexpectedError: sending message", e);
                        message.setType(Imps.MessageType.QUEUED);


                        if (listener != null)
                            listener.onMessageSendFail(message);
                    }

                    @Override
                    public void onSuccess(Void aVoid) {

                        debug("onSuccess: message sent: " + roomMediaMessage.getEvent().getMatrixId());

                        if (mDataHandler.getCrypto().isRoomEncrypted(room.getRoomId()))
                            message.setType(Imps.MessageType.OUTGOING_ENCRYPTED);
                        else
                            message.setType(Imps.MessageType.OUTGOING);

                        if (listener != null)
                            listener.onMessageSendSuccess(message, roomMediaMessage.getEvent().eventId);
                    }
                });
            }

            @Override
            public void onEventCreationFailed(RoomMediaMessage roomMediaMessage, String s) {
                debug("sendMessageAsync:onEventCreationFailed: " + s + ";" + roomMediaMessage);

                if (listener != null)
                    listener.onMessageSendFail(message);

            }

            @Override
            public void onEncryptionFailed(RoomMediaMessage roomMediaMessage) {
                debug("sendMessageAsync:onEncryptionFailed: " + roomMediaMessage);


                if (listener != null)
                    listener.onMessageSendFail(message);

            }
        });

    }


    protected void acceptUnknownDevices (MXUsersDevicesMap devices)
    {

        ArrayList<MXDeviceInfo> knownDevices = new ArrayList<>();

        List<String> userIds = devices.getUserIds();

        Iterator itUserIds = userIds.iterator();
        while (itUserIds.hasNext()) {

            String userId = (String)itUserIds.next();
            List<String> deviceIds = devices.getUserDeviceIds(userId);
            Iterator itDeviceIds = deviceIds.iterator();
            while (itDeviceIds.hasNext())
            {
                String deviceId = (String)itDeviceIds.next();
                knownDevices.add((MXDeviceInfo)devices.getObject(deviceId,userId));
            }
        }

        mDataHandler.getCrypto().setDevicesKnown(knownDevices, new BasicApiCallback("setDevicesKnown"));

    }

    protected Room findRoom (String contactId)
    {

        //first see if we have a room with them already
        Collection<Room> rooms = mDataHandler.getStore().getRooms();
        for (Room room : rooms)
        {
            if (room.getNumberOfMembers() == 2)
            {
                if (room.getMember(contactId) != null)
                {
                    mRoomMap.put(contactId, room);
                    return room;
                }

            }
        }

        return null;
    }

    private void createOneToOneRoom (final String contactId)
    {

        final MatrixAddress addr = new MatrixAddress(contactId);

        mSession.createDirectMessageRoom(addr.getUser(),new ApiCallback<String>() {
            @Override
            public void onNetworkError(Exception e) {
                mConn.debug("createChatGroupAsync:onNetworkError: " + e);

            }

            @Override
            public void onMatrixError(MatrixError e) {
                mConn.debug("createChatGroupAsync:onMatrixError: " + e);

            }

            @Override
            public void onUnexpectedError(Exception e) {
                mConn.debug("createChatGroupAsync:onUnexpectedError: " + e);

            }

            @Override
            public void onSuccess(String roomId) {
                Room room = mDataHandler.getRoom(roomId);
                mRoomMap.put(contactId, room);
             //   room.updateName(addr.getUser(),new BasicApiCallback("RoomUpdate"));
                room.enableEncryptionWithAlgorithm(MXCRYPTO_ALGORITHM_MEGOLM,new BasicApiCallback("CreateRoomEncryption"));
                ChatGroup chatGroup = new ChatGroup(new MatrixAddress(roomId), room.getRoomDisplayName(mContext), mConn.getChatGroupManager());
                ChatSession session = mConn.getChatSessionManager().createChatSession(chatGroup, true);
                session.setUseEncryption(true);
                room.invite(contactId, new BasicApiCallback("RoomInvite"));
                mConn.addRoomContact(room);
            }
        });
    }

    public void enableEncryption (ChatSession session, boolean enableEncryption)
    {
        Room room = getRoom(session);

        if ((!room.isEncrypted()) && enableEncryption)
            room.enableEncryptionWithAlgorithm(MXCRYPTO_ALGORITHM_MEGOLM,new BasicApiCallback("CreateRoomEncryption"));

    }
}
