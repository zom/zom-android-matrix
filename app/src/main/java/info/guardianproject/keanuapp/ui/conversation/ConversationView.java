/*
 * Copyright (C) 2008 Esmertec AG. Copyright (C) 2008 The Android Open Source
 * Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package info.guardianproject.keanuapp.ui.conversation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Browser;
import android.support.design.widget.Snackbar;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.ActionMode;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import info.guardianproject.keanu.core.Preferences;
import info.guardianproject.keanu.core.model.impl.BaseAddress;
import info.guardianproject.keanu.core.service.IChatSessionListener;
import info.guardianproject.keanu.matrix.plugin.MatrixAddress;
import info.guardianproject.keanuapp.R;
import info.guardianproject.keanu.core.model.Address;
import info.guardianproject.keanu.core.model.Contact;
import info.guardianproject.keanu.core.model.ImConnection;
import info.guardianproject.keanu.core.model.ImErrorInfo;
import info.guardianproject.keanu.core.model.Presence;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.service.IChatListener;
import info.guardianproject.keanu.core.service.IChatSession;
import info.guardianproject.keanu.core.service.IChatSessionManager;
import info.guardianproject.keanu.core.service.IContactList;
import info.guardianproject.keanu.core.service.IContactListListener;
import info.guardianproject.keanu.core.service.IContactListManager;
import info.guardianproject.keanu.core.service.IImConnection;
import info.guardianproject.keanu.core.service.ISubscriptionListener;
import info.guardianproject.keanu.core.service.ImServiceConstants;
import info.guardianproject.keanu.core.service.RemoteImService;
import info.guardianproject.keanu.core.service.adapters.ChatListenerAdapter;
import info.guardianproject.keanu.core.tasks.ChatSessionInitTask;
import info.guardianproject.keanu.core.ui.RoundedAvatarDrawable;
import info.guardianproject.keanu.core.util.DatabaseUtils;
import info.guardianproject.keanu.core.util.Debug;
import info.guardianproject.keanu.core.util.LogCleaner;
import info.guardianproject.keanu.core.util.SecureMediaStore;
import info.guardianproject.keanu.core.util.SystemServices;
import info.guardianproject.keanuapp.ImApp;
import info.guardianproject.keanuapp.MainActivity;
import info.guardianproject.keanuapp.tasks.AddContactAsyncTask;
import info.guardianproject.keanuapp.ui.contacts.ContactDisplayActivity;
import info.guardianproject.keanuapp.ui.contacts.GroupDisplayActivity;
import info.guardianproject.keanuapp.ui.legacy.Markup;
import info.guardianproject.keanuapp.ui.legacy.SimpleAlertHandler;
import info.guardianproject.keanuapp.ui.stickers.Sticker;
import info.guardianproject.keanuapp.ui.stickers.StickerGroup;
import info.guardianproject.keanuapp.ui.stickers.StickerManager;
import info.guardianproject.keanuapp.ui.stickers.StickerPagerAdapter;
import info.guardianproject.keanuapp.ui.stickers.StickerSelectListener;
import info.guardianproject.keanuapp.ui.widgets.CursorRecyclerViewAdapter;
import info.guardianproject.keanuapp.ui.widgets.ImageViewActivity;
import info.guardianproject.keanuapp.ui.widgets.MessageViewHolder;
import info.guardianproject.keanuapp.ui.widgets.ShareRequest;

import static android.content.Context.CLIPBOARD_SERVICE;
import static info.guardianproject.keanu.core.KeanuConstants.DEFAULT_AVATAR_HEIGHT;
import static info.guardianproject.keanu.core.KeanuConstants.DEFAULT_AVATAR_WIDTH;
import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;
import static info.guardianproject.keanu.core.provider.Imps.ContactsColumns.SUBSCRIPTION_STATUS_NONE;

public class ConversationView {
    // This projection and index are set for the query of active chats
    public static final String[] CHAT_PROJECTION = { Imps.Contacts._ID, Imps.Contacts.ACCOUNT,
                                             Imps.Contacts.PROVIDER, Imps.Contacts.USERNAME,
                                             Imps.Contacts.NICKNAME, Imps.Contacts.TYPE,
                                             Imps.Presence.PRESENCE_STATUS,
                                             Imps.Chats.LAST_UNREAD_MESSAGE,
                                             Imps.Chats._ID,
                                             Imps.Contacts.SUBSCRIPTION_TYPE,
                                             Imps.Contacts.SUBSCRIPTION_STATUS,
                                             Imps.Contacts.AVATAR_DATA

    };

    public static final int CONTACT_ID_COLUMN = 0;
    public static final int ACCOUNT_COLUMN = 1;
    public static final int PROVIDER_COLUMN = 2;
    public static final int USERNAME_COLUMN = 3;
    public static final int NICKNAME_COLUMN = 4;
    public static final int TYPE_COLUMN = 5;
    public static final int PRESENCE_STATUS_COLUMN = 6;
    public static final int LAST_UNREAD_MESSAGE_COLUMN = 7;
    public static final int CHAT_ID_COLUMN = 8;
    public static final int SUBSCRIPTION_TYPE_COLUMN = 9;
    public static final int SUBSCRIPTION_STATUS_COLUMN = 10;
    public static final int AVATAR_COLUMN = 11;

    //static final int MIME_TYPE_COLUMN = 9;

    static final String[] INVITATION_PROJECT = { Imps.Invitation._ID, Imps.Invitation.PROVIDER,
                                                Imps.Invitation.SENDER, };
    static final int INVITATION_ID_COLUMN = 0;
    static final int INVITATION_PROVIDER_COLUMN = 1;
    static final int INVITATION_SENDER_COLUMN = 2;

    static final StyleSpan STYLE_BOLD = new StyleSpan(Typeface.BOLD);
    static final StyleSpan STYLE_NORMAL = new StyleSpan(Typeface.NORMAL);

    Markup mMarkup;

    ConversationDetailActivity mActivity;
    ImApp mApp;
    private SimpleAlertHandler mHandler;
    IImConnection mConn;

    //private ImageView mStatusIcon;
   // private TextView mTitle;
    /*package*/RecyclerView mHistory;
    EditText mComposeMessage;
    ShareRequest mShareDraft;

    private ImageButton mSendButton, mMicButton;
    private TextView mButtonTalk;
    private ImageButton mButtonAttach;
    private View mViewAttach;

    private ImageView mButtonDeleteVoice;
    private View mViewDeleteVoice;


    private ImageView mDeliveryIcon;
    private boolean mExpectingDelivery;

    private boolean mIsSelected = false;
    private boolean mIsVerified = false;

    private ConversationRecyclerViewAdapter mMessageAdapter;
 //   private boolean isServiceUp;
    private IChatSession mCurrentChatSession;

    long mLastChatId=-1;
    String mRemoteNickname;
    String mRemoteAddress;
    RoundedAvatarDrawable mRemoteAvatar = null;
    Drawable mRemoteHeader = null;
    int mSubscriptionType = Imps.Contacts.SUBSCRIPTION_TYPE_NONE;
    int mSubscriptionStatus = SUBSCRIPTION_STATUS_NONE;

    long mProviderId = -1;
    long mAccountId = -1;
    long mInvitationId;
    private Activity mContext; // TODO
    private int mPresenceStatus;
    private Date mLastSeen;

    private int mViewType;

    private static final int VIEW_TYPE_CHAT = 1;
    private static final int VIEW_TYPE_INVITATION = 2;
    private static final int VIEW_TYPE_SUBSCRIPTION = 3;

