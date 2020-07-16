/*
 * Copyright (C) 2007-2008 Esmertec AG. Copyright (C) 2007-2008 The Android Open
 * Source Project
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

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import info.guardianproject.iocipher.File;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.service.IChatSession;
import info.guardianproject.keanu.core.service.IImConnection;
import info.guardianproject.keanu.core.service.RemoteImService;
import info.guardianproject.keanu.core.ui.RoundedAvatarDrawable;
import info.guardianproject.keanu.core.util.DatabaseUtils;
import info.guardianproject.keanu.core.util.SecureMediaStore;
import org.ocpsoft.prettytime.PrettyTime;

import java.util.Date;
import java.util.Locale;

import info.guardianproject.keanuapp.R;
import info.guardianproject.keanuapp.ImApp;
import info.guardianproject.keanuapp.ui.VectorUtils;
import info.guardianproject.keanuapp.ui.widgets.ConversationViewHolder;
import info.guardianproject.keanuapp.ui.widgets.GlideUtils;
import info.guardianproject.keanuapp.ui.widgets.GroupAvatar;
import info.guardianproject.keanuapp.ui.widgets.LetterAvatar;

import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;
import static info.guardianproject.keanu.core.KeanuConstants.SMALL_AVATAR_HEIGHT;
import static info.guardianproject.keanu.core.KeanuConstants.SMALL_AVATAR_WIDTH;

public class ConversationListItem extends FrameLayout {

    /**
    public final String[] CHAT_PROJECTION = { Imps.Contacts._ID, Imps.Contacts.PROVIDER,
            Imps.Contacts.ACCOUNT, Imps.Contacts.USERNAME,
            Imps.Contacts.NICKNAME, Imps.Contacts.TYPE,
            Imps.Contacts.SUBSCRIPTION_TYPE,
            Imps.Contacts.SUBSCRIPTION_STATUS,
            Imps.Presence.PRESENCE_STATUS,
            Imps.Presence.PRESENCE_CUSTOM_STATUS,
            Imps.Chats.LAST_MESSAGE_DATE,
            Imps.Chats.LAST_UNREAD_MESSAGE,
            Imps.Chats.LAST_READ_DATE,
            Imps.Chats.CHAT_TYPE,
            Imps.Chats.USE_ENCRYPTIONf
            //          Imps.Contacts.AVATAR_HASH,
            //        Imps.Contacts.AVATAR_DATA

    };              **/

    public static final int COLUMN_CONTACT_ID = 0;
    public static final int COLUMN_CONTACT_PROVIDER = 1;
    public static final int COLUMN_CONTACT_ACCOUNT = 2;
    public static final int COLUMN_CONTACT_USERNAME = 3;
    public static final int COLUMN_CONTACT_NICKNAME = 4;
    public static final int COLUMN_CONTACT_TYPE = 5;
    public static final int COLUMN_SUBSCRIPTION_TYPE = 6;
    public static final int COLUMN_SUBSCRIPTION_STATUS = 7;
    public static final int COLUMN_CONTACT_PRESENCE_STATUS = 8;
    public static final int COLUMN_CONTACT_CUSTOM_STATUS = 9;
    public static final int COLUMN_LAST_MESSAGE_DATE = 10;
    public static final int COLUMN_LAST_MESSAGE = 11;
    public static final int COLUMN_LAST_READ_DATE = 12;
    public static final int COLUMN_CHAT_TYPE = 13;
    public static final int COLUMN_USE_ENCRYPTION = 14;


    static Drawable AVATAR_DEFAULT_GROUP = null;
    private PrettyTime sPrettyTime = null;

    public ConversationListItem(Context context, AttributeSet attrs) {
        super(context, attrs);
        sPrettyTime = new PrettyTime(getCurrentLocale());
    }

    public void bind(ConversationViewHolder holder, long contactId, long providerId, long accountId, String address, String nickname, int contactType, String message, long messageDate, String messageType, int presence, int subscription, String underLineText, boolean showChatMsg, boolean scrolling, boolean isMuted, boolean isEncrypted) {

        if (nickname == null) {
            nickname = address.split(":")[0];
        }

        if (isMuted)
        {
            nickname += " \uD83D\uDD15";
        }

        if (!TextUtils.isEmpty(underLineText)) {
            // highlight/underline the word being searched 
            String lowercase = nickname.toLowerCase();
            int start = lowercase.indexOf(underLineText.toLowerCase());
            if (start >= 0) {
                int end = start + underLineText.length();
                SpannableString str = new SpannableString(nickname);
                str.setSpan(new UnderlineSpan(), start, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE);

                holder.mLine1.setText(str);

            }
            else
                holder.mLine1.setText(nickname);

        }
        else
            holder.mLine1.setText(nickname);

        holder.mStatusIcon.setVisibility(View.GONE);


        if (holder.mAvatar != null)
        {
            holder.mAvatar.setVisibility(View.VISIBLE);

            Drawable avatar = null;

            try
            {
                avatar = DatabaseUtils.getAvatarFromAddress(address, SMALL_AVATAR_WIDTH, SMALL_AVATAR_HEIGHT);
            }
            catch (Exception e)
            {
                //problem decoding avatar
                Log.e(LOG_TAG,"error decoding avatar",e);

            }

            if (avatar != null)
            {
                holder.mAvatar.setImageDrawable(avatar);

            }
            else {
                    // int color = getAvatarBorder(presence);
                int padding = 24;
                LetterAvatar lavatar = new LetterAvatar(getContext(), nickname, padding);
                holder.mAvatar.setImageDrawable(lavatar);


            }
        }

        if (showChatMsg && message != null && (!TextUtils.isEmpty(message.trim()))) {

            holder.mMediaThumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
            holder.mMediaThumb.setVisibility(View.GONE);

            if (holder.mLine2 != null)
            {
                holder.mLine2.setText("");
                String vPath = message;//.split(" ")[0];

                if (SecureMediaStore.isVfsUri(vPath)||SecureMediaStore.isContentUri(vPath))
                {

                    if (TextUtils.isEmpty(messageType))
                    {
                        holder.mMediaThumb.setVisibility(View.VISIBLE);
                        holder.mMediaThumb.setImageResource(R.drawable.ic_attach_file_black_36dp);
                        holder.mMediaThumb.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                        holder.mLine2.setText("");
                    }
                    else if (messageType.startsWith("image"))
                    {
                        
                        if (holder.mMediaThumb != null)
                        {
                            holder.mMediaThumb.setVisibility(View.VISIBLE);

                            if (messageType != null && messageType.equals("image/png"))
                            {
                                holder.mMediaThumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
                            }
                            else
                            {
                                holder.mMediaThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);

                            }

                            setThumbnail(getContext().getContentResolver(), holder, Uri.parse(vPath), true);

                                    holder.mLine2.setVisibility(View.GONE);
                                    
                        }
                    }
                    else if (messageType.startsWith("audio"))
                    {
                        mLastMediaUri = null;
                        holder.mMediaThumb.setVisibility(View.VISIBLE);
                        holder.mMediaThumb.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                        holder.mMediaThumb.setImageResource(R.drawable.ic_volume_up_black_24dp);
                        holder.mLine2.setText("");
                    }
                    else if (messageType.startsWith("video"))
                    {
                        Uri uriMedia = Uri.parse(vPath);
                        File fileThumb = new info.guardianproject.iocipher.File(uriMedia.getPath() + ".thumb.jpg");

                        if (!fileThumb.exists()) {
                            mLastMediaUri = null;
                            holder.mMediaThumb.setVisibility(View.VISIBLE);
                            holder.mMediaThumb.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                            holder.mMediaThumb.setImageResource(R.drawable.video256);
                            holder.mLine2.setText("");
                        }
                        else {
                            if (holder.mMediaThumb != null) {
                                holder.mMediaThumb.setVisibility(View.VISIBLE);
                                holder.mMediaThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                setThumbnail(getContext().getContentResolver(), holder, Uri.parse(vPath + ".thumb.jpg"), true);
                                holder.mLine2.setVisibility(View.GONE);
                            }
                        }
                    }
                    else if (messageType.startsWith("application"))
                    {
                        mLastMediaUri = null;
                        holder.mMediaThumb.setVisibility(View.VISIBLE);
                        holder.mMediaThumb.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                        holder.mMediaThumb.setImageResource(R.drawable.ic_attach_file_black_36dp);
                        holder.mLine2.setText("");
                    }
                    else
                    {
                        mLastMediaUri = null;
                        holder.mMediaThumb.setVisibility(View.GONE);
                        holder.mLine2.setText(messageType);
                    }

                }
                else if ((!TextUtils.isEmpty(message)) && message.startsWith("/"))
                {
                    String cmd = message.toString().substring(1);

                    if (cmd.startsWith("sticker"))
                    {
                        String[] cmds = cmd.split(":");

                        String mimeTypeSticker = "image/png";
                        Uri mediaUri = Uri.parse("asset://"+cmds[1]);

                        mLastMediaUri = null;
                        setThumbnail(getContext().getContentResolver(), holder, mediaUri, false);
                        holder.mLine2.setVisibility(View.GONE);

                        holder.mMediaThumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        holder.mMediaThumb.setVisibility(View.VISIBLE);


                    }

                }
                else if ((!TextUtils.isEmpty(message)) && message.startsWith(":"))
                {
                    String[] cmds = message.split(":");

                    try {
                        String[] stickerParts = cmds[1].split("-");
                        String folder = stickerParts[0];
                        StringBuffer name = new StringBuffer();
                        for (int i = 1; i < stickerParts.length; i++) {
                            name.append(stickerParts[i]);
                            if (i+1<stickerParts.length)
                                name.append('-');
                        }
                        String stickerPath = "stickers/" + folder + "/" + name.toString() + ".png";

                        //make sure sticker exists
                        AssetFileDescriptor afd = getContext().getAssets().openFd(stickerPath);
                        afd.getLength();
                        afd.close();

                        //now setup the new URI for loading local sticker asset
                        Uri mediaUri = Uri.parse("asset://localhost/" + stickerPath);
                        mLastMediaUri = null;
                        setThumbnail(getContext().getContentResolver(), holder, mediaUri, false);
                        holder.mLine2.setVisibility(View.GONE);
                        holder.mMediaThumb.setScaleType(ImageView.ScaleType.FIT_CENTER);

                    } catch (Exception e) {
                        try {
                            holder.mLine2.setText(android.text.Html.fromHtml(message).toString());
                        }
                        catch (RuntimeException re){}
                    }
                }
                else
                {
                    if (holder.mMediaThumb != null)
                        holder.mMediaThumb.setVisibility(View.GONE);
                    
                    holder.mLine2.setVisibility(View.VISIBLE);


                    try {
                        holder.mLine2.setText(android.text.Html.fromHtml(message).toString());
                    }
                    catch (RuntimeException re){}
                }
            }

            if (messageDate != -1)
            {
                Date dateLast = new Date(messageDate);
                holder.mStatusText.setText(sPrettyTime.format(dateLast));

            }
            else
            {
                holder.mStatusText.setText("");
            }

        }
        else if (holder.mLine2 != null)
        {
            holder.mLine2.setText("");

            if (holder.mMediaThumb != null)
                holder.mMediaThumb.setVisibility(View.GONE);
        }

        holder.mLine1.setVisibility(View.VISIBLE);

        /**
        if (isEncrypted)
        {
            holder.mStatusIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_encrypted_grey));
            holder.mStatusIcon.setVisibility(View.VISIBLE);
        }
        **/

    }

   
    private Uri mLastMediaUri = null;

    /**
     * @param contentResolver
     * @param aHolder
     * @param mediaUri
     */
    private void setThumbnail(final ContentResolver contentResolver, final ConversationViewHolder aHolder, final Uri mediaUri, boolean centerCrop) {

        if (mLastMediaUri != null && mLastMediaUri.getPath().equals(mediaUri.getPath()))
            return;

        mLastMediaUri = mediaUri;
        aHolder.mMediaThumb.setVisibility(View.VISIBLE);

        if (centerCrop)
            aHolder.mMediaThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        else
            aHolder.mMediaThumb.setScaleType(ImageView.ScaleType.FIT_CENTER);


        GlideUtils.loadImageFromUri(getContext(), mediaUri, aHolder.mMediaThumb);
    }

    private String getGroupCount(ContentResolver resolver, long groupId) {
        String[] projection = { Imps.GroupMembers.NICKNAME };
        Uri uri = ContentUris.withAppendedId(Imps.GroupMembers.CONTENT_URI, groupId);
        Cursor c = resolver.query(uri, projection, null, null, null);
        StringBuilder buf = new StringBuilder();
        if (c != null) {

            buf.append(" (");
            buf.append(c.getCount());
            buf.append(")");

            c.close();
        }

        return buf.toString();
    }

    /**
     * Returns darker version of specified <code>color</code>.
     */
    public static int darker (int color, float factor) {
        int a = Color.alpha( color );
        int r = Color.red( color );
        int g = Color.green( color );
        int b = Color.blue( color );

        return Color.argb( a,
                Math.max( (int)(r * factor), 0 ),
                Math.max( (int)(g * factor), 0 ),
                Math.max( (int)(b * factor), 0 ) );
    }


    @TargetApi(Build.VERSION_CODES.N)
    public Locale getCurrentLocale(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            return getResources().getConfiguration().getLocales().get(0);
        } else{
            //noinspection deprecation
            return getResources().getConfiguration().locale;
        }
    }
}
