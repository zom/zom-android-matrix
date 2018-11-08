package info.guardianproject.keanu.matrix.plugin;

import org.jxmpp.jid.Jid;

import info.guardianproject.keanu.core.model.ChatSession;
import info.guardianproject.keanu.core.model.ChatSessionManager;
import info.guardianproject.keanu.core.model.Message;

public class MatrixChatSessionManager extends ChatSessionManager {

    @Override
    public void sendMessageAsync(ChatSession session, Message message) {

    }

    @Override
    public boolean resourceSupportsOmemo(Jid jid) {
        return false;
    }
}
