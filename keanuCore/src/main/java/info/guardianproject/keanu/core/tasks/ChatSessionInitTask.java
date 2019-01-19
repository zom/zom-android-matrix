package info.guardianproject.keanu.core.tasks;

import android.os.AsyncTask;
import android.os.IBinder;
import android.os.RemoteException;

import info.guardianproject.keanu.core.model.Contact;
import info.guardianproject.keanu.core.model.ImErrorInfo;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.service.IChatSession;
import info.guardianproject.keanu.core.service.IChatSessionListener;
import info.guardianproject.keanu.core.service.IImConnection;
import info.guardianproject.keanu.core.service.RemoteImService;

/**
 * Created by n8fr8 on 10/23/15.
 */
public class ChatSessionInitTask extends AsyncTask<Contact, Long, Long> {

    long mProviderId;
    long mAccountId;
    int mContactType;
    boolean mIsNewSession;

    public ChatSessionInitTask (long providerId, long accountId, int contactType, boolean isNewSession)
    {
        mProviderId = providerId;
        mAccountId = accountId;
        mContactType = contactType;
        mIsNewSession = isNewSession;
    }

    public Long doInBackground (Contact... contacts)
    {


        if (mProviderId != -1 && mAccountId != -1 && contacts != null) {
            try {
                IImConnection conn = RemoteImService.getConnection(mProviderId, mAccountId);

                if (conn == null)
                    return -1L;

                for (Contact contact : contacts) {

                    IChatSession session = conn.getChatSessionManager().getChatSession(contact.getAddress().getAddress());

                    if (session == null)
                    {
                        if ((mContactType & Imps.Contacts.TYPE_MASK) == Imps.Contacts.TYPE_GROUP)
                            conn.getChatSessionManager().createMultiUserChatSession(contact.getAddress().getAddress(), contact.getName(), null, mIsNewSession, null, new IChatSessionListener() {
                                @Override
                                public void onChatSessionCreated(IChatSession session) throws RemoteException {

                                }

                                @Override
                                public void onChatSessionCreateError(String name, ImErrorInfo error) throws RemoteException {

                                }

                                @Override
                                public IBinder asBinder() {
                                    return null;
                                }
                            });
                        else {
                            session = conn.getChatSessionManager().createChatSession(contact.getAddress().getAddress(), mIsNewSession, new IChatSessionListener() {
                                @Override
                                public void onChatSessionCreated(IChatSession session) throws RemoteException {

                                }

                                @Override
                                public void onChatSessionCreateError(String name, ImErrorInfo error) throws RemoteException {

                                }

                                @Override
                                public IBinder asBinder() {
                                    return null;
                                }
                            });
                        }

                    }
                    else if (session.isGroupChatSession())
                    {

                    }

                    if (session != null)
                        return (session.getId());


                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return -1L;
    }

    protected void onPostExecute(Long chatId) {


    }



}
