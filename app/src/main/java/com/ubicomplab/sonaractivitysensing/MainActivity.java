package com.ubicomplab.sonaractivitysensing;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.AudioRecord;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import cz.msebera.android.httpclient.Header;
//import android.support.v4.app.ActivityCompat;
//import android.support.v7.app.AppCompatActivity;
import android.os.Process;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import android.media.AudioFormat;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;

import com.loopj.android.http.*;


import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.UUID;

import static com.ubicomplab.sonaractivitysensing.MainActivity.SaveState.READY;
import static com.ubicomplab.sonaractivitysensing.MainActivity.SaveState.UNREADY;
import static com.ubicomplab.sonaractivitysensing.MainActivity.SaveState.RECORDING;
import static com.ubicomplab.sonaractivitysensing.MainActivity.SaveState.SAVEABLE;

public class MainActivity extends AppCompatActivity {

    private static final String LOCAL_PATH = "/storage/self/primary/Android/data/com.example.recordaudio/files/";

    private static final String SERVER_PASSWORD = "aloeunrehearsedplunderrecovereddelimination";

    // possible situations:
    // no audio data, no metadata
    // yes audio data, no metadata
    // no audio data, yes metadata
    // yes audio data, yes metadata
    enum SaveState {
        READY, RECORDING, SAVEABLE, UNREADY
    }

    private static final String LOG_TAG = "AudioRecordTest";
    private static final int REQUEST_PERMISSIONS = 1;
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

    private ProgressDialog spinnyWheel;

    private int numAsyncUploads;

    private int bytesRead;

    private static boolean mShouldContinue=false;

    // Requesting permissions
    private boolean permissionAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO,
                                     Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                     Manifest.permission.INTERNET};

    private String subject_id;

    private static Spinner activitySpinner;
    private static EditText repsText;

    private static boolean dataEntered = false;

