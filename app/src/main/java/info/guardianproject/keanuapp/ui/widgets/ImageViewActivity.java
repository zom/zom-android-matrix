package info.guardianproject.keanuapp.ui.widgets;

import android.Manifest;
import android.animation.Animator;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Environment;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.github.barteksc.pdfviewer.PDFView;
import com.google.android.exoplayer2.ExoPlayerFactory;
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.util.SecureMediaStore;
import info.guardianproject.keanuapp.ImUrlActivity;
import info.guardianproject.keanuapp.R;
import info.guardianproject.keanuapp.nearby.NearbyShareActivity;

import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;

public class ImageViewActivity extends AppCompatActivity implements PZSImageView.PSZImageViewImageMatrixListener {

    public static final String URIS = "uris";
    public static final String MIME_TYPES = "mime_types";
    public static final String MESSAGE_IDS = "message_ids";
    public static final String CURRENT_INDEX = "current_index";

    private ConditionallyEnabledViewPager viewPagerPhotos;
    private RectF tempRect = new RectF();

    private ArrayList<Uri> uris;
    private ArrayList<String> mimeTypes;
    private ArrayList<String> messagePacketIds;

    private boolean mShowResend = false;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        supportRequestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        getSupportActionBar().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        //getSupportActionBar().setElevation(0);

        mShowResend = getIntent().getBooleanExtra("showResend",false);

        viewPagerPhotos = new ConditionallyEnabledViewPager(this);
        viewPagerPhotos.setBackgroundColor(0x33333333);
        setContentView(viewPagerPhotos);
        //setContentView(R.layout.image_view_activity);
        getSupportActionBar().show();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        setTitle("");

