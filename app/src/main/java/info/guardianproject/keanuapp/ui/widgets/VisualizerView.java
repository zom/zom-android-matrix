package info.guardianproject.keanuapp.ui.widgets;


import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import info.guardianproject.keanuapp.R;

import static android.media.MediaFormat.KEY_CHANNEL_COUNT;
import static android.media.MediaFormat.KEY_SAMPLE_RATE;

/**
 * Originally from: http://android-er.blogspot.com/2015/02/create-audio-visualizer-for-mediaplayer.html
 */
public class VisualizerView extends View {

    private ArrayList<Byte> singleBytes;
    private byte[] mBytes;
    private Paint paint = new Paint();
    private AudioFileLoader audioLoader;
    private int barWidth;
    private int gapWidth;
    private int alphaPlayed = 255;
    private int alphaUnplayed = 255;
    private int numBars;
    private float playFraction = 0.0f;

    // An optional ExoPlayer instance attached to this view
    private SimpleExoPlayer exoPlayer;

    public VisualizerView(Context context) {
        super(context);
        init(null);
    }

    public VisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public VisualizerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        setWillNotDraw(false);
        mBytes = null;
        paint.setAntiAlias(true);

        int color = Color.rgb(255, 255, 255);

        barWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics());
        gapWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics());

        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.VisualizerView);
            if (a != null) {
                barWidth = a.getDimensionPixelSize(R.styleable.VisualizerView_barWidth, barWidth);
                gapWidth = a.getDimensionPixelSize(R.styleable.VisualizerView_gapWidth, gapWidth);
                color = a.getColor(R.styleable.VisualizerView_color, color);
                alphaPlayed = (int) (255 * a.getFloat(R.styleable.VisualizerView_alphaPlayed, alphaPlayed));
                alphaUnplayed = (int) (255 * a.getFloat(R.styleable.VisualizerView_alphaUnplayed, alphaUnplayed));
                a.recycle();
            }
        }

        paint.setStrokeWidth(barWidth);
        paint.setColor(color);
    }

    private float getPlayFraction() {
        return playFraction;
    }

    private void setPlayFraction(float playFraction) {
        this.playFraction = playFraction;
        invalidate();
    }

    public SimpleExoPlayer getExoPlayer() {
        return exoPlayer;
    }

    public void setExoPlayer(SimpleExoPlayer exoPlayer) {
        this.exoPlayer = exoPlayer;
        this.exoPlayer.addListener(new Player.EventListener() {
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                updatePlayerPosition();
            }

            @Override
            public void onSeekProcessed() {
                updatePlayerPosition();
            }
        });
    }

    private void updatePlayerPosition() {
        removeCallbacks(updatePlayerPositionRunnable);
        post(updatePlayerPositionRunnable);
    }

    private Runnable updatePlayerPositionRunnable = new Runnable() {
        @Override
        public void run() {
            if (exoPlayer != null) {
                long duration = exoPlayer.getDuration();
                long pos = exoPlayer.getCurrentPosition();
                if (duration == 0) {
                    setPlayFraction(0);
                } else {
                    setPlayFraction((float) pos / (float) duration);
                }

                if (exoPlayer.getPlaybackState() == Player.STATE_READY) {
                    post(updatePlayerPositionRunnable);
                }
            }
        }
    };

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0 && !isInEditMode()) {
            numBars = w / (barWidth + gapWidth);
        }
    }

    public void updateVisualizer(byte[] bytes) {
        mBytes = bytes;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBytes == null) {
            return;
        }

        // Calc width
        int n = Math.min(numBars, (mBytes == null) ? 0 : mBytes.length);
        int w = n * barWidth + Math.max(0, n - 1) * gapWidth;
        float x = getPlayFraction() * w;

        canvas.saveLayerAlpha(0, 0, x, getHeight(), alphaPlayed,
                Canvas.ALL_SAVE_FLAG);
        drawBars(canvas, paint);
        canvas.restore();
        canvas.saveLayerAlpha(x, 0, getWidth(), getHeight(), alphaUnplayed,
                Canvas.ALL_SAVE_FLAG);
        drawBars(canvas, paint);
        canvas.restore();
    }

    private void drawBars(Canvas canvas, Paint paint) {
        float fIndex = 0;
        float fDelta = Math.max(1.0f, (float) mBytes.length / (float) numBars);
        for (int i = 0; i < numBars; i++) {

            // Calculate average
            int n = 0;
            int sum = 0;
            for (int j = (int) fIndex; j < (int) (fIndex + fDelta); j++) {
                if (j < mBytes.length) {
                    sum += mBytes[j] - Byte.MIN_VALUE; // [-128,127] -> [0,255]
                    n++;
                }
            }
            if (n > 0) {
                sum = sum / n;
            }
            fIndex += fDelta;

            int x = i * barWidth + i * gapWidth + barWidth / 2;
            float yStart = -(float) sum / 255.0f * ((float) getHeight() / 2.0f);
            float yEnd = (float) sum / 255.0f * ((float) getHeight() / 2.0f);
            yStart += getHeight() / 2.0f;
            yEnd += getHeight() / 2.0f;
            canvas.drawRect(x - paint.getStrokeWidth() / 2.0f, yStart, x + paint.getStrokeWidth() / 2.0f, yEnd, paint);
        }
        canvas.drawLine(0, getHeight() / 2.0f, getWidth(), getHeight() / 2.0f, paint);
    }

    public void reset() {
        if (singleBytes != null) {
            singleBytes.clear();
        }
        mBytes = null;
    }

    public void updateVisualizerSingleValue(int audioAmplitude) {
        if (singleBytes == null) {
            singleBytes = new ArrayList<>();
        }
        singleBytes.add((byte) (audioAmplitude / 255 + Byte.MIN_VALUE));
        if (singleBytes.size() > 10000) {
            singleBytes.subList(0, singleBytes.size() - 10000).clear();
        }
        mBytes = new byte[singleBytes.size()];
        for(int i = 0; i < singleBytes.size(); i++) {
            mBytes[i] = singleBytes.get(i);
        }
        invalidate();
    }

    public void loadAudioFile(Uri fileUri) {
        if (fileUri == null) {
            // Cancel loading!
            audioLoader = null;
            return;
        }
        audioLoader = new AudioFileLoader(this);
        audioLoader.execute(fileUri);
    }

    private static class AudioFileLoader extends AsyncTask <Uri, Void, byte[]> {
        WeakReference<VisualizerView> _owner;

        AudioFileLoader(VisualizerView owner) {
            _owner = new WeakReference<VisualizerView>(owner);
        }

        @Override
        protected byte[] doInBackground(Uri... uris) {
            AudioFileLoader thisLoader = this;

            Uri fileUri = uris[0];
            MediaCodec decoder = null;
            try {
                MediaExtractor extractor = new MediaExtractor();
                extractor.setDataSource(fileUri.getPath());
                int numTracks = extractor.getTrackCount();
                for (int i = 0; i < numTracks; ++i) {
                    MediaFormat format = extractor.getTrackFormat(i);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    //Log.d("mime =",mime);
                    if (mime.startsWith("audio/")) {
                        extractor.selectTrack(i);
                        decoder = MediaCodec.createDecoderByType(mime);
                        decoder.configure(format, null, null, 0);

                        //getSampleCryptoInfo(MediaCodec.CryptoInfo info)
                        break;
                    }
                }

                if (extractor == null || decoder == null) {
                    Log.e("VisualizerView", "Can't find audio info!");
                    return null;
                }
                decoder.start();

                ByteBuffer[] inputBuffers = decoder.getInputBuffers();
                ByteBuffer[] outputBuffers = decoder.getOutputBuffers();
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

                ArrayList<Integer> output = new ArrayList<>();
                int outputMax = 0;

                int samplesPerSec = decoder.getOutputFormat().getInteger(KEY_SAMPLE_RATE) / 10;
                int channels = decoder.getOutputFormat().getInteger(KEY_CHANNEL_COUNT);

                int temp = 0;
                boolean isEOS = false;

                while (true) {
                    // Cancel?
                    VisualizerView owner = _owner.get();
                    if (owner == null || owner.audioLoader != this) {
                        return null; // Abort
                    }

                    if (!isEOS) {
                        int inIndex = decoder.dequeueInputBuffer(1000);
                        if (inIndex >= 0) {
                            ByteBuffer buffer = inputBuffers[inIndex];
                            buffer.clear();

                            int sampleSize = extractor.readSampleData(buffer, 0);
                            if (sampleSize < 0) {
                                // We shouldn't stop the playback at this point, just pass the EOS
                                // flag to decoder, we will get it again from the
                                // dequeueOutputBuffer
                                decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                isEOS = true;
                            } else {
                                decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                                extractor.advance();
                            }
                        }
                    }

                    int outIndex = decoder.dequeueOutputBuffer(info, 10000);
                    switch (outIndex) {
                        case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                            Log.d("VisualizerView", "INFO_OUTPUT_BUFFERS_CHANGED");
                            outputBuffers = decoder.getOutputBuffers();
                            break;
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            Log.d("VisualizerView", "New format " + decoder.getOutputFormat());

                            samplesPerSec = decoder.getOutputFormat().getInteger(KEY_SAMPLE_RATE) / 10;
                            channels = decoder.getOutputFormat().getInteger(KEY_CHANNEL_COUNT);
                            break;
                        case MediaCodec.INFO_TRY_AGAIN_LATER:
                            Log.d("VisualizerView", "dequeueOutputBuffer timed out!");
                            break;
                        default:
                            ByteBuffer buffer = outputBuffers[outIndex];

                            if (info.size != 0) {
                                buffer.position(info.offset);
                                buffer.limit(info.offset + info.size);
                            }

                            int sampleSizeInBytes = 2 * channels;
                            int remainingSamples = buffer.remaining() / sampleSizeInBytes;
                            while (remainingSamples > 0) {
                                if (temp + remainingSamples >= samplesPerSec) {
                                    buffer.position(buffer.position() + ((samplesPerSec - temp - 1) * sampleSizeInBytes));
                                    int ssum = 0;
                                    for (int channel = 0; channel < channels; channel++) {
                                        int sample = Math.abs(buffer.getShort());
                                        Log.d("TAG", "SAMPLE " + sample);
                                        ssum += sample; // + Math.abs(Short.MIN_VALUE);
                                    }
                                    ssum /= channels;
                                    outputMax = Math.max(outputMax, ssum);
                                    output.add(ssum);
                                    temp = 0;
                                } else {
                                    temp += remainingSamples;
                                    buffer.position(buffer.position() + remainingSamples * sampleSizeInBytes);
                                }
                                remainingSamples = buffer.remaining() / sampleSizeInBytes;
                            }
                            decoder.releaseOutputBuffer(outIndex, true);
                            break;
                    }

                    // All decoded frames have been rendered, we can stop playing now
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.d("DecodeActivity", "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                        break;
                    }
                }

                byte[] result = new byte[output.size()];
                int idx = 0;
                float maxValue = outputMax; // 32768.0f;
                for (Integer value : output) {
                    byte converted = (byte)(255.0f * (float)value / maxValue - Math.abs(Byte.MIN_VALUE));
                    Log.d("TAG", "Value " + value + " now " + converted);
                    result[idx++] = converted;
                }
                return result;
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(byte[] result) {
            super.onPostExecute(result);
            VisualizerView owner = _owner.get();
            if (owner != null) {
                if (owner.audioLoader == this) {
                    owner.updateVisualizer(result);
                    owner.audioLoader = null;
                }
            }
        }
    }
}