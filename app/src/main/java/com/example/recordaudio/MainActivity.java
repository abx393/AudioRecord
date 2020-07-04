package com.example.recordaudio;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.AudioRecord;
import android.os.Environment;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.RelativeLayout;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.nio.ShortBuffer;
import java.nio.ByteBuffer;

import java.util.Date;
import java.text.SimpleDateFormat;

import static com.example.recordaudio.MainActivity.SaveState.NONE_OR_SAVED;
import static com.example.recordaudio.MainActivity.SaveState.RECORDING;
import static com.example.recordaudio.MainActivity.SaveState.SAVEABLE;

public class MainActivity extends AppCompatActivity {

    // possible situations:
    // no audio data, no metadata
    // yes audio data, no metadata
    // no audio data, yes metadata
    // yes audio data, yes metadata
    enum SaveState {
        RECORDING, SAVEABLE, NONE_OR_SAVED
    }

    private static final String LOG_TAG = "AudioRecordTest";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int freq = 20000;
    private static final int sampleRate = 48000;
    private static final int numSamples = sampleRate * 300;
    private static String fileName = null;
    private static int bufferSize = AudioRecord.getMinBufferSize(sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT);
    private static byte[] audioBuffer = new byte[numSamples *2];

    private AudioRecord recorder = null;
    private MediaPlayer   player = null;

    private int bytesRead;

    private static boolean mShouldContinue=false;

    // Requesting permission to RECORD_AUDIO
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};

    private static EditText nameText;
    private static Spinner exerciseSpinner;
    private static EditText repsText;
    private static Spinner locationSpinner;
    private static Spinner orientationSpinner;
    private static EditText locationText;
    private static EditText commentsText;

    private RelativeLayout layoutRoot;
    private Button recordButton;
    private Button stopButton;
    private Button saveButton;

    private SaveState currentSaveState = NONE_OR_SAVED;
    private TonePlayer tonePlayer;

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
    /*Plays constant frequency tone*/

    private void startRecording() {
        audioBuffer = new byte[numSamples*2];
        mShouldContinue = true;
        tonePlayer.startWav(); //starts audio playing thread
        setCurrentSaveState(RECORDING);
        new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

                AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.UNPROCESSED,
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
                bytesRead = 0;

                while (mShouldContinue) {
                    //Log.d(LOG_TAG, "bytes read: " + bytesRead);
                    int numberOfBytes = record.read(audioBuffer, bytesRead, bufferSize);
                    //if (numberOfBytes>0) Log.d(LOG_TAG, "num of bytes: " + numberOfBytes + ", " + bytesRead + ", " + bufferSize);
                    //else Log.d(LOG_TAG, "Error code: " + numberOfBytes);
                    //else Log.d(LOG_TAG, "" +numberOfBytes);
                    bytesRead += numberOfBytes;
                }
                //Log.d(LOG_TAG, "Buffer size: " + bufferSize);
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
        tonePlayer.stopWav();
    }

    private void saveRecording()  {
        //Retrieve label and metadata from info user recorded in textboxes and drop down menus
        String name = nameText.getText().toString();
        String exercise = exerciseSpinner.getSelectedItem().toString();
//        String reps = repsText.getText().toString();
        String reps = repsText.getText().toString();
        String phoneLocation = locationSpinner.getSelectedItem().toString();
        String phoneOrientation = orientationSpinner.getSelectedItem().toString();
        String location = locationText.getText().toString();
        String comments = commentsText.getText().toString();

        //Generate output files named {timestamp}_audio.csv
        Date currentTime = new Date();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_hh-mm-ss").format(currentTime);
        Log.d(LOG_TAG, timestamp);

        JSONObject data = new JSONObject();
        try {
            data.put("subject", name);
            data.put("activity", exercise);
            data.put("num_reps", Integer.parseInt(TextUtils.isEmpty(reps) ? "0" : reps));
            data.put("phoneLocation", phoneLocation);
            data.put("phoneOrientation", phoneOrientation);
            data.put("device", getDeviceName());
            data.put("device_type", "phone");
            data.put("location", location);
            data.put("comments", comments);
            data.put("n_samples", bytesRead / 2);
            data.put("fs", 48000);
            data.put("chirp_freq", 40);
            data.put("fmin", 16000);
            data.put("fmax", 21000);
            data.put("timestamp", timestamp);
        } catch (JSONException e){
            Log.e(LOG_TAG, "saveRecording(): " + e);
        }
        try {

            //writing audio samples to output file
//            String path = "/storage/emulated/0/ExerciseRx/Doppler/";
            String path = "/storage/self/primary/Android/data/com.example.recordaudio/files/";
            PrintStream ps = new PrintStream(new File(path + timestamp + ".bin"));
            ps.write(audioBuffer, 0, bytesRead);
            /*for (int i=0; i<audioBuffer.length; i++) {
                pw.print(audioBuffer[i]);
            }*/
            ps.flush();
            ps.close();
            //writing label and metadata to notes file
            PrintWriter pw2 = new PrintWriter(new BufferedWriter(new FileWriter(new File(path + timestamp + ".json"))));
            pw2.write(data.toString());
            Log.d(LOG_TAG, data.toString());
            pw2.close();

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

        layoutRoot = findViewById(R.id.layout_root);

        recordButton = (Button) findViewById(R.id.recordButton);
        stopButton = (Button) findViewById(R.id.stopButton);
        saveButton = (Button) findViewById(R.id.saveButton);

        //Setting callback methods for button click events
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
                setCurrentSaveState(SAVEABLE);
            }
        });
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveRecording();
                setCurrentSaveState(NONE_OR_SAVED);
            }
        });

        nameText = findViewById(R.id.name);
        repsText = findViewById(R.id.reps);
        locationText = findViewById(R.id.location);
        commentsText = findViewById(R.id.comments);

        //Add the options to the "Exercise" drop down menu
        exerciseSpinner = (Spinner) findViewById(R.id.exerciseSpinner);
        ArrayList<String> exercises = new ArrayList<String>();
        exercises.add("armlifts");
        exercises.add("bicep_curls");
        exercises.add("chair_stands");
        exercises.add("desk_pushups");
        exercises.add("eating");
        exercises.add("forward_punches");
        exercises.add("jumping_jacks");
        exercises.add("knee_to_elbows");
        exercises.add("lunges");
        exercises.add("marches");
        exercises.add("no_movement");
        exercises.add("side_to_sides");
        exercises.add("talking");
        exercises.add("windmills");