//    private static final long SHOW_TIME_STAMP_INTERVAL = 30 * 1000; // 15 seconds
    private static final long SHOW_DELIVERY_INTERVAL = 10 * 1000; // 5 seconds
    private static final long SHOW_MEDIA_DELIVERY_INTERVAL = 120 * 1000; // 2 minutes
    private static final long DEFAULT_QUERY_INTERVAL = 2000;
    private static final long FAST_QUERY_INTERVAL = 200;

    private RequeryCallback mRequeryCallback = null;

    public SimpleAlertHandler getHandler() {
        return mHandler;
    }

    public int getType() {
        return mViewType;
    }

    private class RequeryCallback implements Runnable {
        public void run() {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
                log("RequeryCallback");
            }
            requeryCursor();

        }
    }


    public void setSelected (boolean isSelected)
    {
        mIsSelected = isSelected;

        if (mIsSelected)
        {
            startListening();

            updateWarningView();
            mComposeMessage.requestFocus();
            userActionDetected();
            updateGroupTitle();

            try {
                mApp.dismissChatNotification(mProviderId,mRemoteAddress);
                mCurrentChatSession.markAsRead();
            }
            catch (Exception e){}

        }
        else
        {
            stopListening();
            sendTypingStatus (false);
        }

    }

    public void inviteContacts (ArrayList<String> invitees)
    {
        if (mConn == null)
            return;

        getChatSession(new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] objects) {
                try
                {
                    for (String invitee : invitees)
                        mCurrentChatSession.inviteContact(invitee);

                }
                catch (RemoteException re)
                {
                    Log.e(getClass().getName(),"error inviting contact",re);
                }

                return null;
            }
        });




    }

    private boolean checkConnection ()
    {
        if (mProviderId == -1 || mAccountId == -1)
            return false;

        if (mConn == null) {

            if (RemoteImService.mImService == null)
            {
                mActivity.startService(new Intent(mActivity,RemoteImService.class));
            }

            while (true) {

                mConn = RemoteImService.getConnection(mProviderId, mAccountId);

                if (mConn != null)
                    break;

                try { Thread.sleep (3000);}catch(Exception e){}
            }

        }

        return true;


    }

    private OnItemClickListener mOnItemClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (!(view instanceof MessageListItem)) {
                return;
            }

            URLSpan[] links = ((MessageListItem) view).getMessageLinks();
            if (links.length > 0) {

                final ArrayList<String> linkUrls = new ArrayList<String>(links.length);
                for (URLSpan u : links) {
                    linkUrls.add(u.getURL());
                }
                ArrayAdapter<String> a = new ArrayAdapter<String>(mActivity,
                        android.R.layout.select_dialog_item, linkUrls);
                AlertDialog.Builder b = new AlertDialog.Builder(mActivity);
                b.setTitle(R.string.select_link_title);
                b.setCancelable(true);
                b.setAdapter(a, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Uri uri = Uri.parse(linkUrls.get(which));
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        intent.putExtra(ImServiceConstants.EXTRA_INTENT_PROVIDER_ID, mProviderId);
                        intent.putExtra(ImServiceConstants.EXTRA_INTENT_ACCOUNT_ID, mAccountId);
                        intent.putExtra(Browser.EXTRA_APPLICATION_ID, mActivity.getPackageName());
                        mActivity.startActivity(intent);
                    }
                });
                b.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                b.show();
            }
        }
    };

    private final static int PROMPT_FOR_DATA_TRANSFER = 9999;
    private final static int SHOW_DATA_PROGRESS = 9998;
    private final static int SHOW_DATA_ERROR = 9997;
    private final static int SHOW_TYPING = 9996;


    private IChatListener mChatListener = new ChatListenerAdapter() {
        @Override
        public boolean onIncomingMessage(IChatSession ses,
                info.guardianproject.keanu.core.model.Message msg) {

            try {
                if (getChatSession().getId() != ses.getId())
                    return false;
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            return mIsSelected;
        }

        @Override
        public void onContactJoined(IChatSession ses, Contact contact) {
        }

        @Override
        public void onContactLeft(IChatSession ses, Contact contact) {
        }

        @Override
        public void onSendMessageError(IChatSession ses,

                info.guardianproject.keanu.core.model.Message msg, ImErrorInfo error) {


            try {
                if (getChatSession().getId() != ses.getId())
                    return;
            }
            catch (RemoteException re){}

        }

        @Override
        public void onIncomingReceipt(IChatSession ses, String packetId, boolean wasEncrypted) throws RemoteException {

            if (getChatSession().getId() != ses.getId())
                return;

            scheduleRequery(DEFAULT_QUERY_INTERVAL);
        }

        @Override
        public void onStatusChanged(IChatSession ses) throws RemoteException {


            if (getChatSession().getId() != ses.getId())
                return;

            scheduleRequery(DEFAULT_QUERY_INTERVAL);

        }


        @Override
        public void onIncomingFileTransfer(String transferFrom, String transferUrl) throws RemoteException {

            String[] path = transferUrl.split("/");
            String sanitizedPath = SystemServices.sanitize(path[path.length - 1]);

            Message message = Message.obtain(null, PROMPT_FOR_DATA_TRANSFER, (int) (mProviderId >> 32),
                    (int) mProviderId, -1);
            message.getData().putString("from", transferFrom);
            message.getData().putString("file", sanitizedPath);
            mHandler.sendMessage(message);

            log("onIncomingFileTransfer: " + transferFrom + " @ " + transferUrl);

        }

        @Override
        public void onIncomingFileTransferProgress(String file, int percent)
                throws RemoteException {

            /**
            android.os.Message message = android.os.Message.obtain(null, SHOW_DATA_PROGRESS, (int) (mProviderId >> 32),
                    (int) mProviderId, -1);
            message.getData().putString("file", file);
            message.getData().putInt("progress", percent);

            mHandler.sendMessage(message);*/

            log ("onIncomingFileTransferProgress: " + file + " " + percent + "%");

        }

        @Override
        public void onIncomingFileTransferError(String file, String err) throws RemoteException {



            Message message = Message.obtain(null, SHOW_DATA_ERROR, (int) (mProviderId >> 32),
                    (int) mProviderId, -1);
            message.getData().putString("file", file);
            message.getData().putString("err", err);

            mHandler.sendMessage(message);

            log("onIncomingFileTransferProgress: " + file + " err: " + err);

        }

        @Override
        public void onContactTyping(IChatSession ses, Contact contact, boolean isTyping) throws RemoteException {
            super.onContactTyping(ses, contact, isTyping);

            if (getChatSession().getId() != ses.getId())
                return;

                if (contact.getPresence() != null) {
                mPresenceStatus = contact.getPresence().getStatus();
                mLastSeen = contact.getPresence().getLastSeen();
            }
            else
            {
                mLastSeen = new Date();
            }

            mActivity.updateLastSeen(mLastSeen);


            Message message = Message.obtain(null, SHOW_TYPING, (int) (mProviderId >> 32),
                    (int) mProviderId, -1);

            message.getData().putBoolean("typing", isTyping);

            mHandler.sendMessage(message);
        }

        @Override
        public void onGroupSubjectChanged(IChatSession ses) throws RemoteException {
            super.onGroupSubjectChanged(ses);
            if (getChatSession().getId() == ses.getId()) {
                updateGroupTitle();
            }
        }
    };

    private void showPromptForData (final String transferFrom, String filePath)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);

        builder.setTitle(mContext.getString(R.string.file_transfer));
        builder.setMessage(transferFrom + ' ' + mActivity.getString(R.string.wants_to_send_you_the_file)
                + " '" + filePath + "'. " + mActivity.getString(R.string.accept_transfer_));

        builder.setNeutralButton(R.string.button_yes_accept_all, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {

                try {
                    mCurrentChatSession.setIncomingFileResponse(transferFrom, true, true);
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                dialog.dismiss();
            }

        });

        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                try {
                    mCurrentChatSession.setIncomingFileResponse(transferFrom, true, false);
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                dialog.dismiss();
            }

        });

        builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                try {
                    mCurrentChatSession.setIncomingFileResponse(transferFrom, false, false);
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }


                // Do nothing
                dialog.dismiss();
            }
        });

        AlertDialog alert = builder.create();
        alert.show();

    }

    private Runnable mUpdateChatCallback = new Runnable() {
        public void run() {
                updateChat();

        }
    };


    private IContactListListener mContactListListener = new IContactListListener.Stub() {
        public void onAllContactListsLoaded() {
        }

        public void onContactChange(int type, IContactList list, Contact contact) {

            if (contact.getAddress().getBareAddress().equals(mRemoteAddress)) {

                if (contact != null && contact.getPresence() != null) {
                    mPresenceStatus = contact.getPresence().getStatus();

                }
                mLastSeen = new Date();
                mActivity.updateLastSeen(mLastSeen);

            }
        }

        public void onContactError(int errorType, ImErrorInfo error, String listName,
                Contact contact) {
        }

        public void onContactsPresenceUpdate(Contact[] contacts) {

            if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
                log("onContactsPresenceUpdate()");
            }

            for (Contact c : contacts) {
                if (c.getAddress().getBareAddress().equals(mRemoteAddress)) {

                    if (c != null && c.getPresence() != null)
                    {
                        mPresenceStatus = c.getPresence().getStatus();

                        if (mPresenceStatus != Presence.OFFLINE) {
                            mLastSeen = c.getPresence().getLastSeen();
                            mActivity.updateLastSeen(mLastSeen);
                        }

                    }

                    mHandler.post(mUpdateChatCallback);
                    scheduleRequery(DEFAULT_QUERY_INTERVAL);
                    break;
                }
            }

        }
    };



    private boolean mIsListening;

    static final void log(String msg) {
        if (Debug.DEBUG_ENABLED)
            Log.d(LOG_TAG, "<ChatView> " + msg);
    }

    public ConversationView(ConversationDetailActivity activity) {

        mActivity = activity;
        mContext = activity;

        mApp = (ImApp)mActivity.getApplication();
        mHandler = new ChatViewHandler(mActivity);

        initViews();
    }

    void registerForConnEvents() {
        mApp.registerForConnEvents(mHandler);
    }

    void unregisterForConnEvents() {
        mApp.unregisterForConnEvents(mHandler);
    }

    protected void initViews() {
      //  mStatusIcon = (ImageView) mActivity.findViewById(R.id.statusIcon);
     //   mDeliveryIcon = (ImageView) mActivity.findViewById(R.id.deliveryIcon);
       // mTitle = (TextView) mActivity.findViewById(R.id.title);
        mHistory = (RecyclerView) mActivity.findViewById(R.id.history);
        final LinearLayoutManager llm = new LinearLayoutManager(mHistory.getContext());
        llm.setStackFromEnd(true);
        mHistory.setLayoutManager(llm);
        mHistory.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                int visibleItemCount = llm.getChildCount();
                int totalItemCount = llm.getItemCount();
                int pastVisibleItems = llm.findFirstVisibleItemPosition();
                if (pastVisibleItems + visibleItemCount >= totalItemCount) {
                    //End of list
                    mMessageAdapter.onScrollStateChanged(null,RecyclerView.SCROLL_STATE_IDLE);

                }
                else
                    mMessageAdapter.onScrollStateChanged(null,RecyclerView.SCROLL_STATE_DRAGGING);

            }
        });

        mComposeMessage = (EditText) mActivity.findViewById(R.id.composeMessage);
        mSendButton = (ImageButton) mActivity.findViewById(R.id.btnSend);
        mMicButton = (ImageButton) mActivity.findViewById(R.id.btnMic);
        mButtonTalk = (TextView)mActivity.findViewById(R.id.buttonHoldToTalk);

        mButtonDeleteVoice = (ImageView)mActivity.findViewById(R.id.btnDeleteVoice);
        mViewDeleteVoice = mActivity.findViewById(R.id.viewDeleteVoice);

        mButtonDeleteVoice.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {

                if(motionEvent.getAction() == MotionEvent.ACTION_MOVE)
                {
                    int resolvedColor = mHistory.getResources().getColor(android.R.color.holo_red_light);
                    mButtonDeleteVoice.setBackgroundColor(resolvedColor);
                }

                return false;
            }
        });


        mButtonAttach = (ImageButton) mActivity.findViewById(R.id.btnAttach);
        mViewAttach = mActivity.findViewById(R.id.attachPanel);

        mButtonAttach.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {


                toggleAttachMenu ();
            }

        });

        mActivity.findViewById(R.id.mediaPreviewCancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearMediaDraft();

            }
        });

        mActivity.findViewById(R.id.btnAttachPicture).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mViewAttach.setVisibility(View.INVISIBLE);
                mActivity.startImagePicker();
            }

        });

        mActivity.findViewById(R.id.btnTakePicture).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mViewAttach.setVisibility(View.INVISIBLE);
                mActivity.startPhotoTaker();
            }

        });


        mActivity.findViewById(R.id.btnAttachAudio).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mViewAttach.setVisibility(View.INVISIBLE);
                mActivity.startFilePicker("audio/*");
            }

        });

        mActivity.findViewById(R.id.btnAttachFile).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mViewAttach.setVisibility(View.INVISIBLE);
                mActivity.startFilePicker("*/*");
            }

        });

        mActivity.findViewById(R.id.btnAttachSticker).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                toggleAttachMenu();
                showStickers();
            }

        });



        mMicButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                //this is the tap to change to hold to talk mode
                if (mMicButton.getVisibility() == View.VISIBLE) {
                    mComposeMessage.setVisibility(View.GONE);
                    mMicButton.setVisibility(View.GONE);

                    // Check if no view has focus:
                    View view = mActivity.getCurrentFocus();
                    if (view != null) {
                        InputMethodManager imm = (InputMethodManager)mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    }

                    mSendButton.setImageResource(R.drawable.ic_keyboard_black_36dp);
                    mSendButton.setVisibility(View.VISIBLE);
                    mButtonTalk.setVisibility(View.VISIBLE);

                }
            }

        });


        final GestureDetector gestureDetector = new GestureDetector(new GestureDetector.SimpleOnGestureListener() {
            public void onLongPress(MotionEvent e) {

                //this is for recording audio directly from one press
                mActivity.startAudioRecording();

            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {


                if (mActivity.isAudioRecording()) {
                    boolean send = true;//inViewInBounds(mMicButton, (int) motionEvent.getX(), (int) motionEvent.getY());
                    mActivity.stopAudioRecording(send);
                }

                return super.onSingleTapUp(e);
            }
        });

        mMicButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return gestureDetector.onTouchEvent(motionEvent);

            }
        });

        mButtonTalk.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch( View btnTalk , MotionEvent theMotion ) {
                switch ( theMotion.getAction() ) {
                    case MotionEvent.ACTION_DOWN:
                        mActivity.startAudioRecording();
                        mButtonTalk.setText(mActivity.getString(R.string.recording_release));
                        mViewDeleteVoice.setVisibility(View.VISIBLE);

                        break;
                    case MotionEvent.ACTION_MOVE:
                        boolean inBounds = inViewInBounds(btnTalk,(int)theMotion.getX(),(int)theMotion.getY());
                        if (!inBounds)
                            mButtonTalk.setText(mActivity.getString(R.string.recording_delete));
                        else {
                            mButtonTalk.setText(mActivity.getString(R.string.recording_release));
                            mViewDeleteVoice.setVisibility(View.VISIBLE);
                        }
                            break;
                    case MotionEvent.ACTION_UP:
                        mButtonTalk.setText(mActivity.getString(R.string.push_to_talk));
                        boolean send = inViewInBounds(btnTalk,(int)theMotion.getX(),(int)theMotion.getY());
                        mActivity.stopAudioRecording(send);
                        mViewDeleteVoice.setVisibility(View.GONE);

                        break;
                }
                return true;
            }
        });
        /**
        mHistory.setOnItemLongClickListener(new OnItemLongClickListener ()
        {

            @TargetApi(Build.VERSION_CODES.HONEYCOMB)
            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {


             if (arg1 instanceof MessageView)
             {

                 String textToCopy = ((MessageView)arg1).getLastMessage();

                 int sdk = android.os.Build.VERSION.SDK_INT;
                 if(sdk < android.os.Build.VERSION_CODES.HONEYCOMB) {
                     android.text.ClipboardManager clipboard = (android.text.ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                     clipboard.setText(textToCopy); //
                 } else {
                     android.content.ClipboardManager clipboard = (android.content.ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                     android.content.ClipData clip = android.content.ClipData.newPlainText("chat",textToCopy);
                     clipboard.setPrimaryClip(clip); //
                 }

                 Toast.makeText(mActivity, mContext.getString(R.string.toast_chat_copied_to_clipboard), Toast.LENGTH_SHORT).show();

                 return true;

             }

                return false;
            }

        });**/

        mComposeMessage.setOnTouchListener(new View.OnTouchListener()
        {

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {

                sendTypingStatus (mComposeMessage.getText().length() > 0);

                return false;
            }
        });

        mComposeMessage.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {

                sendTypingStatus (hasFocus && (mComposeMessage.getText().length() > 0));

            }
        });

        mComposeMessage.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_DPAD_CENTER:
                            sendMessage();
                            return true;

                        case KeyEvent.KEYCODE_ENTER:
                            if (event.isAltPressed()) {
                                mComposeMessage.append("\n");
                                return true;
                            }
                    }

                }


                return false;
            }
        });

        mComposeMessage.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (event != null) {
                    if (event.isAltPressed()) {
                        return false;
                    }
                }

                InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null && imm.isActive(v)) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
                sendMessage();
                return true;
            }
        });

        // TODO: this is a hack to implement BUG #1611278, when dispatchKeyEvent() works with
        // the soft keyboard, we should remove this hack.
        mComposeMessage.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int before, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int after) {

            }

            public void afterTextChanged(Editable s) {
                userActionDetected();
            }
        });

        mSendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                if (mComposeMessage.getVisibility() == View.VISIBLE)
                    sendMessage();
                else
                {
                    mSendButton.setImageResource(R.drawable.ic_send_secure);

                    mSendButton.setVisibility(View.GONE);
                    mButtonTalk.setVisibility(View.GONE);
                    mComposeMessage.setVisibility(View.VISIBLE);
                    mMicButton.setVisibility(View.VISIBLE);


                }
            }
        });

        mMessageAdapter = new ConversationRecyclerViewAdapter(mActivity, null);
        mHistory.setAdapter(mMessageAdapter);

    }

    private boolean mLastIsTyping = false;

    private void sendTypingStatus (boolean isTyping) {

      if (mLastIsTyping != isTyping) {
            try {
                if (mConn != null)
                    mConn.sendTypingStatus(mRemoteAddress, isTyping);
            } catch (Exception ie) {
                Log.e(LOG_TAG, "error sending typing status", ie);
            }

            mLastIsTyping = isTyping;
        }

    }

    private void sendMessageRead (String msgId) {


        try {
            if (mConn != null)
                mConn.sendMessageRead(mRemoteAddress, msgId);
        } catch (Exception ie) {
            Log.e(LOG_TAG, "error sending typing status", ie);
        }


    }
    /**
    PopupMenu mPopupWords = null;
    SearchWordTask taskSearch = null;

    private class SearchWordTask extends AsyncTask<String, Long, ArrayList<String>> {

        private String mLastSearchTerm = null;

        protected ArrayList<String> doInBackground(String... searchTerm) {

            String[] searchTerms = searchTerm[0].split("་");

            if (searchTerms.length > 0) {
                mLastSearchTerm = searchTerms[searchTerms.length - 1];

                ArrayList<String> result = ds.getMatchingWords(mLastSearchTerm);

                return result;
            }
            else
                return null;
        }

        protected void onProgressUpdate(Long... progress) {

        }

        protected void onPostExecute(ArrayList<String> result) {
            if (result != null && result.size() > 0) {

                if (mPopupWords == null) {

                    mPopupWords = new PopupMenu(mActivity, mComposeMessage);

                    mPopupWords.setOnMenuItemClickListener(new
                           PopupMenu.OnMenuItemClickListener() {
                               @Override
                               public boolean onMenuItemClick(MenuItem item) {

                                   String[] currentText = mComposeMessage.getText().toString().split("་");

                                   currentText[currentText.length-1] = item.toString();

                                   mComposeMessage.setText("");

                                   for (int i = 0; i < currentText.length; i++)
                                   {
                                       mComposeMessage.append(currentText[i]);

                                       if ((i+1)!=currentText.length)
                                        mComposeMessage.append("་");
                                   }

                                   mComposeMessage.setSelection(mComposeMessage.getText().length());

                                   return true;
                               }
                           });

                }

                mPopupWords.getMenu().clear();

                for (String item : result) {
                    if (!TextUtils.isEmpty(item)) {
                        SpannableStringBuilder sb = new SpannableStringBuilder(item);
                        sb.setSpan(new CustomTypefaceSpan("", mActivity), 0, item.length(), 0);
                        mPopupWords.getMenu().addSubMenu(sb);

                    }


                }

                mPopupWords.show();

            }
        }
    }

    private void doWordSearch ()
    {

        if (Preferences.getUseTibetanDictionary()) {
            if (ds == null)
                ds = new DictionarySearch(mActivity);

            String searchText = mComposeMessage.getText().toString();

            if (!TextUtils.isEmpty(searchText)) {
                if (taskSearch == null || taskSearch.getStatus() == AsyncTask.Status.FINISHED) {
                    taskSearch = new SearchWordTask();
                    taskSearch.execute(mComposeMessage.getText().toString());

                }
            }
        }

    }**/

    private boolean inViewInBounds(View view, int x, int y){
        Rect outRect = new Rect();
        int[] location = new int[2];

        view.getHitRect(outRect);

        return outRect.contains(x,y);
    }

    public void startListening() {

        mIsListening = true;

        registerChatListener();
        registerForConnEvents();

       // updateWarningView();
    }

    public void stopListening() {
        //Cursor cursor = getMessageCursor();
        //if (cursor != null && (!cursor.isClosed())) {
         //   cursor.close();
       // }

        cancelRequery();
        unregisterChatListener();
        unregisterForConnEvents();
        mIsListening = false;
    }

    void updateChat() {
        setViewType(VIEW_TYPE_CHAT);

        checkConnection();

        startQuery(getChatId());

        updateWarningView();

        if (isGroupChat()) {
            updateGroupTitle();
        }

    }

    int mContactType = -1;

    private void updateSessionInfo(Cursor c) {

        if (c != null && (!c.isClosed()))
        {
            mProviderId = c.getLong(PROVIDER_COLUMN);
            mAccountId = c.getLong(ACCOUNT_COLUMN);
            mPresenceStatus = c.getInt(PRESENCE_STATUS_COLUMN);
            mContactType = c.getInt(TYPE_COLUMN);

            mRemoteNickname = c.getString(NICKNAME_COLUMN);
            mRemoteAddress = c.getString(USERNAME_COLUMN);

            mSubscriptionType = c.getInt(SUBSCRIPTION_TYPE_COLUMN);

            mSubscriptionStatus = c.getInt(SUBSCRIPTION_STATUS_COLUMN);


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


        }


    }

    private boolean hasJoined ()
    {
        return mSubscriptionStatus == Imps.Contacts.SUBSCRIPTION_STATUS_NONE;
    }

    private void showJoinGroupUI ()
    {
        final View joinGroupView = mActivity.findViewById(R.id.join_group_view);

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


    public String getTitle ()
    {
        return mRemoteNickname;

    }

    public String getSubtitle ()
    {
        return mRemoteAddress;
    }

    public Date getLastSeen ()
    {
        return mLastSeen;
    }

    public RoundedAvatarDrawable getIcon ()
    {
        return mRemoteAvatar;
    }

    public Drawable getHeader ()
    {
        return mRemoteHeader;
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


    private void updateGroupTitle() {
        if (isGroupChat()) {

            // Update title
            final String[] projection = { Imps.GroupMembers.NICKNAME };
            Uri contactUri = ContentUris.withAppendedId(Imps.Contacts.CONTENT_URI, mLastChatId);
            ContentResolver cr = mActivity.getContentResolver();
            Cursor c = cr.query(contactUri, projection, null, null, null);
            if (c != null) {
                if (c.moveToFirst()) {
                    int col = c.getColumnIndex(projection[0]);
                    mRemoteNickname = c.getString(col);
                }
                c.close();
            }

            if (mRemoteNickname == null) {
                StringBuilder buf = new StringBuilder();

                int count = -1;

                try {
                    buf.append(mCurrentChatSession.getName());
                    count = mCurrentChatSession.getParticipants().length;
                } catch (Exception e) {
                }

                if (count > 0) {
                    buf.append(" (");
                    buf.append(count);
                    buf.append(")");
                }
                mRemoteNickname = buf.toString();
            }
            mActivity.getSupportActionBar().setTitle(mRemoteNickname);
        }
    }

    private void deleteChat ()
    {
        Uri chatUri = ContentUris.withAppendedId(Imps.Chats.CONTENT_URI, mLastChatId);
        mActivity.getContentResolver().delete(chatUri,null,null);

    }

    public boolean bindChat(long chatId, String address, String name) {
        //log("bind " + this + " " + chatId);

        mLastChatId = chatId;

        setViewType(VIEW_TYPE_CHAT);
        bindQuery();

        return true;
    }

    private boolean bindQuery () {

        Uri contactUri = ContentUris.withAppendedId(Imps.Contacts.CONTENT_URI, mLastChatId);
        Cursor c = mActivity.getContentResolver().query(contactUri, CHAT_PROJECTION, null, null, null);

        if (c == null)
            return false;

        if (c.getColumnCount() < 8)
            return false;

            try {
                c.moveToFirst();
            }
            catch (IllegalStateException ise)
            {
                return false;
            }

            updateSessionInfo(c);

            if (mRemoteAvatar == null)
            {
                try {mRemoteAvatar = DatabaseUtils.getAvatarFromCursor(c, AVATAR_COLUMN, DEFAULT_AVATAR_WIDTH, DEFAULT_AVATAR_HEIGHT);}
                catch (Exception e){}

                if (mRemoteAvatar == null)
                {
                    mRemoteAvatar = new RoundedAvatarDrawable(BitmapFactory.decodeResource(mActivity.getResources(),
                            R.drawable.avatar_unknown));

                }


            }

            if (mRemoteHeader == null)
            {
                try {mRemoteHeader = DatabaseUtils.getHeaderImageFromCursor(c, AVATAR_COLUMN, DEFAULT_AVATAR_WIDTH,DEFAULT_AVATAR_HEIGHT);}
                catch (Exception e){}
            }


            c.close();

            initSession ();

            mHandler.post(mUpdateChatCallback);



            return true;


    }

    boolean showContactName = true;

    private void initSession ()
    {
        mCurrentChatSession = getChatSession();
        if (mCurrentChatSession == null)
            createChatSession();
    }



    private void setViewType(int type) {
        mViewType = type;
        if (type == VIEW_TYPE_CHAT) {
       //     mActivity.findViewById(R.id.invitationPanel).setVisibility(View.GONE);
       //     mActivity.findViewById(R.id.subscription).setVisibility(View.GONE);
            setChatViewEnabled(true);
        } else if (type == VIEW_TYPE_INVITATION) {
            //setChatViewEnabled(false);
         //   mActivity.findViewById(R.id.invitationPanel).setVisibility(View.VISIBLE);
           // mActivity.findViewById(R.id.btnAccept).requestFocus();
        } else if (type == VIEW_TYPE_SUBSCRIPTION) {
            //setChatViewEnabled(false);
         //   mActivity.findViewById(R.id.subscription).setVisibility(View.VISIBLE);

           // mActivity.findViewById(R.id.btnApproveSubscription).requestFocus();
        }
    }

    private void setChatViewEnabled(boolean enabled) {
        mComposeMessage.setEnabled(enabled);
        mSendButton.setEnabled(enabled);
        if (enabled) {
            // This can steal focus from the fragment that's i n front of the user
            //mComposeMessage.requestFocus();
        } else {
            mHistory.setAdapter(null);
        }

    }

    RecyclerView getHistoryView() {
        return mHistory;
    }

    private Uri mUri;
    private LoaderManager mLoaderManager;
    private int loaderId = 100001;

    private synchronized void startQuery(long chatId) {

        mUri = Imps.Messages.getContentUriByThreadId(chatId);

        mLoaderManager = mActivity.getSupportLoaderManager();

        if (mLoaderManager == null)
            mLoaderManager.initLoader(loaderId++, null, new MyLoaderCallbacks());
        else
            mLoaderManager.restartLoader(loaderId++, null, new MyLoaderCallbacks());

    }

    class MyLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {
        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {

            CursorLoader loader = new CursorLoader(mActivity, mUri, null, null, null, Imps.Messages.DEFAULT_SORT_ORDER);

            return loader;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor newCursor) {

            if (newCursor != null) {

                newCursor.setNotificationUri(mActivity.getApplicationContext().getContentResolver(), mUri);
                mMessageAdapter.swapCursor(new DeltaCursor(newCursor));


                if (!mMessageAdapter.isScrolling()) {

                    mHandler.post(new Runnable() {

                        public void run() {
                            if (mMessageAdapter.getItemCount() > 0) {
                                mHistory.getLayoutManager().scrollToPosition(mMessageAdapter.getItemCount() - 1);
                            }
                        }
                    });
                }

            }

        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {

            mMessageAdapter.swapCursor(null);

        }
    }

    void scheduleRequery(long interval) {


        if (mRequeryCallback == null) {
            mRequeryCallback = new RequeryCallback();
        } else {
            mHandler.removeCallbacks(mRequeryCallback);
        }

        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            log("scheduleRequery");
        }
        mHandler.postDelayed(mRequeryCallback, interval);


    }

    void cancelRequery() {

        if (mRequeryCallback != null) {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
                log("cancelRequery");
            }
            mHandler.removeCallbacks(mRequeryCallback);
            mRequeryCallback = null;
        }
    }


    void requeryCursor() {

        mMessageAdapter.notifyDataSetChanged();
        mLoaderManager.restartLoader(loaderId++, null, new MyLoaderCallbacks());
//        updateWarningView();

        /**
        if (mMessageAdapter.isScrolling()) {
            mMessageAdapter.setNeedRequeryCursor(true);
            return;
        }

        // This is redundant if there are messages in view, because the cursor requery will update everything.
        // However, if there are no messages, no update will trigger below, and we still want this to update.


        // TODO: async query?
        Cursor cursor = getMessageCursor();
        if (cursor != null) {
            cursor.requery();
        }*/
    }

    private Cursor getMessageCursor() {
        return mMessageAdapter == null ? null : mMessageAdapter.getCursor();
    }

    public void closeChatSession(boolean doDelete) {
        if (getChatSession() != null) {
            try {

                updateWarningView();
                getChatSession().leave();

            } catch (RemoteException e) {

                mHandler.showServiceErrorAlert(e.getLocalizedMessage());
                LogCleaner.error(LOG_TAG, "send message error",e);
            }
        }

        if (doDelete)
            deleteChat();

    }


    public void showGroupInfo () {

        Intent intent = new Intent(mContext, GroupDisplayActivity.class);
        intent.putExtra("nickname", mRemoteNickname);
        intent.putExtra("address", mRemoteAddress);
        intent.putExtra("provider", mProviderId);
        intent.putExtra("account", mAccountId);
        intent.putExtra("chat", mLastChatId);

        mContext.startActivity(intent);
    }

    public void blockContact() {
        // TODO: unify with codes in ContactListView
        DialogInterface.OnClickListener confirmListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                try {
                    checkConnection();
                    mConn = RemoteImService.getConnection(mProviderId,mAccountId);
                    IContactListManager manager = mConn.getContactListManager();
                    manager.blockContact(mRemoteAddress);
                  //  mNewChatActivity.finish();
                } catch (Exception e) {

                    mHandler.showServiceErrorAlert(e.getLocalizedMessage());
                    LogCleaner.error(LOG_TAG, "send message error",e);
                }
            }
        };

        Resources r = mActivity.getResources();

        // The positive button is deliberately set as no so that
        // the no is the default value
        new AlertDialog.Builder(mContext).setTitle(R.string.confirm)
                .setMessage(r.getString(R.string.confirm_block_contact, mRemoteNickname))
                .setPositiveButton(R.string.yes, confirmListener) // default button
                .setNegativeButton(R.string.no, null).setCancelable(false).show();
    }

    public long getProviderId() {
        return mProviderId;
    }

    public long getAccountId() {
        return mAccountId;
    }

    public long getChatId() {
        return mLastChatId;
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

    public IChatSession getChatSession() {
        return getChatSession(null);
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

    public boolean isGroupChat() {
        return (this.mContactType & Imps.Contacts.TYPE_MASK) == Imps.Contacts.TYPE_GROUP;
    }

    void sendMessage() {

        if (mShareDraft != null)
        {
            mActivity.sendShareRequest(mShareDraft);
            mShareDraft = null;
            mActivity.findViewById(R.id.mediaPreviewContainer).setVisibility(View.GONE);
        }

        String msg = mComposeMessage.getText().toString();
        String replyId = null;

        if (mMessageAdapter.getLastSelectedView() != null)
            replyId = ((MessageListItem)mMessageAdapter.getLastSelectedView()).getPacketId();

        if (!TextUtils.isEmpty(msg))
            sendMessageAsync(msg, replyId);

        sendTypingStatus (false);

    }

    void setMessageDraft (String draftMessage) {

        mComposeMessage.setText(draftMessage);
        mComposeMessage.requestFocus();
    }

    void setMediaDraft (ShareRequest mediaDraft) throws IOException {

        mShareDraft = mediaDraft;

        mActivity.findViewById(R.id.mediaPreviewContainer).setVisibility(View.VISIBLE);
        Bitmap bmpPreview = SecureMediaStore.getThumbnailFile(mActivity,mediaDraft.media,1000);
        ((ImageView)mActivity.findViewById(R.id.mediaPreview)).setImageBitmap(bmpPreview);

        mViewAttach.setVisibility(View.GONE);
        mComposeMessage.setText(" ");
        toggleInputMode();

    }

    void clearMediaDraft () {
        mShareDraft = null;
        mComposeMessage.setText("");
        mActivity.findViewById(R.id.mediaPreviewContainer).setVisibility(View.GONE);
    }

    void deleteMessage (String packetId, String message)
    {
        if (!TextUtils.isEmpty(message)) {
            Uri deleteUri = Uri.parse(message);

            if (deleteUri.getScheme() != null && deleteUri.getScheme().equals("vfs")) {
                info.guardianproject.iocipher.File fileMedia = new info.guardianproject.iocipher.File(deleteUri.getPath());
                fileMedia.delete();
            }
        }

        Imps.deleteMessageInDb(mContext.getContentResolver(), packetId);

        requeryCursor();
    }

    void resendMessage (final String resendMsg, final String mimeType) {

        if (!TextUtils.isEmpty(resendMsg))
        {
            if (SecureMediaStore.isVfsUri(resendMsg)||SecureMediaStore.isContentUri(resendMsg))
            {
                //what do we do with this?

                ShareRequest request = new ShareRequest();
                request.deleteFile = false;
                request.resizeImage = false;
                request.importContent = false;
                request.media = Uri.parse(resendMsg);
                request.mimeType = mimeType;

                try {
                    mActivity.sendShareRequest(request);
                }
                catch (Exception e){
                    Log.w(LOG_TAG,"error setting media draft",e);
                }
            }
            else
            {
                sendMessageAsync(resendMsg, null);
            }
        }
    }

    void sendMessageAsync(final String msg, String replyId) {

        new AsyncTask<String, Void, Boolean>()
        {

            @Override
            protected Boolean doInBackground(String[] msgs) {
                return sendMessage(msgs[0],false,replyId);
            }

            @Override
            protected void onPostExecute(Boolean aBoolean) {
                super.onPostExecute(aBoolean);

                if (aBoolean.booleanValue())
                {
                    mComposeMessage.setText("");
                    mComposeMessage.requestFocus();
                }
            }
        }.execute(msg);


    }

    boolean sendMessage(String msg, boolean isResend, String replyId) {

        //don't send empty messages
        if (TextUtils.isEmpty(msg.trim())) {
            return false;
        }

        checkConnection();

        //otherwise get the session, create if necessary, and then send
        IChatSession session = getChatSession();

        if (session == null)
            createChatSession();
        else {
            try {
                session.sendMessage(msg, isResend, false, true, replyId);
                return true;
                //requeryCursor();
            } catch (RemoteException e) {

              //  mHandler.showServiceErrorAlert(e.getLocalizedMessage());
                LogCleaner.error(LOG_TAG, "send message error",e);
            } catch (Exception e) {

              //  mHandler.showServiceErrorAlert(e.getLocalizedMessage());
                LogCleaner.error(LOG_TAG, "send message error",e);
            }
        }

        return false;
    }



    void registerChatListener() {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            log("registerChatListener " + mLastChatId);
        }
        try {


            if (getChatSession() != null) {
                getChatSession().registerChatListener(mChatListener);
            }

            if (mConn != null)
            {
                IContactListManager listMgr = mConn.getContactListManager();
                listMgr.registerContactListListener(mContactListListener);

            }

        } catch (Exception e) {
            Log.w(LOG_TAG, "<ChatView> registerChatListener fail:" + e.getMessage());
        }
    }

    void unregisterChatListener() {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            log("unregisterChatListener " + mLastChatId);
        }
        try {
            if (getChatSession() != null) {
                getChatSession().unregisterChatListener(mChatListener);
            }

            if (mConn != null) {
                IContactListManager listMgr = mConn.getContactListManager();
                listMgr.unregisterContactListListener(mContactListListener);

            }
        } catch (Exception e) {
            Log.w(LOG_TAG, "<ChatView> unregisterChatListener fail:" + e.getMessage());
        }
    }

    void updateWarningView() {

        int visibility = View.GONE;
        int iconVisibility = View.GONE;
        boolean isConnected;


        try {
            isConnected = (mConn == null) ? false : mConn.getState() == ImConnection.LOGGED_IN;

        } catch (Exception e) {

            isConnected = false;
        }
    }



    public int getRemotePresence ()
    {
        return mPresenceStatus;
    }



    private void userActionDetected() {
        // Check that we have a chat session and that our fragment is resumed
        // The latter filters out bogus TextWatcher events on restore from saved
        if (getChatSession() != null && mIsListening) {
            try {
                getChatSession().markAsRead();
                //updateWarningView();

                sendTypingStatus(mComposeMessage.getText().length() > 0);
            } catch (RemoteException e) {

                mHandler.showServiceErrorAlert(e.getLocalizedMessage());
                LogCleaner.error(LOG_TAG, "send message error",e);
            }
        }

        toggleInputMode ();

    }

    private void toggleInputMode ()
    {
        if (mButtonTalk.getVisibility() == View.GONE) {
            if (mComposeMessage.getText().length() > 0 && mSendButton.getVisibility() == View.GONE) {
                mMicButton.setVisibility(View.GONE);
                mSendButton.setVisibility(View.VISIBLE);
                //mSendButton.setImageResource(R.drawable.ic_send_holo_light);

                    mSendButton.setImageResource(R.drawable.ic_send_secure);


            } else if (mComposeMessage.getText().length() == 0) {
                mMicButton.setVisibility(View.VISIBLE);
                mSendButton.setVisibility(View.GONE);

            }
        }
    }

    private final class ChatViewHandler extends SimpleAlertHandler {


        public ChatViewHandler(Activity activity) {
            super(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            long providerId = ((long) msg.arg1 << 32) | msg.arg2;
            if (providerId != mProviderId) {
                return;
            }

            switch (msg.what) {

            case ImApp.EVENT_CONNECTION_DISCONNECTED:
                log("Handle event connection disconnected.");
                updateWarningView();
                promptDisconnectedEvent(msg);
                return;
            case PROMPT_FOR_DATA_TRANSFER:
                showPromptForData(msg.getData().getString("from"),msg.getData().getString("file"));
                break;
            case SHOW_DATA_ERROR:

                String fileName = msg.getData().getString("file");
                String error = msg.getData().getString("err");

                Toast.makeText(mContext, "Error transferring file: " + error, Toast.LENGTH_LONG).show();
                break;
            case SHOW_DATA_PROGRESS:

                int percent = msg.getData().getInt("progress");


                break;
            case SHOW_TYPING:

                boolean isTyping = msg.getData().getBoolean("typing");
                mActivity.findViewById(R.id.tvTyping).setVisibility(isTyping ? View.VISIBLE : View.GONE);

             default:
                 updateWarningView();
            }

            super.handleMessage(msg);
        }
    }

    public static class DeltaCursor implements Cursor {
        static final String DELTA_COLUMN_NAME = "delta";

        private Cursor mInnerCursor;
        private String[] mColumnNames;
        private int mDateColumn = -1;
        private int mDeltaColumn = -1;

        DeltaCursor(Cursor cursor) {
            mInnerCursor = cursor;

            String[] columnNames = cursor.getColumnNames();
            int len = columnNames.length;

            mColumnNames = new String[len + 1];

            for (int i = 0; i < len; i++) {
                mColumnNames[i] = columnNames[i];
                if (mColumnNames[i].equals(Imps.Messages.DATE)) {
                    mDateColumn = i;
                }
            }

            mDeltaColumn = len;
            mColumnNames[mDeltaColumn] = DELTA_COLUMN_NAME;

            //if (DBG) log("##### DeltaCursor constructor: mDeltaColumn=" +
            //        mDeltaColumn + ", columnName=" + mColumnNames[mDeltaColumn]);
        }

        public int getCount() {
            return mInnerCursor.getCount();
        }

        public int getPosition() {
            return mInnerCursor.getPosition();
        }

        public boolean move(int offset) {
            return mInnerCursor.move(offset);
        }

        public boolean moveToPosition(int position) {
            return mInnerCursor.moveToPosition(position);
        }

        public boolean moveToFirst() {
            return mInnerCursor.moveToFirst();
        }

        public boolean moveToLast() {
            return mInnerCursor.moveToLast();
        }

        public boolean moveToNext() {
            return mInnerCursor.moveToNext();
        }

        public boolean moveToPrevious() {
            return mInnerCursor.moveToPrevious();
        }

        public boolean isFirst() {
            return mInnerCursor.isFirst();
        }

        public boolean isLast() {
            return mInnerCursor.isLast();
        }

        public boolean isBeforeFirst() {
            return mInnerCursor.isBeforeFirst();
        }

        public boolean isAfterLast() {
            return mInnerCursor.isAfterLast();
        }

        public int getColumnIndex(String columnName) {
            if (DELTA_COLUMN_NAME.equals(columnName)) {
                return mDeltaColumn;
            }

            int columnIndex = mInnerCursor.getColumnIndex(columnName);
            return columnIndex;
        }

        public int getColumnIndexOrThrow(String columnName) throws IllegalArgumentException {
            if (DELTA_COLUMN_NAME.equals(columnName)) {
                return mDeltaColumn;
            }

            return mInnerCursor.getColumnIndexOrThrow(columnName);
        }

        public String getColumnName(int columnIndex) {
            if (columnIndex == mDeltaColumn) {
                return DELTA_COLUMN_NAME;
            }

            return mInnerCursor.getColumnName(columnIndex);
        }

        public int getColumnCount() {
            return mInnerCursor.getColumnCount() + 1;
        }

        public void deactivate() {
            mInnerCursor.deactivate();
        }

        public boolean requery() {
            return mInnerCursor.requery();
        }

        public void close() {
            mInnerCursor.close();
        }

        public boolean isClosed() {
            return mInnerCursor.isClosed();
        }

        public void registerContentObserver(ContentObserver observer) {
            mInnerCursor.registerContentObserver(observer);
        }

        public void unregisterContentObserver(ContentObserver observer) {
            mInnerCursor.unregisterContentObserver(observer);
        }

        public void registerDataSetObserver(DataSetObserver observer) {
            mInnerCursor.registerDataSetObserver(observer);
        }

        public void unregisterDataSetObserver(DataSetObserver observer) {
            mInnerCursor.unregisterDataSetObserver(observer);
        }

        public void setNotificationUri(ContentResolver cr, Uri uri) {
            mInnerCursor.setNotificationUri(cr, uri);
        }

        public boolean getWantsAllOnMoveCalls() {
            return mInnerCursor.getWantsAllOnMoveCalls();
        }

        @Override
        public void setExtras(Bundle bundle) {

        }

        public Bundle getExtras() {
            return mInnerCursor.getExtras();
        }

        public Bundle respond(Bundle extras) {
            return mInnerCursor.respond(extras);
        }

        public String[] getColumnNames() {
            return mColumnNames;
        }

        private void checkPosition() {
            int pos = mInnerCursor.getPosition();
            int count = mInnerCursor.getCount();

            if (-1 == pos || count == pos) {
                throw new CursorIndexOutOfBoundsException(pos, count);
            }
        }

        public byte[] getBlob(int column) {
            checkPosition();

            if (column == mDeltaColumn) {
                return null;
            }

            return mInnerCursor.getBlob(column);
        }

        public String getString(int column) {
            checkPosition();

            if (column == mDeltaColumn) {
                long value = getDeltaValue();
                return Long.toString(value);
            }

            return mInnerCursor.getString(column);
        }

        public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {
            checkPosition();

            if (columnIndex == mDeltaColumn) {
                long value = getDeltaValue();
                String strValue = Long.toString(value);
                int len = strValue.length();
                char[] data = buffer.data;
                if (data == null || data.length < len) {
                    buffer.data = strValue.toCharArray();
                } else {
                    strValue.getChars(0, len, data, 0);
                }
                buffer.sizeCopied = strValue.length();
            } else {
                mInnerCursor.copyStringToBuffer(columnIndex, buffer);
            }
        }

        public short getShort(int column) {
            checkPosition();

            if (column == mDeltaColumn) {
                return (short) getDeltaValue();
            }

            return mInnerCursor.getShort(column);
        }

        public int getInt(int column) {
            checkPosition();

            if (column == mDeltaColumn) {
                return (int) getDeltaValue();
            }

            return mInnerCursor.getInt(column);
        }

        public long getLong(int column) {
            //if (DBG) log("DeltaCursor.getLong: column=" + column + ", mDeltaColumn=" + mDeltaColumn);
            checkPosition();

            if (column == mDeltaColumn) {
                return getDeltaValue();
            }

            return mInnerCursor.getLong(column);
        }

        public float getFloat(int column) {
            checkPosition();

            if (column == mDeltaColumn) {
                return getDeltaValue();
            }

            return mInnerCursor.getFloat(column);
        }

        public double getDouble(int column) {
            checkPosition();

            if (column == mDeltaColumn) {
                return getDeltaValue();
            }

            return mInnerCursor.getDouble(column);
        }

        public boolean isNull(int column) {
            checkPosition();

            if (column == mDeltaColumn) {
                return false;
            }

            return mInnerCursor.isNull(column);
        }

        private long getDeltaValue() {
            int pos = mInnerCursor.getPosition();
            //Log.i(LOG_TAG, "getDeltaValue: mPos=" + mPos);

            long t2, t1;

            if (pos == getCount() - 1) {
                t1 = mInnerCursor.getLong(mDateColumn);
                t2 = System.currentTimeMillis();
            } else {
                mInnerCursor.moveToPosition(pos + 1);
                t2 = mInnerCursor.getLong(mDateColumn);
                mInnerCursor.moveToPosition(pos);
                t1 = mInnerCursor.getLong(mDateColumn);
            }

            return t2 - t1;
        }

        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
		public int getType(int arg0) {
            return mInnerCursor.getType(arg0);
        }

        @TargetApi(19)
		@Override
        public Uri getNotificationUri() {
            return mInnerCursor.getNotificationUri();
        }

    }

    public class ConversationRecyclerViewAdapter
            extends CursorRecyclerViewAdapter<MessageViewHolder> implements MessageViewHolder.OnImageClickedListener {

        private int mScrollState;
        private boolean mNeedRequeryCursor;

        private int mNicknameColumn;
        private int mBodyColumn;
        private int mDateColumn;
        private int mTypeColumn;
        private int mErrCodeColumn;
        private int mDeltaColumn;
        private int mDeliveredColumn;
        private int mMimeTypeColumn;
        private int mIdColumn;
        private int mPacketIdColumn;
        private int mReplyIdColumn;

        private ActionMode mActionMode;
        private View mLastSelectedView;

        public ConversationRecyclerViewAdapter(Activity context, Cursor c) {
            super(context, c);
            if (c != null) {
                resolveColumnIndex(c);
            }

            setHasStableIds(true);
        }

        public View getLastSelectedView ()
        {
            return mLastSelectedView;
        }

        private void resolveColumnIndex(Cursor c) {
            mNicknameColumn = c.getColumnIndexOrThrow(Imps.Messages.NICKNAME);
            mBodyColumn = c.getColumnIndexOrThrow(Imps.Messages.BODY);
            mDateColumn = c.getColumnIndexOrThrow(Imps.Messages.DATE);
            mTypeColumn = c.getColumnIndexOrThrow(Imps.Messages.TYPE);
            mErrCodeColumn = c.getColumnIndexOrThrow(Imps.Messages.ERROR_CODE);
            mDeltaColumn = c.getColumnIndexOrThrow(DeltaCursor.DELTA_COLUMN_NAME);
            mDeliveredColumn = c.getColumnIndexOrThrow(Imps.Messages.IS_DELIVERED);
            mMimeTypeColumn = c.getColumnIndexOrThrow(Imps.Messages.MIME_TYPE);
            mIdColumn = c.getColumnIndexOrThrow(Imps.Messages._ID);
            mPacketIdColumn = c.getColumnIndexOrThrow(Imps.Messages.PACKET_ID);
            mReplyIdColumn = c.getColumnIndexOrThrow(Imps.Messages.REPLY_ID);
        }

        @Override
        public Cursor swapCursor(Cursor newCursor) {
            if (newCursor != null) {
                resolveColumnIndex(newCursor);
            }
            return super.swapCursor(newCursor);
        }

        @Override
        public long getItemId (int position)
        {
            Cursor c = getCursor();
            c.moveToPosition(position);
            long chatId =  c.getLong(mIdColumn);
            return chatId;
        }

        @Override
        public int getItemViewType(int position) {

            Cursor c = getCursor();
            c.moveToPosition(position);
            int type = c.getInt(mTypeColumn);
            boolean isLeft = (type == Imps.MessageType.INCOMING_ENCRYPTED)||(type == Imps.MessageType.INCOMING);

            if (isLeft)
                return 0;
            else
                return 1;

        }

        public Cursor getItem (int position)
        {
            Cursor c = getCursor();
            c.moveToPosition(position);
            return c;
        }


        void setLinkifyForMessageView(MessageListItem messageView) {
            try {

                if (messageView == null)
                    return;

                if (mConn != null)
                    messageView.setLinkify(!mConn.isUsingTor() || Preferences.getDoLinkify());

            } catch (RemoteException e) {
                e.printStackTrace();
                messageView.setLinkify(false);
            }
        }


        @Override
        public MessageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            MessageListItem view = null;
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());

            if (viewType == 0)
                view = (MessageListItem)inflater
                    .inflate(R.layout.message_view_left, parent, false);
            else
                view = (MessageListItem)inflater
                        .inflate(R.layout.message_view_right, parent, false);

            view.setOnLongClickListener(new View.OnLongClickListener() {
                // Called when the user long-clicks on someView
                public boolean onLongClick(View view) {
                    if (mActionMode != null) {
                        return false;
                    }

                    mLastSelectedView = view;

                    // Start the CAB using the ActionMode.Callback defined above
                    mActionMode = ((Activity) mContext).startActionMode(mActionModeCallback);

                    return true;
                }
            });


            MessageViewHolder mvh = new MessageViewHolder(view);
            mvh.setLayoutInflater(inflater);
            mvh.setOnImageClickedListener(this);
            view.applyStyleColors();
            return mvh;
        }

        @Override
        public void onBindViewHolder(MessageViewHolder viewHolder, Cursor cursor) {

            MessageListItem messageView = (MessageListItem) viewHolder.itemView;
            setLinkifyForMessageView(messageView);
            messageView.applyStyleColors();

            int messageType = cursor.getInt(mTypeColumn);

            String address = isGroupChat() ? cursor.getString(mNicknameColumn)  : mRemoteAddress;
            String nickname = isGroupChat() ? cursor.getString(mNicknameColumn) : mRemoteNickname;

            String mimeType = cursor.getString(mMimeTypeColumn);
            int id = cursor.getInt(mIdColumn);
            String body = cursor.getString(mBodyColumn);
            long delta = cursor.getLong(mDeltaColumn);
            boolean showTimeStamp = true;//(delta > SHOW_TIME_STAMP_INTERVAL);
            long timestamp = cursor.getLong(mDateColumn);
            String packetId = cursor.getString(mPacketIdColumn);
            String replyId = cursor.getString(mReplyIdColumn);

            Date date = showTimeStamp ? new Date(timestamp) : null;
            boolean isDelivered = cursor.getLong(mDeliveredColumn) > 0;
            long showDeliveryInterval = (mimeType == null) ? SHOW_DELIVERY_INTERVAL : SHOW_MEDIA_DELIVERY_INTERVAL;
            boolean showDelivery = ((System.currentTimeMillis() - timestamp) > showDeliveryInterval);

            MessageListItem.DeliveryState deliveryState = MessageListItem.DeliveryState.NEUTRAL;

            if (showDelivery && !isDelivered && mExpectingDelivery) {
                deliveryState = MessageListItem.DeliveryState.UNDELIVERED;
            }
            else if (isDelivered)
            {
                deliveryState = MessageListItem.DeliveryState.DELIVERED;
            }



            if (!mExpectingDelivery && isDelivered) {
                mExpectingDelivery = true;
            } else if (cursor.getPosition() == cursor.getCount() - 1) {
                /*
                // if showTimeStamp is false for the latest message, then set a timer to query the
                // cursor again in a minute, so we can update the last message timestamp if no new
                // message is received
                if (Log.isLoggable(ImApp.LOG_TAG, Log.DEBUG)) {
                    log("delta = " + delta + ", showTs=" + showTimeStamp);
                }
                *//*
                if (!showDelivery) {
                    scheduleRequery(SHOW_DELIVERY_INTERVAL);
                } else if (!showTimeStamp) {
                    scheduleRequery(SHOW_TIME_STAMP_INTERVAL);
                } else {
                    cancelRequery();
                }*/
            }

            MessageListItem.EncryptionState encState = MessageListItem.EncryptionState.NONE;
            if (messageType == Imps.MessageType.INCOMING_ENCRYPTED)
            {
                messageType = Imps.MessageType.INCOMING;
                encState = MessageListItem.EncryptionState.ENCRYPTED;
            }
            else if (messageType == Imps.MessageType.OUTGOING_ENCRYPTED)
            {
                messageType = Imps.MessageType.OUTGOING;
                encState = MessageListItem.EncryptionState.ENCRYPTED;
            }

            switch (messageType) {
            case Imps.MessageType.INCOMING:
                messageView.bindIncomingMessage(viewHolder,id, messageType, address, nickname, mimeType, body, date, mMarkup, false, encState, showContactName, mPresenceStatus, mCurrentChatSession, packetId, replyId);

                break;

            case Imps.MessageType.OUTGOING:
            case Imps.MessageType.QUEUED:
            case Imps.MessageType.SENDING:


                int errCode = cursor.getInt(mErrCodeColumn);
                if (errCode != 0) {
                    messageView.bindErrorMessage(errCode);
                } else {
                    messageView.bindOutgoingMessage(viewHolder, id, messageType, null, mimeType, body, date, mMarkup, false,
                            deliveryState, encState, packetId);
                }

                break;

            default:
                messageView.bindPresenceMessage(viewHolder, nickname, messageType, date, isGroupChat(), false);
            }

            sendMessageRead(packetId);

        }

        public void onScrollStateChanged(AbsListView viewNew, int scrollState) {
            int oldState = mScrollState;
            mScrollState = scrollState;

            if (getChatSession() != null) {
                try {
                    getChatSession().markAsRead();
                } catch (RemoteException e) {

                mHandler.showServiceErrorAlert(e.getLocalizedMessage());
                LogCleaner.error(LOG_TAG, "send message error",e);
                }
            }


            if (oldState == AbsListView.OnScrollListener.SCROLL_STATE_FLING) {
                if (mNeedRequeryCursor) {
                    requeryCursor();
                } else {
                    notifyDataSetChanged();
                }
            }
        }

        boolean isScrolling() {
            return mScrollState != RecyclerView.SCROLL_STATE_IDLE;
        }

        void setNeedRequeryCursor(boolean requeryCursor) {
            mNeedRequeryCursor = requeryCursor;
        }

        ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

            // Called when the action mode is created; startActionMode() was called
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                // Inflate a menu resource providing context menu items
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.menu_message_context, menu);
                return true;
            }

            // Called each time the action mode is shown. Always called after onCreateActionMode, but
            // may be called multiple times if the mode is invalidated.
            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false; // Return false if nothing is done
            }

            // Called when the user selects a contextual menu item
            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {

                switch (item.getItemId()) {
                    case R.id.menu_message_delete:
                        deleteMessage(((MessageListItem)mLastSelectedView).getPacketId(),((MessageListItem)mLastSelectedView).getLastMessage());
                        mode.finish(); // Action picked, so close the CAB
                        return true;
                    case R.id.menu_message_share:
                        ((MessageListItem)mLastSelectedView).exportMediaFile();
                        mode.finish(); // Action picked, so close the CAB
                        return true;
                    case R.id.menu_message_forward:
                        ((MessageListItem)mLastSelectedView).forwardMediaFile();
                        mode.finish(); // Action picked, so close the CAB
                        return true;
                    case R.id.menu_message_nearby:
                        ((MessageListItem)mLastSelectedView).nearbyMediaFile();
                        mode.finish(); // Action picked, so close the CAB
                        return true;
                    case R.id.menu_message_resend:
                        resendMessage(((MessageListItem)mLastSelectedView).getLastMessage(),((MessageListItem)mLastSelectedView).getMimeType());
                        mode.finish(); // Action picked, so close the CAB
                        return true;
                    case R.id.menu_message_copy:
                        String messageText = ((MessageListItem)mLastSelectedView).getLastMessage();
                        ClipboardManager clipboard = (ClipboardManager) mActivity.getSystemService(CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText(mActivity.getString(R.string.app_name), messageText);
                        clipboard.setPrimaryClip(clip);
                        mode.finish();
                        Toast.makeText(mActivity, R.string.action_copied,Toast.LENGTH_SHORT).show();
                        return true;

                    default:
                        return false;
                }


            }

            // Called when the user exits the action mode
            @Override
            public void onDestroyActionMode(ActionMode mode) {
                mActionMode = null;


                if (mLastSelectedView != null)
                    mLastSelectedView.setSelected(false);


            }
        };

        @Override
        public void onImageClicked(MessageViewHolder viewHolder, Uri image) {
            Cursor c = getCursor();
            if (c != null && c.moveToFirst()) {
                ArrayList<Uri> urisToShow = new ArrayList<>(c.getCount());
                ArrayList<String> mimeTypesToShow = new ArrayList<>(c.getCount());
                ArrayList<String> messagePacketIds = new ArrayList<>(c.getCount());

                do {
                    try {
                        String mime = c.getString(mMimeTypeColumn);
                        if (!TextUtils.isEmpty(mime) && mime.startsWith("image")) {
                            Uri uri = Uri.parse(c.getString(mBodyColumn));
                            urisToShow.add(uri);
                            mimeTypesToShow.add(mime);
                            messagePacketIds.add(c.getString(mPacketIdColumn));
                        }
                    } catch (Exception ignored) {
                    }
                } while (c.moveToNext());

                Intent intent = new Intent(mContext, ImageViewActivity.class);

                intent.putExtra("showResend",true);

                // These two are parallel arrays
                intent.putExtra(ImageViewActivity.URIS, urisToShow);
                intent.putExtra(ImageViewActivity.MIME_TYPES, mimeTypesToShow);
                intent.putExtra(ImageViewActivity.MESSAGE_IDS, messagePacketIds);

                int indexOfCurrent = urisToShow.indexOf(image);
                if (indexOfCurrent == -1) {
                    indexOfCurrent = 0;
                }
                intent.putExtra(ImageViewActivity.CURRENT_INDEX, indexOfCurrent);
                mContext.startActivityForResult(intent,ConversationDetailActivity.REQUEST_IMAGE_VIEW);
            }
        }
    }

    public Cursor getMessageAtPosition(int position) {
        Object item = mMessageAdapter.getItem(position);

        return (Cursor) item;
    }

    public EditText getComposedMessage() {
        return mComposeMessage;
    }

    /**
    public void onServiceConnected() {
            bindChat(mLastChatId, null, null);
            startListening();
    }**/

    private void toggleAttachMenu ()
    {
        if (mViewAttach.getVisibility() == View.INVISIBLE) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

                // get the center for the clipping circle
                int cx = mViewAttach.getLeft();
                int cy = mViewAttach.getHeight();

                // get the final radius for the clipping circle
                float finalRadius = (float) Math.hypot(cx, cy);

                // create the animator for this view (the start radius is zero)
                Animator anim =
                        ViewAnimationUtils.createCircularReveal(mViewAttach, cx, cy, 0, finalRadius);

                // make the view visible and start the animation

                mViewAttach.setVisibility(View.VISIBLE);
                anim.start();
            }
            else
            {
                mViewAttach.setVisibility(View.VISIBLE);

            }

            // Check if no view has focus:
            View view = mActivity.getCurrentFocus();
            if (view != null) {
                InputMethodManager imm = (InputMethodManager)mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
        else {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

                // get the center for the clipping circle
                int cx = mViewAttach.getLeft();
                int cy = mViewAttach.getHeight();

// get the initial radius for the clipping circle
                float initialRadius = (float) Math.hypot(cx, cy);

// create the animation (the final radius is zero)
                Animator anim =
                        ViewAnimationUtils.createCircularReveal(mViewAttach, cx, cy, initialRadius, 0);

// make the view invisible when the animation is done
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        mViewAttach.setVisibility(View.INVISIBLE);
                    }
                });

