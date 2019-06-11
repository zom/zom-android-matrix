package info.guardianproject.keanuapp.ui.widgets;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.util.UUID;

import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;

/**
 * Created by N-Pex on 2019-04-25.
 */
public class AudioRecorder {

    public interface AudioRecorderListener {
        void onAudioRecorded(Uri uri);
    }

    private Context context;
    private AudioRecorderListener listener;
    private boolean isAudioRecording = false;
    private MediaRecorder mediaRecorder;
    private File outputFilePath;
    private VisualizerView visualizerView;

    public AudioRecorder(Context context, AudioRecorderListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public VisualizerView getVisualizerView() {
        return visualizerView;
    }

    public void setVisualizerView(VisualizerView visualizerView) {
        this.visualizerView = visualizerView;
    }

    public boolean isAudioRecording() {
        return isAudioRecording;
    }

    public void startAudioRecording() {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am.getMode() == AudioManager.MODE_NORMAL) {

            mediaRecorder = new MediaRecorder();

            String fileName = UUID.randomUUID().toString().substring(0, 8) + ".m4a";
            outputFilePath = new File(context.getFilesDir(), fileName);

            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

            //maybe we can modify these in the future, or allow people to tweak them
            mediaRecorder.setAudioChannels(1);
            mediaRecorder.setAudioEncodingBitRate(22050);
            mediaRecorder.setAudioSamplingRate(64000);

            mediaRecorder.setOutputFile(outputFilePath.getAbsolutePath());

            try {
                isAudioRecording = true;
                mediaRecorder.prepare();
                mediaRecorder.start();

                if (getVisualizerView() != null) {
                    startLevelListener();
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "couldn't start audio", e);
            }
        }
    }

    public int getAudioAmplitude() {
        return mediaRecorder.getMaxAmplitude();
    }

    private void startLevelListener() {
        getVisualizerView().post(levelListenerRunnable);
    }

    private Runnable levelListenerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAudioRecording) {
                getVisualizerView().updateVisualizerSingleValue(getAudioAmplitude());
                getVisualizerView().postDelayed(levelListenerRunnable, 100);
            }
        }
    };

    public void stopAudioRecording(boolean cancel) {
        if (mediaRecorder != null && outputFilePath != null && isAudioRecording) {

            try {

                mediaRecorder.stop();
                mediaRecorder.reset();
                mediaRecorder.release();

                if (!cancel) {
                    Uri uriAudio = Uri.fromFile(outputFilePath);
                    if (listener != null) {
                        listener.onAudioRecorded(uriAudio);
                    } else {
                        // No listener, abort
                        outputFilePath.delete();
                    }
                } else {
                    outputFilePath.delete();
                }
            } catch (IllegalStateException ise) {
                Log.w(LOG_TAG, "error stopping audio recording: " + ise);
            } catch (RuntimeException re) //stop can fail so we should catch this here
            {
                Log.w(LOG_TAG, "error stopping audio recording: " + re);
            }
            isAudioRecording = false;
        }
    }
}
