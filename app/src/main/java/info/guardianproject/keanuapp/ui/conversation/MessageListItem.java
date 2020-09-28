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

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.service.IChatSession;
import info.guardianproject.keanu.core.ui.RoundedAvatarDrawable;
import info.guardianproject.keanu.core.util.DatabaseUtils;
import info.guardianproject.keanu.core.util.LinkifyHelper;
import info.guardianproject.keanu.matrix.plugin.MatrixAddress;
import info.guardianproject.keanuapp.R;

import info.guardianproject.keanu.core.util.SecureMediaStore;
import info.guardianproject.keanuapp.ImUrlActivity;
import info.guardianproject.keanuapp.nearby.NearbyShareActivity;
import info.guardianproject.keanuapp.ui.legacy.Markup;
import info.guardianproject.keanuapp.ui.onboarding.OnboardingManager;
import info.guardianproject.keanuapp.ui.widgets.AudioWife;
import info.guardianproject.keanuapp.ui.widgets.GlideUtils;
import info.guardianproject.keanuapp.ui.widgets.ImageViewActivity;
import info.guardianproject.keanuapp.ui.widgets.LetterAvatar;
import info.guardianproject.keanuapp.ui.widgets.MessageViewHolder;
import info.guardianproject.keanuapp.ui.widgets.PdfViewActivity;
import info.guardianproject.keanuapp.ui.widgets.QuickReactionsRecyclerViewAdapter;
import info.guardianproject.keanuapp.ui.widgets.VideoViewActivity;
import info.guardianproject.keanuapp.ui.widgets.WebViewActivity;

import org.ocpsoft.prettytime.PrettyTime;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Browser;
import android.provider.MediaStore;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.provider.Settings;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.stefanosiano.powerful_libraries.imageview.PowerfulImageView;
import com.stefanosiano.powerful_libraries.imageview.blur.PivBlurMode;

import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;
import static info.guardianproject.keanu.core.KeanuConstants.SMALL_AVATAR_HEIGHT;
import static info.guardianproject.keanu.core.KeanuConstants.SMALL_AVATAR_WIDTH;

public class MessageListItem extends RelativeLayout {

    public enum DeliveryState {
        NEUTRAL, DELIVERED, UNDELIVERED
    }

    public enum EncryptionState {
        NONE, ENCRYPTED, ENCRYPTED_AND_VERIFIED

    }

    private String lastMessage = null;
    private DeliveryState delivered = null;
    private Uri mediaUri = null;
    private String mimeType = null;
    private String packetId = null;

    private Context context;
    private boolean linkify = false;

    private MessageViewHolder mHolder = null;

    private static PrettyTime sPrettyTime = null;

    public MessageListItem(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;

        sPrettyTime = new PrettyTime(getCurrentLocale());


    }


    private final static int THUMB_HEIGHT_LARGE = 450;
    private final static int THUMB_HEIGHT_SMALL = 100;

    /**
     * This trickery is needed in order to have clickable links that open things
     * in a new {@code Task} rather than in ChatSecure's {@code Task.} Thanks to @commonsware
     * https://stackoverflow.com/a/11417498
     *
     */
    class NewTaskUrlSpan extends ClickableSpan {

        private String urlString;

        NewTaskUrlSpan(String urlString) {
            this.urlString = urlString;
        }