// start the animation
                anim.start();

            }
            else
            {
                mViewAttach.setVisibility(View.INVISIBLE);
            }

        }


        if (mStickerBox != null)
            mStickerBox.setVisibility(View.GONE);
    }

    private ViewPager mStickerPager = null;
    private View mStickerBox = null;

    private void showStickers ()
    {
        if (mStickerPager == null)
        {

            initStickers();
            mStickerBox = mActivity.findViewById(R.id.stickerBox);
        }

        mStickerBox.setVisibility(mStickerBox.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
    }


    private synchronized void initStickers () {


        mStickerPager = (ViewPager) mActivity.findViewById(R.id.stickerPager);

        Collection<StickerGroup> emojiGroups = StickerManager.getInstance(mActivity).getEmojiGroups();

        if (emojiGroups.size() > 0) {
            StickerPagerAdapter emojiPagerAdapter = new StickerPagerAdapter(mActivity, new ArrayList<StickerGroup>(emojiGroups),
                    new StickerSelectListener() {
                        @Override
                        public void onStickerSelected(Sticker s) {

                            sendStickerCode(s.assetUri);

                            mViewAttach.setVisibility(View.INVISIBLE);
                            showStickers();
                        }
                    });

            mStickerPager.setAdapter(emojiPagerAdapter);

        }


    }

    //generate a :pack-sticker: code
    private void sendStickerCode (Uri assetUri)
    {
        StringBuffer stickerCode = new StringBuffer();
        stickerCode.append(":");

        stickerCode.append(assetUri.getPathSegments().get(assetUri.getPathSegments().size()-2));
        stickerCode.append("-");
        stickerCode.append(assetUri.getLastPathSegment().split("\\.")[0]);

        stickerCode.append(":");

        sendMessageAsync(stickerCode.toString(), null);
    }

    void approveSubscription() {

        if (mConn != null)
        {
            try {
                IContactListManager manager = mConn.getContactListManager();
                manager.approveSubscription(new Contact(new MatrixAddress(mRemoteAddress),mRemoteNickname, Imps.Contacts.TYPE_NORMAL));
            } catch (RemoteException e) {

                // mHandler.showServiceErrorAlert(e.getLocalizedMessage());
                LogCleaner.error(LOG_TAG, "approve sub error",e);
            }
        }
    }

    void declineSubscription() {

        if (mConn != null)
        {
            try {
                IContactListManager manager = mConn.getContactListManager();
                manager.declineSubscription(new Contact(new MatrixAddress(mRemoteAddress),mRemoteNickname, Imps.Contacts.TYPE_NORMAL));
            } catch (RemoteException e) {
                // mHandler.showServiceErrorAlert(e.getLocalizedMessage());
                LogCleaner.error(LOG_TAG, "decline sub error",e);
            }
        }
    }

    private void showContactMoved (final Contact contact)
    {
        final View viewNotify = mActivity.findViewById(R.id.upgrade_view);
        ImageView viewImage = (ImageView)mActivity.findViewById(R.id.upgrade_view_image);
        TextView viewDesc = (TextView)mActivity.findViewById(R.id.upgrade_view_text);
        Button buttonAction = (Button)mActivity.findViewById(R.id.upgrade_action);
        View viewUpgradeClose = mActivity.findViewById(R.id.upgrade_close);

        viewNotify.setVisibility(View.VISIBLE);

        viewDesc.setText(mActivity.getString(R.string.contact_migration_notice) + ' ' + contact.getForwardingAddress());

        buttonAction.setText(R.string.contact_migration_action);
        buttonAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                viewNotify.setVisibility(View.GONE);
                startChat(contact.getForwardingAddress());
            }
        });

        viewUpgradeClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                viewNotify.setVisibility(View.GONE);
                contact.setForwardingAddress(null);
            }
        });
    }

    private void startChat (String username)
    {

        if (username != null) {

            new ChatSessionInitTask(mProviderId, mAccountId, Imps.Contacts.TYPE_NORMAL, true) {
                @Override
                protected void onPostExecute(Long chatId) {

                    if (chatId != -1 && true) {
                        Intent intent = new Intent(mActivity, ConversationDetailActivity.class);
                        intent.putExtra("id", chatId);
                        mActivity.startActivity(intent);
                    }

                    super.onPostExecute(chatId);
                }

            }.execute(new Contact(new MatrixAddress(username)));

            mActivity.finish();
        }
    }



}
