package info.guardianproject.keanuapp.ui.conversation;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;

import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.github.barteksc.pdfviewer.PDFView;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.otaliastudios.cameraview.Facing;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;

import info.guardianproject.keanu.core.Preferences;
import info.guardianproject.keanu.core.util.SecureMediaStore;
import info.guardianproject.keanuapp.MultipleImageSelectionActivity;
import info.guardianproject.keanuapp.R;
import info.guardianproject.keanuapp.ui.camera.CameraActivity;
import info.guardianproject.keanuapp.ui.stories.GalleryAdapter;
import info.guardianproject.keanuapp.ui.stories.StoryGalleryActivity;
import info.guardianproject.keanuapp.ui.widgets.AudioRecorder;
import info.guardianproject.keanuapp.ui.widgets.CircularPulseImageButton;
import info.guardianproject.keanuapp.ui.widgets.GlideUtils;
import info.guardianproject.keanuapp.ui.widgets.MediaInfo;
import info.guardianproject.keanuapp.ui.widgets.PopupDialog;
import info.guardianproject.keanuapp.ui.widgets.StoryExoPlayerManager;

public class AddUpdateMediaActivity extends CameraActivity implements GalleryAdapter.GalleryAdapterListener, AudioRecorder.AudioRecorderListener {

    private static final int REQUEST_CODE_GALLERY = 1;
    private static final int REQUEST_CODE_READ_PERMISSIONS = 2;

    private Toolbar toolbar;
    private RecyclerView recyclerViewGallery;
    private BottomSheetBehavior<View> bottomSheetBehavior;
    private View bottomSheet;
    private boolean isInViewerMode = false;
    private CircularPulseImageButton cameraButton;
    private CircularPulseImageButton btnAddMultipleImage;
    private View cameraFlipButton;
    private CircularPulseImageButton microphoneButton;
    private View sendButton;
    private ImageView previewPhoto;
    private SimpleExoPlayerView previewVideo;
    private PDFView previewPdf;
    private ProgressBar progressBar;
    private AudioRecorder audioRecorder;
    private String storyTitle;

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
        btnAddMultipleImage = findViewById(R.id.btnAddMultipleImage);
        Intent intent = getIntent();
        if(intent != null){
            storyTitle = intent.getStringExtra("title");

            if (!TextUtils.isEmpty(intent.getType()))
            {
                if (intent.getType().startsWith("image"))
                    openGallery(StoryGalleryActivity.GALLERY_MODE_IMAGE);
                else if (intent.getType().startsWith("audio"))
                    openGallery(StoryGalleryActivity.GALLERY_MODE_AUDIO);
                else if (intent.getType().startsWith("video"))
                    openGallery(StoryGalleryActivity.GALLERY_MODE_VIDEO);


            }
        }


        btnAddMultipleImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                startActivityForResult(new Intent(AddUpdateMediaActivity.this, MultipleImageSelectionActivity.class).putExtra("title",storyTitle),101);
                //finish();
            }
        });
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
                sendMedia ();
            }
        });

        previewPhoto = findViewById(R.id.previewPhoto);
        previewVideo = findViewById(R.id.previewVideo);
        previewPdf = findViewById(R.id.previewPdf);

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

        mCameraView.setFacing(Facing.FRONT);
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
        }else if(requestCode == 101 && resultCode == RESULT_OK && data != null){
            ArrayList<MediaInfo> list = (ArrayList<MediaInfo>) data.getSerializableExtra("listMediaInfo");
            String title = data.getStringExtra("title");
            Intent result = new Intent();
            result.putExtra("listMediaInfo",list);
            result.putExtra("title",title);
            setResult(Activity.RESULT_OK, result);
            finish();

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
            microphoneButton.setVisibility((addedAudio() == null && addedVideo() == null && !isRecordingVideo) ? View.VISIBLE : View.GONE);
            cameraButton.setVisibility(isRecordingVideo ? View.VISIBLE : View.GONE);
            btnAddMultipleImage.setVisibility(isRecordingVideo ? View.GONE : View.VISIBLE);
            cameraFlipButton.setVisibility(View.GONE);
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
            MediaInfo addedPdf = addedPdf();

            if (addedAudio != null) {
                previewVideo.setVisibility(View.VISIBLE);
                showAudioPreview(addedAudio);
            } else if (addedVideo != null) {
                previewVideo.setVisibility(View.VISIBLE);
                showVideoPreview(addedVideo);
            } else {
                previewVideo.setVisibility(View.GONE);
            }

            if (addedPdf != null) {
                previewPdf.setVisibility(View.VISIBLE);
                showPdfPreview(addedPdf);
            } else {
                previewPdf.setVisibility(View.GONE);
            }
        } else {
            StoryExoPlayerManager.stop(previewVideo);
            for (MediaInfo media : addedMedia) {
                releaseMedia(media);
            }
            addedMedia.clear();
            mCameraView.setVisibility(View.VISIBLE);
            mCameraView.open();
            previewPhoto.setVisibility(View.GONE);
            previewVideo.setVisibility(View.GONE);
            previewPdf.setVisibility(View.GONE);
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

        // Start recording!
        if (audioRecorder == null) {
            audioRecorder = new AudioRecorder(this, this);
        } else if (audioRecorder.isAudioRecording()) {
            audioRecorder.stopAudioRecording(true);
        }
        setViewerMode(true);
        StoryExoPlayerManager.recordAudio(audioRecorder, previewVideo);
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

    private void showVideoPreview(MediaInfo mediaInfo) {
        StoryExoPlayerManager.load(mediaInfo, previewVideo, true);
    }

    private void showAudioPreview(MediaInfo mediaInfo) {
        StoryExoPlayerManager.load(mediaInfo, previewVideo, true);
    }

    private void showPdfPreview(MediaInfo mediaInfo) {
        try {
            InputStream is = null;
            if (SecureMediaStore.isVfsUri(mediaInfo.uri)) {
                is = (new info.guardianproject.iocipher.FileInputStream(mediaInfo.uri.getPath()));
            } else {
                is = (getContentResolver().openInputStream(mediaInfo.uri));
            }
            if (is != null) {
                previewPdf.fromStream(is)
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
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        // TODO Story - remove from VFS if stored!
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

    private void sendMedia ()
    {
        Intent result = new Intent();
        String[] resultUris = new String[addedMedia.size()];
        String[] resultTypes = new String[addedMedia.size()];

        int i = 0;

        for (i = 0; i < addedMedia.size(); i++)
        {
            resultUris[i] = addedMedia.get(i).uri.toString();
            resultTypes[i] = addedMedia.get(i).mimeType;
        }

        result.putExtra("resultUris",resultUris);
        result.putExtra("resultTypes",resultTypes);

        setResult(RESULT_OK,result);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (previewVideo != null && previewVideo.getPlayer() != null)
        {
            previewVideo.getPlayer().stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (previewVideo != null && previewVideo.getPlayer() != null)
        {
            previewVideo.getPlayer().release();
        }
    }
}
