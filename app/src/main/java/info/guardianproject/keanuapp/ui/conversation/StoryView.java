package info.guardianproject.keanuapp.ui.conversation;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PagerSnapHelper;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SnapHelper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ProgressBar;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.github.barteksc.pdfviewer.PDFView;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.util.SecureMediaStore;
import info.guardianproject.keanuapp.R;
import info.guardianproject.keanuapp.ui.widgets.AudioRecorder;
import info.guardianproject.keanuapp.ui.widgets.CircularPulseImageButton;
import info.guardianproject.keanuapp.ui.widgets.MediaInfo;
import info.guardianproject.keanuapp.ui.widgets.MessageViewHolder;
import info.guardianproject.keanuapp.ui.widgets.PZSImageView;
import info.guardianproject.keanuapp.ui.widgets.StoryExoPlayerManager;
import info.guardianproject.keanuapp.ui.widgets.VideoViewActivity;

import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;

/**
 * Created by N-Pex on 2019-03-28.
 */
public class StoryView extends ConversationView implements AudioRecorder.AudioRecorderListener {
    private final ProgressBar progressBar;
    private final SnapHelper snapHelper;
    private final SimpleExoPlayerView previewAudio;
    private AudioRecorder audioRecorder;
    private int currentPage = -1;
    private RecyclerView.ViewHolder currentPageViewHolder = null;

    private static final int AUTO_ADVANCE_TIMEOUT_IMAGE = 5000; // Milliseconds
    private static final int AUTO_ADVANCE_TIMEOUT_PDF = 5000; // Milliseconds

    /**
     * Set to true to automatically advance to next media item. For images this is after a set time, for video and audio when they are played.
     */
    private boolean autoAdvance = true;
    private boolean waitingForMoreData = false; // Set to true if we get an auto advance event while on the last item

    // If this is set, we are in "preview audio" mode.
    private MediaInfo recordedAudio;

    public StoryView(ConversationDetailActivity activity) {
        super(activity);
        final LinearLayoutManager llm = new LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false);
        llm.setStackFromEnd(false);
        mHistory.setLayoutManager(llm);
        snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(mHistory);

