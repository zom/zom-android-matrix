package info.guardianproject.keanuapp.plugin.matrix;

import info.guardianproject.keanuapp.model.Address;
import info.guardianproject.keanuapp.model.ChatGroup;
import info.guardianproject.keanuapp.model.ChatGroupManager;
import info.guardianproject.keanuapp.model.Contact;
import info.guardianproject.keanuapp.model.Invitation;

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
