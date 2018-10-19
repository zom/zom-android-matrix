package info.guardianproject.keanuapp.plugin.matrix;

import java.util.Collection;

import info.guardianproject.keanuapp.model.Contact;
import info.guardianproject.keanuapp.model.ContactList;
import info.guardianproject.keanuapp.model.ContactListManager;
import info.guardianproject.keanuapp.model.ImConnection;
import info.guardianproject.keanuapp.model.ImException;

public class MatrixContactListManager extends ContactListManager {
    @Override
    public String normalizeAddress(String address) {
        return null;
    }

    @Override
    public Contact[] createTemporaryContacts(String[] addresses) {
        return new Contact[0];
    }

    @Override
    protected void doSetContactName(String address, String name) throws ImException {

    }

    @Override
    public void loadContactListsAsync() {

    }

    @Override
    public void approveSubscriptionRequest(Contact contact) {

    }

    @Override
    public void declineSubscriptionRequest(Contact contact) {

    }

    @Override
    protected ImConnection getConnection() {
        return null;
    }

    @Override
    protected void doBlockContactAsync(String address, boolean block) {

    }

    @Override
    protected void doCreateContactListAsync(String name, Collection<Contact> contacts, boolean isDefault) {

    }

    @Override
    protected void doDeleteContactListAsync(ContactList list) {

    }

    @Override
    protected void doAddContactToListAsync(Contact contact, ContactList list, boolean autoPresenceSubscribe) throws ImException {

    }

    @Override
    protected void doRemoveContactFromListAsync(Contact contact, ContactList list) {

    }

    @Override
    protected void setListNameAsync(String name, ContactList list) {

    }
}
