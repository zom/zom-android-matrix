package info.guardianproject.keanuapp.plugin.matrix;

import org.jxmpp.jid.Jid;

import info.guardianproject.keanuapp.model.ChatSession;
import info.guardianproject.keanuapp.model.ChatSessionManager;
import info.guardianproject.keanuapp.model.Message;

public class MatrixChatSessionManager extends ChatSessionManager {

    @Override
    public void sendMessageAsync(ChatSession session, Message message) {

    }

    @Override
    public boolean resourceSupportsOmemo(Jid jid) {
        return false;
    }
}
