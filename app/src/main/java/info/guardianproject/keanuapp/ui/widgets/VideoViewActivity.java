package info.guardianproject.keanuapp.ui.widgets;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
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

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import info.guardianproject.keanu.core.util.SecureMediaStore;
import info.guardianproject.keanuapp.ImUrlActivity;
import info.guardianproject.keanuapp.R;

public class VideoViewActivity extends AppCompatActivity {


    private boolean mShowResend = false;
    private Uri mMediaUri = null;
    private String mMimeType = null;
    private String accessToken = null;

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


            case R.id.menu_message_nearby:
                sendNearby();
                return true;

            default:
        }
        return super.onOptionsItemSelected(item);
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

    public class InputStreamDataSource implements DataSource {

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
                    return getContentResolver().openInputStream(mediaUri);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    return null;
                }
            }

        }
    }
}
