package info.guardianproject.keanu.matrix.plugin;

import android.opengl.Matrix;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.crypto.MXCryptoError;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomMediaMessage;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import info.guardianproject.keanu.core.model.ChatGroup;
import info.guardianproject.keanu.core.model.ChatSession;
import info.guardianproject.keanu.core.model.ChatSessionManager;
import info.guardianproject.keanu.core.model.Contact;
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

    private final static String MESSAGE_TEXT_PLAIN = "text/plain";

    public MatrixChatSessionManager (MatrixConnection conn) {
        super();
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
    public synchronized ChatSession createChatSession(ImEntity participant, boolean isNewSession) {
        ChatSession session = super.createChatSession(participant, isNewSession);

        Room room = null;

        if (participant instanceof ChatGroup) {
            room = mDataHandler.getRoom(session.getParticipant().getAddress().getAddress());

        }
        else if (participant instanceof Contact)
        {
            createOneToOneRoom(session.getParticipant().getAddress().getAddress());
        }

        mRoomMap.put(participant.getAddress().getAddress(),room);

        return session;
    }

    @Override
    public void sendMessageAsync(final ChatSession session, final Message message) {

        Room room = mRoomMap.get(session.getParticipant().getAddress().getAddress());

        if (room == null)
        {
            room = mDataHandler.getRoom(session.getParticipant().getAddress().getAddress());

            if (room != null)
                mRoomMap.put(session.getParticipant().getAddress().getAddress(),room);
            else
            {
                //can't send, no room!
                return;
            }
        }

        final String roomId = room.getRoomId();

        room.sendTextMessage(message.getBody(),message.getBody(),MESSAGE_TEXT_PLAIN,new RoomMediaMessage.EventCreationListener()
        {

            @Override
            public void onEventCreated(final RoomMediaMessage roomMediaMessage) {
                debug("sendMessageAsync:onEventCreated: " + roomMediaMessage);
                message.setID(roomMediaMessage.getEvent().eventId);

                roomMediaMessage.setEventSendingCallback(new ApiCallback<Void>() {
                    @Override
                    public void onNetworkError(Exception e) {
                        debug ("onNetworkError: sending message",e);
                        message.setType(Imps.MessageType.QUEUED);

                    }

                    @Override
                    public void onMatrixError(MatrixError matrixError) {
                        debug ("onMatrixError: sending message: " + matrixError);
                        message.setType(Imps.MessageType.QUEUED);

                        if (matrixError instanceof MXCryptoError) {
                            MXCryptoError mxCryptoError = (MXCryptoError)matrixError;

                            if (matrixError.errcode.equals(mxCryptoError.UNKNOWN_DEVICES_CODE)) {
                                //TODO this just auto "knowns" all, which isn't good. we need to warn the user
                                MXUsersDevicesMap devices = (MXUsersDevicesMap) mxCryptoError.mExceptionData;

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

                                //now resend!
                                sendMessageAsync(session, message);
                            }
                        }

                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        debug ("onUnexpectedError: sending message",e);
                        message.setType(Imps.MessageType.QUEUED);
                    }

                    @Override
                    public void onSuccess(Void aVoid) {

                        debug ("onSuccess: message sent: " + roomMediaMessage.getEvent().getMatrixId());

                        if (mDataHandler.getCrypto().isRoomEncrypted(roomId))
                            message.setType(Imps.MessageType.OUTGOING_ENCRYPTED);

                    }
                });
            }

            @Override
            public void onEventCreationFailed(RoomMediaMessage roomMediaMessage, String s) {
                debug("sendMessageAsync:onEventCreationFailed: " + s + ";" + roomMediaMessage);

            }

            @Override
            public void onEncryptionFailed(RoomMediaMessage roomMediaMessage) {
                debug("sendMessageAsync:onEncryptionFailed: " + roomMediaMessage);

            }
        });
    }

    private void createOneToOneRoom (final String contactId)
    {

        final MatrixAddress addr = new MatrixAddress(contactId);

        //first see if we have a room with them already
        Collection<Room> rooms = mDataHandler.getStore().getRooms();
        for (Room room : rooms)
        {
            if (room.getNumberOfMembers() == 2)
            {
                if (room.getMember(contactId) != null)
                {
                    mRoomMap.put(contactId, room);
                    return;
                }

            }
        }

        //otherwise create a room!
        mSession.createRoom(addr.getUser(), null, null, new ApiCallback<String>() {
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
                room.updateName(addr.getUser(),new BasicApiCallback("RoomUpdate"));

                room.enableEncryptionWithAlgorithm(MXCRYPTO_ALGORITHM_MEGOLM,new BasicApiCallback("CreateRoomEncryption"));
                ChatGroup chatGroup = new ChatGroup(new MatrixAddress(roomId), null, mConn.getChatGroupManager());
                ChatSession session = mConn.getChatSessionManager().createChatSession(chatGroup, true);
                session.setUseEncryption(true);
                room.invite(contactId, new BasicApiCallback("RoomInvite"));
                mConn.addRoomContact(room);
            }
        });
    }
}