        //viewPagerPhotos = (ViewPager) findViewById(R.id.viewPagerPhotos);
        viewPagerPhotos.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                updateTitle();
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });

        viewPagerPhotos.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if ((right - left) > 0 && (bottom - top) > 0 && viewPagerPhotos.getAdapter() == null) {
                    uris = getIntent().getParcelableArrayListExtra(URIS);
                    mimeTypes = getIntent().getStringArrayListExtra(MIME_TYPES);
                    messagePacketIds = getIntent().getStringArrayListExtra(MESSAGE_IDS);

                    if (uris != null && mimeTypes != null && uris.size() > 0 && uris.size() == mimeTypes.size()) {

                        ArrayList<MediaInfo> info = new ArrayList<>(uris.size());
                        for (int i = 0; i < uris.size(); i++) {
                            info.add(new MediaInfo(uris.get(i), mimeTypes.get(i)));
                        }

                        viewPagerPhotos.setAdapter(new MediaPagerAdapter(ImageViewActivity.this, info));
                        int currentIndex = getIntent().getIntExtra(CURRENT_INDEX, 0);
                        viewPagerPhotos.setCurrentItem(currentIndex);
                        updateTitle();
                    }
                }
            }
        });

        /*viewPagerPhotos.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!viewPagerPhotosEnabled) {
                    return true;
                }
                return false;
            }
        });*/
       // getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        /**
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE);*/
    }

    private void updateTitle() {
        if (viewPagerPhotos.getAdapter() != null && viewPagerPhotos.getAdapter().getCount() > 0) {
            String title = getString(R.string.item_x_of_y, viewPagerPhotos.getCurrentItem() + 1, viewPagerPhotos.getAdapter().getCount());
            setTitle(title);
        } else {
            setTitle("");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_message_context, menu);

        menu.findItem(R.id.menu_message_copy).setVisible(false);
        menu.findItem(R.id.menu_message_resend).setVisible(mShowResend);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.menu_message_forward:
                forwardMediaFile();
                return true;
            case R.id.menu_message_share:
                Log.v("Export","call from here");
                exportMediaFile();
                return true;
                /**
            case R.id.menu_message_copy:
                exportMediaFile();
                return true;**/

            case R.id.menu_message_resend:
                resendMediaFile();
                return true;

            case R.id.menu_message_delete:
                deleteMediaFile();
                return true;

            case R.id.menu_message_nearby:
                sendNearby();
                return true;

            default:
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean checkPermissions ()
    {
        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);


        if (permissionCheck ==PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);
            return false;
        }

        return true;
    }

    public void sendNearby ()
    {
        if (checkPermissions()) {

            int currentItem = viewPagerPhotos.getCurrentItem();
            if (currentItem >= 0 && currentItem < uris.size()) {
                String resharePath = uris.get(currentItem).toString();
                Intent shareIntent = new Intent(this, NearbyShareActivity.class);
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.setDataAndType(Uri.parse(resharePath), mimeTypes.get(currentItem));
                startActivity(shareIntent);
            }
        }

    }


    public void exportMediaFile ()
    { if (checkPermissions()) {
        int currentItem = viewPagerPhotos.getCurrentItem();
        if (currentItem >= 0 && currentItem < uris.size()) {
            java.io.File exportPath = SecureMediaStore.exportPath(mimeTypes.get(currentItem), uris.get(currentItem));
            exportMediaFile(mimeTypes.get(currentItem), uris.get(currentItem), exportPath);
            Log.v("ExportPath","ExportPath 1=="+mimeTypes.get(currentItem));
            Log.v("ExportPath","ExportPath 2=="+uris.get(currentItem));
            Log.v("ExportPath","ExportPath 3=="+exportPath);
        }
    }
    };

    private void downloadImage(String uri){
        Uri imageUri = Uri.fromFile(new File(uri));

        DownloadManager.Request request = new DownloadManager.Request(imageUri);
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
        request.setAllowedOverRoaming(false);
        request.setTitle("GadgetSaint Downloading " + "Sample" + ".png");
        request.setDescription("Downloading " + "Sample" + ".png");
        request.setVisibleInDownloadsUi(true);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "/Keanu/"  + "/" + "Sample" + ".png");
        DownloadManager downloadManager= (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
       downloadManager.enqueue(request);

    }

    private void exportMediaFile (String mimeType, Uri mediaUri, java.io.File exportPath)
    {
        try {

            SecureMediaStore.exportContent(mimeType, mediaUri, exportPath);
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(exportPath));
            shareIntent.setType(mimeType);
            startActivity(Intent.createChooser(shareIntent, getResources().getText(R.string.export_media)));
        } catch (IOException e) {
            Toast.makeText(this, "Export Failed " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void forwardMediaFile ()
    {
        int currentItem = viewPagerPhotos.getCurrentItem();
        if (currentItem >= 0 && currentItem < uris.size()) {
            String resharePath = uris.get(currentItem).toString();
            Intent shareIntent = new Intent(this, ImUrlActivity.class);
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.setDataAndType(Uri.parse(resharePath), mimeTypes.get(currentItem));
            startActivity(shareIntent);
        }
    }

    private void resendMediaFile ()
    {
        int currentItem = viewPagerPhotos.getCurrentItem();
        if (currentItem >= 0 && currentItem < uris.size()) {
            String resharePath = uris.get(currentItem).toString();
            String mimeType = mimeTypes.get(currentItem).toString();

            Intent intentResult = new Intent();
            intentResult.putExtra("resendImageUri",resharePath);
            intentResult.putExtra("resendImageMimeType",mimeType);

            setResult(RESULT_OK,intentResult);
            finish();
        }
    }

    private void deleteMediaFile () {
        if (messagePacketIds != null) {
            int currentItem = viewPagerPhotos.getCurrentItem();
            if (currentItem >= 0 && currentItem < uris.size()) {

                Uri deleteUri = uris.get(currentItem);
                if (deleteUri.getScheme() != null && deleteUri.getScheme().equals("vfs"))
                {
                    info.guardianproject.iocipher.File fileMedia = new info.guardianproject.iocipher.File(deleteUri.getPath());
                    fileMedia.delete();
                }

                String messagePacketId = messagePacketIds.get(currentItem);
                Imps.deleteMessageInDb(getContentResolver(), messagePacketId);
                setResult(RESULT_OK);
                finish();
            }
        }
    }

    public class MediaPagerAdapter extends PagerAdapter {
        private final RequestOptions imageRequestOptions;

        Context context;
        List<MediaInfo> listMedia;

        public MediaPagerAdapter(Context context, List<MediaInfo> listMedia)
        {
            super();
            this.context = context;
            this.listMedia = listMedia;
            imageRequestOptions = new RequestOptions().centerInside().diskCacheStrategy(DiskCacheStrategy.NONE).error(R.drawable.broken_image_large);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            MediaInfo mediaInfo = listMedia.get(position);
            View mediaView = null;

            if (mediaInfo.isPDF()) {
                PDFView pdfView = new PDFView(context, null);
                mediaView = pdfView;
                pdfView.setId(position);
                container.addView(mediaView);

                InputStream is = null;
                if (SecureMediaStore.isVfsUri(mediaInfo.uri)) {
                    try {
                        is = (new info.guardianproject.iocipher.FileInputStream(mediaInfo.uri.getPath()));
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        is = (getContentResolver().openInputStream(mediaInfo.uri));
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
            } else if (mediaInfo.isAudio() || mediaInfo.isVideo()) {
                SimpleExoPlayerView playerView = new SimpleExoPlayerView(context);
                mediaView = playerView;
                mediaView.setBackgroundColor(0xff333333);
                mediaView.setId(position);
                container.addView(mediaView);

                DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter(); //test

                TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
                TrackSelector trackSelector =
                        new DefaultTrackSelector(videoTrackSelectionFactory);

                // 2. Create the player
                SimpleExoPlayer exoPlayer = ExoPlayerFactory.newSimpleInstance(context, trackSelector);

                ////Set media controller
                playerView.setUseController(true);//set to true or false to see controllers
                playerView.requestFocus();
                // Bind the player to the view.
                playerView.setPlayer(exoPlayer);

                DataSpec dataSpec = new DataSpec(mediaInfo.uri);
                final VideoViewActivity.InputStreamDataSource inputStreamDataSource = new VideoViewActivity.InputStreamDataSource(context, dataSpec);
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
                MediaSource audioSource = new ExtractorMediaSource(inputStreamDataSource.getUri(),
                        factory, new DefaultExtractorsFactory(), null, null);

                exoPlayer.prepare(audioSource);
                exoPlayer.setPlayWhenReady(false); //run file/link when ready to play.

            } else {
                PZSImageView imageView = new PZSImageView(context);
                mediaView = imageView;

                imageView.setBackgroundColor(0xff333333);
                imageView.setId(position);
                container.addView(imageView);

                try {
                    imageView.setMatrixListener(ImageViewActivity.this);
                    if (SecureMediaStore.isVfsUri(mediaInfo.uri)) {

                        info.guardianproject.iocipher.File fileMedia = new info.guardianproject.iocipher.File(mediaInfo.uri.getPath());
                        Log.v("ExportPath","ExportPath 4=="+fileMedia.getPath());
                        Log.v("ExportPath","ExportPath 5=="+mediaInfo.uri);
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
                            Log.v("ExportPath","ExportPath 66==");
                        }
                    } else {
                        Glide.with(context)
                                .asBitmap()
                                .apply(imageRequestOptions)
                                .load(mediaInfo.uri)
                                .into(imageView);
                        Log.v("ExportPath","ExportPath 666==");
                    }
                } catch (Throwable t) { // may run Out Of Memory
                    Log.w(LOG_TAG, "unable to load thumbnail: " + t);
                }
            }
            return mediaView;
        }

        @Override
        public int getCount() {
            return listMedia.size();
        }

        @Override
        public boolean isViewFromObject(View arg0, Object arg1) {
            return arg0 == arg1;
        }

        @Override
        public void destroyItem(ViewGroup collection, int position, Object arg2) {
            collection.removeView((View) arg2);
        }
    }

    @Override
    public void onImageMatrixSet(PZSImageView view, int imageWidth, int imageHeight, Matrix imageMatrix) {
        if (view.getId() != viewPagerPhotos.getCurrentItem()) {
            return;
        }
        if (imageMatrix != null) {
            tempRect.set(0, 0, imageWidth, imageHeight);
            imageMatrix.mapRect(tempRect);
            int width = view.getWidth() - view.getPaddingLeft() - view.getPaddingRight();
            int height = view.getHeight() - view.getPaddingTop() - view.getPaddingBottom();
            if (tempRect.width() > width || tempRect.height() > height) {
                viewPagerPhotos.enableSwiping = false;
                return;
            }
        }
        viewPagerPhotos.enableSwiping = true;
    }

    class ConditionallyEnabledViewPager extends ViewPager {
        public boolean enableSwiping = true;
        private final GestureDetector gestureDetector;
        private final SwipeToCloseListener gestureDetectorListener;
        private final VelocityTracker velocityTracker;
        private boolean inSwipeToCloseGesture = false;
        private boolean isClosing = false;
        private float startingY = 0;

        public ConditionallyEnabledViewPager(Context context) {
            super(context);
            gestureDetectorListener = new SwipeToCloseListener(context);
            gestureDetector = new GestureDetector(context, gestureDetectorListener);
            velocityTracker = VelocityTracker.obtain();
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if (inSwipeToCloseGesture) {
                if (!isClosing) {
                    velocityTracker.addMovement(ev);
                    if (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP) {
                        velocityTracker.computeCurrentVelocity(1000); // Pixels per second
                        float velocityY = velocityTracker.getYVelocity();
                        float dy = ev.getY() - startingY;
                        ViewConfiguration vc = ViewConfiguration.get(getContext());
                        if (Math.abs(dy) > vc.getScaledTouchSlop() && Math.abs(velocityY) > vc.getScaledMinimumFlingVelocity()) {
                            closeByFling(dy, Math.abs(viewPagerPhotos.getHeight() / velocityY));
                        } else {
                            // Reset all children. Lazy approach, instead of keeping count of "current photo" which
                            // might have changed during the motion event.
                            for (int i = 0; i < viewPagerPhotos.getChildCount(); i++) {
                                View child = viewPagerPhotos.getChildAt(i);
                                if (child.getTranslationY() != 0) {
                                    child.animate().translationY(0).alpha(1.0f).rotation(0).start();
                                }
                            }
                        }
                        inSwipeToCloseGesture = false;
                    } else {
                        gestureDetector.onTouchEvent(ev);
                    }
                }
                return true;
            } else if (enableSwiping && gestureDetector.onTouchEvent(ev)) {
                inSwipeToCloseGesture = true;
                velocityTracker.clear();
                velocityTracker.addMovement(ev);
                return true;
            }
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                startingY = ev.getY();
                gestureDetectorListener.setDisabled(false);
            } else if (ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                gestureDetectorListener.setDisabled(true); // More than one finger, disable swipe to close
            }
            return super.dispatchTouchEvent(ev);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (!enableSwiping) {
                return false;
            }
            return super.onInterceptTouchEvent(ev);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (!enableSwiping) {
                return true;
            }
            return super.onTouchEvent(ev);
        }

        void closeByFling(float dy, float seconds) {

            seconds = Math.min(seconds, 0.7f); // Upper limit on animation time!

            isClosing = true; // No further touches
            View currentPhoto = viewPagerPhotos.findViewById(viewPagerPhotos.getCurrentItem());
            if (currentPhoto != null) {
                currentPhoto.setPivotX(0.8f * currentPhoto.getWidth());
                currentPhoto.setTranslationY(dy);
                currentPhoto.setAlpha(Math.max(0, 1 - Math.abs(dy) / (viewPagerPhotos.getHeight() / 2)));
                currentPhoto.setRotation(30 * (dy / (viewPagerPhotos.getHeight() / 2)));
                currentPhoto.animate().rotation(Math.signum(dy) * 30).translationY(Math.signum(dy) * currentPhoto.getHeight()).alpha(0).setDuration((long)(1000 * seconds)).setListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        finish();
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {

                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {

                    }
                }).start();
            } else {
                // Hm, no animation, just close
                finish();
            }
        }

        @Override
        public boolean performClick() {
            return !enableSwiping || super.performClick();
        }

        private class SwipeToCloseListener extends GestureDetector.SimpleOnGestureListener {
            private final float minDistance;
            private boolean disabled;
            private boolean inGesture;

            public SwipeToCloseListener(Context context) {
                super();
                minDistance = ViewConfiguration.get(context).getScaledTouchSlop();
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                float dy = e2.getY() - e1.getY();
                float dx = e2.getX() - e1.getX();
                if (Math.abs(dy) > minDistance && !disabled) {
                    View currentPhoto = viewPagerPhotos.findViewById(viewPagerPhotos.getCurrentItem());
                    if (currentPhoto != null) {
                        currentPhoto.setPivotX(0.8f * currentPhoto.getWidth());
                        currentPhoto.setTranslationY(dy);
                        currentPhoto.setAlpha(Math.max(0, 1 - Math.abs(dy) / (viewPagerPhotos.getHeight() / 2)));
                        currentPhoto.setRotation(30 * (dy / (viewPagerPhotos.getHeight() / 2)));
                    }
                    inGesture = true;
                    return true;
                } else if (Math.abs(dx) > minDistance && !inGesture) {
                    disabled = true; // Looks like we have a horizontal movement, disable "swipe-to-close"
                }
                return false;
            }

            public void setDisabled(boolean disabled) {
                this.disabled = disabled;
                inGesture = false;
            }
        }
    };

}
