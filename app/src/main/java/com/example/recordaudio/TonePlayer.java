package com.example.recordaudio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.util.Log;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ShortBuffer;

import static android.content.ContentValues.TAG;
import static com.example.recordaudio.MainActivity.SaveState.RECORDING;

public class TonePlayer {
    private static Context applicationContext;

    private static final String LOG_TAG = "AudioRecordTest";

    private static final int freq = 18000;

    private static final int sampleRate = 48000;

    private static final int numSamples = sampleRate * 60;

    private static ShortBuffer samples = ShortBuffer.allocate(numSamples);

    private static String fileName = "";

    private static WavFile wav = null;

    private static boolean mShouldContinue = false;

    private int bufferSize;

    private AudioTrack audioTrack;

    //For playing existing wav file of tone
    public TonePlayer(Context c, String fname) {
        applicationContext = c;

        fileName = fname;

        bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT); //4232
        //Log.d("BUFFER SIZE", "" +bufferSize);
        audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM);

    }

    //For playing generated sine wave
    public TonePlayer() {
        generateFrequencyModulatedSineWave();

        // audio playing interface
        bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT); //4232
        Log.d("BUFFER SIZE", "" +bufferSize);
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


    public void loadWav() {
        File fhandle = openAsset(fileName);

        try {
            wav = WavFile.openWavFile(fhandle);
        } catch (Exception e) {
            Log.e(TAG, "onCreate: could not load wav file", e);
        }
    }

    public void startWav() {
        loadWav();

        Log.v(LOG_TAG, "WAVE FILE starting...");
        mShouldContinue = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                //audioTrack.write(buffer, 0, buffer.length);
                audioTrack.play();

//                setCurrentSaveState(RECORDING);

                Log.v(LOG_TAG, "Audio streaming started");
                short[] buffer = new short[bufferSize];

                //int limit = wav.getNumFrames();
                int totalWritten = 0;
                while (mShouldContinue) {
                    try {
                        wav.readFrames(buffer, 0, buffer.length);
                    } catch (Exception e){
                        Log.e("startWav wav.readFrames", ""+ e);
                    }
                    audioTrack.write(buffer, 0, buffer.length);
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

    public void stopWav() {
        mShouldContinue = false;
        /*try {
            wav.close();
        } catch (IOException e) {
            e.printStackTrace();
        }*/
    }


    public void generateFrequencyModulatedSineWave() {
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                double modulationPeriod = 1.0;
                double freqDeviation = 2300;

                double freqModulated = freq;
                samples = ShortBuffer.allocate(numSamples);

                for (int i=0; i < numSamples; i+=500) {
                    double periodsElapsed = (double) i / sampleRate / modulationPeriod;
                    double modulo = periodsElapsed - Math.floor(periodsElapsed);

                    freqModulated = (double) freq + modulo*freqDeviation;

                    for (int j=0; j<500; j++){
                        double temp = Math.sin(2 * Math.PI * freqModulated * j / sampleRate); // Frequency Modulated Sine wave
                        short sample = (short) (temp * Short.MAX_VALUE);
                        samples.put(sample);
                    }
                    //Log.d("FREQ MODULATED ", freqModulated + "");
                }
            }
        }).start();
    }

    private File openAsset(String filename) {
        AssetManager am = applicationContext.getAssets();
        File file = new File(applicationContext.getExternalCacheDir(), filename);

        try {
            writeBytesToFile(am.open(filename), file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }
    // https://stackoverflow.com/a/21730182
    private void writeBytesToFile(InputStream is, File file) throws IOException {
        FileOutputStream fos = null;
        try {
            byte[] data = new byte[2048];
            int numRead;
            fos = new FileOutputStream(file);
            while((numRead=is.read(data))>-1){
                fos.write(data,0,numRead);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        finally{
            if (fos!=null){
                fos.close();
            }
        }
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
