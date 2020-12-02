package info.guardianproject.keanuapp.ui.contacts;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.StrictMode;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SwitchCompat;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import org.apache.commons.codec.DecoderException;
import org.bouncycastle.crypto.tls.TlsExtensionsUtils;

import info.guardianproject.keanu.core.Preferences;
import info.guardianproject.keanu.core.model.Contact;
import info.guardianproject.keanu.core.model.ImErrorInfo;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.service.IChatListener;
import info.guardianproject.keanu.core.service.IChatSession;
import info.guardianproject.keanu.core.service.IChatSessionListener;
import info.guardianproject.keanu.core.service.IChatSessionManager;
import info.guardianproject.keanu.core.service.IImConnection;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import info.guardianproject.keanu.core.util.Debug;
import info.guardianproject.keanu.matrix.plugin.MatrixAddress;
import info.guardianproject.keanuapp.R;
import info.guardianproject.keanu.core.service.RemoteImService;
import info.guardianproject.keanu.core.service.adapters.ChatListenerAdapter;
import info.guardianproject.keanu.core.util.DatabaseUtils;
import info.guardianproject.keanuapp.ImApp;
import info.guardianproject.keanuapp.MainActivity;
import info.guardianproject.keanuapp.ui.BaseActivity;
import info.guardianproject.keanuapp.ui.onboarding.OnboardingManager;
import info.guardianproject.keanuapp.ui.qr.QrDisplayActivity;
import info.guardianproject.keanuapp.ui.qr.QrShareAsyncTask;
import info.guardianproject.keanuapp.ui.widgets.GroupAvatar;
import info.guardianproject.keanuapp.ui.widgets.LetterAvatar;

import static info.guardianproject.keanu.core.KeanuConstants.DEFAULT_AVATAR_HEIGHT;
import static info.guardianproject.keanu.core.KeanuConstants.DEFAULT_AVATAR_WIDTH;
import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;
import static info.guardianproject.keanu.core.KeanuConstants.SMALL_AVATAR_HEIGHT;
import static info.guardianproject.keanu.core.KeanuConstants.SMALL_AVATAR_WIDTH;

public class GroupDisplayActivity extends BaseActivity implements IChatSessionListener {

    private String mName = null;
    private String mAddress = null;
    private long mProviderId = -1;
    private long mAccountId = -1;
    private long mLastChatId = -1;
    private String mLocalAddress = null;
    private ImApp mApp = null;

    private String mSubject = null;

    private IImConnection mConn;
    private IChatSession mSession;
    private GroupMemberDisplay mYou;
    private Thread mThreadUpdate;
    private boolean mChatListenerRegistered;

    private class GroupMemberDisplay {
        public String username;
        public String nickname;
        public String role;
        public String affiliation;
        public Drawable avatar;
        public boolean online = false;
    }

    private RecyclerView mRecyclerView;
    private ArrayList<GroupMemberDisplay> mMembers;
    private View mActionAddFriends = null;
    private View mActionShare = null, mActionQR;

    private final static int REQUEST_PICK_CONTACTS = 100;

    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setTitle("");
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setContentView(R.layout.awesome_activity_group);

        mApp = (ImApp)getApplication();

        mName = getIntent().getStringExtra("nickname");
        mAddress = getIntent().getStringExtra("address");
        mProviderId = getIntent().getLongExtra("provider", mApp.getDefaultProviderId());
        mAccountId = getIntent().getLongExtra("account", mApp.getDefaultAccountId());
        mLastChatId = getIntent().getLongExtra("chat", -1);
        mSubject = getIntent().getStringExtra("subject");

