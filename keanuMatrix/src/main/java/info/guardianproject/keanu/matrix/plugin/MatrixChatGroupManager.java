package info.guardianproject.keanu.matrix.plugin;

import android.app.backup.BackupDataInputStream;
import android.content.Context;
import android.opengl.Matrix;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.w3c.dom.Text;

import java.util.List;

import info.guardianproject.keanu.core.model.Address;
import info.guardianproject.keanu.core.model.ChatGroup;
import info.guardianproject.keanu.core.model.ChatGroupManager;
import info.guardianproject.keanu.core.model.ChatSession;
import info.guardianproject.keanu.core.model.ChatSessionListener;
import info.guardianproject.keanu.core.model.Contact;
import info.guardianproject.keanu.core.model.Invitation;
import info.guardianproject.keanu.core.model.Message;
import info.guardianproject.keanu.core.model.impl.BaseAddress;
import info.guardianproject.keanu.core.service.IChatSession;
import info.guardianproject.keanu.core.service.IChatSessionListener;

import static org.matrix.androidsdk.crypto.CryptoConstantsKt.MXCRYPTO_ALGORITHM_MEGOLM;

public class MatrixChatGroupManager extends ChatGroupManager {

    private MXDataHandler mDataHandler;

    private MXSession mSession;
    private MatrixConnection mConn;

    private Context mContext;

    public MatrixChatGroupManager (Context context, MatrixConnection conn) {
        mConn = conn;
        mContext = context;
    }

    public void setDataHandler (MXDataHandler dataHandler)
    {
        mDataHandler = dataHandler;
    }

    public void setSession (MXSession session)
    {
        mSession = session;
    }

    public boolean hasChatGroup (String roomId)
    {
        return mGroups.containsKey(roomId);
    }

    @Override
    public ChatGroup getChatGroup (Address addr)
    {
        return getChatGroup(new MatrixAddress(addr.getAddress()),null);
    }

    public ChatGroup getChatGroup (String addr)
    {
        return getChatGroup(new MatrixAddress(addr),null);
    }

    public ChatGroup getChatGroup (MatrixAddress addr, String subject)
    {
        ChatGroup result = super.getChatGroup(addr);

        if (result == null)
        {
            if (TextUtils.isEmpty(subject))
            {
                Room room = mDataHandler.getRoom(addr.getBareAddress());
                if (room != null)
                    subject = room.getRoomDisplayName(mContext);
                else
                    subject = addr.getUser();
            }

            result = new ChatGroup(addr,subject,this);
        }
        else
            result.setName(subject);

        notifyJoinedGroup(result);

        return result;
    }

