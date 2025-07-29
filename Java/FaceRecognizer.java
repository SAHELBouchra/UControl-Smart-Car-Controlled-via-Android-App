package com.example.surveillance_car;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class FaceRecognizer {
    private static final String TAG = "FaceRecognizer";
    private static final String MODEL_PATH = "mobile_face_net.tflite";
    private static final int INPUT_SIZE = 112;
    private static final int EMBEDDING_SIZE = 192;
    private Context context;
    private Interpreter tflite;
    private float[] currentFaceEmbedding;

    public FaceRecognizer(Context context) {
        this.context = context;
        try {
            Interpreter.Options options = new Interpreter.Options();
            tflite = new Interpreter(loadModelFile(context, MODEL_PATH), options);
            Log.d(TAG, "Model loaded successfully");
        } catch (IOException e) {
            Log.e(TAG, "Error loading model: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private MappedByteBuffer loadModelFile(Context context, String modelPath) throws IOException {
        try {
            AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelPath);
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        } catch (IOException e) {
            Log.e(TAG, "Error loading model file: " + e.getMessage());
            throw e;
        }
    }

    public void processImage(Bitmap image) {
        if (tflite == null) {
            Log.e(TAG, "Interpreter is null");
            return;
        }

        try {
            // Preprocess image
            Bitmap resizedImage = Bitmap.createScaledBitmap(image, INPUT_SIZE, INPUT_SIZE, true);
            
            // Reuse arrays to avoid garbage collection
            float[][][][] inputArray = new float[1][INPUT_SIZE][INPUT_SIZE][3];
            
            // Optimize pixel processing
            int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
            resizedImage.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
            
            for (int i = 0; i < pixels.length; i++) {
                int pixel = pixels[i];
                int y = i / INPUT_SIZE;
                int x = i % INPUT_SIZE;
                
                inputArray[0][y][x][0] = (Color.red(pixel) - 127.5f) / 128f;
                inputArray[0][y][x][1] = (Color.green(pixel) - 127.5f) / 128f;
                inputArray[0][y][x][2] = (Color.blue(pixel) - 127.5f) / 128f;
            }

            // Get face embedding
            float[][] outputArray = new float[1][EMBEDDING_SIZE];
            tflite.run(inputArray, outputArray);
            currentFaceEmbedding = outputArray[0];

            // Clean up
            resizedImage.recycle();
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing image: " + e.getMessage());
            e.printStackTrace();
            currentFaceEmbedding = null;
        }
    }

    public float[] getCurrentFaceEmbedding() {
        return currentFaceEmbedding;
    }
} 