        mHandler = new Handler();
        if (Debug.DEBUG_ENABLED) {

            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .penaltyLog()
                    .penaltyFlashScreen()
                    .penaltyDeath()
                    .detectCustomSlowCalls()
                    .detectNetwork()
                    .build());
        }

    }

    private void initData () {

        Cursor cursor = getContentResolver().query(Imps.ProviderSettings.CONTENT_URI, new String[]{Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE}, Imps.ProviderSettings.PROVIDER + "=?", new String[]{Long.toString(mProviderId)}, null);

        if (cursor == null)
            return; //not going to work

        Imps.ProviderSettings.QueryMap providerSettings = new Imps.ProviderSettings.QueryMap(
                cursor, getContentResolver(), mProviderId, false, null);

        mMembers = new ArrayList<>();

        if (mProviderId == -1) {
            mProviderId = getIntent().getLongExtra("provider", mApp.getDefaultProviderId());
            mAccountId = getIntent().getLongExtra("account", mApp.getDefaultAccountId());
        }

        mConn = RemoteImService.getConnection(mProviderId, mAccountId);
        mLocalAddress = '@' + Imps.Account.getUserName(getContentResolver(), mAccountId) + ':' + providerSettings.getDomain();
        try {
            mSession = mConn.getChatSessionManager().getChatSession(mAddress);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        providerSettings.close();

        mYou = new GroupMemberDisplay();
        mYou.username = mLocalAddress;
        mYou.affiliation = "none";
        mYou.role = "none";

        updateMembers();

    }

    private void initRecyclerView () {

        if (mRecyclerView == null)
            mRecyclerView = (RecyclerView) findViewById(R.id.rvRoot);

        mRecyclerView.setAdapter(new RecyclerView.Adapter() {

            private static final int VIEW_TYPE_MEMBER = 0;
            private static final int VIEW_TYPE_HEADER = 1;
            private static final int VIEW_TYPE_FOOTER = 2;

            private int colorTextPrimary = 0xff000000;

            public RecyclerView.Adapter init() {
                TypedValue out = new TypedValue();
                getTheme().resolveAttribute(R.attr.contactTextPrimary, out, true);
                colorTextPrimary = out.data;
                return this;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                switch (viewType) {
                    case VIEW_TYPE_HEADER:
                        return new HeaderViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.awesome_activity_group_header, parent, false));
                    case VIEW_TYPE_FOOTER:
                        return new FooterViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.awesome_activity_group_footer, parent, false));
                }
                return new MemberViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.group_member_view, parent, false));
            }

            @Override
            public void onBindViewHolder(final RecyclerView.ViewHolder holder, int position) {
                if (holder instanceof HeaderViewHolder) {
                    final HeaderViewHolder h = (HeaderViewHolder)holder;

                    Drawable avatar = null;
                    if (DatabaseUtils.hasAvatarContact(getContentResolver(),Imps.Avatars.CONTENT_URI,mAddress))
                    {
                        try {

                            avatar = DatabaseUtils.getAvatarFromAddress(mAddress, DEFAULT_AVATAR_WIDTH, DEFAULT_AVATAR_HEIGHT, false);

                            if (avatar != null)
                                h.avatar.setImageDrawable(avatar);

                        } catch (Exception e) {
                            //problem decoding avatar
                            Log.e(LOG_TAG, "error decoding avatar", e);

                        }
                    }

                    if (avatar == null)
                    {
                        avatar = new GroupAvatar(mAddress.split("@")[0],false);
                        h.avatar.setImageDrawable(avatar);
                    }

                    mActionQR = h.qr;
                    h.qr.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            try {

                                try {
                                    if (mSession != null) {
                                        mSession.setPublic(true);
                                    }
                                } catch (Exception ignored) {
                                }


                                String publicAddress = mSession.getPublicAddress();

                                if (!TextUtils.isEmpty(publicAddress)) {
                                    String inviteLink = OnboardingManager.generateInviteLink(URLEncoder.encode(publicAddress, "UTF-8"));
                                    Intent intent = new Intent(GroupDisplayActivity.this, QrDisplayActivity.class);
                                    intent.putExtra(Intent.EXTRA_TEXT, inviteLink);
                                    startActivity(intent);
                                }

                            } catch (Exception e) {
                                Log.e(LOG_TAG, "couldn't generate QR code", e);
                            }
                        }
                    });

                    h.groupName.setText(mName);
                    h.groupAddress.setText(mAddress);

                    mActionShare = h.actionShare;
                    h.actionShare.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            try {
                                try {
                                    if (mSession != null) {
                                        mSession.setPublic(true);

                                        String publicAddress = mSession.getPublicAddress();

                                        if (!TextUtils.isEmpty(publicAddress)) {
                                            String inviteLink = OnboardingManager.generateInviteLink(URLEncoder.encode(publicAddress,"UTF-8"));
                                            new QrShareAsyncTask(GroupDisplayActivity.this).execute(inviteLink, mName);
                                        }

                                    }
                                } catch (Exception ignored) {
                                }

                            } catch (Exception e) {
                                Log.e(LOG_TAG, "couldn't generate QR code", e);
                            }
                        }
                    });

                    mActionAddFriends = h.actionAddFriends;
                    showAddFriends ();

                    /**
                    h.actionNotifications.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            setNotificationsEnabled(areNotificationsEnabled());
                            h.checkNotifications.setChecked(areNotificationsEnabled());
                        }
                    });*/

                    if (mSession != null) {
                        h.checkNotifications.setChecked(areNotificationsEnabled());
                        h.checkNotifications.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                setNotificationsEnabled(isChecked);
                            }
                        });
                        h.checkNotifications.setEnabled(true);
                    } else {
                        h.checkNotifications.setEnabled(false);
                    }

                    if (Preferences.doGroupEncryption() &&  canInviteOthers(mYou)) {
                        if (mSession != null) {
                            h.checkGroupEncryption.setChecked(isGroupEncryptionEnabled());

                            if (isGroupEncryptionEnabled())
                            {
                                h.checkGroupEncryption.setVisibility(View.GONE);
                                h.actionGroupEncryption.setVisibility(View.GONE);

                            }
                            else {
                                h.checkGroupEncryption.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                                    @Override
                                    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                                        setGroupEncryptionEnabled(isChecked);
                                    }
                                });
                                h.actionGroupEncryption.setVisibility(View.VISIBLE);
                                h.checkGroupEncryption.setVisibility(View.VISIBLE);
                                h.checkGroupEncryption.setEnabled(true);
                            }
                        } else {
                            h.checkGroupEncryption.setEnabled(false);
                        }
                    }
                    else {
                        h.actionGroupEncryption.setVisibility(View.GONE);
                    }

                    if (!canChangeSubject(mYou))
                        h.editGroupName.setVisibility(View.GONE);
                    else {
                        h.editGroupName.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                editGroupSubject();
                            }
                        });
                        h.editGroupName.setVisibility(View.VISIBLE);
                        h.editGroupName.setEnabled(mSession != null);
                    }
                } else if (holder instanceof FooterViewHolder) {
                    FooterViewHolder h = (FooterViewHolder)holder;

                    // Tint the "leave" text and drawable(s)
                    int colorAccent = ResourcesCompat.getColor(getResources(), R.color.holo_orange_light, getTheme());
                    for (Drawable d : h.actionLeave.getCompoundDrawables()) {
                        if (d != null) {
                            DrawableCompat.setTint(d, colorAccent);
                        }
                    }
                    h.actionLeave.setTextColor(colorAccent);
                    h.actionLeave.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            confirmLeaveGroup();
                        }
                    });
                } else if (holder instanceof MemberViewHolder) {
                    MemberViewHolder h = (MemberViewHolder) holder;

                    if (mMembers.size() == 0)
                    {
                        h.line1.setText(R.string.loading);
                        h.line2.setText("");
                    }
                    else {
                        // Reset the padding to match other views in this hierarchy
                        //
                        int padding = getResources().getDimensionPixelOffset(R.dimen.detail_view_padding);
                        h.itemView.setPadding(padding, h.itemView.getPaddingTop(), padding, h.itemView.getPaddingBottom());

                        int idxMember = position - 1;
                        final GroupMemberDisplay member = mMembers.get(idxMember);

                        if (member != null) {
                            String nickname = member.nickname;
                            if (TextUtils.isEmpty(nickname)) {
                                nickname = member.username.split("@")[0].split("\\.")[0];
                            } else {
                                nickname = nickname.split("@")[0].split("\\.")[0];
                            }

                            if (mYou.username.contentEquals(member.username)) {
                                nickname += " " + getString(R.string.group_you);
                            }


                            h.line2.setText(member.username);
                            if (member.affiliation != null && (member.affiliation.contentEquals("owner") || member.affiliation.contentEquals("admin"))) {

                                h.avatarCrown.setImageResource(R.drawable.ic_crown);
                                h.avatarCrown.setVisibility(View.VISIBLE);
                            } else if (member.affiliation != null && (member.affiliation.contentEquals("invited"))) {
                                h.avatarCrown.setImageResource(R.drawable.ic_message_wait_grey);
                                h.avatarCrown.setVisibility(View.VISIBLE);


                            } else {
                                h.avatarCrown.setVisibility(View.GONE);
                            }

                            h.line1.setText(nickname);

                            boolean hasRoleNone = TextUtils.isEmpty(member.role) || "none".equalsIgnoreCase(member.role);
                            h.line1.setTextColor(hasRoleNone ? Color.GRAY : colorTextPrimary);


                            /**
                             if (!member.online)
                             {
                             h.line1.setEnabled(false);
                             h.line2.setEnabled(false);
                             h.avatar.setBackgroundColor(getResources().getColor(R.color.holo_grey_light));
                             }**/
                            if (member.avatar == null) {
                                try {
                                    member.avatar = DatabaseUtils.getAvatarFromAddress(member.username, SMALL_AVATAR_WIDTH, SMALL_AVATAR_HEIGHT);
                                } catch (DecoderException e) {
                                    e.printStackTrace();
                                }
                            }

                            if (member.avatar == null) {
                                padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
                                member.avatar = new LetterAvatar(holder.itemView.getContext(), nickname, padding);
                            }

                            h.avatar.setImageDrawable(member.avatar);
                            h.avatar.setVisibility(View.VISIBLE);
                            h.itemView.setOnClickListener(v -> showMemberInfo(member));
                        }
                    }
                }
            }

            @Override
            public int getItemCount() {

                if (mMembers != null) {
                    if (mMembers.size() == 0) {
                        return 3;
                    } else
                        return 2 + mMembers.size();
                }
                else
                    return 3;
            }

            @Override
            public int getItemViewType(int position) {
                if (position == 0)
                    return VIEW_TYPE_HEADER;
                else if (position == getItemCount() - 1)
                    return VIEW_TYPE_FOOTER;
                return VIEW_TYPE_MEMBER;
            }
        }.init());
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));

    }

    public void updateSession() {

        new AsyncTask<Void,Void,Void>()
        {

            @Override
            protected Void doInBackground(Void... voids) {
                if (mConn == null)
                    initData();

                updateSessionAsync();
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);

                initRecyclerView();
            }
        }.execute();


    }

    private IChatSession updateSessionAsync() {
        try {

            if (mSession == null && mConn != null) {
                mSession = mConn.getChatSessionManager().getChatSession(mAddress);
            }

            if (mSession != null) {
                mSession.registerChatListener(mChatListener);
                mChatListenerRegistered = true;

                mSession.refreshContactFromServer();

                List<Contact> admins = mSession.getGroupChatAdmins();
                List<Contact> owners = mSession.getGroupChatOwners();
                if (admins != null) {
                    for (Contact c : admins) {
                        if (c.getAddress().getBareAddress().equals(mLocalAddress)) {
                            mYou.affiliation = "admin";
                            break;
                        }
                    }
                }
                if (owners != null) {
                    for (Contact c : owners) {
                        if (c.getAddress().getBareAddress().equals(mLocalAddress)) {
                            mYou.affiliation = "owner";
                            break;
                        }
                    }
                }




            }

        }
        catch (RemoteException e){}
        return mSession;
    }

    @Override
    public void onChatSessionCreated(IChatSession session) throws RemoteException {
       // updateSession();
    }

    @Override
    public void onChatSessionCreateError(String name, ImErrorInfo error) throws RemoteException {
       // updateSession();
    }

    @Override
    public IBinder asBinder() {
        return mConn.asBinder();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mConn == null)
        {
            initData();
        }

        if (mConn != null) {
            try {
                mConn.getChatSessionManager().registerChatSessionListener(GroupDisplayActivity.this);
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (mSession != null && !mChatListenerRegistered) {
                try {
                    mSession.registerChatListener(mChatListener);
                    mChatListenerRegistered = true;
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            initRecyclerView();

            updateSession();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mConn != null) {
            try {
                mConn.getChatSessionManager().unregisterChatSessionListener(this);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (mSession != null) {
                try {
                    mSession.unregisterChatListener(mChatListener);
                    mChatListenerRegistered = false;
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private synchronized void updateMembers() {


        mMembers.clear();

        String[] projection = {Imps.GroupMembers.USERNAME, Imps.GroupMembers.NICKNAME, Imps.GroupMembers.ROLE, Imps.GroupMembers.AFFILIATION};
        Uri memberUri = ContentUris.withAppendedId(Imps.GroupMembers.CONTENT_URI, mLastChatId);
        ContentResolver cr = getContentResolver();

        StringBuilder buf = new StringBuilder();
        buf.append(Imps.Messages.NICKNAME).append(" IS NOT NULL ");

        Cursor c = cr.query(memberUri, projection, buf.toString(), null, Imps.GroupMembers.ROLE+","+Imps.GroupMembers.AFFILIATION);
        if (c != null) {
            int colUsername = c.getColumnIndex(Imps.GroupMembers.USERNAME);
            int colNickname = c.getColumnIndex(Imps.GroupMembers.NICKNAME);
            int colRole = c.getColumnIndex(Imps.GroupMembers.ROLE);
            int colAffiliation = c.getColumnIndex(Imps.GroupMembers.AFFILIATION);

            while (c.moveToNext()) {
                GroupMemberDisplay member = new GroupMemberDisplay();
                member.username = c.getString(colUsername);
                member.nickname = c.getString(colNickname);
                member.role = c.getString(colRole);
                member.affiliation = c.getString(colAffiliation);
                if (mLocalAddress.contentEquals(member.username)) {
                    mYou = member;
                }

                boolean isImportant = (member.affiliation.contentEquals("owner") || member.affiliation.contentEquals("admin"));

                if (isImportant)
                    mMembers.add(0,member);
                else
                    mMembers.add(member);
            }
            c.close();
        }


        if (mRecyclerView != null && mRecyclerView.getAdapter() != null)
            mRecyclerView.getAdapter().notifyDataSetChanged();



    }

    public void inviteContacts (ArrayList<String> invitees)
    {
        if (mConn == null)
            return;

        try {
            IChatSessionManager manager = mConn.getChatSessionManager();
            IChatSession session = manager.getChatSession(mAddress);

            for (String invitee : invitees) {
                session.inviteContact(invitee);
                GroupMemberDisplay member = new GroupMemberDisplay();
                MatrixAddress address = new MatrixAddress(invitee);
                member.username = address.getBareAddress();
                member.nickname = address.getUser();
                try {
                    member.avatar = DatabaseUtils.getAvatarFromAddress(member.username, SMALL_AVATAR_WIDTH, SMALL_AVATAR_HEIGHT);
                } catch (DecoderException e) {
                    e.printStackTrace();
                }
                mMembers.add(member);
            }

            mRecyclerView.getAdapter().notifyDataSetChanged();


        }
        catch (Exception e)
        {
            Log.e(LOG_TAG,"error inviting contacts to group",e);
        }

    }

    public void showMemberInfo(final GroupMemberDisplay member) {
        if (member == mYou) {
            return;
        }

        final boolean canGrantAdmin = canGrantAdmin(mYou, member);
        final boolean canKickout = canRevokeMembership(mYou, member);
     //   final boolean isModerator = TextUtils.equals(mYou.role, "moderator")||TextUtils.equals(mYou.role, "admin");

        if ((canGrantAdmin || canKickout)) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            View content = LayoutInflater.from(this).inflate(R.layout.group_member_operations, null);

            MemberViewHolder h = new MemberViewHolder(content);
            String nickname = member.nickname;
            if (TextUtils.isEmpty(nickname))
                nickname = member.username;

            h.line1.setText(nickname);
            h.line2.setText(member.username);
            if (member.affiliation != null && (member.affiliation.contentEquals("owner") || member.affiliation.contentEquals("admin"))) {
                h.avatarCrown.setVisibility(View.VISIBLE);
            } else {
                h.avatarCrown.setVisibility(View.GONE);
            }
            if (member.avatar == null) {
                int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
                member.avatar = new LetterAvatar(h.itemView.getContext(), nickname, padding);
            }
            h.avatar.setImageDrawable(member.avatar);
            h.avatar.setVisibility(View.VISIBLE);

            alert.setView(content);
            final AlertDialog dialog = alert.show();
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            View actionMakeAdmin = content.findViewById(R.id.actionMakeAdmin);
            if (canGrantAdmin) {
                actionMakeAdmin.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                        grantAdmin(member);
                    }
                });
            } else {
                actionMakeAdmin.setVisibility(View.GONE);
            }

            View actionViewProfile = content.findViewById(R.id.actionViewProfile);
            actionViewProfile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    showMemberProfile(member);
                }
            });

            View actionKickout = content.findViewById(R.id.actionKickout);
            if (canKickout) {
                actionKickout.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                        kickout(member);
                    }
                });
            } else {
                actionKickout.setVisibility(View.GONE);
            }
        } else {
            showMemberProfile(member);
        }
    }

    private void showMemberProfile(GroupMemberDisplay member) {
        Intent intent = new Intent(this, ContactDisplayActivity.class);
        intent.putExtra("address", member.username);
        intent.putExtra("nickname", member.nickname);
        intent.putExtra("provider", mProviderId);
        intent.putExtra("account", mAccountId);
        startActivity(intent);
    }

    private void grantAdmin(GroupMemberDisplay member) {
        try {
            if (mSession != null)
                mSession.grantAdmin(member.username);
        } catch (Exception ignored) {}
    }

    private void kickout(GroupMemberDisplay member) {
        try {
            if (mSession != null)
                mSession.kickContact(member.username);
        } catch (Exception ignored) {}
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultIntent) {
        super.onActivityResult(requestCode, resultCode, resultIntent);

        if (resultCode == RESULT_OK) {

            if (requestCode == REQUEST_PICK_CONTACTS) {

                ArrayList<String> invitees = new ArrayList<String>();

                String username = resultIntent.getStringExtra(ContactsPickerActivity.EXTRA_RESULT_USERNAME);

                if (username != null)
                    invitees.add(username);
                else
                    invitees = resultIntent.getStringArrayListExtra(ContactsPickerActivity.EXTRA_RESULT_USERNAMES);

                inviteContacts(invitees);

                mHandler.postDelayed(() -> updateSession(), 3000);
            }
        }
    }

    private class HeaderViewHolder extends RecyclerView.ViewHolder {
        final ImageView avatar;
        final ImageView qr;
        final TextView groupName;
        final EditText groupNameEdit;
        final View editGroupName;
        final TextView groupAddress;
        final TextView actionShare;
        final TextView actionAddFriends;
        final View actionNotifications;
        final View actionGroupEncryption;
        final SwitchCompat checkNotifications;
        final SwitchCompat checkGroupEncryption;

        HeaderViewHolder(View view) {
            super(view);
            avatar = (ImageView) view.findViewById(R.id.ivAvatar);
            qr = (ImageView) view.findViewById(R.id.qrcode);
            groupName = (TextView) view.findViewById(R.id.tvGroupName);
            groupNameEdit = view.findViewById(R.id.tvGroupNameEdit);
            editGroupName = view.findViewById(R.id.edit_group_subject);
            groupAddress = (TextView) view.findViewById(R.id.tvGroupAddress);
            actionShare = (TextView) view.findViewById(R.id.tvActionShare);
            actionAddFriends = (TextView) view.findViewById(R.id.tvActionAddFriends);
            actionNotifications = view.findViewById(R.id.tvActionNotifications);
            actionGroupEncryption = view.findViewById(R.id.tvActionEncryption);
            checkNotifications = (SwitchCompat) view.findViewById(R.id.chkNotifications);
            checkGroupEncryption = (SwitchCompat) view.findViewById(R.id.chkGroupEncryption);
        }
    }

    private class FooterViewHolder extends RecyclerView.ViewHolder {
        final TextView actionLeave;

        FooterViewHolder(View view) {
            super(view);
            actionLeave = (TextView) view.findViewById(R.id.tvActionLeave);
        }
    }

    private class MemberViewHolder extends RecyclerView.ViewHolder {
        final TextView line1;
        final TextView line2;
        final ImageView avatar;
        final ImageView avatarCrown;

        MemberViewHolder(View view) {
            super(view);
            line1 = (TextView) view.findViewById(R.id.line1);
            line2 = (TextView) view.findViewById(R.id.line2);
            avatar = (ImageView) view.findViewById(R.id.avatar);
            avatarCrown = (ImageView) view.findViewById(R.id.avatarCrown);
        }
    }

    private void editGroupSubject() {

        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        input.setText(mName);
        alert.setView(input);
        alert.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String newSubject = input.getText().toString();
                changeGroupSubject(newSubject);
            }
        });

        alert.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Canceled.
            }
        });
        alert.show();
    }

    private void changeGroupSubject (String subject)
    {
        try {
            IChatSession session = mConn.getChatSessionManager().getChatSession(mAddress);
            session.setGroupChatSubject(subject, true);

            // Update the UI
            mName = subject;
            mRecyclerView.getAdapter().notifyItemChanged(0);
        }
        catch (Exception e) {}
    }

    boolean isGroupEncryptionEnabled () {
        try {
            if (mSession != null)
                return mSession.getUseEncryption();
            else
                return false;
        }
        catch (RemoteException re)
        {
            return true;
        }
    }

    public void setGroupEncryptionEnabled(boolean enabled) {
        try {
            if (mSession != null) {
                mSession.useEncryption(enabled);
            }
        }
        catch (Exception ignored){}
    }


    boolean areNotificationsEnabled() {
        try {
            if (mSession != null)
                return !mSession.isMuted();
            else
                return true;
        }
        catch (RemoteException re)
        {
            return true;
        }
    }

    public void setNotificationsEnabled(boolean enabled) {
        try {
            if (mSession != null) {
                mSession.setMuted(!enabled);
            }
        }
        catch (Exception ignored){}
    }

    private void confirmLeaveGroup ()
    {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.confirm_leave_group_title))
                .setMessage(getString(R.string.confirm_leave_group))
                .setPositiveButton(getString(R.string.action_leave), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        leaveGroup();
                    }
                })
                .setNeutralButton(getString(R.string.action_archive), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        archiveGroup();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void archiveGroup ()
    {
        try {


            Uri chatUri = ContentUris.withAppendedId(Imps.Chats.CONTENT_URI, mLastChatId);
            ContentValues values = new ContentValues();
            values.put(Imps.Chats.CHAT_TYPE,Imps.Chats.CHAT_TYPE_ARCHIVED);
            getContentResolver().update(chatUri,values,Imps.Chats.CONTACT_ID + "=" + mLastChatId,null);

            //clear the stack and go back to the main activity
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);


        }
        catch (Exception e)
        {
            Log.e(LOG_TAG,"error leaving group",e);
        }
    }

    private void leaveGroup ()
    {
        try {

            if (mSession != null) {
                mSession.leave();

                //clear the stack and go back to the main activity
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }

        }
        catch (Exception e)
        {
            Log.e(LOG_TAG,"error leaving group",e);
        }
    }

    private final IChatListener mChatListener = new ChatListenerAdapter() {

        boolean ignoreUpdates = false;

        @Override
        public void onContactJoined(IChatSession ses, Contact contact) {
            super.onContactJoined(ses, contact);
            if (!ignoreUpdates) {
                updateMembers();
            }
        }

        @Override
        public void onContactLeft(IChatSession ses, Contact contact) {
            super.onContactLeft(ses, contact);
            if (!ignoreUpdates) {
                updateMembers();
            }
        }

        @Override
        public void onContactRoleChanged(IChatSession ses, Contact contact) {
            super.onContactRoleChanged(ses, contact);
            if (!ignoreUpdates) {
                updateMembers();
            }
        }

        @Override
        public void onBeginMemberListUpdate(IChatSession ses) {
            super.onBeginMemberListUpdate(ses);
            ignoreUpdates = true;
        }

        @Override
        public void onEndMemberListUpdate(IChatSession ses) {
            super.onEndMemberListUpdate(ses);
            ignoreUpdates = false;
            updateMembers();
        }
    };

    private void showAddFriends ()
    {
        if (mActionAddFriends != null) {
            if (!canInviteOthers(mYou)) {
                mActionAddFriends.setVisibility(View.GONE);
                mActionShare.setVisibility(View.GONE);
                mActionQR.setVisibility(View.GONE);
            }
            else {
                mActionAddFriends.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(GroupDisplayActivity.this, ContactsPickerActivity.class);
                        ArrayList<String> usernames = new ArrayList<>(mMembers.size());
                        for (GroupMemberDisplay member : mMembers) {
                            usernames.add(member.username);
                        }
                        intent.putExtra(ContactsPickerActivity.EXTRA_EXCLUDED_CONTACTS, usernames);
                        startActivityForResult(intent, REQUEST_PICK_CONTACTS);
                    }
                });
                mActionShare.setVisibility(View.VISIBLE);
                mActionQR.setVisibility(View.VISIBLE);
                mActionAddFriends.setVisibility(View.VISIBLE);
                mActionAddFriends.setEnabled(mSession != null);
            }
        }
    }

    private boolean canChangeSubject(GroupMemberDisplay member) {
        return TextUtils.equals(member.role, "moderator") ||
                (TextUtils.equals(member.affiliation, "admin") || TextUtils.equals(member.affiliation, "owner"));
    }

    private boolean canInviteOthers(GroupMemberDisplay member) {
        return canChangeSubject(member);
    }

    public boolean canGrantAdmin(GroupMemberDisplay granter, GroupMemberDisplay grantee) {
        return canChangeSubject(granter) &&
                (TextUtils.equals(grantee.affiliation, "member") || TextUtils.equals(grantee.affiliation, "none"));
    }

    public boolean canRevokeMembership(GroupMemberDisplay revoker, GroupMemberDisplay revokee) {
        if (TextUtils.equals(revokee.affiliation, "owner")) {
            return false;
        }
        if (TextUtils.equals(revoker.affiliation, "owner")) {
            return true;
        }
        if (TextUtils.equals(revoker.affiliation, "admin") && !TextUtils.equals(revokee.affiliation, "admin")) {
            return true;
        }
        if (TextUtils.equals(revoker.role, "moderator")) {
            return true;
        }
        return false;
    }

}
