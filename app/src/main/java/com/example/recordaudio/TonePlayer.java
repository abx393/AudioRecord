package com.example.recordaudio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.nio.ShortBuffer;

import static com.example.recordaudio.MainActivity.SaveState.RECORDING;

public class TonePlayer {
    private static final String LOG_TAG = "AudioRecordTest";

    private static final int freq = 20000;

    private static final int sampleRate = 44100;

    private static final int numSamples = sampleRate * 300;

    private static ShortBuffer samples = ShortBuffer.allocate(numSamples);
    private static boolean mShouldContinue = false;

    private int bufferSize;

    private AudioTrack audioTrack;

    public TonePlayer() {
        generateSineWave();

        // audio playing interface
        bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM);
    }

    public void generateSineWave() {
        // populate sine wave in a background thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                /*Generate a sine wave*/
                samples = ShortBuffer.allocate(numSamples);
                for (int i = 0; i < numSamples; i++) {
                    double tmp = Math.sin(2 * Math.PI * i * freq / sampleRate); // Sine wave
                    short sample = (short) (tmp * Short.MAX_VALUE);
                    samples.put(sample);
                }
            }
        }).start();
    }

    public void start() {
        Log.v(LOG_TAG, "TONE PLAYER starting...");
        mShouldContinue = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                //audioTrack.write(buffer, 0, buffer.length);
                audioTrack.play();

//                setCurrentSaveState(RECORDING);

                Log.v(LOG_TAG, "Audio streaming started");
                short[] buffer = new short[bufferSize];
                samples.rewind();

                int limit = numSamples;
                int totalWritten = 0;
                while (samples.position() < limit && mShouldContinue) {
                    int numSamplesLeft = limit - samples.position();
                    int samplesToWrite;
                    if (numSamplesLeft >= buffer.length) {
                        samples.get(buffer);
                        samplesToWrite = buffer.length;
                    } else {
                        for (int i=numSamplesLeft; i<buffer.length; i++) {
                            buffer[i] = 0;
                        }
                        samples.get(buffer, 0, numSamplesLeft);
                        samplesToWrite = numSamplesLeft;
                    }
                    totalWritten += samplesToWrite;
                    audioTrack.write(buffer, 0, samplesToWrite);
                }
                if (!mShouldContinue) {
                    // https://developer.android.com/reference/android/media/AudioTrack#stop()
                    audioTrack.pause();
                    audioTrack.flush();
                }
                Log.v(LOG_TAG, "Audio streaming finished. Samples written: " + totalWritten);
            }
        }).start();
    }

    public void stop() {
        mShouldContinue = false;
    }
}
