package info.guardianproject.keanuapp.ui.widgets;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
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
import com.google.android.exoplayer2.upstream.TransferListener;

import org.apache.commons.io.IOUtils;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import info.guardianproject.iocipher.FileInputStream;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.util.SecureMediaStore;
import info.guardianproject.keanuapp.ImUrlActivity;
import info.guardianproject.keanuapp.R;

public class VideoViewActivity extends AppCompatActivity {


    private boolean mShowResend = false;
    private Uri mMediaUri = null;
    private String mMimeType = null;
    private String accessToken = null;
    private String mMessageId = null;

    private SimpleExoPlayerView mPlayerView = null;
    private SimpleExoPlayer mExoPlayer;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        // supportRequestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        // getSupportActionBar().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        mShowResend = getIntent().getBooleanExtra("showResend", false);

        //setContentView(R.layout.image_view_activity);
        getSupportActionBar().show();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        setTitle("");

        setContentView(R.layout.activity_video_viewer);

        mPlayerView = findViewById(R.id.exoplayer);

        mMediaUri = getIntent().getData();
        mMimeType = getIntent().getType();

        initializePlayer();

    }


    private void initializePlayer() {


        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter(); //test

        TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
        TrackSelector trackSelector =
                new DefaultTrackSelector(videoTrackSelectionFactory);

        // 2. Create the player
        mExoPlayer = ExoPlayerFactory.newSimpleInstance(this, trackSelector);

        ////Set media controller
        mPlayerView.setUseController(true);//set to true or false to see controllers
        mPlayerView.requestFocus();
        // Bind the player to the view.
        mPlayerView.setPlayer(mExoPlayer);

        DataSpec dataSpec = new DataSpec(mMediaUri);
        final InputStreamDataSource inputStreamDataSource = new InputStreamDataSource(this, dataSpec);
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

        mExoPlayer.prepare(audioSource);
        mExoPlayer.setPlayWhenReady(true); //run file/link when ready to play.

    }

    @Override
    protected void onPause() {
        super.onPause();

        mExoPlayer.stop();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

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
                exportMediaFile();
                return true;
            case R.id.menu_message_resend:
                resendMediaFile();
                return true;

            case R.id.menu_message_delete:
                deleteMediaFile ();
            case R.id.menu_message_nearby:
                sendNearby();
                return true;

            case R.id.menu_downLoad:
                if (ContextCompat.checkSelfPermission(VideoViewActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    File sd = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)+"/Keanu/");
                    String extension = mMediaUri.getPath().substring(mMediaUri.getPath().lastIndexOf("."));
                    String filename = "Keanu_"+getDateTime()+extension;
                    new DownloadVideo().execute(mMediaUri,filename,sd);
                } else {
                    ActivityCompat.requestPermissions(VideoViewActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE}, 104);
                }
                return true;
            default:
        }
        return super.onOptionsItemSelected(item);
    }
    private class DownloadVideo extends AsyncTask<Object, Void, Long> {
        String storagePath = null;
        @Override
        protected Long doInBackground(Object... params) {
            Uri videoUri = (Uri) params[0];
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
                fis = new FileInputStream(new info.guardianproject.iocipher.File(videoUri.getPath()));
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
                Toast.makeText(getApplicationContext(),"Video Downloaded at :-"+storagePath,Toast.LENGTH_LONG).show();
            }
        }
    }
    private String getDateTime() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        Date date = new Date();
        return dateFormat.format(date);
    }
    private boolean checkPermissions() {
        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);


        if (permissionCheck == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);
            return false;
        }

        return true;
    }

    public void sendNearby() {
        if (checkPermissions()) {


        }

    }

    private void deleteMediaFile () {
        Uri deleteUri = mMediaUri;
        if (deleteUri.getScheme() != null && deleteUri.getScheme().equals("vfs"))
        {
            info.guardianproject.iocipher.File fileMedia = new info.guardianproject.iocipher.File(deleteUri.getPath());
            fileMedia.delete();
        }

        Imps.deleteMessageInDb(getContentResolver(), mMessageId);
        setResult(RESULT_OK);
        finish();
    }

    public void exportMediaFile() {
        if (checkPermissions()) {

            java.io.File exportPath = SecureMediaStore.exportPath(mMimeType, mMediaUri);
            exportMediaFile(mMimeType, mMediaUri, exportPath);

        }
    }

    ;

    private void exportMediaFile(String mimeType, Uri mediaUri, java.io.File exportPath) {
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

    private void forwardMediaFile() {

        Intent shareIntent = new Intent(this, ImUrlActivity.class);
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.setDataAndType(mMediaUri, mMimeType);
        startActivity(shareIntent);

    }

    private void resendMediaFile() {
        Intent intentResult = new Intent();
        intentResult.putExtra("resendImageUri", mMediaUri);
        intentResult.putExtra("resendImageMimeType", mMimeType);
        setResult(RESULT_OK, intentResult);
        finish();

    }

    public static class InputStreamDataSource implements DataSource {

        private Context context;
        private DataSpec dataSpec;
        private InputStream inputStream;
        private long bytesRemaining;
        private boolean opened;

        public InputStreamDataSource(Context context, DataSpec dataSpec) {
            this.context = context;
            this.dataSpec = dataSpec;
        }

        @Override
        public void addTransferListener(TransferListener transferListener) {

        }

        @Override
        public long open(DataSpec dataSpec) throws IOException {
            try {
                inputStream = convertUriToInputStream(context, dataSpec.uri);
                long skipped = inputStream.skip(dataSpec.position);
                if (skipped < dataSpec.position)
                    throw new EOFException();

                if (dataSpec.length != C.LENGTH_UNSET) {
                    bytesRemaining = dataSpec.length;
                } else {
                    bytesRemaining = inputStream.available();
                    if (bytesRemaining == Integer.MAX_VALUE)
                        bytesRemaining = C.LENGTH_UNSET;
                }
            } catch (IOException e) {
                throw new IOException(e);
            }

            opened = true;
            return bytesRemaining;
        }

        @Override
        public int read(byte[] buffer, int offset, int readLength) throws IOException {
            if (readLength == 0) {
                return 0;
            } else if (bytesRemaining == 0) {
                return C.RESULT_END_OF_INPUT;
            }

            int bytesRead;
            try {
                int bytesToRead = bytesRemaining == C.LENGTH_UNSET ? readLength
                        : (int) Math.min(bytesRemaining, readLength);
                bytesRead = inputStream.read(buffer, offset, bytesToRead);
            } catch (IOException e) {
                throw new IOException(e);
            }

            if (bytesRead == -1) {
                if (bytesRemaining != C.LENGTH_UNSET) {
                    // End of stream reached having not read sufficient data.
                    throw new IOException(new EOFException());
                }
                return C.RESULT_END_OF_INPUT;
            }
            if (bytesRemaining != C.LENGTH_UNSET) {
                bytesRemaining -= bytesRead;
            }
            return bytesRead;
        }

        @Override
        public Uri getUri() {
            return dataSpec.uri;
        }

        @Override
        public Map<String, List<String>> getResponseHeaders() {
            return null;
        }

        @Override
        public void close() throws IOException {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                throw new IOException(e);
            } finally {
                inputStream = null;
                if (opened) {
                    opened = false;
                }
            }
        }

        private InputStream convertUriToInputStream(Context context, Uri mediaUri) {
            //Your implementation of obtaining InputStream from mediaUri

            if (mediaUri.getScheme() == null || mediaUri.getScheme().equals("vfs"))
            {
                try {
                    return new info.guardianproject.iocipher.FileInputStream(mediaUri.getPath());
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    return null;
                }

            }
            else
            {
                try {
                    return context.getContentResolver().openInputStream(mediaUri);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    return null;
                }
            }

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 104:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    File sd = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)+"/Keanu/");
                    String extension = mMediaUri.getPath().substring(mMediaUri.getPath().lastIndexOf("."));
                    String filename = "Keanu_"+getDateTime()+extension;
                    new DownloadVideo().execute(mMediaUri,filename,sd);
                } else {
                    // Permission Denied
                    Toast.makeText(VideoViewActivity.this, "Permission Denied", Toast.LENGTH_SHORT)
                            .show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}