//        exercises.add("Arm lift");
//        exercises.add("Bicep curl");
//        exercises.add("Chair sit-to-stand");
//        exercises.add("Torso Twist");
//        exercises.add("Inclined Desk Push up");
//        exercises.add("Seated Opposite Knee-to-elbow");
//        exercises.add("Standing Marches (Knee Lifts)");
//        exercises.add("Seated Arm Punches");
//        exercises.add("Standing Crossover Toe Touches");
//        exercises.add("Talking");
//        exercises.add("Eating/Drinking");
//        exercises.add("No Movement");

        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, exercises);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        exerciseSpinner.setAdapter(dataAdapter);

        //Add options to the "Phone Location" drop down menu
        locationSpinner = (Spinner) findViewById(R.id.locationSpinner);
        ArrayList<String> locations = new ArrayList<String>();
        locations.add("Table");
        locations.add("Floor");

        ArrayAdapter<String> dataAdapter2 = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, locations);
        dataAdapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        locationSpinner.setAdapter(dataAdapter2);

        //Add options to the "Phone Orientation" drop down menu
        orientationSpinner = (Spinner) findViewById(R.id.orientationSpinner);
        ArrayList<String> orientations = new ArrayList<String>();
        orientations.add("Flat");
        orientations.add("Upright");

        ArrayAdapter<String> dataAdapter3 = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, orientations);
        dataAdapter3.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        orientationSpinner.setAdapter(dataAdapter3);

        tonePlayer = new TonePlayer(this, "chirp_tx.wav");
        setCurrentSaveState(NONE_OR_SAVED);
    }

    private Runnable updateScreen = new Runnable() {
        @Override
        public void run() {
            switch (currentSaveState) {
                case RECORDING:
                    layoutRoot.setBackgroundColor(Color.RED);
                    recordButton.setEnabled(false);
                    stopButton.setEnabled(true);
                    saveButton.setEnabled(false);
                    break;
                case SAVEABLE:
                    layoutRoot.setBackgroundColor(Color.GREEN);
                    recordButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    saveButton.setEnabled(true);
                    break;
                case NONE_OR_SAVED:
                    layoutRoot.setBackgroundColor(Color.WHITE);
                    recordButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    saveButton.setEnabled(false);
                    break;
            }
        }
    };

    private void setCurrentSaveState(SaveState state) {
        currentSaveState = state;
        runOnUiThread(updateScreen);
    }

    // https://stackoverflow.com/a/27836910
    /** Returns the consumer friendly device name */
    public static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        }
        return capitalize(manufacturer) + " " + model;
    }

    private static String capitalize(String str) {
        if (TextUtils.isEmpty(str)) {
            return str;
        }
        char[] arr = str.toCharArray();
        boolean capitalizeNext = true;

        StringBuilder phrase = new StringBuilder();
        for (char c : arr) {
            if (capitalizeNext && Character.isLetter(c)) {
                phrase.append(Character.toUpperCase(c));
                capitalizeNext = false;
                continue;
            } else if (Character.isWhitespace(c)) {
                capitalizeNext = true;
            }
            phrase.append(c);
        }

        return phrase.toString();
    }

}

/* Complex number class*/
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