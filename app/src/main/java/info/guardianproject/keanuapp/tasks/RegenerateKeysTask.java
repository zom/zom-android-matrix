package info.guardianproject.keanuapp.tasks;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.RemoteException;

import java.util.List;

import info.guardianproject.keanuapp.ImApp;
import info.guardianproject.keanuapp.service.IImConnection;

/**
 * Created by n8fr8 on 5/1/17.
 */

public class RegenerateKeysTask extends AsyncTask<String, Void, String> {

    Activity mContext;
    IImConnection mConn;
    long mAccountId;
    long mProviderId;
    ImApp mApp;
    IImConnection mNewConn;
    RegenerateKeysListener mListener;

    Handler mHandler = new Handler();

    String mUserAddress;

    public RegenerateKeysTask(Activity context, ImApp app, String userAddress, long providerId, long accountId, RegenerateKeysListener listener)
    {
        mContext = context;
        mAccountId = accountId;
        mProviderId = providerId;
        mUserAddress = userAddress;

        mApp = app;

        mListener = listener;


    }

    @Override
    protected String doInBackground(String... newDomains) {

        try {
            mConn = mApp.getConnection(mProviderId, mAccountId);

            List<String> fps = mConn.getFingerprints(mUserAddress);



        }
        catch (RemoteException re)
        {
            //fail!
        }

        //failed
        return null;
    }

    @Override
    protected void onPostExecute(String newFingerprint) {
        super.onPostExecute(newFingerprint);

        if (newFingerprint == null)
        {
            if (mListener != null)
                mListener.regenFailed(mProviderId,mAccountId);
        }
        else
        {
            if (mListener != null)
                mListener.regenComplete(newFingerprint);
        }

    }


    public interface RegenerateKeysListener {

        public void regenComplete(String newFingerprint);

        public void regenFailed(long providerId, long accountId);
    }

}
