package info.guardianproject.keanuapp.ui.camera;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.OrientationEventListener;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.otaliastudios.cameraview.BitmapCallback;
import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.Flash;
import com.otaliastudios.cameraview.Mode;
import com.otaliastudios.cameraview.PictureResult;
import com.otaliastudios.cameraview.Size;
import com.otaliastudios.cameraview.VideoCodec;
import com.otaliastudios.cameraview.VideoResult;

import org.apache.commons.io.IOUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileOutputStream;
import info.guardianproject.keanu.core.Preferences;
import info.guardianproject.keanuapp.R;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.util.SecureMediaStore;

import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;


public class CameraActivity extends AppCompatActivity {

    protected CameraView mCameraView;
    OrientationEventListener mOrientationEventListener;
    int mLastOrientation = -1;

    protected boolean mOneAndDone = true;
    public final static String SETTING_ONE_AND_DONE = "oad";

    public static final int ORIENTATION_PORTRAIT = 0;
    public static final int ORIENTATION_LANDSCAPE = 1;
    public static final int ORIENTATION_PORTRAIT_REVERSE = 2;
    public static final int ORIENTATION_LANDSCAPE_REVERSE = 3;

    private final static int VIDEO_KBITRATE = 800;
    private final static int AUDIO_KBITRATE = 64;

    File fileVideoTmp;
    protected boolean isRecordingVideo = false;
    Bitmap thumbnail = null;

   // private final static int MAX_LENGTH_MS = 60 * 2 * 1000;
    private final static int MAX_FILE_SIZE = 100 * 1024 * 1024;

