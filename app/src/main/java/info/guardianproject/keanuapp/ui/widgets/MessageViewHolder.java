package info.guardianproject.keanuapp.ui.widgets;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;


import com.eternitywall.ots.Hash;
import com.stefanosiano.powerful_libraries.imageview.PowerfulImageView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;

import info.guardianproject.keanuapp.R;
import info.guardianproject.keanuapp.ui.conversation.MessageListItem;
import info.guardianproject.keanuapp.ui.conversation.QuickReaction;

/**
 * Created by n8fr8 on 12/11/15.
 */
public class MessageViewHolder extends MediaViewHolder implements QuickReactionsRecyclerViewAdapter.QuickReactionsRecyclerViewAdapterListener {
    public interface OnImageClickedListener {
        void onImageClicked(MessageViewHolder viewHolder, Uri image);
    }

    public interface OnQuickReactionClickedListener {
        void onQuickReactionClicked(MessageViewHolder viewHolder, QuickReaction quickReaction, String messageId);
    }

    public TextView mTextViewForMessages;
    public TextView mTextViewForTimestamp;
    public ImageView mAvatar;

    public ViewGroup mMediaContainer;
    public ViewGroup mAudioContainer;
   public ImageView mMediaThumbnail;
   public ProgressBar progress;


    public RecyclerView mQuickReactionContainer;
   // public VisualizerView mVisualizerView;
   // public ImageView mAudioButton;

    public LayoutInflater mLayoutInflater;
    // save the media uri while the MediaScanner is creating the thumbnail
    // if the holder was reused, the pair is broken

    private OnImageClickedListener onImageClickedListener;
    private OnQuickReactionClickedListener onQuickReactionClickedListener;
    public AudioWife mAudioWife;

    public String mPacketId; //this is the message ID

    public MessageViewHolder(View view) {
        super(view);

        mTextViewForMessages = (TextView) view.findViewById(R.id.message);
        mTextViewForTimestamp = (TextView) view.findViewById(R.id.messagets);
        mAvatar = (ImageView) view.findViewById(R.id.avatar);
        mMediaContainer = (ViewGroup)view.findViewById(R.id.media_thumbnail_container);
        mAudioContainer = (ViewGroup)view.findViewById(R.id.audio_container);
        mMediaThumbnail = (ImageView)view.findViewById(R.id.media_thumbnail);
        progress  = (ProgressBar)view.findViewById(R.id.progress);
        mQuickReactionContainer = view.findViewById(R.id.quick_reaction_container);
       // mVisualizerView = (VisualizerView) view.findViewById(R.id.audio_view);
       // mAudioButton = (ImageView) view.findViewById(R.id.audio_button);

        // disable built-in autoLink so we can add custom ones
        if (mTextViewForMessages != null) {
            mTextViewForMessages.setAutoLinkMask(0);
        }
        //mContainer.setBackgroundResource(R.drawable.message_view_rounded_light);
    }

    public void setOnImageClickedListener(OnImageClickedListener listener) {
        this.onImageClickedListener = listener;
    }

    public void setOnQuickReactionClickedListener(OnQuickReactionClickedListener onQuickReactionClickedListener) {
        this.onQuickReactionClickedListener = onQuickReactionClickedListener;
    }

    public void setOnClickListenerMediaThumbnail(final String mimeType, final Uri mediaUri ) {

        if (mimeType.startsWith("audio") && mAudioContainer != null)
        {
            mAudioContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ((MessageListItem)itemView).onClickMediaIcon(mimeType, mediaUri);
                }
            });

        }
        else {

            View viewForClick = mMediaThumbnail;
            if (mMediaPlay.getVisibility() == View.VISIBLE)
                viewForClick = mMediaPlay;

            viewForClick.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mimeType.startsWith("image")) {
                     if (onImageClickedListener != null) {
                         onImageClickedListener.onImageClicked(MessageViewHolder.this, mediaUri);
                     }
                    } else {
                        ((MessageListItem) itemView).onClickMediaIcon(mimeType, mediaUri);
                    }
                }
            });


        }

    }

    public void resetOnClickListenerMediaThumbnail() {
        mMediaThumbnail.setOnClickListener( null );
        mMediaPlay.setOnClickListener( null );
    }

    long mTimeDiff = -1;

    public void setLayoutInflater (LayoutInflater layoutInflater)
    {
        mLayoutInflater = layoutInflater;
    }

    public void setReactions(String packetId, ArrayList<QuickReaction> quickReactions) {

        if (mQuickReactionContainer != null && mPacketId != null && mPacketId.equals(packetId)) {

            if (quickReactions != null && quickReactions.size() > 0) {
                QuickReactionsRecyclerViewAdapter adapter = new QuickReactionsRecyclerViewAdapter(itemView.getContext(), quickReactions);
                mQuickReactionContainer.setAdapter(adapter);
                adapter.setListener(this);
            } else {
                mQuickReactionContainer.setAdapter(null);
            }
        }
    }

    @Override
    public void onReactionClicked(QuickReaction reaction) {
        if (onQuickReactionClickedListener != null) {
            onQuickReactionClickedListener.onQuickReactionClicked(this, reaction, mPacketId);
        }
    }
}
