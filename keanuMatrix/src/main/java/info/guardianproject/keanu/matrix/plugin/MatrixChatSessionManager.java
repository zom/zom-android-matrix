package info.guardianproject.keanu.matrix.plugin;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.crypto.MXCryptoError;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.data.RoomMediaMessage;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
    public void sendMessageAsync(final ChatSession session, final Message message) {

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

                                        mDataHandler.getCrypto().setDevicesKnown(knownDevices, new ApiCallback<Void>() {
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

                                        //now resend!
                                        sendMessageAsync(session, message);
                                    }
                                }

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