    @Override
    public void createChatGroupAsync(final String subject, boolean isDirect, IChatSessionListener listener) throws Exception {

        if (isDirect)
        {
            List<String> rooms = mDataHandler.getDirectChatRoomIdsList(subject);
            if (rooms != null && rooms.size() > 0)
            {
                Room room = mDataHandler.getRoom(rooms.get(0));
                room.join(new BasicApiCallback("join room"));
                room.enableEncryptionWithAlgorithm(MXCRYPTO_ALGORITHM_MEGOLM, new BasicApiCallback("CreateRoomEncryption"));

                ChatGroup chatGroup = mConn.addRoomContact(room);
                ChatSession session = mConn.getChatSessionManager().createChatSession(chatGroup, true);
                IChatSession iSession = mConn.getChatSessionManager().getChatSessionAdapter(room.getRoomId());
                try {
                    iSession.useEncryption(room.isEncrypted());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

                if (listener != null) {

                    try {
                        listener.onChatSessionCreated(iSession);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }

            mSession.createDirectMessageRoom(subject, new ApiCallback<String>() {
                @Override
                public void onNetworkError(Exception e) {
                    mConn.debug("createChatGroupAsync:onNetworkError: " + e);
                    if (listener != null) {
                        try {
                            listener.onChatSessionCreateError(e.toString(), null);
                        } catch (RemoteException e1) {
                            e1.printStackTrace();
                        }
                    }
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    mConn.debug("createChatGroupAsync:onMatrixError: " + e);
                    if (listener != null) {
                        try {
                            listener.onChatSessionCreateError(e.toString(), null);
                        } catch (RemoteException e1) {
                            e1.printStackTrace();
                        }
                    }
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    mConn.debug("createChatGroupAsync:onUnexpectedError: " + e);
                    if (listener != null) {
                        try {
                            listener.onChatSessionCreateError(e.toString(), null);
                        } catch (RemoteException e1) {
                            e1.printStackTrace();
                        }
                    }
                }

                @Override
                public void onSuccess(String roomId) {
                    Room room = mDataHandler.getRoom(roomId);

                    room.join(new BasicApiCallback("join room"));
                    room.enableEncryptionWithAlgorithm(MXCRYPTO_ALGORITHM_MEGOLM, new BasicApiCallback("CreateRoomEncryption"));

                    ChatGroup chatGroup = mConn.addRoomContact(room);
                    ChatSession session = mConn.getChatSessionManager().createChatSession(chatGroup, true);
                    IChatSession iSession = mConn.getChatSessionManager().getChatSessionAdapter(roomId);
                    try {
                        iSession.useEncryption(true);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }

                    if (listener != null) {

                        try {
                            listener.onChatSessionCreated(iSession);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
        else {
            mSession.createRoom(null, subject, null, new ApiCallback<String>() {
                @Override
                public void onNetworkError(Exception e) {
                    mConn.debug("createChatGroupAsync:onNetworkError: " + e);
                    if (listener != null) {
                        try {
                            listener.onChatSessionCreateError(e.toString(), null);
                        } catch (RemoteException e1) {
                            e1.printStackTrace();
                        }
                    }
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    mConn.debug("createChatGroupAsync:onMatrixError: " + e);
                    if (listener != null) {
                        try {
                            listener.onChatSessionCreateError(e.toString(), null);
                        } catch (RemoteException e1) {
                            e1.printStackTrace();
                        }
                    }
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    mConn.debug("createChatGroupAsync:onUnexpectedError: " + e);
                    if (listener != null) {
                        try {
                            listener.onChatSessionCreateError(e.toString(), null);
                        } catch (RemoteException e1) {
                            e1.printStackTrace();
                        }
                    }
                }

                @Override
                public void onSuccess(String roomId) {
                    Room room = mDataHandler.getRoom(roomId);

                    if (subject != null)
                        room.updateName(subject, new BasicApiCallback("RoomUpdate"));

                    room.join(new BasicApiCallback("join room"));
                    room.enableEncryptionWithAlgorithm(MXCRYPTO_ALGORITHM_MEGOLM, new BasicApiCallback("CreateRoomEncryption"));

                    ChatGroup chatGroup = mConn.addRoomContact(room);
                    ChatSession session = mConn.getChatSessionManager().createChatSession(chatGroup, true);
                    IChatSession iSession = mConn.getChatSessionManager().getChatSessionAdapter(roomId);
                    try {
                        iSession.useEncryption(true);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }

                    if (listener != null) {

                        try {
                            listener.onChatSessionCreated(iSession);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }

    }

    @Override
    public void deleteChatGroupAsync(ChatGroup group) {
        Room room = mDataHandler.getRoom(group.getAddress().getAddress());
        if (room != null)
            mDataHandler.deleteRoom(room.getRoomId());
    }

    @Override
    protected void addGroupMemberAsync(ChatGroup group, Contact contact) {
        inviteUserAsync(group, contact);
    }

    @Override
    public void removeGroupMemberAsync(ChatGroup group, Contact contact) {
        Room room = mDataHandler.getRoom(group.getAddress().getAddress());
        room.kick(contact.getAddress().getAddress(),":(",new BasicApiCallback("removeGroupMemberAsync"));
    }

    @Override
    public void joinChatGroupAsync(Address address, String subject) {
        Room room = mDataHandler.getRoom(address.getAddress());

        if (room != null && room.isInvited())
            room.join(new ApiCallback<Void>() {
                @Override
                public void onNetworkError(Exception e) {
                    mConn.debug("acceptInvitationAsync.join.onNetworkError");

                }

                @Override
                public void onMatrixError(MatrixError matrixError) {
                    mConn.debug("acceptInvitationAsync.join.onMatrixError");

                }

                @Override
                public void onUnexpectedError(Exception e) {
                    mConn.debug("acceptInvitationAsync.join.onUnexpectedError");

                }

                @Override
                public void onSuccess(Void aVoid) {
                    mConn.debug("acceptInvitationAsync.join.onSuccess");

                }
            });
    }

    @Override
    public void leaveChatGroupAsync(ChatGroup group) {

        Room room = mDataHandler.getRoom(group.getAddress().getAddress());

        if (room != null ) {
            room.leave(new BasicApiCallback("Leave Room")
            {
                @Override
                public void onSuccess(Object o) {
                    debug ("Left Room: onSuccess: " + o);
                }

            });
        }
    }

    @Override
    public void inviteUserAsync(ChatGroup group, Contact invitee) {

        Room room = mDataHandler.getRoom(group.getAddress().getAddress());

        if (room != null ) {

            room.invite(invitee.getAddress().getAddress(),new BasicApiCallback("InviteRoom"));

            mConn.updateGroupMembers(room, group);
        }


    }

    @Override
    public void acceptInvitationAsync(Invitation invitation) {

        Room room = mDataHandler.getRoom(invitation.getGroupAddress().getAddress());
        ChatGroup group = getChatGroup(room.getRoomId());
        group.setJoined(true);
        notifyJoinedGroup(group);

        if (room != null && room.isInvited())
            room.join(new ApiCallback<Void>() {
                @Override
                public void onNetworkError(Exception e) {
                    mConn.debug("acceptInvitationAsync.join.onNetworkError");

                }

                @Override
                public void onMatrixError(MatrixError matrixError) {
                    mConn.debug("acceptInvitationAsync.join.onMatrixError");

                }

                @Override
                public void onUnexpectedError(Exception e) {
                    mConn.debug("acceptInvitationAsync.join.onUnexpectedError");

                }

                @Override
                public void onSuccess(Void aVoid) {
                    mConn.debug("acceptInvitationAsync.join.onSuccess");

                }
            });


    }

    @Override
    public void rejectInvitationAsync(Invitation invitation) {
        Room room = mDataHandler.getRoom(invitation.getGroupAddress().getAddress());
        if (room != null)
            room.leave(new BasicApiCallback("rejectInvitationAsync.leave"));
        //do nothing
    }

    @Override
    public String getDefaultGroupChatService() {
        return null;
    }

    @Override
    public void setGroupSubject(ChatGroup group, String subject) {

        Room room = mDataHandler.getRoom(group.getAddress().getAddress());

        if (room != null)
        {
            room.updateName(subject,new BasicApiCallback("setGroupSubject"));
        }
    }

    @Override
    public void grantAdminAsync(ChatGroup group, Contact contact) {
        Room room = mDataHandler.getRoom(group.getAddress().getAddress());

        if (room != null)
        {
            RoomMember member = room.getMember(contact.getAddress().getAddress());
            room.getState().getPowerLevels().setUserPowerLevel(member.getUserId(),100);

            mConn.updateGroupMembers(room, group);
        }
    }
}
