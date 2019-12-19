package com.example.recordaudio;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.os.Bundle;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.AudioRecord;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.os.Bundle;
import androidx.annotation.NonNull;
//import android.support.v4.app.ActivityCompat;
//import android.support.v7.app.AppCompatActivity;
import android.os.Process;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import android.media.AudioTrack;
import android.media.AudioFormat;
import android.media.AudioManager;

import java.io.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.nio.ShortBuffer;

import java.util.Date;
import java.text.SimpleDateFormat;

public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = "AudioRecordTest";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int freq = 10000;
    private static final int sampleRate = 44100;
    private static final int numSamples = sampleRate * 10;
    private static String fileName = null;
    private static int bufferSize = AudioRecord.getMinBufferSize(sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT);
    private static short[] audioBuffer = new short[bufferSize / 2];

    private AudioRecord recorder = null;
    private MediaPlayer   player = null;

    private static ShortBuffer samples = ShortBuffer.allocate(numSamples);
    private static boolean mShouldContinue=false;
    // Requesting permission to RECORD_AUDIO
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted  = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted ) finish();

    }

    private void playTone() {
        new Thread(new Runnable() {
            @Override
            public void run() {

                for (int i = 0; i < numSamples; i++) {
                    double tmp = Math.sin(2 * Math.PI * i * freq / sampleRate); // Sine wave
                    short sample = (short) (tmp * Short.MAX_VALUE);
                    //sample = 0; //********************************************************************************
                    samples.put(sample);  // Higher amplitude increases volume
                }
                int bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                AudioTrack audioTrack = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STREAM);
                //audioTrack.write(buffer, 0, buffer.length);
                audioTrack.play();

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
                    audioTrack.release();
                }
                Log.v(LOG_TAG, "Audio streaming finished. Samples written: " + totalWritten);
            }
        }).start();
    }
    private void startRecording() {
        mShouldContinue = true;
        playTone();
        new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

                AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize);
                if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e(LOG_TAG, "Audio Record can't initialize.");
                    return;
                }
                record.startRecording();

                Log.v(LOG_TAG, "Start recording");
                long shortsRead = 0;
                while (mShouldContinue) {
                    int numberOfShort = record.read(audioBuffer, 0, audioBuffer.length);
                    shortsRead += numberOfShort;

                }
                //String s = Arrays.toString(audioBuffer);
                //double[][] spec = computeSpectrogram(audioBuffer, freq-100, freq+100, 20000, 17000);
                //Log.d("SAMPLES ", s);
                //Log.d("TRANSFORM ", Arrays.deepToString(spec));
                record.stop();
                record.release();
            }
        }).start();
    }
    private void stopRecording() {
        mShouldContinue = false;
    }

    private void saveRecording()  {
        Date currentTime = new Date();
        String timestamp = new SimpleDateFormat("yyyy_MM_dd_hh_mm_ss").format(currentTime);
        Log.d(LOG_TAG, timestamp);
        try {
            PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(timestamp + "_audio.csv")));
            for (int i=0; i<audioBuffer.length; i++) {
                pw.println(audioBuffer[i] + ", ");
            }
            pw.close();
        } catch (IOException e){
            Log.e(LOG_TAG, "saveRecording(): " + e);
        }
    }

    private Complex[] dft(short[] samples, int freqLow, int freqHigh) {
        int n = samples.length;
        int lowerBin1 = (int) ((double) freq * freqLow/sampleRate);
        //int upperBin1 = 19980/sampleRate * 20000;
        //int lowerBin2 = 20020/sampleRate * 20000;
        int upperBin2 = (int) ((double) freq * freqHigh/sampleRate);

        Complex[] res = new Complex[-lowerBin1+upperBin2];
        int idx = 0;
        for (int i=upperBin2 - 1; i>=lowerBin1; i--) {
            res[idx] = new Complex(0, 0);
            for (int j=0; j<n; j++) {
                Complex tmp = new Complex(Math.cos(2*Math.PI*j*i/n),
                                            -Math.sin(2*Math.PI*j*i/n));
                Complex sample = new Complex(samples[j], 0);
                tmp = tmp.mult(sample);
                res[idx] = tmp.add(res[idx]);
            }
            idx++;
        }
        return res;
    }
    private double[][] computeSpectrogram(short[] samples, int freqLow, int freqHigh, int nfft, int overlap) {
        int lowerBin1 = (int) ((double) freq * freqLow/sampleRate);
        //int upperBin1 = Math.floor(9980/sampleRate * 20000);
        //int lowerBin2 = Math.floor(10020/sampleRate * 20000);
        int upperBin2 = (int) ((double) freq * freqHigh/sampleRate);

        int shift = nfft - overlap;
        double[][] spec = new double[upperBin2-lowerBin1][60];
        for (int i=0; i<60; i++){
            short[] seg = Arrays.copyOfRange(samples, i*shift, i*shift+nfft);
            Complex[] tcomplex = dft(seg, freqLow, freqHigh);
            for (int j=0; j<spec.length; j++) {
                spec[j][i] = Math.sqrt(Math.pow(tcomplex[j].re, 2) + Math.pow(tcomplex[j].im, 2));
            }
        }
        return spec;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
        EditText nameText = (EditText) findViewById(R.id.name);
        Button recordButton = (Button) findViewById(R.id.recordButton);
        Button stopButton = (Button) findViewById(R.id.stopButton);
        Button saveButton = (Button) findViewById(R.id.saveButton);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view){
                startRecording();
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view){
                stopRecording();
            }
        });
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveRecording();
            }
        });

        Spinner exerciseSpinner = (Spinner) findViewById(R.id.exerciseSpinner);

        ArrayList<String> exercises = new ArrayList<String>();
        exercises.add("Arm lift");
        exercises.add("Bicep curl");
        exercises.add("Chair sit-to-stand");
        exercises.add("Torso Twist");
        exercises.add("Inclined Desk Push up");
        exercises.add("Seated Opposite Knee-to-elbow");
        exercises.add("Standing Marches (Knee Lifts)");
        exercises.add("Seated Arm Punches");
        exercises.add("Non-exercise");

        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, exercises);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        exerciseSpinner.setAdapter(dataAdapter);

        Spinner locationSpinner = (Spinner) findViewById(R.id.locationSpinner);
        ArrayList<String> locations = new ArrayList<String>();
        locations.add("Table");
        locations.add("Floor");

        ArrayAdapter<String> dataAdapter2 = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, locations);
        dataAdapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        locationSpinner.setAdapter(dataAdapter2);

        Spinner orientationSpinner = (Spinner) findViewById(R.id.orientationSpinner);
        ArrayList<String> orientations = new ArrayList<String>();
        orientations.add("Flat");
        orientations.add("Upright");

        ArrayAdapter<String> dataAdapter3 = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, orientations);
        dataAdapter3.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        orientationSpinner.setAdapter(dataAdapter3);

    }
}
class Complex {
    public final double re;
    public final double im;
    public Complex(double r, double i) {
        this.re = r;
        this.im = i;
    }
    public Complex add(Complex that){
        return new Complex(this.re + that.re, this.im + that.im);
    }
    public Complex sub(Complex that) {
        return new Complex(this.re - that.re, this.im - that.im);
    }
    public Complex mult(Complex that) {
        return new Complex(this.re*that.re - this.im * that.im, this.re * that.im + this.im * that.re);
    }
    @Override
    public String toString() {
        return String.format("(%f, %f)", re, im);
    }
}