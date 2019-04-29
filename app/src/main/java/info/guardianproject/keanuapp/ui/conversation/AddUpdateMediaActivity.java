package info.guardianproject.keanuapp.ui.conversation;

import android.Manifest;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import info.guardianproject.keanu.core.Preferences;
import info.guardianproject.keanuapp.R;
import info.guardianproject.keanuapp.ui.camera.CameraActivity;
import info.guardianproject.keanuapp.ui.stories.GalleryAdapter;
import info.guardianproject.keanuapp.ui.stories.StoryGalleryActivity;
import info.guardianproject.keanuapp.ui.widgets.AudioRecorder;
import info.guardianproject.keanuapp.ui.widgets.CircularPulseImageButton;
import info.guardianproject.keanuapp.ui.widgets.GlideUtils;
import info.guardianproject.keanuapp.ui.widgets.MediaInfo;
import info.guardianproject.keanuapp.ui.widgets.PopupDialog;
import info.guardianproject.keanuapp.ui.widgets.VideoViewActivity;
import info.guardianproject.keanuapp.ui.widgets.VisualizerView;

public class AddUpdateMediaActivity extends CameraActivity implements GalleryAdapter.GalleryAdapterListener, AudioRecorder.AudioRecorderListener {

    private static final int REQUEST_CODE_GALLERY = 1;
    private static final int REQUEST_CODE_READ_PERMISSIONS = 2;

    private Toolbar toolbar;
    private RecyclerView recyclerViewGallery;
    private BottomSheetBehavior<View> bottomSheetBehavior;
    private View bottomSheet;
    private boolean isInViewerMode = false;
    private CircularPulseImageButton cameraButton;
    private View cameraFlipButton;
    private CircularPulseImageButton microphoneButton;
    private View sendButton;
    private ImageView previewPhoto;
    private SimpleExoPlayerView previewVideo;
    private VisualizerView previewAudio;
    private SimpleExoPlayer exoPlayer;
    private DefaultTrackSelector trackSelector;
    private ProgressBar progressBar;
    private AudioRecorder audioRecorder;

    private ArrayList<MediaInfo> addedMedia = new ArrayList<>();

    @Override
    protected void setContent() {
        setContentView(R.layout.awesome_activity_add_update_media);
    }

    @Override
    protected void setupActionBar() {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mCameraView.setLayerType(View.LAYER_TYPE_HARDWARE, null); // Make sure its hardware, so texture preview works as expected!

        mOneAndDone = false;

        // Secured from screen shots?
        if (Preferences.doBlockScreenshots()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE);
        }

        cameraButton = findViewById(R.id.btnCameraVideo);

