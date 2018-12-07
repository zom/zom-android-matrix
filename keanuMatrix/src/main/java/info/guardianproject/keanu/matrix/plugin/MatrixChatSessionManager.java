package info.guardianproject.keanu.matrix.plugin;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.data.RoomMediaMessage;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;

import info.guardianproject.keanu.core.model.ChatSession;
import info.guardianproject.keanu.core.model.ChatSessionManager;
import info.guardianproject.keanu.core.model.Message;

import static info.guardianproject.keanu.core.service.RemoteImService.debug;

public class MatrixChatSessionManager extends ChatSessionManager {

    private MXDataHandler mDataHandler;

    public MatrixChatSessionManager () {
    }

    public void setDataHandler (MXDataHandler dataHandler)
    {
        mDataHandler = dataHandler;
    }

    public ChatSession getSession (String address)
    {
        return mSessions.get(address);
    }

    @Override
    public void sendMessageAsync(ChatSession session, final Message message) {

        mDataHandler.getRoom(session.getParticipant().getAddress().getAddress())
                .sendTextMessage(message.getBody(),message.getBody(),"text/plain",new RoomMediaMessage.EventCreationListener()
                {

                    @Override
                    public void onEventCreated(final RoomMediaMessage roomMediaMessage) {
                        debug("sendMessageAsync:onEventCreated: " + roomMediaMessage);
                        message.setID(roomMediaMessage.getEvent().eventId);

                        roomMediaMessage.setEventSendingCallback(new ApiCallback<Void>() {
                            @Override
                            public void onNetworkError(Exception e) {
                                debug ("onNetworkError: sending message",e);
                            }

                            @Override
                            public void onMatrixError(MatrixError matrixError) {
                                debug ("onMatrixError: sending message: " + matrixError);

                            }

                            @Override
                            public void onUnexpectedError(Exception e) {
                                debug ("onUnexpectedError: sending message",e);

                            }

                            @Override
                            public void onSuccess(Void aVoid) {
                                debug ("onSuccess: message sent: " + roomMediaMessage.getEvent().getMatrixId());

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
}
