package info.guardianproject.keanu.matrix.plugin;

import android.content.ClipData;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.crypto.MXCryptoError;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomMediaMessage;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomDirectoryVisibility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import info.guardianproject.keanu.core.model.ChatGroup;
import info.guardianproject.keanu.core.model.ChatSession;
import info.guardianproject.keanu.core.model.ChatSessionListener;
import info.guardianproject.keanu.core.model.ChatSessionManager;
import info.guardianproject.keanu.core.model.ImEntity;
import info.guardianproject.keanu.core.model.Message;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.service.adapters.ChatSessionAdapter;

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
    public ChatSession createChatSession(final ImEntity participant, boolean isNewSession) {
        ChatSession session = super.createChatSession(participant, isNewSession);

        String uid = participant.getAddress().getAddress();

        Room room =  mRoomMap.get(uid);
        if (room == null) {
            if (participant instanceof ChatGroup) {
                room = mDataHandler.getRoom(uid);

                if (room != null) {
                    mConn.checkRoomEncryption(room);
                    mRoomMap.put(uid, room);
                }
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
            room = mDataHandler.getRoom(userId);

            if (room != null)
                mRoomMap.put(userId,room);
            else
            {
                //can't send, no room!
                return null;
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

            Event eventReplyTo = null;

            if (!TextUtils.isEmpty(message.getReplyId()))
            {
                eventReplyTo = mSession.getDataHandler().getStore().getEvent(message.getReplyId(),room.getRoomId());
            }

            // Is it a quick reaction?
            if (eventReplyTo != null && message.getBody() != null && isSingleEmoji(message.getBody())) {
                sendReaction(room, eventReplyTo, session, message, listener);
            } else {
                sendMessageWithRoomAndReply(room, eventReplyTo, session, message, listener);
            }
        }
        else
        {
            sendMediaMessage(session, message, listener);
        }
    }

    // Code adapted from iOS
    private boolean isSingleEmoji(String s) {
        if (TextUtils.isEmpty(s)) {
            return false;
        }

        boolean isEmoji = false;

        char hs = s.charAt(0);

        // Surrogate pair
        if (0xd800 <= hs &&
                hs <= 0xdbff) {
            if (s.length() > 1) {
                char ls = s.charAt(1);
                int uc = ((hs - 0xd800) * 0x400) + (ls - 0xdc00) + 0x10000;
                if (0x1d000 <= uc &&
                        uc <= 0x1f9ff) {
                    isEmoji = true;
                }
            }
        } else if (s.length() > 1) {
            char ls = s.charAt(1);
            if (ls == 0x20e3 ||
                    ls == 0xfe0f ||
                    ls == 0xd83c) {
                isEmoji = true;
            }
        } else {
            // Non surrogate
            if (0x2100 <= hs &&
                    hs <= 0x27ff) {
                isEmoji = true;
            } else if (0x2B05 <= hs &&
                    hs <= 0x2b07) {
                isEmoji = true;
            } else if (0x2934 <= hs &&
                    hs <= 0x2935) {
                isEmoji = true;
            } else if (0x3297 <= hs &&
                    hs <= 0x3299) {
                isEmoji = true;
            } else if (hs == 0xa9 ||
                    hs == 0xae ||
                    hs == 0x303d ||
                    hs == 0x3030 ||
                    hs == 0x2b55 ||
                    hs == 0x2b1c ||
                    hs == 0x2b1b ||
                    hs == 0x2b50) {
                isEmoji = true;
            }
        }
        return isEmoji;
    }

    private void sendMessageWithRoomAndReply (Room room, Event replyToEvent, final ChatSession session, final Message message, final ChatSessionListener listener)
    {
//        int cpCount = message.getBody().codePointCount(0, message.getBody().length());
//        boolean isQuickReaction = (message.getReplyId() != null && cpCount > 0) /* && isEmoji */;
//
//        if (isQuickReaction) {
//            message.setContentType("m.reaction");
//        }

        room.sendTextMessage(message.getBody(), null, MESSAGE_TEXT_PLAIN, replyToEvent, new RoomMediaMessage.EventCreationListener() {

            @Override
            public void onEventCreated(final RoomMediaMessage roomMediaMessage) {

                String tempMsgId = roomMediaMessage.getEvent().eventId;

                debug("sendMessageAsync:onEventCreated: " + tempMsgId);

                if (listener != null)
                    listener.onMessageSendQueued(message,  tempMsgId);

                roomMediaMessage.setEventSendingCallback(new ApiCallback<Void>() {



                    @Override
                    public void onNetworkError(Exception e) {
                        debug("onNetworkError: sending message", e);
                        message.setType(Imps.MessageType.QUEUED);
                        String finalMsgId = roomMediaMessage.getEvent().eventId;

                        if (listener != null)
                            listener.onMessageSendFail(message,  finalMsgId);
                    }

                    @Override
                    public void onMatrixError(MatrixError matrixError) {
                        debug("onMatrixError: sending message: " + matrixError);
                        message.setType(Imps.MessageType.QUEUED);
                        String finalMsgId = roomMediaMessage.getEvent().eventId;

                        if (listener != null)
                            listener.onMessageSendFail(message, finalMsgId);

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

                        String finalMsgId = roomMediaMessage.getEvent().eventId;


                        if (listener != null)
                            listener.onMessageSendFail(message, finalMsgId);
                    }

                    @Override
                    public void onSuccess(Void aVoid) {
                        String finalMsgId = roomMediaMessage.getEvent().eventId;

                        debug("onSuccess: message sent: " + finalMsgId);


                        if (mDataHandler.getRoom(room.getRoomId()).isEncrypted())
                            message.setType(Imps.MessageType.OUTGOING_ENCRYPTED);
                        else
                            message.setType(Imps.MessageType.OUTGOING);

                        if (listener != null)
                            listener.onMessageSendSuccess(message, finalMsgId);
                    }
                });

//                if (isQuickReaction) {
//                    roomMediaMessage.setMessageType("m.reaction");
//                    roomMediaMessage.getEvent().setType("m.reaction");
//                    JsonObject newObject = new JsonObject();
//                    newObject.addProperty("event_id", message.getReplyId());
//                    newObject.addProperty("rel_type", "m.annotation");
//                    newObject.addProperty("key", message.getBody());
//                    JsonObject content = new JsonObject();
//                    content.add("m.relates_to", newObject);
//                    roomMediaMessage.getEvent().contentJson = content;
//                }
            }

            @Override
            public void onEventCreationFailed(RoomMediaMessage roomMediaMessage, String s) {
                debug("sendMessageAsync:onEventCreationFailed: " + s + ";" + roomMediaMessage);

                if (listener != null)
                    listener.onMessageSendFail(message, roomMediaMessage.getEvent().eventId);

            }

            @Override
            public void onEncryptionFailed(RoomMediaMessage roomMediaMessage) {
                debug("sendMessageAsync:onEncryptionFailed: " + roomMediaMessage);


                if (listener != null)
                    listener.onMessageSendFail(message, roomMediaMessage.getEvent().eventId);

            }


        });
    }

    private void sendReaction (Room room, Event replyToEvent, final ChatSession session, final Message message, final ChatSessionListener listener)
    {
//        int cpCount = message.getBody().codePointCount(0, message.getBody().length());
//        boolean isQuickReaction = (message.getReplyId() != null && cpCount > 0) /* && isEmoji */;
//
//        if (isQuickReaction) {
//            message.setContentType("m.reaction");
//        }
        
        room.sendTextMessage(message.getBody(), null, MESSAGE_TEXT_PLAIN, replyToEvent, "m.reaction", new RoomMediaMessage.EventCreationListener() {

            @Override
            public void onEventCreated(final RoomMediaMessage roomMediaMessage) {

                String tempMsgId = roomMediaMessage.getEvent().eventId;

                debug("sendMessageAsync:onEventCreated: " + tempMsgId);

                roomMediaMessage.setMessageType("m.reaction");
                roomMediaMessage.getEvent().setType("m.reaction");
                JsonObject newObject = new JsonObject();
                newObject.addProperty("event_id", message.getReplyId());
                newObject.addProperty("rel_type", "m.annotation");
                newObject.addProperty("key", message.getBody());
                JsonObject content = new JsonObject();
                content.add("m.relates_to", newObject);
                roomMediaMessage.getEvent().contentJson = content;

                if (listener != null)
                    listener.onMessageSendQueued(message,  tempMsgId);

                roomMediaMessage.setEventSendingCallback(new ApiCallback<Void>() {



                    @Override
                    public void onNetworkError(Exception e) {
                        debug("onNetworkError: sending message", e);
                        message.setType(Imps.MessageType.QUEUED);
                        String finalMsgId = roomMediaMessage.getEvent().eventId;

                        if (listener != null)
                            listener.onMessageSendFail(message,  finalMsgId);
                    }

                    @Override
                    public void onMatrixError(MatrixError matrixError) {
                        debug("onMatrixError: sending message: " + matrixError);
                        message.setType(Imps.MessageType.QUEUED);
                        String finalMsgId = roomMediaMessage.getEvent().eventId;

                        if (listener != null)
                            listener.onMessageSendFail(message, finalMsgId);

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

                        String finalMsgId = roomMediaMessage.getEvent().eventId;


                        if (listener != null)
                            listener.onMessageSendFail(message, finalMsgId);
                    }

                    @Override
                    public void onSuccess(Void aVoid) {
                        String finalMsgId = roomMediaMessage.getEvent().eventId;

                        debug("onSuccess: message sent: " + finalMsgId);


                        if (mDataHandler.getRoom(room.getRoomId()).isEncrypted())
                            message.setType(Imps.MessageType.OUTGOING_ENCRYPTED);
                        else
                            message.setType(Imps.MessageType.OUTGOING);

                        if (listener != null)
                            listener.onMessageSendSuccess(message, finalMsgId);
                    }
                });


            }

            @Override
            public void onEventCreationFailed(RoomMediaMessage roomMediaMessage, String s) {
                debug("sendMessageAsync:onEventCreationFailed: " + s + ";" + roomMediaMessage);

                if (listener != null)
                    listener.onMessageSendFail(message, roomMediaMessage.getEvent().eventId);

            }

            @Override
            public void onEncryptionFailed(RoomMediaMessage roomMediaMessage) {
                debug("sendMessageAsync:onEncryptionFailed: " + roomMediaMessage);


                if (listener != null)
                    listener.onMessageSendFail(message, roomMediaMessage.getEvent().eventId);

            }


        });
    }

    public void sendMediaMessage(final ChatSession session, final Message message, final ChatSessionListener listener) {

        Room room = getRoom(session);
        if (room == null)
            return;

        Uri uriMedia = Uri.parse(message.getBody());
        String mimeType = message.getContentType();

        ClipData.Item clipItemData = new ClipData.Item(uriMedia);

        RoomMediaMessage msg = new RoomMediaMessage(clipItemData, mimeType);

        if (mimeType.startsWith("image"))
            msg.setMessageType("m.image");
        else if (mimeType.startsWith("audio"))
            msg.setMessageType("m.audio");
        else if (mimeType.startsWith("video"))
            msg.setMessageType("m.video");
        else
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
                            listener.onMessageSendFail(message, roomMediaMessage.getEvent().eventId);
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
                                //sendMessageAsync(session, message, listener);
                                if (listener != null)
                                    listener.onMessageSendFail(message, roomMediaMessage.getEvent().eventId);
                            }
                        }

                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        debug("onUnexpectedError: sending message", e);
                        message.setType(Imps.MessageType.QUEUED);

                        if (listener != null)
                            listener.onMessageSendFail(message,  roomMediaMessage.getEvent().eventId);
                    }

                    @Override
                    public void onSuccess(Void aVoid) {

                        debug("onSuccess: message sent: " + roomMediaMessage.getEvent().eventId);

                        if (mDataHandler.getRoom(room.getRoomId()).isEncrypted())
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
                    listener.onMessageSendFail(message, roomMediaMessage.getEvent().eventId);

            }

            @Override
            public void onEncryptionFailed(RoomMediaMessage roomMediaMessage) {
                debug("sendMessageAsync:onEncryptionFailed: " + roomMediaMessage);


                if (listener != null)
                    listener.onMessageSendFail(message, roomMediaMessage.getEvent().eventId);

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

    /**
    private void createOneToOneRoom (final String contactId)
    {

        mSession.createDirectMessageRoom(contactId,new ApiCallback<String>() {
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
    }**/

    public void enableEncryption (ChatSession session, boolean enableEncryption)
    {
        Room room = getRoom(session);

        if ((!room.isEncrypted()) && enableEncryption)
            room.enableEncryptionWithAlgorithm(MXCRYPTO_ALGORITHM_MEGOLM,new BasicApiCallback("CreateRoomEncryption"));

    }

    public String getPublicAddress (ChatSession session)
    {

        Room room = getRoom(session);

        List<String> listAlias = room.getAliases();

        if (listAlias != null && (!listAlias.isEmpty()))
        {
            return listAlias.get(0);
        }

        return null;
    }

    public void setPublic (ChatSession session, boolean isPublic)
    {
        Room room = getRoom(session);

        if (isPublic)
        {
            List<String> listAlias = room.getAliases();
            if (listAlias == null || listAlias.isEmpty())
            {
                String alias = "#" + room.getRoomId().substring(1);
                room.addAlias(alias,new BasicApiCallback("setAlias"));
                room.updateCanonicalAlias(alias,new BasicApiCallback("setAlias"));

            }

            room.updateJoinRules(RoomState.JOIN_RULE_PUBLIC, new BasicApiCallback("updateJoinRules"));
            room.updateGuestAccess(RoomState.GUEST_ACCESS_CAN_JOIN, new BasicApiCallback("updateGuestAccess"));

        }
        else
        {
            room.updateJoinRules(RoomState.JOIN_RULE_INVITE, new BasicApiCallback("updateJoinRules"));
            room.updateGuestAccess(RoomState.GUEST_ACCESS_FORBIDDEN, new BasicApiCallback("updateGuestAccess"));
            room.updateDirectoryVisibility(RoomDirectoryVisibility.DIRECTORY_VISIBILITY_PRIVATE , new BasicApiCallback("updateDirectoryVisibility"));

        }
    }
}