    private Handler mHandler = new Handler ()
    {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            if (msg.what == 1)
            {
              //  Toast.makeText(CameraActivity.this,"\uD83D\uDCF7",Toast.LENGTH_SHORT).show();
            }
            else if (msg.what == 2)
            {
                mCameraView.close();
                finish();
            }
        }
    };

    private Executor mExec = new ThreadPoolExecutor(1,3,60L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    protected void setupActionBar() {
        setTitle("");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    protected void setContent() {
        setContentView(R.layout.activity_camera);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContent();
        setupActionBar();

        mOneAndDone = getIntent().getBooleanExtra(SETTING_ONE_AND_DONE,true);

        mCameraView = findViewById(R.id.camera_view);

        mCameraView.addCameraListener(new CameraListener() {


            @Override
            public void onPictureTaken(PictureResult result) {
                super.onPictureTaken(result);

                if (isRecordingVideo) {
                    startVideoRecording ();
                    result.toBitmap(new BitmapCallback() {
                        @Override
                        public void onBitmapReady(@Nullable Bitmap bitmap) {
                            thumbnail = bitmap;
                        }
                    });
                }
                else {
                    result.toBitmap(bitmap -> {
                        storeBitmap(bitmap);
                        if (mOneAndDone)
                            mHandler.sendEmptyMessage(2);
                    });


                    mHandler.sendEmptyMessage(1);
                }

            }

            @Override
            public void onVideoTaken(final VideoResult result) {
                super.onVideoTaken(result);

                mExec.execute(new Runnable() {
                    @Override
                    public void run() {
                        storeVideo(result.getFile());
                    }
                });

            }

            @Override
            public void onCameraClosed() {
                super.onCameraClosed();

            }
        });
        mCameraView.setVideoSize(source -> {
            ArrayList<Size> result = new ArrayList<>();

            for (Size size : source)
            {
                if (size.getWidth() < 1000)
                    result.add(size);
            }
            return result;
        });

        View btnCamera = findViewById(R.id.btnCamera);
        if (btnCamera != null) {
            btnCamera.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    btnCameraClicked();
		}
            });
        }

        View btnCameraVideo = findViewById(R.id.btnCameraVideo);
        if (btnCameraVideo != null) {
            btnCameraVideo.setOnClickListener(new View.OnClickListener() {
                @Override
                public synchronized void onClick(View view) {
                    btnCameraVideoClicked();
                }
            });
        }

        View btnToggleCamera = findViewById(R.id.toggle_camera);
        if (btnToggleCamera != null) {
            btnToggleCamera.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mCameraView.toggleFacing();
                }
            });
        }

        View btnToggleFlash = findViewById(R.id.toggle_flash);
        if (btnToggleFlash != null) {
            btnToggleFlash.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mCameraView.setFlash(Flash.AUTO);
                }
            });
        }

        mOrientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {

                if (orientation < 0) {
                    return; // Flip screen, Not take account
                }

                int curOrientation;

                if (orientation <= 45) {
                    curOrientation = ORIENTATION_PORTRAIT;
                } else if (orientation <= 135) {
                    curOrientation = ORIENTATION_LANDSCAPE_REVERSE;
                } else if (orientation <= 225) {
                    curOrientation = ORIENTATION_PORTRAIT_REVERSE;
                } else if (orientation <= 315) {
                    curOrientation = ORIENTATION_LANDSCAPE;
                } else {
                    curOrientation = ORIENTATION_PORTRAIT;
                }
                if (curOrientation != mLastOrientation) {

                    mLastOrientation = curOrientation;

                }
            }
        };

        if (mOrientationEventListener.canDetectOrientation()) {
            mOrientationEventListener.enable();
        } else {
            mOrientationEventListener.disable();
        }

    }

    protected void btnCameraClicked() {
        if (!mCameraView.isTakingVideo()) {
            mCameraView.setMode(Mode.PICTURE);
            mCameraView.takePictureSnapshot();
        }
    }

    protected void btnCameraVideoClicked() {
        if (isRecordingVideo) {
            ((ImageView) findViewById(R.id.btnCameraVideo)).setImageResource(R.drawable.ic_video_rec);

            mCameraView.stopVideo();
            isRecordingVideo = false;
        } else {
            isRecordingVideo = true;
            startVideoRecording();
        }
    }

    private void startVideoRecording ()
    {
        mCameraView.setMode(Mode.VIDEO);

        //mCameraView.setVideoMaxDuration(MAX_LENGTH_MS);
        mCameraView.setVideoMaxSize(MAX_FILE_SIZE);

        mCameraView.setVideoCodec(VideoCodec.H_264);
        mCameraView.setVideoBitRate(VIDEO_KBITRATE * 1000);
        mCameraView.setAudioBitRate(AUDIO_KBITRATE * 1000);

        ((ImageView)findViewById(R.id.btnCameraVideo)).setImageResource(R.drawable.ic_video_stop);

        fileVideoTmp = new File(getFilesDir(), "tmp.mp4");
        mCameraView.takeVideo(fileVideoTmp);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mOrientationEventListener.disable();
        System.gc();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private Bitmap rotate (Bitmap bitmap, int rotationDegrees)
    {

        Matrix matrix = new Matrix();
        matrix.postRotate(-rotationDegrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            //hideSystemUI();
        }
    }

    protected void hideSystemUI() {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE
                        // Set the content to appear under the system bars so that the
                        // content doesn't resize when the system bars hide and show.
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        // Hide the nav bar and status bar
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    // Shows the system bars by removing all the flags
// except for the ones that make the content appear under the system bars.
    protected void showSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCameraView.open();

    }

    @Override
    protected void onPause() {

        if (mCameraView.isOpened())
            mCameraView.close();

        super.onPause();
    }

    private void storeVideo (java.io.File fileVideo)
    {
        // import
        String sessionId = "self";
        String offerId = UUID.randomUUID().toString();

        try {

            final Uri vfsUri = SecureMediaStore.createContentPath(sessionId,"cam" + new Date().getTime() + ".mp4");

            OutputStream out = new info.guardianproject.iocipher.FileOutputStream(new File(vfsUri.getPath()));
            InputStream in = new java.io.FileInputStream(fileVideo);
            IOUtils.copyLarge(in, out);
            in.close();
            out.close();

            if (thumbnail == null) {
                thumbnail = ThumbnailUtils.createVideoThumbnail(fileVideo.getPath(), MediaStore.Images.Thumbnails.MINI_KIND);
            }

            fileVideo.delete();
            System.gc();


            if (thumbnail != null)
                thumbnail.compress(Bitmap.CompressFormat.JPEG,
                        80,new info.guardianproject.iocipher.FileOutputStream(new File(vfsUri.getPath()+".thumb.jpg")));


            String mimeType = "video/mp4";

            //adds in an empty message, so it can exist in the gallery and be forwarded
            Imps.insertMessageInDb(
                    getContentResolver(), false, new Date().getTime(), true, null, vfsUri.toString(),
                    System.currentTimeMillis(), Imps.MessageType.OUTGOING_ENCRYPTED,
                    0, offerId, mimeType, null);

            if (mOneAndDone) {
                Intent data = new Intent();
                data.setDataAndType(vfsUri,mimeType);
                setResult(RESULT_OK, data);
                finish();
            } else {
                onVideo(vfsUri, mimeType);
            }

            if (Preferences.useProofMode()) {

                try {
                    ProofMode.generateProof(CameraActivity.this, vfsUri);
                } catch (FileNotFoundException e) {
                    Log.e(LOG_TAG,"error generating proof for photo",e);
                }
            }


        }
        catch (IOException ioe)
        {
            Log.e(LOG_TAG,"error importing photo",ioe);
        }
    }

    private void storeBitmap (Bitmap bitmap)
    {
        // import
        String sessionId = "self";
        String offerId = UUID.randomUUID().toString();

        try {

            final Uri vfsUri = SecureMediaStore.createContentPath(sessionId,"cam" + new Date().getTime() + ".jpg");

            OutputStream out = new FileOutputStream(new File(vfsUri.getPath()));
         //   bitmap = getResizedBitmap(bitmap,SecureMediaStore.DEFAULT_IMAGE_WIDTH,SecureMediaStore.DEFAULT_IMAGE_WIDTH);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100 /*ignored for JPG*/, out);

            bitmap.recycle();
            System.gc();

            String mimeType = "image/jpeg";

            //adds in an empty message, so it can exist in the gallery and be forwarded
            Imps.insertMessageInDb(
                    getContentResolver(), false, new Date().getTime(), true, null, vfsUri.toString(),
                    System.currentTimeMillis(), Imps.MessageType.OUTGOING_ENCRYPTED,
                    0, offerId, mimeType, null);

            if (mOneAndDone) {
                Intent data = new Intent();
                data.setDataAndType(vfsUri,mimeType);
                setResult(RESULT_OK, data);
                finish();
            } else {
                onBitmap(vfsUri, mimeType);
            }

            if (Preferences.useProofMode()) {

                try {
                    ProofMode.generateProof(CameraActivity.this, vfsUri);
                } catch (FileNotFoundException e) {
                    Log.e(LOG_TAG,"error generating proof for photo",e);
                }
            }


        }
        catch (IOException ioe)
        {
            Log.e(LOG_TAG,"error importing photo",ioe);
        }
    }

    public Bitmap getResizedBitmap(Bitmap bm, int maxWidth, int maxHeight) {

        float scale = Math.min(((float)maxHeight / bm.getWidth()), ((float)maxWidth / bm.getHeight()));

        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scale, scale);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, false);
        bm.recycle();
        return resizedBitmap;
    }

    /**
     * Called when a bitmap has been stored in virtual file system. Base class does nothing.
     * @param vfsUri
     * @param mimeType
     */
    protected void onBitmap(Uri vfsUri, String mimeType) {

    }

    /**
     * Called when a video has been stored in virtual file system. Base class does nothing.
     * @param vfsUri
     * @param mimeType
     */
    protected void onVideo(Uri vfsUri, String mimeType) {

    }

}
