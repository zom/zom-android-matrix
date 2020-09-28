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

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Browser;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;
import androidx.core.view.inputmethod.InputContentInfoCompat;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.viewpager.widget.ViewPager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.ActionMode;
import android.view.ContextMenu;
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
import android.view.inputmethod.EditorInfo;
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

import com.gsconrad.richcontentedittext.RichContentEditText;
import com.stefanosiano.powerful_libraries.imageview.blur.PivBlurMode;
import com.tougee.recorderview.AudioRecordView;
import com.vanniktech.emoji.EmojiEditText;
import com.vanniktech.emoji.EmojiImageView;
import com.vanniktech.emoji.EmojiPopup;
import com.vanniktech.emoji.EmojiUtils;
import com.vanniktech.emoji.SingleEmojiTrait;
import com.vanniktech.emoji.emoji.Emoji;
import com.vanniktech.emoji.listeners.OnEmojiClickListener;
import com.vanniktech.emoji.listeners.OnEmojiPopupDismissListener;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import info.guardianproject.iocipher.FileInputStream;
import info.guardianproject.keanu.core.Preferences;
import info.guardianproject.keanu.core.service.IChatSessionListener;
import info.guardianproject.keanu.core.type.CustomTypefaceEditText;
import info.guardianproject.keanu.matrix.plugin.MatrixAddress;
import info.guardianproject.keanuapp.R;
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
import info.guardianproject.keanuapp.ui.widgets.PopupDialog;
import info.guardianproject.keanuapp.ui.widgets.ShareRequest;
import info.guardianproject.keanuapp.ui.widgets.VideoViewActivity;

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
    CustomTypefaceEditText mComposeMessage;
    ShareRequest mShareDraft;

    protected ImageButton mSendButton;//, mMicButton;
    private TextView mButtonTalk;
    private ImageButton mButtonAttach;
    private View mViewAttach;

    private ImageView mButtonDeleteVoice;
    private View mViewDeleteVoice;

    private AudioRecordView mAudioRecordView;
    private View mBtnAttachSticker;
    private ImageView mDeliveryIcon;
    private boolean mExpectingDelivery;

    private boolean mIsSelected = false;
    private boolean mIsVerified = false;

    private ConversationRecyclerViewAdapter mMessageAdapter;
 //   private boolean isServiceUp;
    protected IChatSession mCurrentChatSession;

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

                mHandler.post(new Runnable ()
                {
                    public void run ()
                    {
                        updateGroupTitle();
                    }
                });
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

        mComposeMessage = (CustomTypefaceEditText) mActivity.findViewById(R.id.composeMessage);
        mComposeMessage.setReachContentClickListner(new CustomTypefaceEditText.OnReachContentSelect() {
            @Override
            public void onReachContentClick(InputContentInfoCompat inputContentInfoCompat) {
                ShareRequest request = new ShareRequest();
                request.deleteFile = false;
                request.resizeImage = false;
                request.importContent = true;
                request.media = inputContentInfoCompat.getContentUri();
                request.mimeType = "image/gif";
                mActivity.sendShareRequest(request);
            }
        });
        /*mComposeMessage.setOnRichContentListener(new RichContentEditText.OnRichContentListener() {
            @Override
            public void onRichContent(Uri contentUri, ClipDescription description) {
                Log.v("mComposeMessage","mComposeMessage==="+contentUri);
                ShareRequest request = new ShareRequest();
                request.deleteFile = false;
                request.resizeImage = false;
                request.importContent = true;
                request.media = contentUri;
                request.mimeType = "image/gif";
                mActivity.sendShareRequest(request);

            }
        });*/


        mSendButton = (ImageButton) mActivity.findViewById(R.id.btnSend);
       // mMicButton = (ImageButton) mActivity.findViewById(R.id.btnMic);

        mButtonDeleteVoice = (ImageView)mActivity.findViewById(R.id.btnDeleteVoice);
        mViewDeleteVoice = mActivity.findViewById(R.id.viewDeleteVoice);

        if (mButtonDeleteVoice != null) {
            mButtonDeleteVoice.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {

                    if (motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
                        int resolvedColor = mHistory.getResources().getColor(android.R.color.holo_red_light);
                        mButtonDeleteVoice.setBackgroundColor(resolvedColor);
                    }

                    return false;
                }
            });
        }

        mButtonAttach = (ImageButton) mActivity.findViewById(R.id.btnAttach);
        mViewAttach = mActivity.findViewById(R.id.attachPanel);

        if (mButtonAttach != null) {
            mButtonAttach.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {


                    toggleAttachMenu();
                }

            });
        }

        View mediaPreviewCancel = mActivity.findViewById(R.id.mediaPreviewCancel);
        if (mediaPreviewCancel != null) {
            mediaPreviewCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    clearMediaDraft();
                }
            });
        }

        View btnAttachPicture = mActivity.findViewById(R.id.btnAttachPicture);
        if (btnAttachPicture != null) {
            btnAttachPicture.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mViewAttach.setVisibility(View.INVISIBLE);
                    mActivity.startImagePicker();
                }
            });
        }

        View btnTakePicture = mActivity.findViewById(R.id.btnTakePicture);
        if (btnTakePicture != null) {
            btnTakePicture.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mViewAttach.setVisibility(View.INVISIBLE);
                    mActivity.startPhotoTaker();
                }
            });
        }

        View btnAttachAudio = mActivity.findViewById(R.id.btnAttachAudio);
        if (btnAttachAudio != null) {
            btnAttachAudio.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mViewAttach.setVisibility(View.INVISIBLE);
                    mActivity.startFilePicker("audio/*");
                }
            });
        }

        View btnAttachFile = mActivity.findViewById(R.id.btnAttachFile);
        if (btnAttachFile != null) {
            btnAttachFile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mViewAttach.setVisibility(View.INVISIBLE);
                    mActivity.startFilePicker("*/*");
                }
            });
        }

        mBtnAttachSticker = mActivity.findViewById(R.id.btnAttachSticker);
        if (mBtnAttachSticker != null) {
            mBtnAttachSticker.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleStickers();
                }
            });
        }

        View btnCreateStory = mActivity.findViewById(R.id.btnCreateStory);
        if (btnCreateStory != null) {
            btnCreateStory.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mViewAttach.setVisibility(View.INVISIBLE);
                    mActivity.startStoryEditor();
                }
            });
        }

        mAudioRecordView = mActivity.findViewById(R.id.record_view);
        if(mAudioRecordView != null){
            mAudioRecordView.setActivity(mActivity);
            mAudioRecordView.setCallback(new AudioRecordView.Callback() {
                @Override
                public void onRecordStart(boolean b) {
                    Log.d("AudioRecord","onRecordStart: " + b);
                    mActivity.startAudioRecording();
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void onRecordEnd() {
                    Log.d("AudioRecord","onRecordEnd");
                    if (mActivity.isAudioRecording()) {
                        boolean send = true;//inViewInBounds(mMicButton, (int) motionEvent.getX(), (int) motionEvent.getY());
                        mActivity.stopAudioRecording(send);
                    }
                }

                @Override
                public void onRecordCancel() {
                    Log.d("AudioRecord","onRecordCancel");
                    if (mActivity.isAudioRecording()) {
                        boolean send = false;
                        mActivity.stopAudioRecording(send);
                    }
                }
            });
        }

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
                onSendButtonClicked();
            }
        });

        mMessageAdapter = createRecyclerViewAdapter();
        mHistory.setAdapter(mMessageAdapter);

    }

    protected void onSendButtonClicked() {
        if (mComposeMessage.getVisibility() == View.VISIBLE)
            sendMessage();
        else {
            mSendButton.setImageResource(R.drawable.ic_send_secure);

            mSendButton.setVisibility(View.GONE);
            if (mButtonTalk != null) {
                mButtonTalk.setVisibility(View.GONE);
            }
            mComposeMessage.setVisibility(View.VISIBLE);
            if(mAudioRecordView != null){
                mAudioRecordView.setVisibility(View.VISIBLE);
            }


            //     mMicButton.setVisibility(View.VISIBLE);
        }
    }


    protected ConversationRecyclerViewAdapter createRecyclerViewAdapter() {
        return new ConversationRecyclerViewAdapter(mActivity, null);
    }

    private boolean mLastIsTyping = false;

    private void sendTypingStatus (final boolean isTyping) {

        new AsyncTask<Void,Void,Void>()
        {

            @Override
            protected Void doInBackground(Void... voids) {
                if (mLastIsTyping != isTyping) {
                    try {
                        if (mConn != null)
                            mConn.sendTypingStatus(mRemoteAddress, isTyping);
                    } catch (Exception ie) {
                        Log.e(LOG_TAG, "error sending typing status", ie);
                    }

                    mLastIsTyping = isTyping;
                }
                return null;
            }
        }.execute();



    }

    private void sendMessageRead (final String msgId) {

        new AsyncTask<Void,Void,Void>()
        {

            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    if (mConn != null)
                        mConn.sendMessageRead(mRemoteAddress, msgId);
                } catch (Exception ie) {
                    Log.e(LOG_TAG, "error sending typing status", ie);
                }
                return null;
            }
        }.execute();



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
        checkConnection ();

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
        if (!TextUtils.isEmpty(mRemoteNickname))
            return mRemoteNickname;
        else
            return mRemoteAddress;


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
                mRemoteNickname = mRemoteAddress;
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

        if (userToNick.isEmpty())
            updateMembers();

        return true;
    }

    private boolean bindQuery () {

        Uri contactUri = ContentUris.withAppendedId(Imps.Contacts.CONTENT_URI, mLastChatId);
        Cursor c = mActivity.getContentResolver().query(contactUri, CHAT_PROJECTION, null, null, null);

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

        updateSessionInfo(c);

        if (mRemoteAvatar == null)
        {
             try {mRemoteAvatar = (RoundedAvatarDrawable) DatabaseUtils.getAvatarFromAddress(mRemoteAddress, DEFAULT_AVATAR_WIDTH, DEFAULT_AVATAR_HEIGHT);}
            catch (Exception e){}

            if (mRemoteAvatar == null)
            {
                mRemoteAvatar = new RoundedAvatarDrawable(BitmapFactory.decodeResource(mActivity.getResources(),
                        R.drawable.avatar_unknown));

            }


        }

        if (mRemoteHeader == null)
        {
            try {mRemoteHeader = DatabaseUtils.getAvatarFromAddress(mRemoteAddress, DEFAULT_AVATAR_WIDTH,DEFAULT_AVATAR_HEIGHT);}
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
        else
        {
            try {
                mCurrentChatSession.refreshContactFromServer();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }


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

    protected Uri mUri;
    private LoaderManager mLoaderManager, mReplyLoaderManager;
    protected int loaderId = 100001;

    // This will map a message id to a loader for replies to that id
    protected Map<String,Integer> eventReplyLoaders = new HashMap<String, Integer>();

    private synchronized void startQuery(long chatId) {

        mUri = Imps.Messages.getContentUriByThreadId(chatId);

        if (mLoaderManager == null) {
            mLoaderManager = LoaderManager.getInstance(mActivity);
            mLoaderManager.initLoader(loaderId++, null, new MyLoaderCallbacks());
        }
        else
            mLoaderManager.restartLoader(loaderId++, null, new MyLoaderCallbacks());

    }

    protected Loader<Cursor> createLoader() {
        // For now, assume Quick Reactions are only 1 char long. We don't want to show them as
        // "separate messages", so filter those out here.
        String selection =
                Imps.Messages.REPLY_ID + " IS NULL" +
                        " OR " +
                "LENGTH(" + Imps.Messages.BODY + ") > 2" +
                        " OR " +
                        "(" +
                            "UNICODE("+ Imps.Messages.BODY+") NOT IN (0x2b05,0x2b06,0x2b07,0x2934,0x2935,0x3297,0x3298,0x3299,0xa9,0xae,0x303d,0x3030,0x2b55,0x2b1c,0x2b1b,0x2b50) AND " +
                            "(UNICODE("+ Imps.Messages.BODY+") > 0x1f9ff OR UNICODE("+ Imps.Messages.BODY+") < 0x1d000) AND " +
                            "(UNICODE("+ Imps.Messages.BODY+") > 0x27ff OR UNICODE("+Imps.Messages.BODY+") < 0x2100)" +
                        ")"
                ;
        CursorLoader loader = new CursorLoader(mActivity, mUri, null, selection, null, Imps.Messages.DEFAULT_SORT_ORDER);
        return loader;
    }

    protected void loaderFinished() {
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

    class MyLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {
        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            return createLoader();
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor newCursor) {

            if (newCursor != null) {

                newCursor.setNotificationUri(mActivity.getApplicationContext().getContentResolver(), mUri);
                mMessageAdapter.swapCursor(new DeltaCursor(newCursor));
                loaderFinished();
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


    public void showGroupInfo (String setSubject) {

        Intent intent = new Intent(mContext, GroupDisplayActivity.class);
        intent.putExtra("address", mRemoteAddress);
        intent.putExtra("provider", mProviderId);
        intent.putExtra("account", mAccountId);
        intent.putExtra("chat", mLastChatId);

        if (!TextUtils.isEmpty(setSubject)) {
            intent.putExtra("subject", setSubject);
            intent.putExtra("nickname", setSubject);

        }
        else {
            intent.putExtra("nickname", mRemoteNickname);

        }

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
                                mCurrentChatSession.refreshContactFromServer();
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

    public boolean isGroupChat() {
        return (this.mContactType & Imps.Contacts.TYPE_MASK) == Imps.Contacts.TYPE_GROUP;
    }

    protected void sendMessage() {

        if (mShareDraft != null)
        {
            Log.v("ImageSend","sendMessage");
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

    void refreshMessage (final String packetId) {

        try {
            mCurrentChatSession.refreshMessage(packetId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    void checkReceipt (final String packetId) {

        try {
            mCurrentChatSession.checkReceipt(packetId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
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
                return sendMessage(msgs[0],false,replyId, false);
            }

            @Override
            protected void onPostExecute(Boolean aBoolean) {
                super.onPostExecute(aBoolean);

                if (aBoolean.booleanValue())
                {
                    mComposeMessage.setText("");
                    mComposeMessage.requestFocus();
                }


                sendTypingStatus (false);
            }
        }.execute(msg);


    }

    boolean sendMessage(String msg, boolean isResend, String replyId, boolean isQuickReaction) {

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
                if (isQuickReaction)
                   session.sendReaction(msg,isResend,replyId);
                else
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

        getChatSession(new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] objects) {
                try {
                    mCurrentChatSession.registerChatListener(mChatListener);

                    if (mConn != null)
                    {
                        IContactListManager listMgr = mConn.getContactListManager();
                        listMgr.registerContactListListener(mContactListListener);
                    }

                } catch (RemoteException e) {
                    Log.w(LOG_TAG, "<ChatView> registerChatListener fail:" + e.getMessage());
                }

                return null;
            }

        });

    }

    void unregisterChatListener() {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            log("unregisterChatListener " + mLastChatId);
        }


        getChatSession(new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] objects) {
                try {
                    mCurrentChatSession.unregisterChatListener(mChatListener);

                    if (mConn != null) {
                        IContactListManager listMgr = mConn.getContactListManager();
                        listMgr.unregisterContactListListener(mContactListListener);

                    }

                } catch (RemoteException e) {
                    Log.w(LOG_TAG, "<ChatView> registerChatListener fail:" + e.getMessage());
                }

                return null;
            }

        });


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
        if (mButtonTalk == null || mButtonTalk.getVisibility() == View.GONE) {
            if (mComposeMessage.getText().length() > 0 && mSendButton.getVisibility() == View.GONE) {
                if(mAudioRecordView != null){
                    mAudioRecordView.setVisibility(View.GONE);
                }


                if (mBtnAttachSticker != null)
                    mBtnAttachSticker.setVisibility(View.GONE);

                mSendButton.setVisibility(View.VISIBLE);
                mSendButton.setImageResource(R.drawable.ic_send_secure);

            } else if (mComposeMessage.getText().length() == 0) {
                if (mBtnAttachSticker != null)
                    mBtnAttachSticker.setVisibility(View.VISIBLE);
                if(mAudioRecordView != null){
                    mAudioRecordView.setVisibility(View.VISIBLE);
                }

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
                View typingView = mActivity.findViewById(R.id.tvTyping);
                if (typingView != null) {
                    typingView.setVisibility(isTyping ? View.VISIBLE : View.GONE);
                }
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
            extends CursorRecyclerViewAdapter<MessageViewHolder> implements MessageViewHolder.OnImageClickedListener, MessageViewHolder.OnQuickReactionClickedListener {

        private int mScrollState;
        private boolean mNeedRequeryCursor;

        private int mNicknameColumn;
        protected int mBodyColumn;
        private int mDateColumn;
        private int mTypeColumn;
        private int mErrCodeColumn;
        private int mDeltaColumn;
        private int mDeliveredColumn;
        protected int mMimeTypeColumn;
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

                    mLastSelectedView = view;

                    if (((MessageListItem)mLastSelectedView).isNotDecrypted())
                        refreshMessage(((MessageListItem)mLastSelectedView).getPacketId());

                    if (mActionMode != null) {
                        return false;
                    }

                    // Start the CAB using the ActionMode.Callback defined above
                    mActionMode = ((Activity) mContext).startActionMode(mActionModeCallback);

                    return true;
                }
            });


            MessageViewHolder mvh = new MessageViewHolder(view);
            mvh.setLayoutInflater(inflater);
            mvh.setOnImageClickedListener(this);
            mvh.setOnQuickReactionClickedListener(this);
            view.applyStyleColors();
            return mvh;
        }

        @Override
        public void onBindViewHolder(final MessageViewHolder viewHolder, Cursor cursor) {

            MessageListItem messageView = (MessageListItem) viewHolder.itemView;
            setLinkifyForMessageView(messageView);
            messageView.applyStyleColors();

            int messageType = cursor.getInt(mTypeColumn);

            final String roomAddress = isGroupChat() ? cursor.getString(mNicknameColumn)  : mRemoteAddress;
            final String userAddress = isGroupChat() ? cursor.getString(mNicknameColumn) : mRemoteNickname;

            String nick = userToNick.get(userAddress);
            if (TextUtils.isEmpty(nick))
                nick = userAddress;

            final String mimeType = cursor.getString(mMimeTypeColumn);
            final int id = cursor.getInt(mIdColumn);
            final String body = cursor.getString(mBodyColumn);
            final long delta = cursor.getLong(mDeltaColumn);
            boolean showTimeStamp = true;//(delta > SHOW_TIME_STAMP_INTERVAL);
            final long timestamp = cursor.getLong(mDateColumn);
            final String packetId = cursor.getString(mPacketIdColumn);
            final String replyId = cursor.getString(mReplyIdColumn);

            final Date date = showTimeStamp ? new Date(timestamp) : null;
            final boolean isDelivered = cursor.getLong(mDeliveredColumn) > 0;
            final long showDeliveryInterval = (mimeType == null) ? SHOW_DELIVERY_INTERVAL : SHOW_MEDIA_DELIVERY_INTERVAL;
            final boolean showDelivery = ((System.currentTimeMillis() - timestamp) > showDeliveryInterval);

            viewHolder.mPacketId = packetId;

            MessageListItem.DeliveryState deliveryState = MessageListItem.DeliveryState.NEUTRAL;

            if (showDelivery && !isDelivered && mExpectingDelivery) {
                deliveryState = MessageListItem.DeliveryState.UNDELIVERED;

            }
            else if (isDelivered)
            {
                deliveryState = MessageListItem.DeliveryState.DELIVERED;

            }

            if (isDelivered  || (messageType ==Imps.MessageType.OUTGOING||messageType ==Imps.MessageType.OUTGOING_ENCRYPTED)) {
                mExpectingDelivery = false;
                viewHolder.progress.setVisibility(View.GONE);
              //  viewHolder.mMediaThumbnail.setPivBlurMode(PivBlurMode.DISABLED);
             //   viewHolder.mMediaThumbnail.setBlurRadius(0);
               // Log.v("ImageSend","isDelivered");
            } else if (cursor.getPosition() == cursor.getCount() - 1) {
                //Log.v("ImageSend","isDelivered last");

                if(messageType ==Imps.MessageType.QUEUED){
                    viewHolder.progress.setVisibility(View.VISIBLE);
                //    viewHolder.mMediaThumbnail.setPivBlurMode(PivBlurMode.GAUSSIAN5X5);
                 //   viewHolder.mMediaThumbnail.setBlurRadius(10);
                    viewHolder.progress.setProgress(3);
                    viewHolder.progress.setProgress(6);
                    viewHolder.progress.setProgress(7);
                   // Log.v("ImageSend","isDelivered last 2");
                }else if(messageType == Imps.MessageType.SENDING){
                   viewHolder.progress.setVisibility(View.VISIBLE);
              //      viewHolder.mMediaThumbnail.setPivBlurMode(PivBlurMode.GAUSSIAN5X5);
               //     viewHolder.mMediaThumbnail.setBlurRadius(10);
                    viewHolder.progress.setProgress(3);
                    viewHolder.progress.setProgress(6);
                    viewHolder.progress.setProgress(7);
                    //Log.v("ImageSend","isDelivered last 3");
                }
                else {
                    viewHolder.progress.setVisibility(View.VISIBLE);
                    viewHolder.progress.setProgress(8);
                    viewHolder.progress.setProgress(9);
                    viewHolder.progress.setProgress(10);
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            viewHolder.progress.setVisibility(View.GONE);
                      //      viewHolder.mMediaThumbnail.setPivBlurMode(PivBlurMode.DISABLED);
                    //        viewHolder.mMediaThumbnail.setBlurRadius(0);
                        }
                    },500);

                }

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

                messageView.bindIncomingMessage(viewHolder,id, messageType, roomAddress, nick, mimeType, body, date, mMarkup, false, encState, showContactName, mPresenceStatus, mCurrentChatSession, packetId, replyId);

                if (messageView.isNotDecrypted())
                    refreshMessage(messageView.getPacketId());



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

                    if (!messageView.isDelivered())
                    {
                        checkReceipt(messageView.getPacketId());
                    }
                }

                break;

            default:
               // Log.v("ImageSend","default in switch");
                messageView.bindPresenceMessage(viewHolder, userAddress, nick, messageType, date, isGroupChat(), false);

            }

            // Set quick reaction listener
            View contextMenuView = (messageType == Imps.MessageType.INCOMING) ?
                viewHolder.mAvatar :
                (viewHolder.itemView.findViewById(R.id.message_container) != null) ? viewHolder.itemView.findViewById(R.id.message_container) : viewHolder.itemView;
            if (contextMenuView != null) {
                contextMenuView.setOnCreateContextMenuListener((menu, v, menuInfo) -> {
                    mActivity.getMenuInflater().inflate(R.menu.menu_message_avatar, menu);
                    menu.findItem(R.id.menu_message_add_qr).setOnMenuItemClickListener(item -> {
                        // Pick emoji as quick reaction
                        contextMenuView.post(() -> showQuickReactionsPopup(packetId, (View) mHistory.getParent()));
                        return true;
                    });
                });
                contextMenuView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            v.showContextMenu(v.getWidth() / 2.0f, v.getHeight() / 2.0f);
                        } else {
                            v.showContextMenu();
                        }
                        return true;
                    }


                });
            }

            if (!isDelivered)
              sendMessageRead(packetId);

            viewHolder.setReactions(packetId,null);

            mReactionExec.execute(new Runnable() {
                @Override
                public void run() {
                    loadMessageReactions(viewHolder, packetId);
                }
            });

            if (messageView.isSelected())
                viewHolder.mContainer.setBackgroundColor(mContext.getColor(R.color.holo_blue_bright));

        }

        Executor mReactionExec = new ThreadPoolExecutor( 2, 4, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

        private void loadMessageReactions(MessageViewHolder messageViewHolder, String messageId) {

            final String[] REACTION_PROJECTION = {
                    Imps.Messages.NICKNAME,
                    Imps.Messages.BODY,
                    Imps.Messages.DATE
            };

            StringBuilder buf = new StringBuilder();
            buf.append(Imps.Messages.REPLY_ID).append("=").append("\"").append(messageId).append("\"");
            Cursor newCursor = mActivity.getContentResolver().query(mUri, REACTION_PROJECTION, buf.toString(), null, Imps.Messages.REVERSE_SORT_ORDER);

            int nicknameCol = 0;
            int bodyCol = 1;

            final Map<String, QuickReaction> map = new HashMap<>();

            while (newCursor.moveToNext())
            {
                String reaction = newCursor.getString(bodyCol);
                String address = newCursor.getString(nicknameCol);
                if (address == null) {
                    address = ((ImApp) mActivity.getApplication()).getDefaultUsername();
                }

                if (!TextUtils.isEmpty(address) && reaction != null && EmojiUtils.isOnlyEmojis(reaction)) {
                    QuickReaction react = map.get(reaction);
                    if (react == null) {
                        react = new QuickReaction(reaction, null);
                        map.put(reaction, react);
                    }
                    react.senders.add(address);
                    if (address.equals(((ImApp) mActivity.getApplication()).getDefaultUsername())) {
                        react.sentByMe = true;
                    }
                }
            }

            mHandler.post(new Runnable (){

                public void run ()
                {
                    messageViewHolder.setReactions(messageId, new ArrayList<>(map.values()));

                }
            });

            newCursor.close();
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
                    case R.id.menu_downLoad:
                        if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(mActivity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                            Uri mediaUri = ((MessageListItem)mLastSelectedView).getMediaUri();
                            File sd = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)+"/Keanu/");
                            String extension = mediaUri.getPath().substring(mediaUri.getPath().lastIndexOf("."));
                            String filename = "Keanu_"+getDateTime()+extension;
                            new DownloadAudio().execute(mediaUri,filename,sd);
                        } else {
                            ActivityCompat.requestPermissions(mActivity,
                                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE}, 104);
                        }
                        mode.finish();
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

                mLastSelectedView = null;
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
                        if (!TextUtils.isEmpty(mime) && (
                                mime.startsWith("image/") ||
                                        mime.startsWith("audio/") ||
                                        mime.startsWith("video/") ||
                                        mime.contentEquals("application/pdf"))) {
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

        @Override
        public void onQuickReactionClicked(MessageViewHolder viewHolder, QuickReaction quickReaction, String messageId) {
            // TODO - Remove my own reaction, but that is just sending it twice right?
            //if (quickReaction.sentByMe) {
            //}
            sendQuickReaction(quickReaction.reaction,messageId);
        }
    }

    private void sendQuickReaction(String reaction, String messageId) {
        sendMessage(reaction,false,messageId,true);
    }
    private class DownloadAudio extends AsyncTask<Object, Void, Long> {
        String storagePath = null;

        @Override
        protected Long doInBackground(Object... params) {
            Uri audioUri = (Uri) params[0];
            String filename = (String) params[1];
            File sd = (File) params[2];
            storagePath = sd.getPath();

            long bytesCopied= 0;
            if(!sd.exists()){
                sd.mkdirs();
            }
            File dest = new File(sd, filename);
            FileInputStream fis = null;
            FileOutputStream fos = null;
            try {
                fis = new FileInputStream(new info.guardianproject.iocipher.File(audioUri.getPath()));
                fos = new java.io.FileOutputStream(dest, false);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            try {
                bytesCopied = IOUtils.copyLarge(fis, fos);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return bytesCopied;
        }

        protected void onPostExecute(Long result) {
            if(result > 0){
                Toast.makeText(mActivity,"Audio Downloaded at :-"+storagePath.toString(),Toast.LENGTH_LONG).show();
            }
        }
    }
    private String getDateTime() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        Date date = new Date();
        return dateFormat.format(date);
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

    private void toggleStickers ()
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

                            //mViewAttach.setVisibility(View.INVISIBLE);

                            toggleStickers();
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

            new ChatSessionInitTask(mProviderId, mAccountId, Imps.Contacts.TYPE_NORMAL, true, true, true) {
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

    private HashMap<String, String> userToNick = new HashMap<>();

    private synchronized void updateMembers() {

        new Thread ()
        {
            public void run ()
            {

                final HashMap<String, String> members = new HashMap<>();

                String[] projection = {Imps.GroupMembers.USERNAME,Imps.GroupMembers.NICKNAME};
                Uri memberUri = ContentUris.withAppendedId(Imps.GroupMembers.CONTENT_URI, mLastChatId);
                ContentResolver cr = mActivity.getContentResolver();

                StringBuilder buf = new StringBuilder();
                buf.append(Imps.Messages.NICKNAME).append(" IS NOT NULL ");

                Cursor c = cr.query(memberUri, projection, buf.toString(), null, Imps.GroupMembers.ROLE+","+Imps.GroupMembers.AFFILIATION);
                if (c != null) {
                    int colUsername = c.getColumnIndex(Imps.GroupMembers.USERNAME);
                    int colNickname = c.getColumnIndex(Imps.GroupMembers.NICKNAME);

                    while (c.moveToNext()) {
                        String user = c.getString(colUsername);
                        String nick = c.getString(colNickname);
                        userToNick.put(user,nick);
                    }
                    c.close();
                }

            }
        }.start();


    }

    private EmojiPopup emojiPopup;
    private void showQuickReactionsPopup(final String messageId, View rootView) {
        try {
            if (emojiPopup != null) {
                return;
            }
            Context context = rootView.getContext();

            final EmojiEditText editText = new EmojiEditText(context);
            editText.setImeOptions(EditorInfo.IME_ACTION_SEND);
            editText.setInputType(InputType.TYPE_NULL);
            editText.setBackgroundColor(0x80000000);
            editText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // If tap on background, close popup!
                    if (emojiPopup != null) {
                        InputMethodManager imm = (InputMethodManager)v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                        emojiPopup.dismiss();
                    }
                }
            });
            SingleEmojiTrait.install(editText);

            ((ViewGroup)rootView).addView(editText, ViewGroup.LayoutParams.MATCH_PARENT, 1);

            rootView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    emojiPopup = EmojiPopup.Builder.fromRootView(rootView)
                            .setOnEmojiPopupDismissListener(new OnEmojiPopupDismissListener() {
                                @Override
                                public void onEmojiPopupDismiss() {
                                    emojiPopup = null;
                                    ((ViewGroup)rootView).removeView(editText);
                                }
                            })
                            .setOnEmojiClickListener(new OnEmojiClickListener() {
                                @Override
                                public void onEmojiClick(@NonNull EmojiImageView emoji, @NonNull Emoji variant) {
                                    sendQuickReaction(variant.getUnicode(), messageId);
                                    emojiPopup.dismiss();
                                }
                            })
                            .build(editText);
                    emojiPopup.toggle();
                }
            },200);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
