package info.guardianproject.keanuapp.ui.widgets;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;

import java.io.IOException;

/**
 * Created by N-Pex on 2019-05-31.
 * <p>
 * A class that plays audio from a DB cursor. It uses the very helpful ConcatenatingMediaSource
 * provided by ExoPlayer to automatically add new items to a playlist.
 */
public class StoryAudioPlayer {
    private Context context;
    private SimpleExoPlayer player;
    private ConcatenatingMediaSource concatenatingMediaSource;
    private Cursor cursor;
    private int currentPosition = -1;

    public StoryAudioPlayer(Context context) {
        this.context = context;
    }

    public SimpleExoPlayer getPlayer() {
        return getOrCreatePlayer();
    }

    public void reset() {
        if (this.player != null) {
            this.player.setPlayWhenReady(false);
        }
        if (this.concatenatingMediaSource != null) {
            concatenatingMediaSource.removeMediaSourceRange(0, concatenatingMediaSource.getSize());
        }
        currentPosition = -1;
        if (cursor != null) {
            cursor.close();
        }
        cursor = null;
    }

    public void updateCursor(Cursor cursor, int mimeTypeColumn, int uriColumn) {
        if (this.cursor != null) {
            this.cursor.close();
        }
        this.cursor = cursor;
        if (this.cursor != null) {
            getOrCreatePlayer();

            cursor.moveToLast();
            do {
                if(cursor.getPosition() != -1){
                    cursor.moveToPosition(cursor.getPosition());
                    String mime = cursor.getString(mimeTypeColumn);
                    Uri uri = Uri.parse(cursor.getString(uriColumn));

                    DataSpec dataSpec = new DataSpec(uri);
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
                    MediaSource mediaSource = new ExtractorMediaSource(inputStreamDataSource.getUri(),
                            factory, new DefaultExtractorsFactory(), null, null);
                    Log.d("AudioPlayer", "Add media source " + uri);
                    concatenatingMediaSource.addMediaSource(mediaSource);
                }

            }while (cursor.moveToPrevious());
          /*  for (int index = (currentPosition + 1); index < this.cursor.getCount(); index++) {
                Log.d("AudioPlayer", "index " + index);

                cursor.moveToPosition(index);
                String mime = cursor.getString(mimeTypeColumn);
                Uri uri = Uri.parse(cursor.getString(uriColumn));

                DataSpec dataSpec = new DataSpec(uri);
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
                MediaSource mediaSource = new ExtractorMediaSource(inputStreamDataSource.getUri(),
                        factory, new DefaultExtractorsFactory(), null, null);
                Log.d("AudioPlayer", "Add media source " + uri);
                concatenatingMediaSource.addMediaSource(mediaSource);
            }*/
            currentPosition = this.cursor.getCount() - 1;
        }

        player.setPlayWhenReady(true);
        player.prepare(concatenatingMediaSource);

    }

    private SimpleExoPlayer getOrCreatePlayer() {
        if (player == null) {
            DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
            TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
            DefaultTrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
            player = ExoPlayerFactory.newSimpleInstance(context, trackSelector);

            concatenatingMediaSource = new ConcatenatingMediaSource();
            player.prepare(concatenatingMediaSource);

        }
        return player;
    }
}
