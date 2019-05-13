package info.guardianproject.keanuapp.ui.conversation;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.Date;
import java.util.List;

import info.guardianproject.keanu.core.model.Contact;
import info.guardianproject.keanu.core.model.ImErrorInfo;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.service.IChatSession;
import info.guardianproject.keanu.core.service.IChatSessionListener;
import info.guardianproject.keanu.core.service.IChatSessionManager;
import info.guardianproject.keanu.core.service.IImConnection;
import info.guardianproject.keanu.core.service.RemoteImService;
import info.guardianproject.keanu.core.ui.RoundedAvatarDrawable;
import info.guardianproject.keanu.core.util.DatabaseUtils;
import info.guardianproject.keanu.core.util.LogCleaner;
import info.guardianproject.keanuapp.MainActivity;
import info.guardianproject.keanuapp.R;

import static info.guardianproject.keanu.core.KeanuConstants.DEFAULT_AVATAR_HEIGHT;
import static info.guardianproject.keanu.core.KeanuConstants.DEFAULT_AVATAR_WIDTH;
import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;
import static info.guardianproject.keanu.core.provider.Imps.ContactsColumns.SUBSCRIPTION_STATUS_NONE;
import static info.guardianproject.keanuapp.ui.conversation.ConversationView.ACCOUNT_COLUMN;
import static info.guardianproject.keanuapp.ui.conversation.ConversationView.CHAT_PROJECTION;
import static info.guardianproject.keanuapp.ui.conversation.ConversationView.NICKNAME_COLUMN;
import static info.guardianproject.keanuapp.ui.conversation.ConversationView.PRESENCE_STATUS_COLUMN;
import static info.guardianproject.keanuapp.ui.conversation.ConversationView.PROVIDER_COLUMN;
import static info.guardianproject.keanuapp.ui.conversation.ConversationView.SUBSCRIPTION_STATUS_COLUMN;
import static info.guardianproject.keanuapp.ui.conversation.ConversationView.SUBSCRIPTION_TYPE_COLUMN;
import static info.guardianproject.keanuapp.ui.conversation.ConversationView.TYPE_COLUMN;
import static info.guardianproject.keanuapp.ui.conversation.ConversationView.USERNAME_COLUMN;
import static info.guardianproject.keanuapp.ui.conversation.StoryActivity.TAG_STORYMODE_INDICATOR;

public class StoryIntroActivity extends AppCompatActivity {

    private Handler mHandler = new Handler();
    private IChatSession mCurrentChatSession;
    private IImConnection mConn;

    long mLastChatId=-1;
    String mRemoteNickname;
    String mRemoteAddress;
    int mSubscriptionType = Imps.Contacts.SUBSCRIPTION_TYPE_NONE;
    int mSubscriptionStatus = SUBSCRIPTION_STATUS_NONE;

    long mProviderId = -1;
    long mAccountId = -1;
    long mInvitationId;
    private int mPresenceStatus;
    private Date mLastSeen;