        // Override functionality from super - We use the same button for photo and video: click
        // means take photo, long press means take video.
        //
        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                capturePhoto();
            }
        });
        cameraButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                captureVideoStart();
                return true;
            }
        });
        cameraButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isRecordingVideo && (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
                    captureVideoStop(); // Stop!
                }
                return false;
            }
        });

        cameraFlipButton = findViewById(R.id.toggle_camera);

        microphoneButton = findViewById(R.id.btnMicrophone);
        microphoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                microphoneButtonClicked();
            }
        });
        microphoneButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                captureAudioStart();
                return true;
            }
        });
        microphoneButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (audioRecorder != null && audioRecorder.isAudioRecording() && (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
                    captureAudioStop(); // Stop!
                }
                return false;
            }
        });

        sendButton = findViewById(R.id.btnSend);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Done, send!
                //TODO
            }
        });

        previewPhoto = findViewById(R.id.previewPhoto);
        previewVideo = findViewById(R.id.previewVideo);
        previewAudio = findViewById(R.id.previewAudio);

        progressBar = findViewById(R.id.progress_circular);

        recyclerViewGallery = findViewById(R.id.rvGallery);
        LinearLayoutManager llm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        recyclerViewGallery.setLayoutManager(llm);
        int spacingInPixels = getResources().getDimensionPixelSize(R.dimen.story_contrib_gallery_padding);
        recyclerViewGallery.addItemDecoration(new GalleryOnerowItemDecoration(spacingInPixels));
        setGalleryAdapter();

        // init the bottom sheet behavior
        bottomSheet = findViewById(R.id.bottom_sheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);

        // change the state of the bottom sheet
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        bottomSheetBehavior.setHideable(false);

        // set callback for changes
        bottomSheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                switch (newState) {
                    case BottomSheetBehavior.STATE_EXPANDED:
                        // Open the gallery and collapse again
                        openGallery(StoryGalleryActivity.GALLERY_MODE_ALL);
                        break;
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
            }
        });
        setProcessing(false);
        setViewerMode(false);
    }

    void openGallery(int galleryMode) {
        Intent intent = new Intent(this, StoryGalleryActivity.class);
        intent.putExtra(StoryGalleryActivity.ARG_GALLERY_MODE, galleryMode);
        startActivityForResult(intent, REQUEST_CODE_GALLERY);
    }

    private void setGalleryAdapter() {
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permissionCheck == PackageManager.PERMISSION_DENIED)
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_CODE_READ_PERMISSIONS);
            return;
        }
        recyclerViewGallery.setAdapter(new GalleryAdapter(this, this));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        if (requestCode == REQUEST_CODE_READ_PERMISSIONS) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setGalleryAdapter();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_GALLERY && resultCode == RESULT_OK && data != null) {
            MediaInfo selectedMedia = data.getParcelableExtra(StoryGalleryActivity.RESULT_SELECTED_MEDIA);
            if (selectedMedia != null) {
                onMediaItemClicked(selectedMedia);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (isInViewerMode) {
                // Exit viewer mode
                setViewerMode(false);
                return true;
            }
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStop() {
        super.onStop();
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    @Override
    protected void hideSystemUI() {
        // Override and do nothing
    }

    @Override
    protected void showSystemUI() {
        // Override and do nothing
    }

    private void capturePhoto() {
        setProcessing(true);
        btnCameraClicked();
        setViewerMode(true);
    }

    private void captureVideoStart() {
        cameraButton.setAnimating(true);
        btnCameraVideoClicked();
        setViewerMode(true);
    }

    private void captureVideoStop() {
        cameraButton.setAnimating(false);
        setProcessing(true);
        btnCameraVideoClicked();
    }

    @Override
    protected void onBitmap(Uri vfsUri, String mimeType) {
        super.onBitmap(vfsUri, mimeType);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mCameraView.close();
                setProcessing(false);
                addedMedia.add(new MediaInfo(vfsUri, mimeType));
                setViewerMode(true);
            }
        });
    }

    @Override
    protected void onVideo(Uri vfsUri, String mimeType) {
        super.onVideo(vfsUri, mimeType);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mCameraView.close();
                setProcessing(false);
                addedMedia.add(new MediaInfo(vfsUri, mimeType));
                setViewerMode(true);
            }
        });
    }

    private void setViewerMode(boolean viewerMode) {
        isInViewerMode = viewerMode;
        if (isInViewerMode) {
            microphoneButton.setVisibility((addedAudio() == null && addedVideo() == null && !isRecordingVideo) ? View.VISIBLE : View.INVISIBLE);
            cameraButton.setVisibility(isRecordingVideo ? View.VISIBLE : View.INVISIBLE);
            cameraFlipButton.setVisibility(View.INVISIBLE);
            bottomSheet.setVisibility(View.GONE);
            sendButton.setVisibility(addedMedia.size() > 0 ? View.VISIBLE : View.GONE);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_close_white_24dp);
            if (!isRecordingVideo) {
                mCameraView.close();
                mCameraView.setVisibility(View.GONE);
            }

            MediaInfo addedPhoto = addedPhoto();
            if (addedPhoto != null) {
                previewPhoto.setVisibility(View.VISIBLE);
                GlideUtils.loadImageFromUri(this, addedPhoto.uri, previewPhoto);
            } else {
                previewPhoto.setVisibility(View.GONE);
            }

            MediaInfo addedAudio = addedAudio();
            MediaInfo addedVideo = addedVideo();

            if (addedAudio != null) {
                previewVideo.setVisibility(View.VISIBLE);
                previewAudio.setVisibility(View.VISIBLE);
                showAudioPreview(addedAudio);
            } else if (addedVideo != null) {
                previewVideo.setVisibility(View.VISIBLE);
                previewAudio.setVisibility(View.GONE);
                showVideoPreview(addedVideo);
            } else {
                previewVideo.setVisibility(View.GONE);
                previewAudio.setVisibility(View.GONE);
            }
        } else {
            if (exoPlayer != null) {
                exoPlayer.setPlayWhenReady(false);
            }
            for (MediaInfo media : addedMedia) {
                releaseMedia(media);
            }
            addedMedia.clear();
            mCameraView.setVisibility(View.VISIBLE);
            mCameraView.open();
            previewPhoto.setVisibility(View.GONE);
            previewVideo.setVisibility(View.GONE);
            previewAudio.setVisibility(View.GONE);
            microphoneButton.setVisibility(View.VISIBLE);
            cameraButton.setVisibility(View.VISIBLE);
            cameraFlipButton.setVisibility(View.VISIBLE);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            bottomSheet.setVisibility(View.VISIBLE);
            sendButton.setVisibility(View.GONE);
            getSupportActionBar().setHomeAsUpIndicator(null);
        }
    }

    private void setProcessing(boolean processing) {
        progressBar.setVisibility(processing ? View.VISIBLE : View.GONE);
    }

    private boolean hasShownMicPopup = false;

    private void showMicPopup() {
        if (hasShownMicPopup) {
            return;
        }
        Dialog dialog = PopupDialog.showPopupFromAnchor(microphoneButton, R.layout.story_contrib_mic_popup, false);
        if (dialog != null) {
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    hasShownMicPopup = true;
                }
            });
        } else {
            // Something went wrong. Just mark as "shown" and get on with life...s
            hasShownMicPopup = true;
        }
    }

    private void microphoneButtonClicked() {
        if (!hasShownMicPopup) {
            showMicPopup();
            return;
        }

        // Open gallery in audio mode
        openGallery(StoryGalleryActivity.GALLERY_MODE_AUDIO);
    }

    private void captureAudioStart() {
        if (!hasShownMicPopup) {
            showMicPopup();
            return;
        }

        // Start recording!
        if (audioRecorder == null) {
            audioRecorder = new AudioRecorder(this, this);
        } else if (audioRecorder.isAudioRecording()) {
            audioRecorder.stopAudioRecording(true);
        }
        setViewerMode(true);
        previewAudio.setVisibility(View.VISIBLE);
        audioRecorder.setVisualizerView(previewAudio);
        audioRecorder.startAudioRecording();

        microphoneButton.setAnimating(true);
    }

    private void captureAudioStop() {
        microphoneButton.setAnimating(false);
        if (audioRecorder != null && audioRecorder.isAudioRecording()) {
            audioRecorder.stopAudioRecording(false);
        }
    }

    /**
     * Returns MediaItem for added photo, if any
     */
    private MediaInfo addedPhoto() {
        for (MediaInfo media : addedMedia) {
            if (media.isImage()) {
                return media;
            }
        }
        return null;
    }

    private MediaInfo addedAudio() {
        for (MediaInfo media : addedMedia) {
            if (media.isAudio()) {
                return media;
            }
        }
        return null;
    }

    private MediaInfo addedVideo() {
        for (MediaInfo media : addedMedia) {
            if (media.isVideo()) {
                return media;
            }
        }
        return null;
    }

    private MediaInfo addedPdf() {
        for (MediaInfo media : addedMedia) {
            if (media.isPDF()) {
                return media;
            }
        }
        return null;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (addedMedia.size() > 0) {
            outState.putParcelableArrayList("addedMedia", addedMedia);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState.containsKey("addedMedia")) {
            addedMedia = savedInstanceState.getParcelableArrayList("addedMedia");
            setViewerMode(true);
            savedInstanceState.remove("addedMedia");
        }
    }

    private void updatePlayerPosition() {
        previewAudio.removeCallbacks(updatePlayerPositionRunnable);
        previewAudio.post(updatePlayerPositionRunnable);
    }

    private Runnable updatePlayerPositionRunnable = new Runnable() {
        @Override
        public void run() {
            long duration = exoPlayer.getDuration();
            long pos = exoPlayer.getCurrentPosition();
            if (duration == 0) {
                previewAudio.setPlayFraction(0);
            } else {
                previewAudio.setPlayFraction((float)pos / (float)duration);
            }

            if (exoPlayer.getPlaybackState() == Player.STATE_READY) {
                previewAudio.post(updatePlayerPositionRunnable);
            }
        }
    };

    private void showVideoPreview(MediaInfo mediaInfo) {
        // Had the player been setup?
        if (exoPlayer == null) {

            DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter(); //test

            TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
            trackSelector =
                    new DefaultTrackSelector(videoTrackSelectionFactory);

            exoPlayer = ExoPlayerFactory.newSimpleInstance(this, trackSelector);
            exoPlayer.addListener(new Player.EventListener() {
                @Override
                public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                    updatePlayerPosition();
                }

                @Override
                public void onSeekProcessed() {
                    updatePlayerPosition();
                }
            });

            // Bind the player to the view.
            previewVideo.setPlayer(exoPlayer);
        }

        previewVideo.getVideoSurfaceView().setAlpha(mediaInfo.isAudio() ? 0 : 1);

        DataSpec dataSpec = new DataSpec(mediaInfo.uri);
        final VideoViewActivity.InputStreamDataSource inputStreamDataSource = new VideoViewActivity.InputStreamDataSource(this, dataSpec);
        try {
            inputStreamDataSource.open(dataSpec);
        } catch (IOException e) {
            e.printStackTrace();
        }

        DataSource.Factory factory = new DataSource.Factory() {

            @Override
            public DataSource createDataSource() {
                return inputStreamDataSource;
            }
        };
        MediaSource mediaSource = new ExtractorMediaSource(inputStreamDataSource.getUri(),
                factory, new DefaultExtractorsFactory(), null, null);

        //LoopingMediaSource loopingSource = new LoopingMediaSource(mediaSource);
        exoPlayer.prepare(mediaSource);
        exoPlayer.setPlayWhenReady(true); //run file/link when ready to play.
    }

    private void showAudioPreview(MediaInfo mediaInfo) {
        previewAudio.loadAudioFile(mediaInfo.uri);
        showVideoPreview(mediaInfo);
    }

    @Override
    public void onMediaItemClicked(MediaInfo mediaInfo) {
        MediaInfo old = null;
        if (mediaInfo.isImage()) {
            old = addedPhoto();
        } else if (mediaInfo.isAudio()) {
            old = addedAudio();
        } else if (mediaInfo.isVideo()) {
            old = addedVideo();
        } else if (mediaInfo.isPDF()) {
            old = addedPdf();
        }
        if (old != null) {
            addedMedia.remove(old);
            releaseMedia(old);
        }
        addedMedia.add(mediaInfo);
        setViewerMode(true);
    }

    private void releaseMedia(MediaInfo mediaInfo) {
        // TODO - remove from VFS if stored!
        if (mediaInfo.isAudio()) {
            if (mediaInfo.uri.getPath().startsWith(getFilesDir().getPath())) {
                // Local recording, delete!
                new File(mediaInfo.uri.getPath()).delete();
            }
        }
    }

    @Override
    public void onAudioRecorded(Uri uri) {
        onMediaItemClicked(new MediaInfo(uri, "audio/mp4"));
    }

    public class GalleryOnerowItemDecoration extends RecyclerView.ItemDecoration {

        private int spacing;

        public GalleryOnerowItemDecoration(int spacing) {
            this.spacing = spacing;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            outRect.set(0,0,0,0);
            if (parent.getChildAdapterPosition(view) > 0) {
                outRect.left = spacing;
            }
        }
    }
}
