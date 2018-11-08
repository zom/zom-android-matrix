package info.guardianproject.keanuapp.tasks;

import android.os.AsyncTask;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;

import info.guardianproject.keanu.core.model.ImErrorInfo;
import info.guardianproject.keanu.core.service.IContactList;
import info.guardianproject.keanu.core.service.IContactListManager;
import info.guardianproject.keanu.core.service.IImConnection;
import info.guardianproject.keanu.core.service.RemoteImService;

import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;

/**
 * Created by n8fr8 on 6/9/15.
 */
public class AddContactAsyncTask extends AsyncTask<String, Void, Integer> {

    long mProviderId;
    long mAccountId;

    public AddContactAsyncTask(long providerId, long accountId)
    {
        mProviderId = providerId;
        mAccountId = accountId;

    }

    @Override
    public Integer doInBackground(String... strings) {

        String address = strings[0];
        String fingerprint = strings[1];
        String nickname = null;

        if (strings.length > 2)
            nickname = strings[2];

        return addToContactList(address, fingerprint, nickname);
    }

    @Override
    protected void onPostExecute(Integer response) {
        super.onPostExecute(response);

    }

    private int addToContactList (String address, String otrFingperint, String nickname)
    {
        int res = -1;

        try {
            IImConnection conn = RemoteImService.getConnection(mProviderId,mAccountId);
            if (conn == null)
               conn = RemoteImService.createConnection(mProviderId,mAccountId);

            IContactList list = getContactList(conn);

            if (list != null) {

                    res = list.addContact(address, nickname);
                    if (res != ImErrorInfo.NO_ERROR) {

                        //what to do here?
                    }



            }

        } catch (RemoteException re) {
            Log.e(LOG_TAG, "error adding contact", re);
        }

        return res;
    }

    private IContactList getContactList(IImConnection conn) {
        if (conn == null) {
            return null;
        }

        try {
            IContactListManager contactListMgr = conn.getContactListManager();

            // Use the default list
            List<IBinder> lists = contactListMgr.getContactLists();
            for (IBinder binder : lists) {
                IContactList list = IContactList.Stub.asInterface(binder);
                if (list.isDefault()) {
                    return list;
                }
            }

            // No default list, use the first one as default list
            if (!lists.isEmpty()) {
                return IContactList.Stub.asInterface(lists.get(0));
            }

            return null;

        } catch (RemoteException e) {
            // If the service has died, there is no list for now.
            return null;
        }
    }
}
