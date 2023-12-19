package com.example.vnyk;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.media.AudioManager;
import android.media.MediaPlayer;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.RelativeLayout;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    private RelativeLayout mainLayout;
    private TextView textView;
    private Handler handler;
    private SensorEventListener sensorEventListener;
    private SensorManager sensorManager;
    private ToggleButton toggleButton;
    private boolean isGoSlow = false;
    private Vibrator vibrator;
    private MediaPlayer mediaPlayer;
    private MediaPlayer mediaPlayerGoSlow;
    private MediaPlayer mediaPlayerSteerSlowly;
    private Sensor sensorShake;
    private ImageView backgroundImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainLayout = findViewById(R.id.layout);
        textView = findViewById(R.id.textView);
        toggleButton = findViewById(R.id.toggleButton);
        backgroundImage = findViewById(R.id.backgroundImage);

        // Create a handler to update the UI on the main thread
        handler = new Handler(Looper.getMainLooper());
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorShake = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        mediaPlayer = MediaPlayer.create(this, R.raw.slow);
        mediaPlayer.setOnCompletionListener(mp -> { /* Callback when playback completes (optional) */ });
        mediaPlayerGoSlow = MediaPlayer.create(this, R.raw.slow);
        mediaPlayerSteerSlowly = MediaPlayer.create(this, R.raw.steer);
        mediaPlayerGoSlow.setOnCompletionListener(mp -> { /* Callback when "Go Slow" playback completes (optional) */ });
        mediaPlayerSteerSlowly.setOnCompletionListener(mp -> { /* Callback when "Steer Slowly" playback completes (optional) */ });

        sensorEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (event != null) {
                    float x_accl = event.values[0];
                    float y_accl = event.values[1];
                    float z_accl = event.values[2];

                    // Calculate the overall acceleration magnitude
                    double accelerationMagnitude = Math.sqrt(x_accl * x_accl + y_accl * y_accl + z_accl * z_accl);

                    // Set a threshold value based on your requirements
                    double threshold = 15.0;
                    double steerThreshold = 10.0;

                    // Log acceleration magnitude for debugging
                    Log.d("Sensor", "Acceleration Magnitude: " + accelerationMagnitude);

                    // Compare the magnitude with the threshold
                    if (accelerationMagnitude > threshold) {
                        isGoSlow = true;
                        updateUI("Go Slow");
                        vibrateForTwoSeconds();
                        playVoiceMessage();
                    } else if (accelerationMagnitude > steerThreshold) {
                        Log.d("Sensor", "Steer Slowly condition met");
                        updateUI("Steer Slowly");
                        vibrateForTwoSeconds();
                        playVoiceMessage(); // Play the voice message
                    } else {
                        isGoSlow = false;
                        updateUI("Stable Driving");
                    }
                }
            }





            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                // You can handle accuracy changes if needed
            }
        };

        toggleButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Toggle button is checked (user pressed "Stop")
                stopSensor();
            } else {
                // Toggle button is unchecked (user pressed "Start")
                startSensor();
            }
        });
    }

    private void startSensor() {
        // Register the sensor listener when Toggle button is checked
        sensorManager.registerListener(sensorEventListener, sensorShake, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void stopSensor() {
        // Unregister the sensor listener when Toggle button is unchecked
        sensorManager.unregisterListener(sensorEventListener);
        isGoSlow = false;
        // Optionally reset the text and background color
        updateUI("press start to monitor");
    }

    private MediaPlayer currentMediaPlayer; // Variable to keep track of the currently playing MediaPlayer

    private void playVoiceMessage() {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(
                afChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            if (currentMediaPlayer != null) {
                // If there is a MediaPlayer playing, release it before starting a new one
                releaseMediaPlayer();
            }

            // Use different audio file based on the condition
            if (isGoSlow) {
                currentMediaPlayer = mediaPlayerGoSlow;
            } else {
                currentMediaPlayer = mediaPlayerSteerSlowly;
            }

            currentMediaPlayer.setOnCompletionListener(mp -> {
                // Callback when playback completes
                releaseMediaPlayer(); // Release the MediaPlayer after completion
            });

            currentMediaPlayer.start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        releaseMediaPlayers();
    }

    private void releaseMediaPlayers() {
        if (mediaPlayerGoSlow != null) {
            mediaPlayerGoSlow.release();
            mediaPlayerGoSlow = null;
        }

        if (mediaPlayerSteerSlowly != null) {
            mediaPlayerSteerSlowly.release();
            mediaPlayerSteerSlowly = null;
        }

        releaseMediaPlayer(); // Also release the common MediaPlayer instance
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
            AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            audioManager.abandonAudioFocus(afChangeListener);
        }
    }

    private final AudioManager.OnAudioFocusChangeListener afChangeListener =
            focusChange -> {
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                    // Permanent loss of audio focus
                    releaseMediaPlayer();
                } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                        focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                    // Temporary loss of audio focus, pause playback if necessary
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        mediaPlayer.pause();
                    }
                } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                    // Gained audio focus
                    if (mediaPlayer != null) {
                        mediaPlayer.start();
                    }
                }
            };

    private void updateUI(final String message) {
        handler.post(() -> {
            textView.setText(message);
            if ("Go Slow".equals(message) || "Steer Slowly".equals(message)) {
                // Change the background color of the whole screen to red when "Go Slow" or "Steer Slowly" is displayed
                mainLayout.setBackgroundColor(Color.RED);
                setBackgroundImage(R.drawable.c); // Remove background image
                // Delay the removal of the message after 3 seconds
                handler.postDelayed(() -> {
                    // Reset the background color and image after the delay
                    mainLayout.setBackgroundColor(Color.WHITE);
                    setBackgroundImage(R.drawable.b); // Remove background image
                    textView.setText("Stable Driving"); // Reset the message to "Stable Driving"
                }, 3000); // 3 seconds delay (3000 milliseconds)
            } else {
                // Reset the background color and image for other messages
                mainLayout.setBackgroundColor(Color.WHITE);
                setBackgroundImage(R.drawable.b); // Remove background image
            }
        });
    }



    private void setBackgroundImage(int resourceId) {
        if (resourceId != 0) {
            // Set the background image
            backgroundImage.setImageResource(resourceId);
            backgroundImage.setVisibility(View.VISIBLE);
        } else {
            // Remove background image
            backgroundImage.setVisibility(View.GONE);
        }
    }

    private void vibrateForTwoSeconds() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(2000, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } else {
            // For devices below Android Oreo
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(2000);
            }
        }
    }
}
