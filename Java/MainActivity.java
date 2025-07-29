package com.example.surveillance_car;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.tensorflow.lite.Interpreter;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSION_BLUETOOTH = 2;
    private static final int REQUEST_CODE_SPEECH_INPUT = 2;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice connectedDevice;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private Button bluetoothButton;
    private TextView bluetoothStatus;
    private boolean isConnected = false;
    private ConnectionCheckThread connectionCheckThread;

    private CardView forwardButton, backwardButton, leftButton, rightButton;
    private CardView Instructions;
    private Button stopButton, Manual, Auto, Voice;
    private CardView SpeedUP, SpeedDOWN;
    private BufferedReader inputReader;
    private TextView Humidity, Temperature;

    private boolean isMovingForward = false;
    private boolean isMovingBackward = false;

    private Handler handler = new Handler();



    // Add this inner class for connection monitoring
    private class ConnectionCheckThread extends Thread {
        private volatile boolean running = true;

        @Override
        public void run() {
            while (running) {
                if (bluetoothSocket != null && isConnected) {
                    try {
                        // Try to send a dummy byte to check connection
                        bluetoothSocket.getOutputStream().write(0);
                    } catch (IOException e) {
                        // Connection lost
                        runOnUiThread(() -> handleConnectionLost());
                        break;
                    }
                }
                try {
                    Thread.sleep(1000); // Check every second
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        public void stopChecking() {
            running = false;
            interrupt();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothButton = findViewById(R.id.bluetoothButton);
        bluetoothStatus = findViewById(R.id.bluetoothStatus);

        forwardButton = findViewById(R.id.dPadUp);
        backwardButton = findViewById(R.id.dPadDown);
        leftButton = findViewById(R.id.dPadLeft);
        rightButton = findViewById(R.id.dPadRight);
        stopButton = findViewById(R.id.centerButton);
        Manual = findViewById(R.id.manualButton);
        Auto = findViewById(R.id.autoButton);
        Voice = findViewById(R.id.voiceButton);

        SpeedUP = findViewById(R.id.increaseSpeedButton);
        SpeedDOWN = findViewById(R.id.decreaseSpeedButton);

        Humidity = findViewById(R.id.humidityValue);
        Temperature = findViewById(R.id.temperatureValue);

        Instructions = findViewById(R.id.InstructionsCV);

        forwardButton.setEnabled(false);
        backwardButton.setEnabled(false);
        leftButton.setEnabled(false);
        rightButton.setEnabled(false);
        stopButton.setEnabled(false);



        Manual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommand("S");
                forwardButton.setEnabled(true);
                backwardButton.setEnabled(true);
                leftButton.setEnabled(true);
                rightButton.setEnabled(true);
                stopButton.setEnabled(true);
                forwardButton.setFocusable(true);


            }
        });
        Auto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                forwardButton.setEnabled(false);
                backwardButton.setEnabled(false);
                leftButton.setEnabled(false);
                rightButton.setEnabled(false);
                stopButton.setEnabled(true);
                sendCommand("A");

            }
        });

        Voice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startVoiceRecognition();

            }
        });

        SpeedUP.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommand("I");
            }
        });
        SpeedDOWN.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommand("D");
            }
        });
        // Set button listeners for commands
        forwardButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startContinuousCommand("F");
                    break;
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacksAndMessages(null);  // Stop all callbacks
                    sendCommand("S");  // Send stop command
                    break;
            }
            return true;
        });

        backwardButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startContinuousCommand("B");
                    break;
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacksAndMessages(null);  // Stop all callbacks
                    sendCommand("S");  // Send stop command
                    break;
            }
            return true;
        });

        leftButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startContinuousCommand("L");
                    break;
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacksAndMessages(null);  // Stop all callbacks
                    sendCommand("S");  // Send stop command
                    break;
            }
            return true;
        });

        rightButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startContinuousCommand("R");
                    break;
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacksAndMessages(null);  // Stop all callbacks
                    sendCommand("S");  // Send stop command
                    break;
            }
            return true;
        });
        stopButton.setOnClickListener(v -> sendCommand("S"));

        Instructions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInstructionsDialog();
            }
        });


        // Initialize Bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available on this device", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Set initial button click listener
        bluetoothButton.setOnClickListener(v -> checkBluetoothAndPermissions());

        // Just request permissions without showing devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(
                new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                },
                REQUEST_PERMISSION_BLUETOOTH
            );
        }
    }

    private void checkBluetoothAndPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    },
                    REQUEST_PERMISSION_BLUETOOTH
                );
                return;
            }
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            showBluetoothDevices();
        }
    }

    @SuppressLint("MissingPermission")
    private void showBluetoothDevices() {
        // Check if Bluetooth is enabled
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }

        // Get paired devices
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            // Convert devices to array for dialog
            final BluetoothDevice[] devices = pairedDevices.toArray(new BluetoothDevice[0]);
            String[] deviceNames = new String[devices.length];
            for (int i = 0; i < devices.length; i++) {
                deviceNames[i] = devices[i].getName() + "\n" + devices[i].getAddress();
            }

            // Show device selection dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Select a Bluetooth device");
            builder.setItems(deviceNames, (dialog, which) -> {
                bluetoothStatus.setText("Connecting...");
                connectToDevice(devices[which]);
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        } else {
            Toast.makeText(this, "No paired devices found", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice(BluetoothDevice device) {
        new Thread(() -> {
            try {
                if (bluetoothSocket != null) {
                    bluetoothSocket.close();
                }

                bluetoothSocket = device.createRfcommSocketToServiceRecord(
                        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));

                bluetoothSocket.connect();
                connectedDevice = device;
                isConnected = true;

                // Initialize streams
                outputStream = bluetoothSocket.getOutputStream();
                inputStream = bluetoothSocket.getInputStream();

                runOnUiThread(() -> {
                    bluetoothStatus.setText("Connected to " + device.getName());
                    bluetoothButton.setText("Disconnect");
                    bluetoothButton.setOnClickListener(v -> disconnectDevice());
                });

                // Start reading sensor data
                readSensorData();

            } catch (IOException e) {
                Log.e(TAG, "Connection failed: " + e.getMessage());
                isConnected = false;
                runOnUiThread(() -> {
                    bluetoothStatus.setText("Connection failed");
                    Toast.makeText(MainActivity.this,
                            "Failed to connect: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
                if (bluetoothSocket != null) {
                    try {
                        bluetoothSocket.close();
                    } catch (IOException closeException) {
                        Log.e(TAG, "Error closing socket: " + closeException.getMessage());
                    }
                    bluetoothSocket = null;
                }
            }
        }).start();
    }

    private void disconnectDevice() {
        isConnected = false;
        if (connectionCheckThread != null) {
            connectionCheckThread.stopChecking();
            connectionCheckThread = null;
        }

        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
                bluetoothSocket = null;
                connectedDevice = null;

                bluetoothStatus.setText("Disconnected");
                bluetoothStatus.setTextColor(getResources().getColor(android.R.color.white));
                bluetoothButton.setText("Connect Bluetooth Device");
                bluetoothButton.setOnClickListener(v -> showBluetoothDevices());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void handleConnectionLost() {
        isConnected = false;
        bluetoothStatus.setText("Connection Lost!");
        bluetoothStatus.setTextColor(getResources().getColor(android.R.color.holo_red_light));
        bluetoothButton.setText("Connect Bluetooth Device");
        Toast.makeText(this, "Bluetooth connection lost", Toast.LENGTH_SHORT).show();

        // Clean up the socket
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            bluetoothSocket = null;
            connectedDevice = null;  // Clear the connected device
        }

        // Reset button to show device list again
        bluetoothButton.setOnClickListener(v -> {
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                showBluetoothDevices();
            }
        });
    }
    private void sendCommand(String command) {
        if (outputStream != null) {
            try {
                outputStream.write(command.getBytes());
            } catch (IOException e) {
                Toast.makeText(this, "Failed to send command", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error sending command", e);
            }
        } else {
            Toast.makeText(this, "Not connected to Bluetooth", Toast.LENGTH_SHORT).show();
        }
    }

    private void startContinuousCommand(String command) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                sendCommand(command);
                handler.postDelayed(this, 100);  // Repeat every 100 ms
            }
        });
    }


    @Override
    protected void onDestroy() {
        if (connectionCheckThread != null) {
            connectionCheckThread.stopChecking();
        }
        disconnectDevice();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_BLUETOOTH) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Don't show devices here, just enable the button
                bluetoothButton.setEnabled(true);
            } else {
                Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show();
                bluetoothButton.setEnabled(false);
            }
        }
    }
    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a command...");

        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT);
        } catch (Exception e) {
            Toast.makeText(this, "Speech recognition is not supported on this device.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                    showBluetoothDevices();
                } else {
                    Toast.makeText(this, "Bluetooth must be enabled to connect", Toast.LENGTH_SHORT).show();
                }
                break;

            case REQUEST_CODE_SPEECH_INPUT:
                if (resultCode == RESULT_OK && data != null) {
                    ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (result != null && !result.isEmpty()) {
                        String voiceCommand = result.get(0).toLowerCase();
                        processVoiceCommand(voiceCommand);
                    }
                }
                break;
        }
    }

    private void processVoiceCommand(String command) {
        command = command.trim().toLowerCase(); // Normalize input

        if (command.equals("go forward")) {
            if (!isMovingForward) {
                startContinuousCommand("F"); // Start continuous forward movement
                isMovingForward = true;
                isMovingBackward = false;
            }
        } else if (command.equals("go back")) {
            if (!isMovingBackward) {
                startContinuousCommand("B"); // Start continuous backward movement
                isMovingBackward = true;
                isMovingForward = false;
            }
        } else if (command.equals("stop")) {
            handler.removeCallbacksAndMessages(null); // Stop continuous movement
            sendCommand("S");
            isMovingForward = false;
            isMovingBackward = false;
        } else if (command.equals("turn left")) {
            if (isMovingForward || isMovingBackward) { // Only turn if moving
                handler.removeCallbacksAndMessages(null); // Stop sending F/B
                sendCommand("L"); // Turn left
                new Handler().postDelayed(() -> {
                    sendCommand("S"); // Stop after 800ms
                    if (isMovingForward) {
                        startContinuousCommand("F"); // Resume moving forward
                    } else if (isMovingBackward) {
                        startContinuousCommand("B"); // Resume moving backward
                    }
                }, 800);
            }
        } else if (command.equals("turn right")) {
            if (isMovingForward || isMovingBackward) { // Only turn if moving
                handler.removeCallbacksAndMessages(null); // Stop sending F/B
                sendCommand("R"); // Turn right
                new Handler().postDelayed(() -> {
                    sendCommand("S"); // Stop after 800ms
                    if (isMovingForward) {
                        startContinuousCommand("F"); // Resume moving forward
                    } else if (isMovingBackward) {
                        startContinuousCommand("B"); // Resume moving backward
                    }
                }, 800);
            }
        }
    }
    private void showInstructionsDialog() {
        String instructions = "Welcome to the Surveillance Car App! Here's how to use it:\n\n" +
                "1. Manual Mode: Press the 'Manual' button to enable manual controls.\n" +
                "   - Use the directional buttons to move the car forward, backward, left, or right.\n" +
                "   - Press the 'Stop' button to halt the car.\n\n" +
                "2. Automatic Mode: Press the 'Auto' button to enable automatic navigation.\n" +
                "   - Press the 'Stop' button to halt the car.\n\n" +
                "3. Voice Commands: Press the 'Voice' button to give commands like:\n" +
                "   - 'Go forward'\n" +
                "   - 'Go back'\n" +
                "   - 'Turn left' or 'Turn right'\n" +
                "   - 'Stop'\n\n" +
                "4. Speed Control:\n" +
                "   - Use the Arrow UP icon to increase the car's speed.\n" +
                "   - Use the Arrow DOWN icon to decrease the car's speed.\n\n" +
                "5. Temperature and Humidity:\n"+
                "The current temperature and humidity are displayed at the top left of the screen.\n\n" +
                "Ensure the car is paired via Bluetooth and connected before starting.\n\n"+
                "ENJOY THE APP!! \uD83D\uDE0A";

        SpannableString spannable = new SpannableString(instructions);

        // Keywords/phrases to bold
        String[] keywords = {
                "1. Manual Mode", "'Manual'","2. Automatic Mode","3. Voice Commands", "'Auto'", "'Voice'", "'Go forward'",
                "'Go backward'", "'Turn left'", "'Turn right'", "'Stop'","4. Speed Control",
                "Arrow UP", "Arrow DOWN","humidity","temperature","5. Temperature and Humidity","ENJOY THE APP!!"
        };
        String[] coloredKeywords = {"1. Manual Mode", "2. Automatic Mode", "3. Voice Commands", "4. Speed Control", "5. Temperature and Humidity","ENJOY THE APP!!"};

        // Apply bold formatting
        applyBoldToKeywords(spannable, instructions, keywords);
        applyColorToKeywords(spannable, instructions, coloredKeywords, getResources().getColor(android.R.color.holo_blue_light));

        // Show dialog
        new AlertDialog.Builder(this)
                .setTitle("How to Use the App")
                .setMessage(spannable) // Use formatted text
                .setPositiveButton("Got It", null) // Close dialog
                .show();
    }

    // Helper method to apply bold formatting
    private void applyBoldToKeywords(SpannableString spannable, String text, String[] keywords) {
        for (String keyword : keywords) {
            int start = text.indexOf(keyword);
            while (start >= 0) { // Handle multiple occurrences
                int end = start + keyword.length();
                spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                start = text.indexOf(keyword, end); // Find next occurrence
            }
        }
    }
    private void applyColorToKeywords(SpannableString spannable, String text, String[] keywords, int color) {
        for (String keyword : keywords) {
            int start = text.indexOf(keyword);
            while (start >= 0) { // Handle multiple occurrences
                int end = start + keyword.length();
                spannable.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                start = text.indexOf(keyword, end); // Find next occurrence
            }
        }
    }


    private void readSensorData() {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(inputStream));

            while (isConnected) {
                if (reader != null) {
                    String data = reader.readLine();
                    if (data != null) {
                        Log.d(TAG, "Received data: " + data);

                        // Parse data format: "H:45.00,T:23.00"
                        try {
                            String[] values = data.split(",");
                            if (values.length == 2) {
                                final String humidity = values[0].split(":")[1].trim();
                                final String temperature = values[1].split(":")[1].trim();

                                runOnUiThread(() -> {
                                    try {
                                        // Update temperature TextView
                                        TextView temperatureView = findViewById(R.id.temperatureValue);
                                        temperatureView.setText(temperature + "°C");

                                        // Update humidity TextView
                                        TextView humidityView = findViewById(R.id.humidityValue);
                                        humidityView.setText(humidity + "%");

                                        // Log the values
                                        Log.d(TAG, "Temperature: " + temperature + "°C, Humidity: " + humidity + "%");

                                    } catch (Exception e) {
                                        Log.e(TAG, "Error updating UI: " + e.getMessage());
                                    }
                                });
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing sensor data: " + data + " Error: " + e.getMessage());
                        }
                    }
                }
                Thread.sleep(100); // Small delay to prevent CPU overuse
            }

        } catch (IOException e) {
            Log.e(TAG, "Error reading sensor data: " + e.getMessage());
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this,
                        "Lost connection to device",
                        Toast.LENGTH_SHORT).show();
                handleConnectionLost();
            });
        } catch (InterruptedException e) {
            Log.e(TAG, "Thread interrupted: " + e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing reader: " + e.getMessage());
                }
            }
        }
    }

    private void updateSensorData(String data) {
        if (data.startsWith("H:") && data.contains(",")) {
            String[] parts = data.split(",");
            if (parts.length == 2) {
                try {
                    String humidity = parts[0].split(":")[1].trim();
                    String temperature = parts[1].split(":")[1].trim();

                    Humidity.setText(humidity + " %");
                    Temperature.setText(temperature + " °C");

                    Log.d(TAG, "Updated Humidity: " + humidity + " %");
                    Log.d(TAG, "Updated Temperature: " + temperature + " °C");
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing sensor data: " + data, e);
                }
            }
        } else {
            Log.e(TAG, "Invalid sensor data received: " + data);
        }
    }


}