    private int mViewType;
    private int mContactType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.awesome_activity_story_intro);

        setupStoryMode();
    }

    private void setupStoryMode () {
        mLastChatId = getIntent().getLongExtra("id", -1);
        mRemoteAddress = getIntent().getStringExtra("address");

        bindQuery(mLastChatId);
    }

    private void launchStoryMode () {

        final Intent intent = new Intent(this, StoryActivity.class);
        intent.putExtra("id", mLastChatId);
        intent.putExtra("address", mRemoteAddress);
        intent.putExtra("nickname", getIntent().getStringExtra("nickname"));


        getChatSession(new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] objects) {

                List<Contact> admins = null;
                try {

                    mCurrentChatSession.refreshContactFromServer();

                    boolean isContrib = false;

                    admins = mCurrentChatSession.getGroupChatAdmins();

                    if (admins != null) {
                        for (Contact c : admins) {
                            if (c.getAddress().getBareAddress().equals(mLocalAddress)) {
                                isContrib = true;
                                break;

                            }
                        }
                    }

                    if (!isContrib)
                    {
                        admins = mCurrentChatSession.getGroupChatOwners();

                        if (admins != null) {
                            for (Contact c : admins) {
                                if (c.getAddress().getBareAddress().equals(mLocalAddress)) {
                                    isContrib = true;
                                    break;

                                }
                            }
                        }
                    }

                    intent.putExtra(StoryActivity.ARG_CONTRIBUTOR_MODE,isContrib);

                    startActivity(intent);
                    finish();

                } catch (RemoteException e) {
                    e.printStackTrace();
                }

                return null;
            }
        });

    }

    private boolean bindQuery (long chatId) {

        Uri contactUri = ContentUris.withAppendedId(Imps.Contacts.CONTENT_URI, chatId);
        Cursor c = getContentResolver().query(contactUri, CHAT_PROJECTION, null, null, null);

        if (c == null)
            return false;

        if (c.getColumnCount() < 8 || c.getCount() == 0)
            return false;

        try {
            c.moveToFirst();
        }
        catch (IllegalStateException ise)
        {
            return false;
        }

        if (c != null && (!c.isClosed()) && c.getCount() > 0)
        {
            mProviderId = c.getLong(PROVIDER_COLUMN);
            mAccountId = c.getLong(ACCOUNT_COLUMN);
            mPresenceStatus = c.getInt(PRESENCE_STATUS_COLUMN);
            mContactType = c.getInt(TYPE_COLUMN);

            mRemoteNickname = c.getString(NICKNAME_COLUMN);
            mRemoteAddress = c.getString(USERNAME_COLUMN);

            mSubscriptionType = c.getInt(SUBSCRIPTION_TYPE_COLUMN);

            mSubscriptionStatus = c.getInt(SUBSCRIPTION_STATUS_COLUMN);

            initData();

            if (!hasJoined())
            {
                mHandler.post(new Runnable ()
                {
                    public void run ()
                    {
                        showJoinGroupUI();
                    }
                });
            }
            else
            {
                launchStoryMode();
            }


        }


        c.close();

        initSession ();


        return true;


    }

    private void initSession ()
    {
        mCurrentChatSession = getChatSession(null);
        if (mCurrentChatSession == null)
            createChatSession();
    }

    private void createChatSession() {

        try
        {
            if (mConn != null) {
                IChatSessionManager sessionMgr = mConn.getChatSessionManager();
                if (sessionMgr != null) {
                    sessionMgr.createChatSession(mRemoteAddress, false, new IChatSessionListener() {
                        @Override
                        public void onChatSessionCreated(IChatSession session) throws RemoteException {
                            mCurrentChatSession = session;
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

        } catch (Exception e) {

            //mHandler.showServiceErrorAlert(e.getLocalizedMessage());
            LogCleaner.error(LOG_TAG, "issue getting chat session", e);
        }

    }
    
    public IChatSession getChatSession(AsyncTask task) {

        try {

            if (mCurrentChatSession != null) {
                if (task != null)
                    task.execute();
                return mCurrentChatSession;
            }
            else if (mConn != null)
            {
                IChatSessionManager sessionMgr = mConn.getChatSessionManager();
                if (sessionMgr != null) {

                    IChatSession session = sessionMgr.getChatSession(mRemoteAddress);

                    if (session == null) {
                        sessionMgr.createChatSession(mRemoteAddress, false, new IChatSessionListener() {
                            @Override
                            public void onChatSessionCreated(IChatSession session) throws RemoteException {
                                mCurrentChatSession = session;

                                if (task != null)
                                    task.execute();
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
                    else
                    {
                        mCurrentChatSession = session;
                        if (task != null)
                            task.execute();
                    }

                    return session;

                }
            }


        } catch (Exception e) {

            //mHandler.showServiceErrorAlert(e.getLocalizedMessage());
            LogCleaner.error(LOG_TAG, "error getting chat session", e);
        }

        return null;
    }

    private boolean hasJoined ()
    {
        return mSubscriptionStatus == Imps.Contacts.SUBSCRIPTION_STATUS_NONE;
    }

    private void showJoinGroupUI ()
    {
        final View joinGroupView = findViewById(R.id.join_group_view);

        joinGroupView.setVisibility(View.VISIBLE);

        final View btnJoinAccept = joinGroupView.findViewById(R.id.btnJoinAccept);
        final View btnJoinDecline = joinGroupView.findViewById(R.id.btnJoinDecline);
        final TextView title = joinGroupView.findViewById(R.id.room_join_title);

        title.setText(title.getContext().getString(R.string.room_invited, mRemoteNickname));

        btnJoinAccept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                getChatSession(new AsyncTask() {
                    @Override
                    protected Object doInBackground(Object[] objects) {

                        getChatSession(new AsyncTask() {
                            @Override
                            protected Object doInBackground(Object[] objects) {

                                if (mCurrentChatSession != null)
                                    setGroupSeen();

                                return null;
                            }

                            @Override
                            protected void onPostExecute(Object o) {
                                super.onPostExecute(o);

                            }
                        });


                        return null;
                    }

                    @Override
                    protected void onPostExecute(Object o) {
                        super.onPostExecute(o);

                        joinGroupView.setVisibility(View.GONE);
                    }
                });
            }
        });
        btnJoinDecline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                getChatSession(new AsyncTask() {
                    @Override
                    protected Object doInBackground(Object[] objects) {

                        if (mCurrentChatSession != null) {
                            try {
                                mCurrentChatSession.leave();

                            }
                            catch (RemoteException re){}
                        }

                        return null;
                    }

                    @Override
                    protected void onPostExecute(Object o) {
                        super.onPostExecute(o);

                        //clear the stack and go back to the main activity
                        Intent intent = new Intent(v.getContext(), MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        v.getContext().startActivity(intent);
                    }
                });


            }
        });


    }

    private void setGroupSeen() {

        try {
            if (mCurrentChatSession != null)
                mCurrentChatSession.markAsSeen();

            mSubscriptionStatus = SUBSCRIPTION_STATUS_NONE;
        }
        catch (RemoteException re)
        {
            Log.e(getClass().getName(),"error setting subscription / markAsSeen()",re);
        }
    }

    private String mLocalAddress;

    private void initData () {

        Cursor cursor = getContentResolver().query(Imps.ProviderSettings.CONTENT_URI, new String[]{Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE}, Imps.ProviderSettings.PROVIDER + "=?", new String[]{Long.toString(mProviderId)}, null);

        if (cursor == null)
            return; //not going to work

        Imps.ProviderSettings.QueryMap providerSettings = new Imps.ProviderSettings.QueryMap(
                cursor, getContentResolver(), mProviderId, false, null);



        mConn = RemoteImService.getConnection(mProviderId, mAccountId);
        mLocalAddress = '@' + Imps.Account.getUserName(getContentResolver(), mAccountId) + ':' + providerSettings.getDomain();


        providerSettings.close();

    }


}
