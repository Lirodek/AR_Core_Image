package com.example.ar_core_image;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.LightEstimate;
import com.google.ar.core.Plane;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    GLSurfaceView mSurfaceView;
    MainRenderer mainRenderer;
    TextView myTextView;
    float light = 1.0f;
    SeekBar lightControl;
    SeekbarController seekControl = new SeekbarController();

    float[] colorControl = new float[4];


    Session session;
    Config mConfig;

    DisplayListener displayListener = new DisplayListener();
    CallBack callBack = new CallBack();

    float mCurrentX, mCurrentY;

    boolean userRequestedInstall = true, mTouched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        noTitleBar();
        setContentView(R.layout.activity_main);

        mSurfaceView = (GLSurfaceView) findViewById(R.id.gl_surface_view);
        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if(displayManager != null)
            displayManager.registerDisplayListener(displayListener, null);

        mainRenderer = new MainRenderer(callBack, this);
        settingSurfaceView();
    }

    @Override
    protected void onPause() {
        super.onPause();

        mSurfaceView.onPause();
        session.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        requstCameraPermission();
        try {
            if (session == null) {
                switch (ArCoreApk.getInstance().requestInstall(this, userRequestedInstall)) {
                    case INSTALLED:
                        session = new Session(this);
                        Log.d("??????", "ARCroe Session get?????????");
                        break;
                    default:
                        Log.d("??????", "AR Core??? ????????? ?????????");
                        userRequestedInstall = false;
                        break;
                }
            }
        } catch (Exception e) {
        }


        mConfig = new Config(session);


        // ????????? ?????????????????? ????????? ??????
        mConfig.setFocusMode(Config.FocusMode.AUTO);

        // ????????? ?????????????????? ??????
        setUpImgDB(mConfig);

        session.configure(mConfig);

        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
        }

        mSurfaceView.onResume();
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    void setUpImgDB(Config config){

        // ????????? ????????? ????????????
        AugmentedImageDatabase imageDatabase = new AugmentedImageDatabase(session);

        try {
            // ?????? ????????? ??????
            InputStream is = getAssets().open("img4.png");
            // ?????? ??????????????? BitMap ??????
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            imageDatabase.addImage("img4go", bitmap);
            is.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        // session Config??? ????????? ????????? ????????????????????? ??????
        // ??????????????? ?????????
        config.setAugmentedImageDatabase(imageDatabase);

    }

    // ????????? ?????? ????????? ?????? ????????? ??????
    void drawImages(Frame frame){
        mainRenderer.isImgFind = false;
        // frame(Camera) ?????? ?????? ??????????????? Collection?????? ????????????.
        Collection<AugmentedImage> updateAugmentedImages = frame.getUpdatedTrackables(AugmentedImage.class);

        for (AugmentedImage img : updateAugmentedImages) {
            Log.d("???????????????/"," ?????????");
            if (img.getTrackingState() == TrackingState.TRACKING) {
                mainRenderer.isImgFind = true;
                Pose imagePose = img.getCenterPose();
                Log.d("????????? ??????",img.getIndex() + " , " + img.getName() + "," +
                        imagePose.tx() + "," + imagePose.ty() + "," + imagePose.tz());
                float[] matrix = new float[16];
                imagePose.toMatrix(matrix, 0);

                mainRenderer.mObj.setModelMatrix(matrix);
            }
        }
    }

    class CallBack implements MainRenderer.RenderCallBack{

        @Override
        public void preRender() {
            if(mainRenderer.mViewport){
                Display display = getWindowManager().getDefaultDisplay();
                int displayRotation = display.getRotation();
                mainRenderer.updateSession(session, displayRotation); // ?????????????????? ?????? ?????????
            }
            session.setCameraTextureName(mainRenderer.getTextureId());
            Frame frame = null;

            try {
                frame = session.update();
            } catch (CameraNotAvailableException e) {
                e.printStackTrace();
            }

            if(frame.hasDisplayGeometryChanged())
                mainRenderer.mCamera.transformDisplayGeometry(frame);

            Camera camera = frame.getCamera();
            float[] projMatrix = new float[16];
            camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100f);
            float[] viewMatrix = new float[16];
            camera.getViewMatrix(viewMatrix, 0);

            // ????????? ?????? ????????? ?????? ????????? ??????
            drawImages(frame);

            mainRenderer.setPorojectMatrix(projMatrix);
            mainRenderer.updateViewMatrix(viewMatrix);
        }
    }

    class DisplayListener implements DisplayManager.DisplayListener{
        @Override
        public void onDisplayAdded(int i) { }
        @Override
        public void onDisplayRemoved(int i) {  }
        @Override
        public void onDisplayChanged(int i) {
            synchronized (this){
                mainRenderer.mViewport = true;
            }
        }
    }
    class OnClick implements View.OnClickListener{

        @Override
        public void onClick(View view) {
            ColorDrawable cd = (ColorDrawable) view.getBackground();
            int color = cd.getColor();
            colorControl[0] = Color.red(color)   / 255f;
            colorControl[1] = Color.green(color) / 255f;
            colorControl[2] = Color.blue(color)  / 255f;
            colorControl[3] = light;
            mainRenderer.mObj.setColorCorrection(colorControl);
        }
    }
    class SeekbarController implements SeekBar.OnSeekBarChangeListener{
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean b) {
            light = (float) progress / 100;
            System.out.println(light);
            colorControl[3] = light;
            mainRenderer.mObj.setColorCorrection(colorControl);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    }

    private void settingSurfaceView(){
        mSurfaceView.setPreserveEGLContextOnPause(true);
        mSurfaceView.setEGLContextClientVersion(2);
        mSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        mSurfaceView.setRenderer(mainRenderer);
    }

    private void noTitleBar() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void requstCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 0);
    }

    public boolean onTouchEvent(MotionEvent event){
        if(event.getAction()==MotionEvent.ACTION_DOWN){
            mTouched = true;
            mCurrentX = event.getX();
            mCurrentY = event.getY();
        }
        return true;
    }
}