        @Override
        public void onClick(View widget) {

            OnboardingManager.DecodedInviteLink diLink = null;
            try {
                diLink = OnboardingManager.decodeInviteLink(urlString);
                //not an invite link, so just send it out
                if (diLink == null) {
                    Uri uri = Uri.parse(urlString);
                    Context context = widget.getContext();
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
                else
                {
                    //it is an invite link, so target it back at us!
                    Uri uri = Uri.parse(urlString);
                    Context context = widget.getContext();
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
                    intent.setPackage(context.getPackageName()); //The package name of the app to which intent is to be sent
                    // intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }


        }
    }

    class URLSpanConverter implements LinkifyHelper.SpanConverter<URLSpan, ClickableSpan> {
        @Override
        public NewTaskUrlSpan convert(URLSpan span) {
            return (new NewTaskUrlSpan(span.getURL()));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mHolder.mAudioWife != null)
        {
            mHolder.mAudioWife.pause();
        }

    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    public void setLinkify(boolean linkify) {
        this.linkify = linkify;
    }

    public URLSpan[] getMessageLinks() {
        return mHolder.mTextViewForMessages.getUrls();
    }

    public String getMimeType () { return mimeType; }
    public String getLastMessage () {
        return lastMessage;
    }

    public boolean isNotDecrypted () {
        return lastMessage == null || lastMessage.equals(context.getString(R.string.unable_to_decrypt));
    }

    public boolean isDelivered () {
        return delivered == DeliveryState.DELIVERED;
    }

    public String getPacketId () { return packetId; }

    public ProgressBar getProgressBar(){
        return mHolder.progress;
    }
    public ImageView getMediaThumbnail(){
        return mHolder.mMediaThumbnail;
    }

    public void bindIncomingMessage(MessageViewHolder holder, int id, int messageType, String userAddress, String nickname, final String mimeType, final String body, Date date, Markup smileyRes,
                                    boolean scrolling, EncryptionState encryption, boolean showContact, int presenceStatus, IChatSession session, String packetId, String replyId) {

        mHolder = holder;
        applyStyleColors();
        mHolder.mTextViewForMessages.setVisibility(View.VISIBLE);
        mHolder.mAudioContainer.setVisibility(View.GONE);
        mHolder.mMediaContainer.setVisibility(View.GONE);
        mHolder.mTextViewForTimestamp.setVisibility(View.VISIBLE);
        mHolder.mPacketId = packetId;

        this.packetId = packetId;


        if (nickname.startsWith("@"))
            nickname = new MatrixAddress(userAddress).getUser();

        lastMessage = body;
        showAvatar(userAddress, nickname, true, presenceStatus);

        mHolder.resetOnClickListenerMediaThumbnail();

        boolean cmdSuccess = false;

        if( mimeType != null ) {

            Uri mediaUri = Uri.parse(body);
            lastMessage = body;

            if (mediaUri != null && mediaUri.getScheme() != null) {
                if (mimeType.startsWith("audio")) {
                 //   mHolder.mAudioButton.setImageResource(R.drawable.media_audio_play);

                    try {
                        mHolder.mAudioContainer.setVisibility(View.VISIBLE);
                        showAudioPlayer(mimeType, mediaUri, id, mHolder, mHolder.mLayoutInflater);
                        cmdSuccess = true;
                    } catch (Exception e) {
                        mHolder.mAudioContainer.setVisibility(View.GONE);
                    }

                } else {

                    mHolder.mTextViewForMessages.setVisibility(View.GONE);
                    mHolder.mMediaContainer.setVisibility(View.VISIBLE);
                    boolean centerCrop = mimeType.contains("jpg")||mimeType.contains("jpeg")||mimeType.contains("video")|| mimeType.contains("html");
                    cmdSuccess = showMediaThumbnail(mediaUri.getLastPathSegment(),mimeType, mediaUri, id, mHolder, centerCrop);

                }
            }

        }
        else if ((!TextUtils.isEmpty(lastMessage))
                && (lastMessage.charAt(0) == '/'||lastMessage.charAt(0) == ':'||lastMessage.startsWith("aesgcm://")))
        {

            if (lastMessage.startsWith("/sticker:"))
            {
                String[] cmds = lastMessage.split(":");

                String mimeTypeSticker = "image/png";

                try {

                    String assetPath = cmds[1].split(" ")[0].toLowerCase();//just get up to any whitespace;

                    //make sure sticker exists
                    AssetFileDescriptor afd = getContext().getAssets().openFd(assetPath);
                    afd.getLength();
                    afd.close();

                    //now setup the new URI for loading local sticker asset
                    Uri mediaUri = Uri.parse("asset://localhost/" + assetPath);

                    //now load the thumbnail
                    cmdSuccess = showMediaThumbnail(mediaUri.getLastPathSegment(), mimeTypeSticker, mediaUri, id, mHolder, false);
                }
                catch (Exception e)
                {
                    Log.e(LOG_TAG, "error loading sticker bitmap: " + cmds[1],e);
                    cmdSuccess = false;
                }

            }
            else if (lastMessage.startsWith(":"))
            {
                String[] cmds = lastMessage.split(":");

                String mimeTypeSticker = "image/png";
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

                    //now load the thumbnail
                    cmdSuccess = showMediaThumbnail(mediaUri.getLastPathSegment(), mimeTypeSticker, mediaUri, id, mHolder, false);
                } catch (Exception e) {
                    cmdSuccess = false;
                }
            }
            else if (lastMessage.startsWith("aesgcm://"))
            {
                //now load the thumbnail
                cmdSuccess = showDownloadThumbnail(lastMessage, id, mHolder, session, packetId);
            }

            if (!cmdSuccess)
            {
                mHolder.mTextViewForMessages.setText(formatMessage(lastMessage));
            }
            else
            {
                mHolder.mContainer.setBackgroundResource(android.R.color.transparent);
            }

        }
        else if (!TextUtils.isEmpty(lastMessage))
        {
            mHolder.mTextViewForMessages.setText(formatMessage(lastMessage));
        }

        if (date != null)
        {

            String contact = null;
            if (showContact) {
                if (nickname != null) {
                    contact = nickname;
                }
            }

           CharSequence tsText = formatTimeStamp(date,messageType, null, encryption, contact);


         mHolder.mTextViewForTimestamp.setText(tsText);

        }
        else
        {

            mHolder.mTextViewForTimestamp.setText("");

        }

        if (linkify)
           LinkifyHelper.addLinks(mHolder.mTextViewForMessages, new URLSpanConverter());


    }

    private boolean showDownloadThumbnail (final String mediaLink, int id, MessageViewHolder holder, final IChatSession session, final String packetId)
    {

        holder.mMediaThumbnail.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                    new Thread ()
                    {
                        public void run ()
                        {
                            try {
                                session.downloadMedia(mediaLink,packetId
                                        /**
                                         if (msg.getID() != null
                                         &&
                                         Imps.messageExists(mContentResolver, msg.getID())) {
                                         return false; //this message is a duplicate
                                         }**/);
                            }
                            catch (Exception e){
                                Log.e("Download","error downloading media",e);
                            }
                        }
                    }.start();

            }
        });

