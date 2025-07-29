package com.example.surveillance_car;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;

import java.util.concurrent.ExecutionException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import android.content.pm.PackageManager;
import android.widget.Toast;
import android.app.AlertDialog;
import android.widget.EditText;
import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Type;
import com.google.gson.reflect.TypeToken;
import android.os.Handler;
import android.widget.Button;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
    private PreviewView viewFinder;
    private FaceRecognizer faceRecognizer;
    private ImageAnalysis imageAnalysis;
    private boolean isProcessingFrame = false;
    private float[] registeredFaceEmbedding = null;
    private static final String SHARED_PREFS_NAME = "FaceStorage";
    private static final String FACES_KEY = "registered_faces";
    private Map<String, float[]> registeredFaces; // Store multiple faces with names

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize views
        viewFinder = findViewById(R.id.viewFinder);
        
        // Initialize face recognizer
        faceRecognizer = new FaceRecognizer(this);
        
        // Initialize registeredFaces map
        registeredFaces = loadRegisteredFaces();

        // Set up button click listeners
        Button registerButton = findViewById(R.id.registerButton);
        Button loginButton = findViewById(R.id.loginButton);
        Button manageFacesButton = findViewById(R.id.manageFacesButton);

        registerButton.setOnClickListener(v -> registerFace());
        loginButton.setOnClickListener(v -> loginWithFace());
        manageFacesButton.setOnClickListener(v -> {
            Log.d(TAG, "Manage Faces button clicked");
            showRegisteredFaces();
        });

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, 
                REQUIRED_PERMISSIONS, 
                REQUEST_CODE_PERMISSIONS);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
            ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                imageAnalysis = new ImageAnalysis.Builder()
                    .setTargetResolution(new Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), 
                    image -> {
                        if (isProcessingFrame) return;
                        isProcessingFrame = true;

                        Bitmap bitmap = imageProxyToBitmap(image);
                        processFaceRecognition(bitmap);
                        
                        image.close();
                        isProcessingFrame = false;
                    });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (Exception e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void registerFace() {
        if (faceRecognizer == null) {
            Log.e(TAG, "FaceRecognizer is null");
            return;
        }

        float[] embedding = faceRecognizer.getCurrentFaceEmbedding();
        if (embedding != null) {
            Log.d(TAG, "Got face embedding for registration");
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Register Face");

            final EditText input = new EditText(this);
            input.setHint("Enter name for this face");
            builder.setView(input);

            builder.setPositiveButton("OK", (dialog, which) -> {
                String name = input.getText().toString().trim();
                if (!name.isEmpty()) {
                    try {
                        registeredFaces.put(name, embedding);
                        saveRegisteredFaces();
                        Log.d(TAG, "Face registered for: " + name);
                    } catch (Exception e) {
                        Log.e(TAG, "Error saving face: " + e.getMessage());
                    }
                }
            });
            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

            builder.show();
        } else {
            Log.e(TAG, "No face embedding available");
        }
    }

    private void loginWithFace() {

        float[] currentEmbedding = faceRecognizer.getCurrentFaceEmbedding();
        if (currentEmbedding != null) {
            // Show processing status
            
            // Move face comparison to background thread
            new Thread(() -> {
                String recognizedPerson = null;
                float highestSimilarity = 0;

                // Compare with all registered faces
                for (Map.Entry<String, float[]> entry : registeredFaces.entrySet()) {
                    float similarity = calculateCosineSimilarity(entry.getValue(), currentEmbedding);
                    Log.d(TAG, "Similarity with " + entry.getKey() + ": " + similarity);
                    
                    if (similarity > 0.75f && similarity > highestSimilarity) { // Lowered threshold slightly
                        highestSimilarity = similarity;
                        recognizedPerson = entry.getKey();
                    }
                }

                final String finalRecognizedPerson = recognizedPerson;
                runOnUiThread(() -> {
                    if (finalRecognizedPerson != null) {
                        
                        // Start MainActivity immediately
                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                    }
                });
            }).start();
        }
    }

    private float calculateCosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1 == null || vec2 == null || vec1.length != vec2.length) {
            Log.e(TAG, "Invalid vectors for similarity calculation");
            return 0;
        }

        float dotProduct = 0;
        float norm1 = 0;
        float norm2 = 0;
        
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        if (norm1 <= 0 || norm2 <= 0) {
            return 0;
        }

        float similarity = dotProduct / (float)(Math.sqrt(norm1) * Math.sqrt(norm2));
        return similarity;
    }

    private void saveFaceEmbedding(float[] embedding) {
        SharedPreferences prefs = getSharedPreferences("FaceLogin", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Gson gson = new Gson();
        String json = gson.toJson(embedding);
        editor.putString("face_embedding", json);
        editor.apply();
    }

    private float[] loadFaceEmbedding() {
        SharedPreferences prefs = getSharedPreferences("FaceLogin", MODE_PRIVATE);
        String json = prefs.getString("face_embedding", null);
        if (json != null) {
            Gson gson = new Gson();
            return gson.fromJson(json, float[].class);
        }
        return null;
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void processFaceRecognition(Bitmap bitmap) {
        if (bitmap == null) {
            Log.e(TAG, "Bitmap is null");
            return;
        }

        try {
            // Process the image through FaceRecognizer
            faceRecognizer.processImage(bitmap);


        } catch (Exception e) {
            Log.e(TAG, "Error processing face recognition", e);
        } finally {
            // Recycle bitmap to free memory
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private void saveRegisteredFaces() {
        try {
            SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            Gson gson = new Gson();

            String json = gson.toJson(registeredFaces);
            Log.d(TAG, "Saving faces JSON: " + json);
            editor.putString(FACES_KEY, json);
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving faces: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Map<String, float[]> loadRegisteredFaces() {
        try {
            SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
            String json = prefs.getString(FACES_KEY, "");
            Log.d(TAG, "Loaded faces JSON: " + json);
            
            if (json.isEmpty()) {
                Log.d(TAG, "No saved faces found");
                return new HashMap<>();
            }

            Gson gson = new Gson();
            Type type = new TypeToken<HashMap<String, float[]>>(){}.getType();
            Map<String, float[]> faces = gson.fromJson(json, type);
            Log.d(TAG, "Loaded " + (faces != null ? faces.size() : 0) + " faces");
            return faces != null ? faces : new HashMap<>();
        } catch (Exception e) {
            Log.e(TAG, "Error loading faces: " + e.getMessage());
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    // Add method to manage registered faces
    private void showRegisteredFaces() {
        Log.d(TAG, "showRegisteredFaces called");
        
        if (registeredFaces == null) {
            Log.e(TAG, "registeredFaces is null");
            registeredFaces = new HashMap<>();
        }

        if (registeredFaces.isEmpty()) {
            Log.d(TAG, "No faces registered");
            Toast.makeText(this, "No faces registered", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Number of registered faces: " + registeredFaces.size());
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Registered Faces");

        // Convert registered faces to array for display
        final String[] names = registeredFaces.keySet().toArray(new String[0]);
        
        // Add delete option for each face
        builder.setItems(names, (dialog, which) -> {
            final String selectedName = names[which];
            Log.d(TAG, "Selected face: " + selectedName);
            // Show delete confirmation for single face
            new AlertDialog.Builder(this)
                .setTitle("Delete Face")
                .setMessage("Do you want to delete " + selectedName + "'s face?")
                .setPositiveButton("Delete", (dialogInterface, i) -> {
                    registeredFaces.remove(selectedName);
                    saveRegisteredFaces();
                    Toast.makeText(this, 
                        selectedName + "'s face deleted", 
                        Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        builder.setPositiveButton("Close", null);
        builder.setNeutralButton("Delete All", (dialog, which) -> {
            Log.d(TAG, "Delete All clicked");
            new AlertDialog.Builder(this)
                .setTitle("Confirm Delete All")
                .setMessage("Are you sure you want to delete all registered faces?")
                .setPositiveButton("Yes", (dialogInterface, i) -> {
                    registeredFaces.clear();
                    saveRegisteredFaces();
                    Toast.makeText(this, 
                        "All faces deleted", 
                        Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("No", null)
                .show();
        });

        builder.show();
    }
} 