        progressBar = activity.findViewById(R.id.progress_horizontal);
        mHistory.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (currentPage != getCurrentPagePosition()) {
                        // Only react on change
                        setCurrentPage();
                        updateProgressCurrentPage();
                        autoAdvance = true;
                    }
                } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    getHistoryView().removeCallbacks(advanceToNextRunnable);
                    autoAdvance = false;
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
            }
        });
        mMicButton.setOnClickListener(null);
        mMicButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                captureAudioStart();
                return true;
            }
        });
        mMicButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (audioRecorder != null && audioRecorder.isAudioRecording() && (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
                    captureAudioStop(); // Stop!
                }
                return false;
            }
        });
        previewAudio = activity.findViewById(R.id.previewAudio);
        previewAudio.setVisibility(View.GONE);

        mComposeMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                autoAdvance = false;
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        activity.findViewById(R.id.composeMessage).clearFocus();
   }


    @Override
    protected void onSendButtonClicked() {
        // If we have recorded audio, send that!
        if (recordedAudio != null) {
            // TODO Story - Send the audio! It's in recorderAudio.uri (not in VFS). Need to delete afterwards.
            ((StoryActivity)mActivity).sendMedia(recordedAudio.uri,"audio/m4a",true);
            setRecordedAudio(null);
            return;
        }
        super.onSendButtonClicked();
    }

    @Override
    protected void sendMessage() {
        super.sendMessage();
        View view = mActivity.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }

    }

    private int getCurrentPagePosition() {
        View snapView = snapHelper.findSnapView(mHistory.getLayoutManager());
        if (snapView != null) {
            return mHistory.getLayoutManager().getPosition(snapView);
        }
        return RecyclerView.NO_POSITION;
    }

    private void updateProgressCurrentPage() {
        if (currentPage >= 0) {
            progressBar.setProgress(currentPage + 1);
        }
    }

    private void setCurrentPage() {

        if (currentPageViewHolder != null) {
            if (currentPageViewHolder.itemView instanceof SimpleExoPlayerView) {
                ((SimpleExoPlayerView)currentPageViewHolder.itemView).getPlayer().setPlayWhenReady(false);
            }
        }

        currentPage = getCurrentPagePosition();
        currentPageViewHolder = (currentPage >= 0) ? getHistoryView().findViewHolderForAdapterPosition(currentPage) : null;
        if (currentPageViewHolder != null) {
            if (currentPageViewHolder.itemView instanceof SimpleExoPlayerView) {
                SimpleExoPlayerView playerView = (SimpleExoPlayerView) currentPageViewHolder.itemView;
                playerView.getPlayer().setPlayWhenReady(true);
            } else if (currentPageViewHolder.itemView instanceof PZSImageView) {
                getHistoryView().removeCallbacks(advanceToNextRunnable);
                getHistoryView().postDelayed(advanceToNextRunnable, AUTO_ADVANCE_TIMEOUT_IMAGE);
            } else if (currentPageViewHolder.itemView instanceof PDFView) {
                getHistoryView().removeCallbacks(advanceToNextRunnable);
                getHistoryView().postDelayed(advanceToNextRunnable, AUTO_ADVANCE_TIMEOUT_PDF);
            }
        }
    }

    @Override
    protected Loader<Cursor> createLoader() {
        String selection = "mime_type LIKE 'image/%' OR mime_type LIKE 'audio/%' OR mime_type LIKE 'video/%' OR mime_type LIKE 'application/pdf'";
        CursorLoader loader = new CursorLoader(mActivity, mUri, null, selection, null, Imps.Messages.DEFAULT_SORT_ORDER);
        return loader;
    }

    @Override
    protected void loaderFinished() {
        // Dont call super, we don't want to scroll to last message
        //TODO - find last read message and scroll to that

        // If we are on the previously last message, advance?
        int n = getHistoryView().getAdapter().getItemCount();
        if (currentPage == n - 1 - 1 && autoAdvance && waitingForMoreData) {
            waitingForMoreData = false;
            getHistoryView().post(new Runnable() {
                @Override
                public void run() {
                    advanceToNext();
                }
            });
        }
    }

    @Override
    protected ConversationRecyclerViewAdapter createRecyclerViewAdapter() {
        return new StoryRecyclerViewAdapter(mActivity, null);
    }

    private void captureAudioStart() {
        mComposeMessage.setVisibility(View.INVISIBLE);

        // Start recording!
        if (audioRecorder == null) {
            audioRecorder = new AudioRecorder(previewAudio.getContext(), this);
        } else if (audioRecorder.isAudioRecording()) {
            audioRecorder.stopAudioRecording(true);
        }
        StoryExoPlayerManager.recordAudio(audioRecorder, previewAudio);
        audioRecorder.startAudioRecording();

        ((CircularPulseImageButton)mMicButton).setAnimating(true);
    }

    private void captureAudioStop() {
        ((CircularPulseImageButton)mMicButton).setAnimating(false);
        if (audioRecorder != null && audioRecorder.isAudioRecording()) {
            audioRecorder.stopAudioRecording(false);
        }
    }

    private void setRecordedAudio(MediaInfo recordedAudio) {
        this.recordedAudio = recordedAudio;
        if (this.recordedAudio != null) {
            mMicButton.setVisibility(View.GONE);
            mSendButton.setVisibility(View.VISIBLE);
            Drawable d = ActivityCompat.getDrawable(mActivity, R.drawable.ic_close_white_24dp).mutate();
            DrawableCompat.setTint(d, Color.GRAY);
            mActivity.getSupportActionBar().setHomeAsUpIndicator(d);
            mActivity.setBackButtonHandler(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    StoryExoPlayerManager.stop(previewAudio);
                    setRecordedAudio(null);
                }
            });
        } else {
            mActivity.getSupportActionBar().setHomeAsUpIndicator(null);
            mActivity.setBackButtonHandler(null);
            previewAudio.setVisibility(View.GONE);
            mComposeMessage.setVisibility(View.VISIBLE);
            mComposeMessage.setText("");
            mMicButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onAudioRecorded(Uri uri) {
        setRecordedAudio(new MediaInfo(uri, "audio/mp4"));
        StoryExoPlayerManager.load(recordedAudio, previewAudio, true);
    }

    class StoryRecyclerViewAdapter extends ConversationRecyclerViewAdapter implements PZSImageView.PSZImageViewImageMatrixListener {
        private final RequestOptions imageRequestOptions;

        public StoryRecyclerViewAdapter(Activity context, Cursor c) {
            super(context, c);
            imageRequestOptions = new RequestOptions().centerInside().diskCacheStrategy(DiskCacheStrategy.NONE).error(R.drawable.broken_image_large);
        }

        @Override
        public int getItemCount() {
            int count = super.getItemCount();
            if (progressBar != null) {
                progressBar.setMax(count);
                updateProgressCurrentPage();
            }
            return count;
        }

        @Override
        public int getItemViewType(int position) {
            try {
                Cursor c = getCursor();
                c.moveToPosition(position);
                String mime = c.getString(mMimeTypeColumn);
                if (!TextUtils.isEmpty(mime)) {
                    if (mime.startsWith("audio/") || mime.startsWith("video/")) {
                        return 1;
                    } else if (mime.contentEquals("application/pdf")) {
                        return 2;
                    }
                }
            } catch (Exception ignored) {
            }
            return 0; // Image
        }

        @Override
        public MessageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

            View mediaView = null;
            Context context = parent.getContext();

            switch (viewType) {
                case 2:
                    mediaView = new PDFView(context, null);
                    break;
                case 1:
                    SimpleExoPlayerView playerView = (SimpleExoPlayerView) LayoutInflater.from(context).inflate(R.layout.story_viewer_exo_player, parent, false);
                    mediaView = playerView;
                    mediaView.setBackgroundColor(0xff333333);
                    break;
                case 0:
                default:
                    PZSImageView imageView = new PZSImageView(context);
                    mediaView = imageView;
                    imageView.setBackgroundColor(0xff333333);
                    break;
            }

            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            mediaView.setLayoutParams(lp);

            MessageViewHolder mvh = new MessageViewHolder(mediaView);
            mvh.setLayoutInflater(LayoutInflater.from(parent.getContext()));
            mvh.setOnImageClickedListener(this);
            return mvh;
        }

        @Override
        public void onBindViewHolder(MessageViewHolder viewHolder, Cursor cursor) {

            int viewType = getItemViewType(cursor.getPosition());
            Context context = viewHolder.itemView.getContext();

            try {
                String mime = cursor.getString(mMimeTypeColumn);
                Uri uri = Uri.parse(cursor.getString(mBodyColumn));

                switch (viewType) {
                    case 2:
                        PDFView pdfView = (PDFView) viewHolder.itemView;
                        pdfView.post(new Runnable() {
                                         @Override
                                         public void run() {
                                             pdfView.recycle();
                                         }
                                     });
                        InputStream is = null;
                        if (SecureMediaStore.isVfsUri(uri)) {
                            try {
                                is = (new info.guardianproject.iocipher.FileInputStream(uri.getPath()));
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            }
                        } else {
                            try {
                                is = (context.getContentResolver().openInputStream(uri));
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            }
                        }
                        if (is != null) {
                            pdfView.fromStream(is)
                                    .enableSwipe(true) // allows to block changing pages using swipe
                                    .swipeHorizontal(false)
                                    .enableDoubletap(true)
                                    .defaultPage(0)
                                    .enableAnnotationRendering(false) // render annotations (such as comments, colors or forms)
                                    .password(null)
                                    .scrollHandle(null)
                                    .enableAntialiasing(true) // improve rendering a little bit on low-res screens
                                    // spacing between pages in dp. To define spacing color, set view background
                                    .spacing(0)
                                    .load();
                        }
                        break;
                    case 1:
                        SimpleExoPlayerView playerView = (SimpleExoPlayerView)viewHolder.itemView;
                        MediaInfo mediaInfo = new MediaInfo(uri, mime);
                        StoryExoPlayerManager.load(mediaInfo, playerView, false);
                        playerView.getPlayer().addListener(new Player.EventListener() {
                            @Override
                            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                                if (playbackState == Player.STATE_ENDED) {
                                    advanceToNext();
                                }
                            }
                        });
                        break;
                    case 0:
                    default:
                        PZSImageView imageView = (PZSImageView)viewHolder.itemView;

                        try {
                            imageView.setMatrixListener(this);
                            if (SecureMediaStore.isVfsUri(uri)) {

                                info.guardianproject.iocipher.File fileMedia = new info.guardianproject.iocipher.File(uri.getPath());

                                if (fileMedia.exists()) {
                                    Glide.with(context)
                                            .asBitmap()
                                            .apply(imageRequestOptions)
                                            .load(new info.guardianproject.iocipher.FileInputStream(fileMedia))
                                            .into(imageView);
                                } else {
                                    Glide.with(context)
                                            .asBitmap()
                                            .apply(imageRequestOptions)
                                            .load(R.drawable.broken_image_large)
                                            .into(imageView);
                                }
                            } else {
                                Glide.with(context)
                                        .asBitmap()
                                        .apply(imageRequestOptions)
                                        .load(uri)
                                        .into(imageView);
                            }
                        } catch (Throwable t) { // may run Out Of Memory
                            Log.w(LOG_TAG, "unable to load thumbnail: " + t);
                        }

                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (currentPage == -1) {
                currentPage = 0;
                getHistoryView().post(new Runnable() {
                    @Override
                    public void run() {
                        setCurrentPage();
                    }
                });
            }
        }

        @Override
        public void onViewRecycled(@NonNull MessageViewHolder holder) {
            if (holder.itemView instanceof PDFView) {
                final PDFView pdfView = (PDFView)holder.itemView;
                pdfView.post(new Runnable() {
                    @Override
                    public void run() {
                        pdfView.recycle();
                    }
                });
            } else if (holder.itemView instanceof SimpleExoPlayerView) {
                ((SimpleExoPlayerView)holder.itemView).getPlayer().stop(true);
                ((SimpleExoPlayerView)holder.itemView).getPlayer().release();
                ((SimpleExoPlayerView)holder.itemView).setPlayer(null);
            }
            super.onViewRecycled(holder);
        }

        @Override
        public void onImageMatrixSet(PZSImageView view, int imageWidth, int imageHeight, Matrix imageMatrix) {
            //TODO
        }
    }

    private Runnable advanceToNextRunnable = new Runnable() {
        @Override
        public void run() {
            advanceToNext();
        }
    };

    private void advanceToNext() {
        if (autoAdvance) {
            if (currentPage >= 0) {
                // At end of data?
                if ((currentPage + 1) < getHistoryView().getAdapter().getItemCount()) {
                    waitingForMoreData = false;
                    getHistoryView().smoothScrollToPosition(currentPage + 1);
                } else {
                    waitingForMoreData = true;
                }
            }
        }
    }
}
