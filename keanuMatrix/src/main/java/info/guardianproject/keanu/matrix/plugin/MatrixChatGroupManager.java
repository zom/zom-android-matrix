package info.guardianproject.keanu.matrix.plugin;

import info.guardianproject.keanu.core.model.Address;
import info.guardianproject.keanu.core.model.ChatGroup;
import info.guardianproject.keanu.core.model.ChatGroupManager;
import info.guardianproject.keanu.core.model.Contact;
import info.guardianproject.keanu.core.model.Invitation;

public class MatrixChatGroupManager extends ChatGroupManager {

    @Override
    public boolean createChatGroupAsync(String address, String subject, String nickname) throws Exception {
        return false;
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
