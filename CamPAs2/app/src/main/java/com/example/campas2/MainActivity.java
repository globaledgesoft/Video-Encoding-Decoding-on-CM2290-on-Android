package com.example.campas2;

import static android.service.controls.ControlsProviderService.TAG;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaCodec;
import android.media.MediaFeature;
import android.media.MediaFormat;
import android.media.MediaMetadata;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.hardware.Camera.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.VideoView;

import com.google.android.material.transition.Hold;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback{

    static final int OUTPUT_WIDTH = 640;
    static final int OUTPUT_HEIGHT = 480;

    private static final int MY_CAMERA_REQUEST_CODE = 100;
    private static final int MY_AUDIO_REQUEST_CODE = 200;
    private static final int MY_STORAGE_REQUEST_CODE = 300;
    private Camera mCamera;
    private CameraPreview mPreview;
    private SurfaceView preview;
    private SurfaceHolder mHolder;
    MediaRecorder mediaRecorder;
    private boolean isRecording;
    public static final int MEDIA_TYPE_VIDEO = 2;
    private Uri videoUri;
    private String videoUriString;
    MediaMetadataRetriever mRetriever;

    private VideoView videoView_;
    private boolean videoPlaying = false;

    File file;
    static File mediaStorageDir;
    String fileName;

    ImageButton captureButton, galleryButton;
    int id = 0;
    boolean preparedMR = false;

    //encoder type
    private int vEncoder;

    boolean openingGallery = false;

    TextView description;

    RadioButton default_, hevc, h263, h264, mpeg4sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getSupportActionBar().hide();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Putting video description
        description = (TextView) findViewById(R.id.text_view_id);
        description.setMovementMethod(new ScrollingMovementMethod());
        String initText = "Welcome to CamCodec."
                + "\n" + "Here you can record video using different encoding schemes."
                + "Also you can select video from the gallery (right top corner) to play video."
                + "\n" + "Enjoy the App!!"
                + "\n" + "Cheerios ... ";
        description.setText(initText);

        // RadioButton initialisation
        default_ = findViewById(R.id.default_);
        hevc = findViewById(R.id.hevc);
        h263 = findViewById(R.id.h263);
        h264 = findViewById(R.id.h264);
        mpeg4sp = findViewById(R.id.mpeg4sp);

//        check for camera feature
        checkCameraHardware(this);

        // check for camera permission
        CheckCameraPermission();
        // check for audio permission
        CheckAudioPermission();
        // check for storage permission
        CheckStoragePermission();
        // set default encoder
        vEncoder = MediaRecorder.VideoEncoder.DEFAULT;

        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);
