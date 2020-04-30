package info.guardianproject.keanuapp.ui.widgets;

import android.Manifest;
import android.animation.Animator;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.guardianproject.keanu.core.model.ImErrorInfo;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.service.IConnectionListener;
import info.guardianproject.keanu.core.service.IImConnection;
import info.guardianproject.keanu.core.service.RemoteImService;
import info.guardianproject.keanu.core.util.SecureMediaStore;
import info.guardianproject.keanuapp.ImApp;
import info.guardianproject.keanuapp.ImUrlActivity;
import info.guardianproject.keanuapp.R;
import info.guardianproject.keanuapp.nearby.NearbyShareActivity;

import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;
import static info.guardianproject.keanuapp.ui.widgets.SecureCameraActivity.THUMBNAIL_SIZE;

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

            case R.id.menu_downLoad:
                Log.v("Download","call from here");

                if (ContextCompat.checkSelfPermission(ImageViewActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    int currentItem = viewPagerPhotos.getCurrentItem();
                    if (currentItem >= 0 && currentItem < uris.size()) {
                        java.io.File exportPath = SecureMediaStore.exportPath(mimeTypes.get(currentItem), uris.get(currentItem));
                        new DownloadImage().execute(Uri.fromFile(exportPath));
                     /*   Uri mOutputFileUri = FileProvider.getUriForFile(this,
                                getPackageName() + ".provider",
                                exportPath);*/
                    }
                } else {
                    // Request permission from the user
                    ActivityCompat.requestPermissions(ImageViewActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE}, 104);
                }
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



    private void exportMediaFile (String mimeType, Uri mediaUri, java.io.File exportPath)
    {
        try {

            SecureMediaStore.exportContent(mimeType, mediaUri, exportPath);
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(exportPath));
            shareIntent.setType(mimeType);

            Log.v("ExportPath","ExportPath 4=="+Uri.fromFile(exportPath));
            startActivity(Intent.createChooser(shareIntent, getResources().getText(R.string.export_media)));
        } catch (IOException e) {
            Toast.makeText(this, "Export Failed " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }

    }
    private class DownloadImage extends AsyncTask<Uri, Void, Bitmap> {
        private Bitmap downloadImageBitmap(Uri sUrl) throws IOException {
            Bitmap bitmap = getThumbnail(sUrl,ImageViewActivity.this);
            return bitmap;
        }

        @Override
        protected Bitmap doInBackground(Uri... params) {
            try {
                return downloadImageBitmap(params[0]);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        protected void onPostExecute(Bitmap result) {
            Log.v("saveFile","saveFileInDownloadFolder result=="+result);
            saveFileInDownloadFolder(result);
        }
    }
    public  Bitmap getThumbnail(Uri uri,Context context) throws FileNotFoundException, IOException{
        InputStream input = context.getContentResolver().openInputStream(uri);

        BitmapFactory.Options onlyBoundsOptions = new BitmapFactory.Options();
        onlyBoundsOptions.inJustDecodeBounds = true;
        onlyBoundsOptions.inDither=true;//optional
        onlyBoundsOptions.inPreferredConfig=Bitmap.Config.ARGB_8888;//optional
        BitmapFactory.decodeStream(input, null, onlyBoundsOptions);
        input.close();

        if ((onlyBoundsOptions.outWidth == -1) || (onlyBoundsOptions.outHeight == -1)) {
            return null;
        }

        int originalSize = (onlyBoundsOptions.outHeight > onlyBoundsOptions.outWidth) ? onlyBoundsOptions.outHeight : onlyBoundsOptions.outWidth;

        double ratio = (originalSize > THUMBNAIL_SIZE) ? (originalSize / THUMBNAIL_SIZE) : 1.0;

        BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
        bitmapOptions.inSampleSize = getPowerOfTwoForSampleRatio(ratio);
        bitmapOptions.inDither = true; //optional
        bitmapOptions.inPreferredConfig=Bitmap.Config.ARGB_8888;//
        input = context.getContentResolver().openInputStream(uri);
        Bitmap bitmap = BitmapFactory.decodeStream(input, null, bitmapOptions);
        input.close();
        return bitmap;
    }
    private  int getPowerOfTwoForSampleRatio(double ratio){
        int k = Integer.highestOneBit((int)Math.floor(ratio));
        if(k==0) return 1;
        else return k;
    }

    public void saveFileInDownloadFolder(Bitmap bitmap){
        String filename = "Keanu_"+getDateTime()+".jpeg";
        File sd = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File dest = new File(sd, filename);
        try {
            FileOutputStream out = new FileOutputStream(dest);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();
            Toast.makeText(getApplicationContext(),"Save Image Successfully.",Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.v("saveFile","saveFileInDownloadFolder=="+e.getMessage());
            e.printStackTrace();
        }
    }
    private String getDateTime() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        Date date = new Date();
        return dateFormat.format(date);
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 104:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    int currentItem = viewPagerPhotos.getCurrentItem();
                    if (currentItem >= 0 && currentItem < uris.size()) {
                        java.io.File exportPath = SecureMediaStore.exportPath(mimeTypes.get(currentItem), uris.get(currentItem));
                        new DownloadImage().execute(Uri.fromFile(exportPath));
                    }
                } else {
                    // Permission Denied
                    Toast.makeText(ImageViewActivity.this, "Permission Denied", Toast.LENGTH_SHORT)
                            .show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }



}
