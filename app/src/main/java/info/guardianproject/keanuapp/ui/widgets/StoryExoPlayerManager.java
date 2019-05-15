package info.guardianproject.keanuapp.ui.widgets;

import android.view.View;

import com.google.android.exoplayer2.ExoPlayerFactory;
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

import java.io.IOException;

import info.guardianproject.keanuapp.R;

/**
 * Created by N-Pex on 2019-04-29.
 */
public class StoryExoPlayerManager {

    public static void recordAudio(AudioRecorder audioRecorder, SimpleExoPlayerView view) {
        view.setVisibility(View.VISIBLE);
        VisualizerView visualizerView = view.findViewById(R.id.exo_visualizer_view);
        if (visualizerView != null) {
            visualizerView.reset();
            visualizerView.setVisibility(View.VISIBLE);
            audioRecorder.setVisualizerView(visualizerView);
        }
    }

    public static void stop(SimpleExoPlayerView view) {
        if (view.getPlayer() != null) {
            view.getPlayer().setPlayWhenReady(false);
        }
    }

    public static void load(MediaInfo mediaInfo, SimpleExoPlayerView view, boolean play) {
        VisualizerView visualizerView = view.findViewById(R.id.exo_visualizer_view);
        visualizerView.reset();

        // Had the player been setup?
        if (view.getPlayer() == null) {

            DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter(); //test

            TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
            DefaultTrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);

            SimpleExoPlayer exoPlayer = ExoPlayerFactory.newSimpleInstance(view.getContext(), trackSelector);

            // Bind to views
            visualizerView.setExoPlayer(exoPlayer);
            view.setPlayer(exoPlayer);
        }

        if (mediaInfo.isAudio()) {
            visualizerView.loadAudioFile(mediaInfo.uri);
            visualizerView.setVisibility(View.VISIBLE);
        } else {
            visualizerView.setVisibility(View.GONE);
        }

        view.getVideoSurfaceView().setAlpha(mediaInfo.isAudio() ? 0 : 1);

        DataSpec dataSpec = new DataSpec(mediaInfo.uri);
        final VideoViewActivity.InputStreamDataSource inputStreamDataSource = new VideoViewActivity.InputStreamDataSource(view.getContext(), dataSpec);
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
        ((SimpleExoPlayer)view.getPlayer()).prepare(mediaSource);
        if (play) {
            view.getPlayer().setPlayWhenReady(true); //run file/link when ready to play.
        }
    }
}