        holder.mTextViewForMessages.setText(lastMessage);
        holder.mTextViewForMessages.setVisibility(View.GONE);

        holder.mMediaThumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);

        holder.mMediaThumbnail.setImageResource(R.drawable.clouddownload);
        holder.mMediaThumbnail.setBackgroundResource(android.R.color.transparent);
        holder.mMediaContainer.setVisibility(View.VISIBLE);
        holder.mContainer.setBackgroundResource(android.R.color.transparent);

        return true;

    }

    private boolean showMediaThumbnail (String displayName, String mimeType, Uri mediaUri, int id, MessageViewHolder holder, boolean centerCrop)
    {
        this.mediaUri = mediaUri;
        this.mimeType = mimeType;

        /* Guess the MIME type in case we received a file that we can display or play*/
        if (TextUtils.isEmpty(mimeType) || mimeType.startsWith("application")) {
            String guessed = URLConnection.guessContentTypeFromName(mediaUri.toString());
            if (!TextUtils.isEmpty(guessed)) {
                if (TextUtils.equals(guessed, "video/3gpp"))
                    mimeType = "audio/3gpp";
                else
                    mimeType = guessed;
            }
        }


        holder.mTextViewForMessages.setText("");
        holder.mTextViewForMessages.setVisibility(View.GONE);
        holder.mMediaPlay.setVisibility(View.GONE);

       // holder.mMediaThumbnail.getLayoutParams().height = THUMB_HEIGHT_LARGE;
        holder.mMediaThumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);

        /**
        if (centerCrop)
            holder.mMediaThumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        else
            holder.mMediaThumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
         **/

        if( mimeType.startsWith("image/") ) {

            holder.mAvatar.setVisibility(View.VISIBLE);
            holder.mTextViewForTimestamp.setVisibility(View.VISIBLE);
           // holder.mMediaThumbnail.getLayoutParams().height = THUMB_HEIGHT_LARGE;
            setImageThumbnail( getContext().getContentResolver(), id, holder, mediaUri );
          //  holder.mMediaThumbnail.setBackgroundResource(android.R.color.transparent);

        }
        else if (mimeType.startsWith("video/")) {

            holder.mAvatar.setVisibility(View.VISIBLE);
            holder.mTextViewForTimestamp.setVisibility(View.VISIBLE);
         //   holder.mMediaThumbnail.getLayoutParams().height = THUMB_HEIGHT_LARGE;
            setVideoThumbnail( getContext().getContentResolver(), id, holder, mediaUri );
            holder.mMediaThumbnail.setBackgroundResource(android.R.color.transparent);
            holder.mMediaPlay.setImageResource(R.drawable.media_audio_play);
            holder.mMediaPlay.setVisibility(View.VISIBLE);

          //  holder.mTextViewForMessages.setText(mediaUri.getLastPathSegment() + " (" + mimeType + ")");
           // holder.mTextViewForMessages.setVisibility(View.VISIBLE);
        }
        else if (mimeType.contains("html")) {

            holder.mAvatar.setVisibility(View.VISIBLE);
            holder.mTextViewForTimestamp.setVisibility(View.VISIBLE);
         //   holder.mMediaThumbnail.getLayoutParams().height = THUMB_HEIGHT_LARGE;
       //
            String thumbUri = getImageFromContent(context,mediaUri);

            if (!TextUtils.isEmpty(thumbUri)) {
                setImageThumbnail(getContext().getContentResolver(), id, holder, Uri.parse(thumbUri));
                holder.mMediaPlay.setImageResource(R.drawable.ic_cloud_download_black_48dp);
                holder.mMediaPlay.setVisibility(View.VISIBLE);
            }
            else {
                holder.mMediaPlay.setVisibility(View.GONE);
                holder.mMediaThumbnail.setImageResource(R.drawable.file_unknown);
            }

            holder.mMediaThumbnail.setBackgroundResource(android.R.color.transparent);


            try {

                displayName = URLDecoder.decode(displayName, "UTF-8");
                displayName = displayName.replace('_', ' ');
                displayName = displayName.split("\\.")[0];
            }
            catch (Exception e){}

            holder.mTextViewForMessages.setText(displayName);
            holder.mTextViewForMessages.setVisibility(View.VISIBLE);

        }
        else if (mimeType.equals("text/csv") && mediaUri.getLastPathSegment().contains("proof.csv"))
        {
            holder.mAvatar.setVisibility(View.GONE);
            holder.mTextViewForTimestamp.setVisibility(View.GONE);
            holder.mMediaThumbnail.setScaleType(ImageView.ScaleType.FIT_START);
            holder.mMediaThumbnail.setImageResource(R.drawable.proofmodebadge); // generic file icon
        //    holder.mMediaThumbnail.getLayoutParams().height = THUMB_HEIGHT_SMALL;
            holder.mTextViewForMessages.setVisibility(View.GONE);
        }
        else
        {

            holder.mAvatar.setVisibility(View.VISIBLE);
            holder.mTextViewForTimestamp.setVisibility(View.VISIBLE);
            holder.mMediaThumbnail.setScaleType(ImageView.ScaleType.FIT_START);
          //  holder.mMediaThumbnail.getLayoutParams().height = THUMB_HEIGHT_LARGE;


            if (TextUtils.isEmpty(mimeType))
                holder.mMediaThumbnail.setImageResource(R.drawable.file_unknown); // generic file icon
            else if (mimeType.contains("pdf"))
                holder.mMediaThumbnail.setImageResource(R.drawable.file_pdf); // generic file icon
            else if (mimeType.contains("doc")||mimeType.contains("word"))
                holder.mMediaThumbnail.setImageResource(R.drawable.file_doc); // generic file icon
            else if (mimeType.contains("zip"))
                holder.mMediaThumbnail.setImageResource(R.drawable.file_zip); // generic file icon
            else
                holder.mMediaThumbnail.setImageResource(R.drawable.file_unknown); // generic file icon

            try {

                displayName = URLDecoder.decode(displayName, "UTF-8");
                displayName = displayName.replace('_', ' ');
                displayName = displayName.split("\\.")[0];
            }
            catch (Exception e){}

            holder.mTextViewForMessages.setText(displayName);
            holder.mTextViewForMessages.setVisibility(View.VISIBLE);
        }
        
        holder.setOnClickListenerMediaThumbnail(mimeType, mediaUri);

        holder.mMediaContainer.setVisibility(View.VISIBLE);
        holder.mContainer.setBackgroundResource(android.R.color.transparent);

        return true;

    }

    private void showAudioPlayer (String mimeType, Uri newMediaUri, int id, MessageViewHolder holder, LayoutInflater inflater) throws Exception
    {
        if (this.mediaUri != null && this.mediaUri.equals(newMediaUri))
            return;

        /* Guess the MIME type in case we received a file that we can display or play*/
        if (TextUtils.isEmpty(mimeType) || mimeType.startsWith("application")) {
            String guessed = URLConnection.guessContentTypeFromName(mediaUri.toString());
            if (!TextUtils.isEmpty(guessed)) {
                if (TextUtils.equals(guessed, "video/3gpp"))
                    mimeType = "audio/3gpp";
                else
                    mimeType = guessed;
            }
        }

        this.mediaUri = newMediaUri;
        this.mimeType = mimeType;

        holder.mAvatar.setVisibility(View.VISIBLE);
        holder.mTextViewForTimestamp.setVisibility(View.VISIBLE);
        mHolder.mTextViewForMessages.setText("");

        if (holder.mAudioWife != null)
        {
            holder.mAudioWife.pause();
            holder.mAudioWife.release();
        }

        holder.mAudioContainer.removeAllViews();
        // when done playing, release the resources

        holder.mAudioWife = new AudioWife();
        holder.mAudioWife.init(context, mediaUri, mimeType)
                .useDefaultUi(holder.mAudioContainer, inflater);

    }

    protected String convertMediaUriToPath(Uri uri) {
        String path = null;

        String [] proj={MediaStore.Images.Media.DATA};
        Cursor cursor = getContext().getContentResolver().query(uri, proj,  null, null, null);
        if (cursor != null && (!cursor.isClosed()))
        {
            if (cursor.isBeforeFirst())
            {
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                cursor.moveToFirst();
                path = cursor.getString(column_index);
            }

            cursor.close();
        }

        return path;
    }

    public void onClickMediaIcon(String mimeType, Uri mediaUri) {


        if (mimeType.startsWith("image")) {

            Intent intent = new Intent(context, ImageViewActivity.class);
            ArrayList<Uri> urisToShow = new ArrayList<>();
            urisToShow.add(mediaUri); // TODO - add all in thread!
            intent.putExtra(ImageViewActivity.URIS, urisToShow);
            context.startActivity(intent);

        }
        else if (mimeType.contains("pdf")) {
            Intent intent = new Intent(context, PdfViewActivity.class);
            intent.setDataAndType(mediaUri,mimeType);
            intent.putExtra("id",packetId);

            context.startActivity(intent);
        }
        else if (mimeType.contains("html")||mimeType.contains("text/plain")) {
            Intent intent = new Intent(context, WebViewActivity.class);
            intent.setDataAndType(mediaUri,mimeType);
            intent.putExtra("id",packetId);

            context.startActivity(intent);
        }
        else if (mimeType.contains("video")) {
            Intent intent = new Intent(context, VideoViewActivity.class);
            intent.setDataAndType(mediaUri,mimeType);
            intent.putExtra("id",packetId);

            context.startActivity(intent);
        }
        else if (mediaUri.getScheme().equals("content"))
        {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= 11)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);

            //set a general mime type not specific
            intent.setDataAndType(mediaUri, mimeType);

            Context context = getContext();

            if (isIntentAvailable(context, intent))
            {
                context.startActivity(intent);
            }
            else
            {

                intent = new Intent(Intent.ACTION_SEND);
                intent.setDataAndType(mediaUri, mimeType);

                if (isIntentAvailable(context, intent))
                {
                    context.startActivity(intent);
                }
                else {
                    Toast.makeText(getContext(), R.string.there_is_no_viewer_available_for_this_file_format, Toast.LENGTH_LONG).show();
                }
            }
        }
        else
        {
            exportMediaFile();
            /**
            String body = convertMediaUriToPath(mediaUri);

            if (body == null)
                body = new File(mediaUri.getPath()).getAbsolutePath();


            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= 11)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);

            //set a general mime type not specific
            intent.setDataAndType(Uri.parse(body), mimeType);

            **/
        }
    }

    private String getImageFromContent (Context context, Uri mediaUri)
    {
        InputStream is;

        if ((mediaUri.getScheme() == null || mediaUri.getScheme().equals("vfs"))&&mediaUri.getPath() != null)
        {
            try {
                is = (new FileInputStream(mediaUri.getPath()));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return null;
            }
        }
        else
        {
            try {
                is = (context.getContentResolver().openInputStream(mediaUri));

            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        if (is != null)
        {
            try
            {

                byte[] buffer = new byte[is.available()];
                is.read(buffer);
                is.close();
                String html = new String(buffer);

                List<String> urls = extractUrls(html);

                for (String url : urls)
                {
                    if (url.endsWith("jpg")||url.endsWith("gif"))
                        return url;

                }


            }
            catch (IOException ioe)
            {
                ioe.printStackTrace();
                return null;
            }

        }

        return null;
    }

    /**
     * Returns a list with all links contained in the input
     */
    public static List<String> extractUrls(String text)
    {
        List<String> containedUrls = new ArrayList<String>();
        String urlRegex = "((https?|ftp|gopher|telnet|file):((//)|(\\\\))+[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*)";
        Pattern pattern = Pattern.compile(urlRegex, Pattern.CASE_INSENSITIVE);
        Matcher urlMatcher = pattern.matcher(text);

        while (urlMatcher.find())
        {
            containedUrls.add(text.substring(urlMatcher.start(0),
                    urlMatcher.end(0)));
        }

        return containedUrls;
    }

    public Uri getMediaUri(){
        return mediaUri;
    }

    private void forwardMediaFile (String mimeType, Uri mediaUri)
    {

        String resharePath = "vfs:/" + mediaUri.getPath();
        Intent shareIntent = new Intent(context, ImUrlActivity.class);
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.setDataAndType(Uri.parse(resharePath), mimeType);
        context.startActivity(shareIntent);


    }

    public void nearbyMediaFile ()
    {
        if (mimeType != null && mediaUri != null) {
            String resharePath = "vfs:/" + mediaUri.getPath();
            Intent shareIntent = new Intent(context, NearbyShareActivity.class);
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.setDataAndType(Uri.parse(resharePath), mimeType);
            context.startActivity(shareIntent);
        }

    }

    public void forwardMediaFile ()
    {
        if (mimeType != null && mediaUri != null) {
            forwardMediaFile(mimeType, mediaUri);
        }
        else
        {
            Intent shareIntent = new Intent(context, ImUrlActivity.class);
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_TEXT, lastMessage);
            shareIntent.setType("text/plain");
            context.startActivity(shareIntent);
        }
    }

    public void exportMediaFile ()
    {
        

        if (mimeType != null && mediaUri != null) {
            java.io.File exportPath = SecureMediaStore.exportPath(mimeType, mediaUri);
            exportMediaFile(mimeType, mediaUri, exportPath, true);
            Log.v("ExportPath","ExportPath messageList=="+exportPath);

        }
        else
        {
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_TEXT,lastMessage);
            shareIntent.setType("text/plain");
            context.startActivity(Intent.createChooser(shareIntent, getResources().getText(R.string.export_media)));
        }

    };

    private void exportMediaFile (String mimeType, Uri mediaUri, java.io.File exportPath, boolean doView)
    {
        try {

            SecureMediaStore.exportContent(mimeType, mediaUri, exportPath);

            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(exportPath));
            shareIntent.setType(mimeType);
            context.startActivity(Intent.createChooser(shareIntent, getResources().getText(R.string.export_media)));

            } catch (Exception e) {
            Toast.makeText(getContext(), "Export Failed " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    public static boolean isIntentAvailable(Context context, Intent intent) {
        final PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> list =
                packageManager.queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    /**
     * @param contentResolver
     * @param id
     * @param aHolder
     * @param mediaUri
     */
    private void setVideoThumbnail(final ContentResolver contentResolver, final int id, final MessageViewHolder aHolder, final Uri mediaUri) {

        //if the same URI, we don't need to reload
        if (aHolder.mMediaUri != null
                && aHolder.mMediaUri.getPath() != null
                && aHolder.mMediaUri.getPath().equals(mediaUri.getPath()))
            return;

        // pair this holder to the uri. if the holder is recycled, the pairing is broken
        aHolder.mMediaUri = mediaUri;
        // if a content uri - already scanned

        if (SecureMediaStore.isVfsUri(mediaUri)) {
            File fileThumb = new File(mediaUri.getPath() + ".thumb.jpg");
            if (fileThumb.exists()) {
                //GlideUtils.loadVideoFromUri(context, mediaUri, aHolder.mMediaThumbnail);
                GlideUtils.loadImageFromUri(context, Uri.parse("vfs://" + fileThumb.getAbsolutePath()), aHolder.mMediaThumbnail);
            }
            else
            {
                aHolder.mMediaThumbnail.setImageResource(R.drawable.video256); // generic file icon
                aHolder.mMediaThumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
            }
        }
        else if (mediaUri.getScheme().equals("content")||mediaUri.getScheme().equals("file")) {

            GlideUtils.loadImageFromUri(context, mediaUri, aHolder.mMediaThumbnail);
        }
        else
        {
            aHolder.mMediaThumbnail.setImageResource(R.drawable.video256); // generic file icon
            aHolder.mMediaThumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
        }
    }
    /**
     * @param contentResolver
     * @param id
     * @param aHolder
     * @param mediaUri
     */
    private void setImageThumbnail(final ContentResolver contentResolver, final int id, final MessageViewHolder aHolder, final Uri mediaUri) {

        //if the same URI, we don't need to reload
        if (aHolder.mMediaUri != null
                && aHolder.mMediaUri.getPath() != null
                && aHolder.mMediaUri.getPath().equals(mediaUri.getPath()))
            return;


        // pair this holder to the uri. if the holder is recycled, the pairing is broken
        aHolder.mMediaUri = mediaUri;
        // if a content uri - already scanned

        GlideUtils.loadImageFromUri(context, mediaUri, aHolder.mMediaThumbnail);

    }


    private Spanned formatMessage (String body)
    {

        if (body != null)
            try {

                body = body.replace("\n","<br/>");

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    return Html.fromHtml(body, Html.FROM_HTML_MODE_LEGACY|Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH);
                } else {
                    return Html.fromHtml(body);
                }
            }
            catch (RuntimeException re){
                return null;
            }
        else
            return null;
    }

    public void bindOutgoingMessage(MessageViewHolder holder, int id, int messageType, String address, final String mimeType, final String body, Date date, Markup smileyRes, boolean scrolling,
                                    DeliveryState delivery, EncryptionState encryption, String packetId) {

        mHolder = holder;
        applyStyleColors();

        mHolder.mTextViewForMessages.setVisibility(View.VISIBLE);
        mHolder.mAudioContainer.setVisibility(View.GONE);
        mHolder.mMediaContainer.setVisibility(View.GONE);
        mHolder.mTextViewForTimestamp.setVisibility(View.VISIBLE);

        mHolder.resetOnClickListenerMediaThumbnail();

        this.packetId = packetId;
        lastMessage = body;
        delivered = delivery;

        if( mimeType != null ) {

            String mediaPath = body;

        //    if (body.contains(" "))
          //      mediaPath = body.split(" ")[0];

            Uri mediaUri = Uri.parse( mediaPath ) ;

            if (mimeType.startsWith("audio"))
            {
                try {
                    mHolder.mAudioContainer.setVisibility(View.VISIBLE);
                    showAudioPlayer(mimeType, mediaUri, id, mHolder, mHolder.mLayoutInflater);
                }
                catch (Exception e)
                {
                    mHolder.mAudioContainer.setVisibility(View.GONE);
                }

            }
            else {
              //  Log.v("ImageSend","showMediaThumbnail _1");
                mHolder.mTextViewForMessages.setVisibility(View.GONE);
                mHolder.mMediaContainer.setVisibility(View.VISIBLE);
                String displayName = mediaUri.getLastPathSegment();
                boolean centerCrop = false;//mimeType.contains("jpg")||mimeType.contains("jpeg")||mimeType.contains("video")|| mimeType.contains("html");
                showMediaThumbnail(displayName,mimeType, mediaUri, id, mHolder, centerCrop);
            }

        }
        else if ((!TextUtils.isEmpty(lastMessage)) && (lastMessage.charAt(0) == '/'||lastMessage.charAt(0) == ':')) {
//            String cmd = lastMessage.toString().substring(1);
            boolean cmdSuccess = false;

            if (lastMessage.startsWith("/sticker:")) {
                String[] cmds = lastMessage.split(":");

                String mimeTypeSticker = "image/png";
                try {
                    //make sure sticker exists
                    AssetFileDescriptor afd = getContext().getAssets().openFd(cmds[1]);
                    afd.getLength();
                    afd.close();

                    //now setup the new URI for loading local sticker asset
                    Uri mediaUri = Uri.parse("asset://localhost/" + cmds[1].toLowerCase());

                    //now load the thumbnail
                   // Log.v("ImageSend","showMediaThumbnail _2");
                    cmdSuccess = showMediaThumbnail(mediaUri.getLastPathSegment(), mimeTypeSticker, mediaUri, id, mHolder, false);
                } catch (Exception e) {
                    cmdSuccess = false;
                }

            }
            else if (lastMessage.startsWith(":"))
            {
                String[] cmds = lastMessage.split(":");

                String mimeTypeSticker = "image/png";
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

                    //now load the thumbnail
                    //Log.v("ImageSend","showMediaThumbnail _3");
                    cmdSuccess = showMediaThumbnail(mediaUri.getLastPathSegment(), mimeTypeSticker, mediaUri, id, mHolder, false);
                } catch (Exception e) {
                    cmdSuccess = false;
                }
            }

            if (!cmdSuccess)
            {
                mHolder.mTextViewForMessages.setText(new SpannableString(lastMessage));
            }
            else
            {
                holder.mContainer.setBackgroundResource(android.R.color.transparent);
            }

        }
        else {
            mHolder.mTextViewForMessages.setText(new SpannableString(lastMessage));
        }


        if (date != null)
        {

            CharSequence tsText = formatTimeStamp(date,messageType, delivery, encryption, null);
            mHolder.mTextViewForTimestamp.setText(tsText);


        }
        else
        {
            mHolder.mTextViewForTimestamp.setText("");

        }
        if (linkify)
            LinkifyHelper.addLinks(mHolder.mTextViewForMessages, new URLSpanConverter());
        LinkifyHelper.addTorSafeLinks(mHolder.mTextViewForMessages);
    }

    private void showAvatar (String address, String nickname, boolean isLeft, int presenceStatus)
    {
        if (mHolder.mAvatar == null)
            return;

        mHolder.mAvatar.setVisibility(View.GONE);

        if (address != null && isLeft)
        {

            RoundedAvatarDrawable avatar = null;

            try { avatar = (RoundedAvatarDrawable)DatabaseUtils.getAvatarFromAddress(address, SMALL_AVATAR_WIDTH, SMALL_AVATAR_HEIGHT);}
            catch (Exception e){}

            if (avatar != null)
            {
                mHolder.mAvatar.setVisibility(View.VISIBLE);
                mHolder.mAvatar.setImageDrawable(avatar);

                //setAvatarBorder(presenceStatus, avatar);

            }
            else
            {
              //  int color = getAvatarBorder(presenceStatus);
                int padding = 24;

                if (nickname.length() > 0) {
                    LetterAvatar lavatar = new LetterAvatar(getContext(), nickname, padding);

                    mHolder.mAvatar.setVisibility(View.VISIBLE);
                    mHolder.mAvatar.setImageDrawable(lavatar);
                }
            }
        }
    }

    /**
    public int getAvatarBorder(int status) {
        switch (status) {
        case Presence.AVAILABLE:
            return (getResources().getColor(R.color.holo_green_light));

        case Presence.IDLE:
            return (getResources().getColor(R.color.holo_green_dark));
        case Presence.AWAY:
            return (getResources().getColor(R.color.holo_orange_light));

        case Presence.DO_NOT_DISTURB:
            return(getResources().getColor(R.color.holo_red_dark));

        case Presence.OFFLINE:
            return(getResources().getColor(R.color.holo_grey_dark));

        default:
        }

        return Color.TRANSPARENT;
    }**/

    public void bindPresenceMessage(MessageViewHolder holder, String userAddress, String nickname, int type, Date date, boolean isGroupChat, boolean scrolling) {

        mHolder = holder;
        mHolder.mContainer.setBackgroundResource(android.R.color.transparent);
        mHolder.mTextViewForMessages.setVisibility(View.GONE);
        mHolder.mTextViewForTimestamp.setVisibility(View.VISIBLE);

        if (TextUtils.isEmpty(nickname)) {
            nickname = userAddress.split("\\|")[0];

            if (nickname.startsWith("@"))
                nickname = new MatrixAddress(userAddress).getUser();
        }

        CharSequence message = formatPresenceUpdates(nickname, type, date, isGroupChat, scrolling);
        mHolder.mTextViewForTimestamp.setText(message);

    }

    public void bindErrorMessage(int errCode) {

        mHolder = (MessageViewHolder)getTag();

        mHolder.mTextViewForMessages.setText(R.string.msg_sent_failed);
        mHolder.mTextViewForMessages.setTextColor(getResources().getColor(R.color.error));

    }

    private SpannableString formatTimeStamp(Date date, int messageType, DeliveryState delivery, EncryptionState encryptionState, String nickname) {


        StringBuilder deliveryText = new StringBuilder();

        if (nickname != null)
        {
            deliveryText.append(nickname);
            deliveryText.append(' ');
        }

        deliveryText.append(sPrettyTime.format(date));

        SpannableString spanText = null;

        spanText = new SpannableString(deliveryText.toString());

        if (delivery != null)
        {
            deliveryText.append(' ');
            //this is for delivery

            if (messageType == Imps.MessageType.QUEUED || messageType == Imps.MessageType.SENDING)
            {
                //do nothing
                deliveryText.append("X");
                spanText = new SpannableString(deliveryText.toString());
                int len = spanText.length();
                spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_message_wait_grey), len-1, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            else if (delivery == DeliveryState.DELIVERED) {

                if (encryptionState == EncryptionState.ENCRYPTED || encryptionState == EncryptionState.ENCRYPTED_AND_VERIFIED)
                {
                    deliveryText.append("XX");
                    spanText = new SpannableString(deliveryText.toString());
                    int len = spanText.length();

                    spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_delivered_grey), len - 2, len - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_encrypted_grey), len - 1, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                } else{
                    deliveryText.append("X");
                    spanText = new SpannableString(deliveryText.toString());
                    int len = spanText.length();
                    spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_delivered_grey), len-1, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                }



            } else if (delivery == DeliveryState.UNDELIVERED) {

                if (encryptionState == EncryptionState.ENCRYPTED||encryptionState == EncryptionState.ENCRYPTED_AND_VERIFIED) {
                    deliveryText.append("XX");
                    spanText = new SpannableString(deliveryText.toString());
                    int len = spanText.length();
                    spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_sent_grey),len-2,len-1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_encrypted_grey), len - 1, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                else
                {
                    deliveryText.append("X");
                    spanText = new SpannableString(deliveryText.toString());
                    int len = spanText.length();
                    spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_sent_grey),len-1,len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                }


            }
            else if (delivery == DeliveryState.NEUTRAL) {

                if (encryptionState == EncryptionState.ENCRYPTED||encryptionState == EncryptionState.ENCRYPTED_AND_VERIFIED) {
                    deliveryText.append("XX");
                    spanText = new SpannableString(deliveryText.toString());
                    int len = spanText.length();
                    spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_sent_grey),len-2,len-1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_encrypted_grey), len - 1, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                else
                {
                    deliveryText.append("X");
                    spanText = new SpannableString(deliveryText.toString());
                    int len = spanText.length();
                    spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_sent_grey),len-1,len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                }

            }


        }
        else
        {
            if (encryptionState == EncryptionState.ENCRYPTED||encryptionState == EncryptionState.ENCRYPTED_AND_VERIFIED)
            {
                deliveryText.append('X');
                spanText = new SpannableString(deliveryText.toString());
                int len = spanText.length();

                if (encryptionState == EncryptionState.ENCRYPTED||encryptionState == EncryptionState.ENCRYPTED_AND_VERIFIED)
                    spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_encrypted_grey), len-1, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            else if (messageType == Imps.MessageType.OUTGOING)
            {
                //do nothing
                deliveryText.append("X");
                spanText = new SpannableString(deliveryText.toString());
                int len = spanText.length();
                spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_message_wait_grey), len-1, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        return spanText;
    }

    private CharSequence formatPresenceUpdates(String contact, int type, Date date, boolean isGroupChat,
            boolean scrolling) {
        String body;

        Resources resources =getResources();

        switch (type) {
        case Imps.MessageType.PRESENCE_AVAILABLE:
            body = resources.getString(isGroupChat ? R.string.contact_joined
                                                   : R.string.contact_online, contact);
            break;

        case Imps.MessageType.PRESENCE_AWAY:
            body = resources.getString(R.string.contact_away, contact);
            break;

        case Imps.MessageType.PRESENCE_DND:
            body = resources.getString(R.string.contact_busy, contact);
            break;

        case Imps.MessageType.PRESENCE_UNAVAILABLE:
            body = resources.getString(isGroupChat ? R.string.contact_left
                                                   : R.string.contact_offline, contact);
            break;

        default:
            return null;
        }

        body += " - ";
        body += formatTimeStamp(date,type, null, EncryptionState.NONE, null);

        if (scrolling) {
            return body;
        } else {
            SpannableString spanText = new SpannableString(body);
            int len = spanText.length();
            spanText.setSpan(new StyleSpan(Typeface.ITALIC), 0, len,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spanText.setSpan(new RelativeSizeSpan((float) 0.8), 0, len,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            return spanText;
        }
    }

    /**
    public void setAvatarBorder(int status, RoundedAvatarDrawable avatar) {
        switch (status) {
        case Presence.AVAILABLE:
            avatar.setBorderColor(getResources().getColor(R.color.holo_green_light));
            break;

        case Presence.IDLE:
            avatar.setBorderColor(getResources().getColor(R.color.holo_green_dark));

            break;

        case Presence.AWAY:
            avatar.setBorderColor(getResources().getColor(R.color.holo_orange_light));
            break;

        case Presence.DO_NOT_DISTURB:
            avatar.setBorderColor(getResources().getColor(R.color.holo_red_dark));

            break;

        case Presence.OFFLINE:
            avatar.setBorderColor(getResources().getColor(R.color.holo_grey_light));

            break;


        default:
        }
    }**/

    public void applyStyleColors ()
    {
        //not set color
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getContext());
        int themeColorHeader = settings.getInt("themeColor",-1);
        int themeColorText = settings.getInt("themeColorText",-1);
        int themeColorBg = settings.getInt("themeColorBg",-1);

        if (mHolder != null) {
            if (themeColorText != -1) {
                if (mHolder.mTextViewForMessages != null)
                    mHolder.mTextViewForMessages.setTextColor(themeColorText);

                if (mHolder.mTextViewForTimestamp != null)
                    mHolder.mTextViewForTimestamp.setTextColor(themeColorText);

            }

            if (themeColorBg != -1)
            {

                int textBubbleBg = getContrastColor(themeColorText);
                 if (textBubbleBg == Color.BLACK)
                    mHolder.mContainer.setBackgroundResource(R.drawable.message_view_rounded_dark);
                 else
                    mHolder.mContainer.setBackgroundResource(R.drawable.message_view_rounded_light);

                //mHolder.mContainer.setBackgroundResource(android.R.color.transparent);
                //mHolder.mContainer.setBackgroundColor(themeColorBg);
            }
            else
            {
                mHolder.mContainer.setBackgroundResource(R.drawable.message_view_rounded_light);
            }

            if (themeColorHeader != -1) {
                QuickReactionsRecyclerViewAdapter.setThemeColor(getContext(), themeColorHeader);
            } else {
                QuickReactionsRecyclerViewAdapter.setThemeColor(getContext(), ContextCompat.getColor(getContext(), R.color.app_accent));
            }
        }

    }

    public static int getContrastColor(int colorIn) {
        @SuppressLint("Range") double y = (299 * Color.red(colorIn) + 587 * Color.green(colorIn) + 114 * Color.blue(colorIn)) / 1000;
        return y >= 128 ? Color.BLACK : Color.WHITE;
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