//    private RelativeLayout layoutRoot;
    private Button startButton;
    private Button stopButton;
    private Button saveButton;
    private Button discardButton;
    private Button syncButton;

    private SaveState currentSaveState = UNREADY;
    private TonePlayer tonePlayer;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_PERMISSIONS:
                permissionAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionAccepted) finish();
    }

    public void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) this.getSystemService(Activity.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.
        View view = this.getCurrentFocus();
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null) {
            view = new View(this);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void clearInputs() {
        activitySpinner.setSelection(0, false);
        repsText.setText("");
        checkValidInputs();
    }

    private void checkValidInputs() {
        String activity = activitySpinner.getSelectedItem().toString();
        String num_reps = repsText.getText().toString();

        boolean activity_valid = false;
        TextView activity_label = findViewById(R.id.activityLabel);
        if (!activity.equals("Select activity...")) {
            activity_valid = true;
            activity_label.setTextColor(ContextCompat.getColor(this, R.color.grey));
        } else {
            activity_label.setTextColor(ContextCompat.getColor(this, R.color.orange));
        }

        boolean num_reps_valid = false;
        TextView num_reps_label = findViewById(R.id.repsLabel);
        if (num_reps.length() > 0) {
            num_reps_valid = true;
            num_reps_label.setTextColor(ContextCompat.getColor(this, R.color.grey));
        } else {
            num_reps_label.setTextColor(ContextCompat.getColor(this, R.color.orange));
        }

        Log.d("chinchilla", "checkValidInputs: "+activity_valid+", "+num_reps_valid);
        if (activity_valid && num_reps_valid) {
            Log.d("chinchilla", "checkValidInputs: setting save state!");
            setCurrentSaveState(READY);
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);

        loginPopup("Welcome!", "Please enter your subject ID.");

        startButton = findViewById(R.id.recordButton);
        stopButton = findViewById(R.id.stopButton);
        saveButton = findViewById(R.id.saveButton);
        discardButton = findViewById(R.id.discardButton);
        syncButton = findViewById(R.id.syncButton);

        //Setting callback methods for button click events
        startButton.setOnClickListener(new View.OnClickListener() {
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
                setCurrentSaveState(UNREADY);
            }
        });
        discardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setCurrentSaveState(UNREADY);
            }
        });

        syncButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                syncFiles();
            }
        });

        repsText = findViewById(R.id.repsEntry);
        TextWatcher repsTextWatcher = new TextWatcher() {
            public void afterTextChanged(Editable s) {
                checkValidInputs();
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkValidInputs();
            }
        };
        repsText.addTextChangedListener(repsTextWatcher);


        //Add the options to the activities drop down menu
        ArrayList<String> activities = new ArrayList<>();
        activities.add("Select activity...");
        activities.add("armlifts");
        activities.add("bicep_curls");
        activities.add("chair_stands");
        activities.add("desk_pushups");
        activities.add("eating");
        activities.add("forward_punches");
        activities.add("jumping_jacks");
        activities.add("knee_to_elbows");
        activities.add("lunges");
        activities.add("marches");
        activities.add("no_movement");
        activities.add("side_to_sides");
        activities.add("talking");
        activities.add("windmills");
        activitySpinner = findViewById(R.id.exerciseSpinner);
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, activities);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        activitySpinner.setAdapter(dataAdapter);

        activitySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                checkValidInputs();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                checkValidInputs();
            }

        });

        tonePlayer = new TonePlayer(this, "chirp_tx.wav");
        setCurrentSaveState(UNREADY);

        clearInputs();
        checkSyncComplete(false);
    }

    private Runnable updateScreen = new Runnable() {
        @Override
        public void run() {
            switch (currentSaveState) {
                case READY:
                    startButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    saveButton.setEnabled(false);
                    discardButton.setEnabled(false);
                    activitySpinner.setEnabled(true);
                    repsText.setEnabled(true);
                    break;
                case RECORDING:
                    startButton.setEnabled(false);
                    stopButton.setEnabled(true);
                    saveButton.setEnabled(false);
                    discardButton.setEnabled(false);
                    activitySpinner.setEnabled(false);
                    repsText.setEnabled(false);
                    break;
                case SAVEABLE:
                    startButton.setEnabled(false);
                    stopButton.setEnabled(false);
                    saveButton.setEnabled(true);
                    discardButton.setEnabled(true);
                    activitySpinner.setEnabled(false);
                    repsText.setEnabled(false);
                    break;
                case UNREADY:
                    startButton.setEnabled(false);
                    stopButton.setEnabled(false);
                    saveButton.setEnabled(false);
                    discardButton.setEnabled(false);
                    activitySpinner.setEnabled(true);
                    repsText.setEnabled(true);
                    break;
            }
        }
    };

    private void startRecording() {
        hideKeyboard();
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
                    int numberOfBytes = record.read(audioBuffer, bytesRead, bufferSize);
                    bytesRead += numberOfBytes;
                }
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
        String activity = activitySpinner.getSelectedItem().toString();
        String num_reps = repsText.getText().toString();

        //Generate output files named {timestamp}_audio.csv
        Date currentTime = new Date();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_hh-mm-ss").format(currentTime);
        Log.d(LOG_TAG, timestamp);

        JSONObject data = new JSONObject();

        String device_type = "phone";

        String device_id = UUID.randomUUID().toString();
        String device = getDeviceName();

        try {
            data.put("subject", subject_id);
            data.put("activity", activity);
            data.put("num_reps", Integer.parseInt(TextUtils.isEmpty(num_reps) ? "0" : num_reps));
            data.put("device", device);
            data.put("device_type", device_type);
            data.put("device_id", device_id);
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

            String fname = device_type+"_"+subject_id+"_"+activity+"_"+num_reps+"_reps_"+timestamp;

            File folder = new File(LOCAL_PATH);
            boolean success = true;
            if (!folder.exists()) {
                success = folder.mkdirs();
            }
            if (!success) {
                alert("Uh oh...", "Could not create folder in local storage. Please check app permissions.");
            } else {

                PrintStream ps = new PrintStream(new File(LOCAL_PATH + fname + ".bin"));
                ps.write(audioBuffer, 0, bytesRead);
                ps.flush();
                ps.close();
                //writing label and metadata to notes file
                PrintWriter pw2 = new PrintWriter(new BufferedWriter(new FileWriter(new File(LOCAL_PATH + fname + ".json"))));
                pw2.write(data.toString());
                Log.d(LOG_TAG, data.toString());
                pw2.close();
            }

        } catch (IOException e){
            Log.e(LOG_TAG, "saveRecording(): " + e);
        }

        syncButton.setEnabled(true);
        clearInputs();
    }

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

    public void syncFiles() {
        File dir = new File(LOCAL_PATH);
        numAsyncUploads = 0;
        if (dir.exists()) {
            File[] files = dir.listFiles();
            for (int i = 0; i < files.length; ++i) {
                File file = files[i];
                if (!file.isDirectory()) {
                    numAsyncUploads += 1;
                    syncButton.setEnabled(false);

                    Log.d("chinchilla", "found a file: "+file.getAbsolutePath());
                    uploadFile(file);
                }
            }
        }

        if (numAsyncUploads > 0) {
            spinnyWheel = ProgressDialog.show(this, "Syncing...",
                    "Please wait while we save your sonar recordings.", true);
            spinnyWheel.show();
        } else {
            checkSyncComplete(true);
        }
    }

    public void checkSyncComplete(boolean chatty) {
        File dir = new File(LOCAL_PATH);
        boolean complete = true;
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.isDirectory()) {
                        complete = false;
                    }
                }
            }
        }
        if (complete) {
            if (chatty) alert("Sync Complete", "All files successfully uploaded");
            syncButton.setEnabled(false);
        } else {
            if (chatty) alert("Sync Failed", "Please check your Wi-Fi connection and try again.");
        }
    }

    private void uploadFile(File f) {
        final File file = f;

        RequestParams params = new RequestParams();
        params.put("password", SERVER_PASSWORD);
        try {
            params.put("file", file);
        } catch(FileNotFoundException e) {
            alert("Something went wrong...", "could not find filepath "+file.getName());
        }

        FileSyncClient.post("upload.php", params, new TextHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseString) {
                if (responseString.trim().equals("success") || responseString.trim().equals("file already exists")) {
                    // upload successful! okay to delete now
                    if (file.exists()) {
                        if (file.delete()) {
                            Log.d("chinchilla", "file deleted:" + file.getPath());
                        } else {
                            Log.d("chinchilla", "file NOT deleted:" + file.getPath());
                        }
                    }
                }
                Log.d("chinchilla", "POST succeeded ––– status code = " + statusCode + ", response: " + responseString);
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                Log.d("chinchilla", "uploadFile failure! status code = "+statusCode+", response: "+responseString);
            }

            @Override
            public void onFinish() {
                numAsyncUploads -= 1;
                if (numAsyncUploads <= 0) {
                    syncButton.setEnabled(true);
                    spinnyWheel.dismiss();
                    checkSyncComplete(true);
                } else {
                    syncButton.setEnabled(false);
                }
                Log.d("chinchilla", "onFinish, async tasks = "+numAsyncUploads);
                checkSyncComplete(false);
            }


        });
    }


    private void alert(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                }).show();
    }

    private void loginPopup(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);

        builder.setCancelable(false);

        // Set up the input
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("Login", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                subject_id = input.getText().toString();
                login(subject_id);
            }
        });

        builder.show();
    }

    private void toastPopup(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }


    private void login(String subject_id) {
        spinnyWheel = ProgressDialog.show(this, "Logging in ...",
                "", true);
        spinnyWheel.show();

        String device_id = UUID.randomUUID().toString();
        String device = getDeviceName();

        RequestParams params = new RequestParams();
        params.put("subject_id", subject_id);
        params.put("password", SERVER_PASSWORD);
        params.put("device_id", device_id);
        params.put("device", device);

        FileSyncClient.post("login.php", params, new TextHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseString) {
                if (responseString.trim().equals("valid")) {
                    // login successful!
                    toastPopup("Login successful!");
                } else if (responseString.trim().equals("invalid")){
                    loginPopup("Login Error", "Invalid subject ID. Please try again.");
                } else {
                    loginPopup("Login Error", "Error: "+responseString.trim()+"\nPlease try again.");
                }
                Log.d("chinchilla", "POST succeeded ––– status code = " + statusCode + ", response: " + responseString);
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                Log.d("chinchilla", "POST failed ––– status code = " + statusCode + ", response: " + responseString);
                loginPopup("Connection Error", "Could not verify subject ID. Please check Wi-Fi and try again.");
            }

            @Override
            public void onFinish() {
                spinnyWheel.dismiss();
//                checkSyncComplete();
            }


        });
    }


}
