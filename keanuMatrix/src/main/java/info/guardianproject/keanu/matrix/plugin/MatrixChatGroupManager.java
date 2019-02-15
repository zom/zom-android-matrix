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
import org.matrix.androidsdk.rest.model.CreateRoomParams;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomDirectoryVisibility;
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
        return getChatGroup(new MatrixAddress(addr.getAddress()));
    }

    public ChatGroup getChatGroup (String addr)
    {
        return getChatGroup(new MatrixAddress(addr));
    }

    public synchronized ChatGroup getChatGroup (MatrixAddress addr)
    {
        ChatGroup result = super.getChatGroup(addr);

        if (result == null)
        {
            result = new ChatGroup(addr,null,this);
        }

        return result;
    }

    @Override
    public synchronized void createChatGroupAsync(final String subject, final boolean isDirect, final IChatSessionListener listener) throws Exception {

        if (isDirect)
        {
            List<String> rooms = mDataHandler.getDirectChatRoomIdsList(subject);

            if (rooms != null && rooms.size() > 0)
            {
                //found an existing room!

                final Room room = mDataHandler.getRoom(rooms.get(0));

                if (!room.isMember()) {
                    room.join(new BasicApiCallback("join room") {
                                  @Override
                                  public void onSuccess(Object o) {
                                      super.onSuccess(o);

                                      ChatGroup chatGroup = mConn.addRoomContact(room);

                                      ChatSession session = mConn.getChatSessionManager().createChatSession(chatGroup, true);
                                      IChatSession iSession = mConn.getChatSessionManager().getChatSessionAdapter(room.getRoomId());

                                      if (listener != null) {

                                          try {
                                              listener.onChatSessionCreated(iSession);
                                          } catch (RemoteException e) {
                                              e.printStackTrace();
                                          }
                                      }

                                      mConn.addRoomContact(room);

                                  }
                              }
                    );
                }


                return;
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

                    if (!room.isMember()) {
                        room.join(new BasicApiCallback("join room") {
                                      @Override
                                      public void onSuccess(Object o) {
                                          super.onSuccess(o);

                                          setRoomDefaults(room);


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

                                          mConn.updateGroupMembers(room, chatGroup);


                                      }
                                  }
                        );
                    }

                }
            });
        }
        else {

            mSession.createRoom(subject, null, null, new ApiCallback<String>() {
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

                    if (!TextUtils.isEmpty(subject))
                        room.updateName(subject, new BasicApiCallback("RoomUpdate"));

                    if (!room.isMember()) {
                        room.join(new BasicApiCallback("join room") {
                                      @Override
                                      public void onSuccess(Object o) {
                                          super.onSuccess(o);

                                          setRoomDefaults(room);

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

                                          mConn.updateGroupMembers(room, chatGroup);


                                      }
                                  }
                        );
                    }


                }
            });
        }

    }

    private void setRoomDefaults (Room room)
    {


        if (!room.isEncrypted())
            room.enableEncryptionWithAlgorithm(MXCRYPTO_ALGORITHM_MEGOLM, new BasicApiCallback("CreateRoomEncryption"));

        room.setIsURLPreviewAllowedByUser(false, new BasicApiCallback("setIsURLPreviewAllowedByUser:false"));
        room.updateDirectoryVisibility(RoomDirectoryVisibility.DIRECTORY_VISIBILITY_PRIVATE,new BasicApiCallback("updateDirectoryVisibility:private"));
        room.updateHistoryVisibility("joined",new BasicApiCallback("updateHistoryVisibility:joined"));
        room.updateGuestAccess("forbidden", new BasicApiCallback("updateGuestAccess:forbidden"));

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

        if (room != null && room.isInvited() && (!room.isMember()))
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
    public void inviteUserAsync(final ChatGroup group, Contact invitee) {

        final Room room = mDataHandler.getRoom(group.getAddress().getAddress());

        if (room != null ) {

            room.invite(invitee.getAddress().getAddress(), new ApiCallback<Void>() {
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
                    mConn.updateGroupMembers(room, group);

                }
            });

            mConn.updateGroupMembers(room, group);


        }


    }

    @Override
    public void acceptInvitationAsync(Invitation invitation) {

        Room room = mDataHandler.getRoom(invitation.getGroupAddress().getAddress());

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
                    ChatGroup group = getChatGroup(room.getRoomId());
                    group.setJoined(true);
                    group.setName(room.getRoomDisplayName(mContext));
                    notifyJoinedGroup(group);
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
            if (!subject.equals(room.getRoomDisplayName(mContext)))
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
