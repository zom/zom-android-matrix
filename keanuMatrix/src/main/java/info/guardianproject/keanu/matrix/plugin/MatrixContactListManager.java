package info.guardianproject.keanu.matrix.plugin;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;

import info.guardianproject.keanu.core.model.Contact;
import info.guardianproject.keanu.core.model.ContactList;
import info.guardianproject.keanu.core.model.ContactListManager;
import info.guardianproject.keanu.core.model.ImConnection;
import info.guardianproject.keanu.core.model.ImException;

import static info.guardianproject.keanu.core.model.ContactListListener.LIST_CONTACT_ADDED;


public class MatrixContactListManager extends ContactListManager {

    Context mContext;
    MatrixConnection mConn;

    public MatrixContactListManager (Context context, MatrixConnection conn)
    {
        super ();

        mContext = context;
        mConn = conn;

    }


    @Override
    public String normalizeAddress(String address) {
        return address;
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

        if (mDefaultContactList == null) {
            String generalGroupName = "Buddies";

            Collection<Contact> contacts = new ArrayList<Contact>();
            MatrixAddress groupAddress = new MatrixAddress('@' + generalGroupName + ":matrix.org");

            mDefaultContactList = new ContactList(groupAddress,generalGroupName, true, contacts, this);
            mDefaultContactList.setDefault(true);

            notifyContactListCreated(mDefaultContactList);
            notifyContactListLoaded(mDefaultContactList);

        }

        notifyContactListsLoaded();


    }

    @Override
    public void approveSubscriptionRequest(Contact contact) {

    }

    @Override
    public void declineSubscriptionRequest(Contact contact) {

    }

    @Override
    protected ImConnection getConnection() {
        return mConn;
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

        if (!mDefaultContactList.containsContact(contact)) {
            try {
                mDefaultContactList.addExistingContact(contact);
                notifyContactListUpdated(mDefaultContactList, LIST_CONTACT_ADDED, contact);
            } catch (ImException e) {
                Log.d("Contacts", "could not add contact to list: " + e.getLocalizedMessage());
            }
        }

    }

    protected void updateContactList ()
    {
        notifyContactListLoaded(mDefaultContactList);
    }

    @Override
    protected void doRemoveContactFromListAsync(Contact contact, ContactList list) {

    }

    @Override
    protected void setListNameAsync(String name, ContactList list) {

    }
}