//        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
//        preview.addView(mPreview);
        preview = (SurfaceView) findViewById(R.id.surface_view);
        mHolder = preview.getHolder(); // getting the surface
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        isRecording = false;

        //retrieving media metadata
        mRetriever = new MediaMetadataRetriever();

        // Add a listener to the Capture button
        videoView_ = findViewById(R.id.videoView);
        galleryButton = (ImageButton)(findViewById(R.id.gallery));
        captureButton = (ImageButton) findViewById(R.id.record);
        captureButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        if (!openingGallery) {
                            if (isRecording) {
                                galleryButton.setVisibility(View.VISIBLE);
                                // stop recording and release camera
                                mediaRecorder.stop();  // stop the recording
                                // save the recorded video
                                // release the MediaRecorder object
                                releaseMediaRecorder();

                                // take camera access back from MediaRecorder
                                mCamera.lock();
                                // inform the user that recording has stopped
                                captureButton.setImageResource(R.drawable.record);
//                                captureButton.setText(R.string.record);
                                isRecording = false;

                                setDescriptionText(videoUri);

                            } else {
                                preview.setVisibility(View.VISIBLE);
                                videoView_.setVisibility(View.INVISIBLE);

                            // initialize video camera
                                setCameraStart();
                                galleryButton.setVisibility(View.INVISIBLE);
                                if (prepareVideoRecorder()) {
                                    // Camera is available and unlocked, MediaRecorder is prepared,
                                    // now you can start recording
                                    mediaRecorder.start();
                                    // inform the user that recording has started
                                    captureButton.setImageResource(R.drawable.stop);
//                                    captureButton.setText("Stop");
                                    description.setText("Recording Started");
                                    isRecording = true;
                                } else {
                                    // prepare didn't work, release the camera
                                    releaseMediaRecorder();
                                    // inform user
                                }
                            }
                        } else {
                            if (id == 1) {
                                videoView_.start();
                                captureButton.setImageResource(R.drawable.pause);
//                                captureButton.setText("Stop");
                                id = 2;
                            }else if (id == 2){
                                    if(videoView_.isPlaying()){
                                        videoView_.pause();
                                        captureButton.setImageResource(R.drawable.play);
                                        galleryButton.setImageResource(R.drawable.gallery_exit);
//                                        captureButton.setText("Play");
                                    }else{
                                        videoView_.start();
                                        captureButton.setImageResource(R.drawable.pause);
                                        galleryButton.setImageResource(R.drawable.gallery_exit);
//                                        captureButton.setText("Stop");
                                    }
                            }
                        }
                    }
                }
        );

        galleryButton.setOnClickListener(
                view -> {
                    if (!videoPlaying){
                        Intent intent = new Intent(Intent.ACTION_PICK);
                        /*filtering type of data */
                        intent.setType("*/*");
                        intent.setAction(Intent.ACTION_GET_CONTENT);
                        startActivityForResult(Intent.createChooser(intent, "Select Video"), MY_CAMERA_REQUEST_CODE);
                        openingGallery = true;
                    } else {
                        description.setText("");
                        videoPlaying = false;
                        videoView_.stopPlayback();
                        captureButton.setImageResource(R.drawable.record);
                        galleryButton.setImageResource(R.drawable.gallery);
                        preview.setVisibility(View.VISIBLE);
                        videoView_.setVisibility(View.INVISIBLE);
                        openingGallery = false;
                        id = 1;
                    }

                }
        );
    }

    private void setDescriptionText(Uri videoUri){
        mRetriever.setDataSource(getApplicationContext(), videoUri);
        Float f = Float.parseFloat(mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        File fn = new File(videoUri.getPath());
        String vHeight = mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        String vWidth = mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String videoDesc;
        videoDesc =  "> File Name : " + fn.getName()
                + "\n" + "> Path : " + fn.getAbsolutePath()
                + "\n" + "> Duration:" + f/100 + " sec"
                + "\n" + "> MIME : " + mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                + "\n" + "> Size : " + vHeight + "x" + vWidth
                + "\n" + "> FRAME COUNT :" +mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);
        description.setText(videoDesc);
    }

    boolean CheckCameraPermission(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    MY_CAMERA_REQUEST_CODE);
            return false;
        }else {
            return true;
        }
    }

    boolean CheckAudioPermission(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    MY_AUDIO_REQUEST_CODE);
            return false;
        } else {
            return true;
        }
    }

    boolean CheckStoragePermission(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    MY_STORAGE_REQUEST_CODE);
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show();
            }
        }
        if (requestCode == MY_AUDIO_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "audio permission granted", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "audio permission denied", Toast.LENGTH_LONG).show();
            }
        }
        if (requestCode == MY_STORAGE_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "storage permission granted", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "storage permission denied", Toast.LENGTH_LONG).show();
            }
        }

    }

    public void onRadioButtonClick(View view){
        boolean checked = ((RadioButton) view).isChecked();
        switch(view.getId()){
            case R.id.default_:
                if(checked){
                    vEncoder = MediaRecorder.VideoEncoder.DEFAULT;
                    default_.setChecked(true);
                    hevc.setChecked(false);
                    h263.setChecked(false);
                    h264.setChecked(false);
                    mpeg4sp.setChecked(false);
                }
                break;

            case R.id.h263:
                if(checked){
                    vEncoder = MediaRecorder.VideoEncoder.H263;
                    default_.setChecked(false);
                    hevc.setChecked(false);
                    h263.setChecked(true);
                    h264.setChecked(false);
                    mpeg4sp.setChecked(false);
                }

                break;

            case R.id.h264:
                if(checked){
                    vEncoder = MediaRecorder.VideoEncoder.H264;
                    default_.setChecked(false);
                    hevc.setChecked(false);
                    h263.setChecked(false);
                    h264.setChecked(true);
                    mpeg4sp.setChecked(false);
                }

                break;

            case R.id.hevc:
                if(checked){
                    vEncoder = MediaRecorder.VideoEncoder.HEVC;
                    default_.setChecked(false);
                    hevc.setChecked(true);
                    h263.setChecked(false);
                    h264.setChecked(false);
                    mpeg4sp.setChecked(false);
                }

                break;

            case R.id.mpeg4sp:
                if(checked){
                    vEncoder = MediaRecorder.VideoEncoder.MPEG_4_SP;
                    default_.setChecked(false);
                    hevc.setChecked(false);
                    h263.setChecked(false);
                    h264.setChecked(false);
                    mpeg4sp.setChecked(true);
                }

                break;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseMediaRecorder();       // if you are using MediaRecorder, release it first
        releaseCamera();              // release the camera immediately on pause event
    }

    private void releaseMediaRecorder(){
        if (mediaRecorder != null) {
            mediaRecorder.reset();   // clear recorder configuration
            mediaRecorder.release(); // release the recorder object
            mediaRecorder = null;
            mCamera.lock();           // lock camera for later use
        }
    }

    private void releaseCamera(){
        if (mCamera != null){
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    /** Check if this device has a camera */
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }
    private boolean prepareVideoRecorder(){

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setCamera(mCamera);
        // Step 2: Set sources
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        // Step 3: Set output format and encoding (for versions prior to API Level 8)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mediaRecorder.setVideoEncoder(vEncoder);
        // Step 4: Set output file
        videoUri = getOutputMediaFileUri(MEDIA_TYPE_VIDEO);
        videoUriString = videoUri.getPath();
        mediaRecorder.setOutputFile(videoUriString);
        // Step 5: Set the preview output
        mediaRecorder.setPreviewDisplay(mHolder.getSurface());
        // Step 6: Prepare configured MediaRecorder
        try {
            mediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Log.d("TAG", "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d("TAG", "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    /** Create a file Uri for saving an image or video */
    private Uri getOutputMediaFileUri(int type){
        return Uri.fromFile(getOutputMediaFile(type));
    }

    /** Create a File for saving an image or video */
    private File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.
        mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "CamCodec");
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("CamCodec", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if(type == MEDIA_TYPE_VIDEO) {
            fileName = "VID_"+ timeStamp + ".mp4";
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    fileName);
        } else {
            return null;
        }
        return mediaFile;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            Log.d(TAG, "Uri: "+ uri);
            Log.d(TAG, "DataString: "+ data.getData());
            Log.d(TAG, "Type: "+ data.getType());
            file = new File(uri.getPath());
            Log.d(TAG, "Filepath: "+ file.getPath());

            if (uri != null){
                preview.setVisibility(View.INVISIBLE);
                videoView_.setVisibility(View.VISIBLE);
                videoView_.setVideoURI(uri);
                videoView_.start();
                captureButton.setImageResource(R.drawable.pause);
                galleryButton.setImageResource(R.drawable.gallery_exit);
                openingGallery = true;
                id = 2;
                videoPlaying = true;
                setDescriptionText(uri);

                // video finish listener
                videoView_.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        mp.start();
                        Toast.makeText(MainActivity.this, "Video Finish",
                                Toast.LENGTH_SHORT).show();
                        captureButton.setImageResource(R.drawable.record);
                        galleryButton.setImageResource(R.drawable.gallery);
                        videoPlaying = false;
                        openingGallery = false;
                        videoView_.stopPlayback();
                        videoView_.setVisibility(View.INVISIBLE);
                        preview.setVisibility(View.VISIBLE);
                        id = 1;
                    }
                });
            }
        }
    }

    public void setCameraStart(){
        mCamera = getCameraInstance();
        Camera.Parameters cParam = mCamera.getParameters();
        cParam.set("rotation", 0);
        mCamera.setDisplayOrientation(0);
        mCamera.setParameters(cParam);
        try {
            mCamera.setPreviewDisplay(mHolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.startPreview();
        mCamera.unlock();
        preparedMR = false;
    }

    @Override
    public void surfaceCreated( SurfaceHolder surfaceHolder) {
        setCameraStart();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        Log.d("TAG", "msg");

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        Log.d("TAG", "msg");
    }

}
