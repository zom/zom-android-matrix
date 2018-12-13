package info.guardianproject.keanu.matrix.plugin;

import android.opengl.Matrix;
import android.text.TextUtils;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.w3c.dom.Text;

import info.guardianproject.keanu.core.model.Address;
import info.guardianproject.keanu.core.model.ChatGroup;
import info.guardianproject.keanu.core.model.ChatGroupManager;
import info.guardianproject.keanu.core.model.ChatSession;
import info.guardianproject.keanu.core.model.Contact;
import info.guardianproject.keanu.core.model.Invitation;
import static org.matrix.androidsdk.crypto.CryptoConstantsKt.MXCRYPTO_ALGORITHM_MEGOLM;

public class MatrixChatGroupManager extends ChatGroupManager {

    private MXDataHandler mDataHandler;

    private MXSession mSession;
    private MatrixConnection mConn;

    public MatrixChatGroupManager (MatrixConnection conn) {
        mConn = conn;
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
        return getChatGroup(new MatrixAddress(addr.getAddress()),addr.getAddress());
    }

    public ChatGroup getChatGroup (MatrixAddress addr, String subject)
    {
        ChatGroup result = super.getChatGroup(addr);

        if (result == null)
        {
            result = new ChatGroup(addr,subject,this);
        }
        else
            result.setName(subject);

        notifyJoinedGroup(result);

        return result;
    }

    @Override
    public ChatGroup createChatGroupAsync(final String address, final String subject, String nickname) throws Exception {


        if (!TextUtils.isEmpty(address))
        {
            Room room = mDataHandler.getRoom(address);
            ChatGroup chatGroup = mConn.addRoomContact(room);
            mConn.getChatSessionManager().createChatSession(chatGroup, false);
        }
        else {
            mSession.createRoom(subject, subject, null, new ApiCallback<String>() {
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
                    room.updateName(subject,new BasicApiCallback("RoomUpdate"));
                    mConn.addRoomContact(room);
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

                        }
                    });

                    room.enableEncryptionWithAlgorithm(MXCRYPTO_ALGORITHM_MEGOLM,new BasicApiCallback("CreateRoomEncryption"));
                    ChatGroup chatGroup = new ChatGroup(new MatrixAddress(roomId), subject, MatrixChatGroupManager.this);
                    ChatSession session = mConn.getChatSessionManager().createChatSession(chatGroup, true);
                    session.setUseEncryption(true);


                }
            });
        }


        return null;
    }

    @Override
    public void deleteChatGroupAsync(ChatGroup group) {

    }

    @Override
    protected void addGroupMemberAsync(ChatGroup group, Contact contact) {

    }

    @Override
    public void removeGroupMemberAsync(ChatGroup group, Contact contact) {

    }

    @Override
    public void joinChatGroupAsync(Address address, String subject) {

    }

    @Override
    public void leaveChatGroupAsync(ChatGroup group) {

        Room room = mDataHandler.getRoom(group.getAddress().getAddress());

        if (room != null ) {
            mDataHandler.getRoom(group.getAddress().getAddress()).leave(new BasicApiCallback("Leave Room")
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

    }

    @Override
    public void acceptInvitationAsync(Invitation invitation) {

    }

    @Override
    public void rejectInvitationAsync(Invitation invitation) {

    }

    @Override
    public String getDefaultGroupChatService() {
        return null;
    }

    @Override
    public void setGroupSubject(ChatGroup group, String subject) {

    }

    @Override
    public void grantAdminAsync(ChatGroup group, Contact contact) {

    }
}
