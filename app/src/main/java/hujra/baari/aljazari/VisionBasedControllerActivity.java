package hujra.baari.aljazari;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatActivity;

//import android.support.v4.app.ActivityCompat;
//import android.support.v4.content.ContextCompat;
//import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.ArrayList;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.imgcodecs.Imgcodecs;
//import org.opencv.imgproc.Moments;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.imgproc.Imgproc;
//import org.opencv.samples.facedetect.Moments;

import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.graphics.Bitmap;
import android.view.MotionEvent;
import android.widget.TextView;
import android.widget.Toast;

import hujra.baari.aljazari.bluetooth.DeviceListActivity;
import hujra.baari.aljazari.bluetooth.BluetoothRfcommClient;

import java.util.Scanner;

import static java.lang.Math.abs;
import static java.lang.Math.exp;
import static java.lang.Math.pow;
import static java.lang.Math.signum;
//import static org.opencv.core.Core.FONT_HERSHEY_PLAIN;
import static org.opencv.imgproc.Imgproc.LINE_AA;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

public class VisionBasedControllerActivity extends AppCompatActivity implements CvCameraViewListener2,SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "OpenCV Application";

    private Mat mRgba;
    private Mat mGray;
    private Mat bitmap;
    private File mCascadeFile;
    private CascadeClassifier mJavaDetector;

    private int Vid_Res;
    //PID Speed Control Variables
    private float min_spd, max_spd, Kp, Kd, Ki;

    private MenuItem Menu_BTConnect,Menu_Options;

    private float[] err;
    private int n_seg;

    public static boolean init_time = false;
    private static long time_diff = 0;
    private static float fr = 200;
    Bitmap bmp;

    public double ClkFreq;
    public long Prev_tick, Cur_tick;

    public float prev_err, int_err, diff_err;
    //private float[] PID_params;

    // Message types sent from the BluetoothRfcommClient Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_FPS = 6;


    // Key names received from the BluetoothRfcommClient Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the RFCOMM services
    private BluetoothRfcommClient mRfcommClient = null;
    private TextView mTxtStatus;
    private TextView mFPStv;

    //private static native void nativedetect(long inputImage, long type, long width, long height);


    private CameraBridgeViewBase mOpenCvCameraView;

    //No need for OpenCV Manager if this line is included + jniLibs folder contains armeabi-v7a folder copied from OpenCV SDK folder
    static{ System.loadLibrary("opencv_java4"); }

    //OpenCVLoader

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    ClkFreq = Core.getTickFrequency();
                    //mOpenCvCameraView.enableFpsMeter();
                    //mOpenCvCameraView.setMaxFrameSize(100,100);
                    // Load native library after(!) OpenCV initialization
                    //System.loadLibrary("JNI_Interface");
                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    public VisionBasedControllerActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
        n_seg = 8;
        err = new float[n_seg];
        prev_err = 0;
        int_err = 0;
        diff_err = 0;
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vision_controller);
        Log.i(TAG, "called onCreate");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // camera_permissions();     //Required explicit user permissions for targetSdkVersion > 22
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.lf_activity_surface_view);
        mOpenCvCameraView.setCameraIndex(0);
        mOpenCvCameraView.setCvCameraViewListener(this);

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // If BT is not on, request that it be enabled.
        if (!mBluetoothAdapter.isEnabled()){
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
        // Initialize the BluetoothRfcommClient to perform bluetooth connections
        mRfcommClient = new BluetoothRfcommClient(this, mHandler);
        mTxtStatus = (TextView) findViewById(R.id.bt_status_lf);
        mFPStv = (TextView) findViewById(R.id.fps_monitor);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        Vid_Res = Integer.parseInt(prefs.getString( "vid_res", "1" ));
        min_spd = Float.parseFloat(prefs.getString("min_spd","50.0"));
        max_spd = Float.parseFloat(prefs.getString("max_spd","80.0"));
        Kp = Float.parseFloat(prefs.getString("pid_kp","85.0"));
        Kd = Float.parseFloat(prefs.getString("pid_kd","85.0"));
        Ki = Float.parseFloat(prefs.getString("pid_ki","1.0"));
        OpenCVLoader.initDebug();
        mOpenCvCameraView.enableView();
        mOpenCvCameraView.setCameraPermissionGranted();
        Log.d(TAG, "OpenCV library found inside package. Using it!");
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            //OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            //mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
            OpenCVLoader.initDebug();
            mOpenCvCameraView.enableView();
            //System.loadLibrary("opencv_java3");
        }
        if (mRfcommClient != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mRfcommClient.getState() == BluetoothRfcommClient.STATE_NONE) {
                // Start the Bluetooth  RFCOMM services
                mRfcommClient.start();
            }
        }
    }

    @Override
    public void onDestroy() {
        mOpenCvCameraView.disableView();
        if (mRfcommClient != null) mRfcommClient.stop();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle("Line Follower")
                .setMessage("Close this activity?")
                .setPositiveButton("Yes", (dialog, which) -> finish())
                .setNegativeButton("No", null)
                .show();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
        bitmap = Mat.zeros(135, 90, CvType.CV_8UC4);
    }

    @Override
    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }

 /*   public void camera_permissions(){//Ask User to grant Camera permissions to this app
        //Check if permission is already granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // Give first an explanation, if needed.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},1);
            }
        }
    }*/

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        Size s_orig = new Size(mRgba.width(), mRgba.height());


        switch (Vid_Res) {
            case 1: {
                Size s = new Size(320, 240);
                //Imgproc.putText(mRgba, "QVGA", new org.opencv.core.Point(150, 150), FONT_HERSHEY_PLAIN, 20.0, new Scalar(255, 0, 25, 255), 5);
                Imgproc.resize(mRgba, mRgba, s);
            }
            break;
            case 2: {
                Size s = new Size(640, 480);
                //Imgproc.putText(mRgba, "VGA", new org.opencv.core.Point(150, 150), FONT_HERSHEY_PLAIN, 20.0, new Scalar(255, 0, 25, 255), 5);
                Imgproc.resize(mRgba, mRgba, s);
            }
            break;
            case 3: {
                //Imgproc.putText(mRgba, "Native", new org.opencv.core.Point(150, 150), FONT_HERSHEY_PLAIN, 20.0, new Scalar(255, 0, 25, 255), 5);
            }
            break;
        }

        Line_Detection(mRgba);
        PID_Speed_Controller();
        frame_rate();

        Imgproc.resize(mRgba, mRgba, s_orig);
        //Imgproc.putText(mRgba, String.format("%.2f, %.2f, %.2f, %.2f, %.2f",min_spd,max_spd,Kp,Kd,Ki), new org.opencv.core.Point(50, 50), FONT_HERSHEY_PLAIN, 5.0, new Scalar(255, 0, 25, 255), 3);
        return mRgba;
    }

    private void PID_Speed_Controller() {
        float curr_err = 1.0f;
        for (int i = n_seg - 4; i >= 0; i--) {
            curr_err = err[i];      //Pick any valid error, starting from the nearest segment
            if (curr_err < 0.9f)
                break;
        }
        if (curr_err > 0.9f)  //No valid line segment found, Use last known error
            curr_err = prev_err;

        float line_curvature = 0.0f;    //[0(Straight) 1(Crooked)]
        for (int i = n_seg - 1; i >= 0; i--) {
            line_curvature += abs(err[i]);
        }
        line_curvature = line_curvature / (float) n_seg;     //[0 1]

        //Basic PID-Controller
        //float curr_err_pow = signum(curr_err) * (float) pow((float) abs(curr_err * 2f), PID_params[2]);
        //curr_err = curr_err_pow;
        float def_speed = min_spd + (max_spd-min_spd)*((float)1.0-line_curvature);//PID_params[0] + PID_params[1] * (-0.582f + 1.582f * (float) exp(-line_curvature));
        float lspeed = def_speed;
        float rspeed = def_speed;
        float lrdir = 0;
        diff_err = curr_err - prev_err;
        int_err = int_err + curr_err;
        if (int_err > 0.25) int_err = 0.25f;
        if (int_err < -0.25) int_err = -0.25f;

        rspeed += (curr_err * Kp + diff_err * Kd + int_err * Ki);     //PID Gains
        lspeed -= (curr_err * Kp + diff_err * Kd + int_err * Ki);

        float limit = 254;
        if(lspeed<-(limit-0.5)) lspeed = -limit;
        if(rspeed<-(limit-0.5)) rspeed = -limit;
        if(lspeed>(limit-0.5)) lspeed = limit;
        if(rspeed>(limit-0.5)) rspeed = limit;

        lrdir = 0;  //wheel direction encoding in a single byte
        if(lspeed<0)
            lrdir = lrdir + 2;
        if(rspeed<0)
            lrdir = lrdir + 1;

        lspeed = abs(lspeed);
        rspeed = abs(rspeed);

        String msg = "l" + String.valueOf(Character.toChars((char)lspeed)) + "r" + String.valueOf(Character.toChars((char)rspeed)) + "d" + String.valueOf(Character.toChars((char)lrdir));
        sendMessage(msg);
        prev_err = curr_err;
        // Imgproc.putText(mRgba, String.format("%.2f", def_speed), new org.opencv.core.Point(50, 50), FONT_HERSHEY_PLAIN, 5.0, new Scalar(255, 0, 25, 255), 1);
    }

    private void sendMessage(String message){
        // Check that we're actually connected before trying anything
        //Log.i(TAG, "BT..");
        if (mRfcommClient.getState() != BluetoothRfcommClient.STATE_CONNECTED) {
            // Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }
        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothRfcommClient to write
            byte[] send = message.getBytes();
            mRfcommClient.write(send);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device MAC address
                    String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    // Get the BLuetoothDevice object
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    // Attempt to connect to the device
                    mRfcommClient.connect(device);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode != Activity.RESULT_OK) {
                    // User did not enable Bluetooth or an error occurred
                    Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
        }
    }
    // The Handler that gets information back from the BluetoothRfcommClient
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothRfcommClient.STATE_CONNECTED:
                            mTxtStatus.setText(R.string.title_connected_to);
                            mTxtStatus.append(" " + mConnectedDeviceName);
                            break;
                        case BluetoothRfcommClient.STATE_CONNECTING:
                            mTxtStatus.setText(R.string.title_connecting);
                            break;
                        case BluetoothRfcommClient.STATE_NONE:
                            mTxtStatus.setText(R.string.title_not_connected);
                            break;
                    }
                    break;
                case MESSAGE_READ:
                    // byte[] readBuf = (byte[]) msg.obj;
                    // int data_length = msg.arg1;
                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_FPS:
                    mFPStv.setText("FPS = "+String.valueOf((float)msg.arg1/10));
                    break;
            }
        }
    };

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();
        openOptionsMenu();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "called onCreateOptionsMenu");
        Menu_BTConnect = menu.add("Connect BT");
        Menu_Options = menu.add("Options");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);
        if (item == Menu_BTConnect){
            Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
        }
        else if(item == Menu_Options){
            startActivity( new Intent(this, line_follower_preferences.class) );
        }
        return true;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        switch (key) {
            case "vid_res":
                Vid_Res = Integer.parseInt(prefs.getString("vid_res", "1"));
                break;
            case "min_spd":
                min_spd = Float.parseFloat(prefs.getString("min_spd", "50.0"));
                break;
            case "max_spd":
                max_spd = Float.parseFloat(prefs.getString("max_spd", "80.0"));
                break;
            case "pid_kp":
                Kp = Float.parseFloat(prefs.getString("pid_kp", "85.0"));
                break;
            case "pid_kd":
                Kd = Float.parseFloat(prefs.getString("pid_kd", "85.0"));
                break;
            case "pid_ki":
                Ki = Float.parseFloat(prefs.getString("pid_ki", "1.0"));
                break;
        }
    }

    private void saveFrameToPath(Bitmap bmp, String pPath) {
        Mat frame;// = new Mat();
        frame = new Mat(bmp.getHeight(), bmp.getWidth(), CvType.CV_8UC3, new Scalar(128));
        Utils.bitmapToMat(bmp, frame);
        Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGB2BGR);
        Imgcodecs.imwrite(pPath, frame);
    }

    private void saveMatImage(Mat image, String pPath) {
        //Mat frame;// = new Mat();
        //frame = new Mat ( bmp.getHeight(), bmp.getWidth(), CvType.CV_8UC3, new Scalar(128));
        //Utils.bitmapToMat(bmp, frame);
        //Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGB2BGR);
        Imgcodecs.imwrite(pPath, image);
    }

    private void frame_rate() {
        //mOpenCvCameraView.enableFpsMeter(); //Builtin fps meter, gives almost the same value
        if (!init_time) {
            time_diff = SystemClock.elapsedRealtime();
            init_time = true;
        } else {
            long temp = SystemClock.elapsedRealtime();
            //        fr = (float) (1000/(float)(temp - time_diff))*(float)0.1 + fr*(float)0.9;
            fr = (float) ((float) (temp - time_diff)) * (float) 0.01 + fr * (float) 0.99;

            //        myOCV(mRgba.getNativeObjAddr(),String.format("%.1f", fr));
            //don't use native puttext function to increase speed maybe
            //Imgproc.putText(mRgba, String.format("%.2f fps", 1000 / fr), new org.opencv.core.Point(25, 25), FONT_HERSHEY_PLAIN, 1.0, new Scalar(255, 0, 25, 255), 1);
            float fps = 1000/fr;
            Message msg = new Message();
            msg.what = MESSAGE_FPS;
            msg.arg1 = (int)(fps*10);//String.valueOf(fps);
            mHandler.sendMessage(msg);

            time_diff = temp;
        }

        /*Cur_tick = Core.getTickCount();
        double fps = ClkFreq / (Cur_tick - Prev_tick);
        Prev_tick = Cur_tick;*/
        //Imgproc.putText(mRgba, String.format("%.2f fps", fps), new org.opencv.core.Point(100, 100), FONT_HERSHEY_PLAIN, 6.0, new Scalar(255, 0, 25, 255), 1);
    }

    private void Line_Detection(Mat frame) {
        int c = frame.width();
        int r = frame.height();
        int p = n_seg;                  //Number of image portions (Set globally)
        Rect[] roi = new Rect[p];
        Mat[] pimg = new Mat[p];    //Array of Mats to store image portions
        for (int i = 0; i < p; i++) {
            roi[i] = new Rect(c * i / p, 0, c / p, r);   //Divide image column wise (Image is by default landscape mode in Smartphone)
            pimg[i] = new Mat(frame, roi[i]);
            err[i] = Line_Detection_Worker(pimg[i]);
        }
    }

    private float Line_Detection_Worker(Mat frame) {
        Mat temp = Mat.zeros(1, 1, CvType.CV_8UC1);
        Core.bitwise_not(frame, temp);//invert color channels & SEARCH for Cyan instead of Red. Because the conditions are easier set. Otherwise Red wraps around 180 i.e. [0 10] and [170 180]. so two condition checks.
        Imgproc.cvtColor(temp, temp, Imgproc.COLOR_RGBA2RGB);//convert to HSV color space
        Imgproc.cvtColor(temp, temp, Imgproc.COLOR_RGB2HSV);//convert to HSV color space
        Core.inRange(temp, new Scalar(85, 70, 50), new Scalar(95, 255, 255), temp);//Red
//        Core.inRange(temp, new Scalar(150-5, 70, 50), new Scalar(150+5, 255, 255), temp);//Green

        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(temp, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

        MatOfPoint MainContour = null;
        double maxArea = 100;   //Less than 100 area are not considered
        for (int i = 0; i < contours.size(); i++) {
            MatOfPoint currentContour = contours.get(i);
            double area = Imgproc.contourArea(currentContour);
            if (area > maxArea) {
                maxArea = area;
                MainContour = currentContour;
            }
        }
        contours.clear();
        Point centroid = new Point(0, 0);
        int center_frame_c = frame.width() / 2;
        int center_frame_r = frame.height() / 2;
        float err = 1.0f;

        if (MainContour != null) {  //Found at least one contour
            contours.add(MainContour);
            centroid = getContourCenter(MainContour);
            err = ((float) centroid.y - (float) center_frame_r) / (float) center_frame_r;
            Imgproc.drawContours(frame, contours, -1, new Scalar(0, 255, 0), 1);
            Imgproc.circle(frame, new Point(centroid.x, centroid.y), 4, new Scalar(255, 255, 255), -1);
            Imgproc.circle(frame, new Point(center_frame_c, center_frame_r), 4, new Scalar(0, 0, 255), -1);
            Imgproc.putText(frame, "" + err, new Point(center_frame_c - 15, frame.height() - 15), Imgproc.FONT_HERSHEY_PLAIN, 1, new Scalar(200, 0, 200), 1, LINE_AA, false);
        }
        return err; //(Range [-0.5 0.5]), 1 if no sufficiently big contour found
    }

    private Point getContourCenter(MatOfPoint contour) {
        Moments moments = contourMoments(contour);
        Point centroid = new Point();
        centroid.x = moments.get_m10() / moments.get_m00();
        centroid.y = moments.get_m01() / moments.get_m00();
        return centroid;
    }

    public static Moments contourMoments(MatOfPoint contour) {
        Moments m = new Moments();
        int lpt = contour.checkVector(2);
        boolean is_float = true;//(contour.depth() == CvType.CV_32F);
        Point[] ptsi = contour.toArray();
//PointF[] ptsf = contour.toArray();

        //CV_Assert( contour.depth() == CV_32S || contour.depth() == CV_32F );

        if (lpt == 0)
            return m;

        double a00 = 0, a10 = 0, a01 = 0, a20 = 0, a11 = 0, a02 = 0, a30 = 0, a21 = 0, a12 = 0, a03 = 0;
        double xi, yi, xi2, yi2, xi_1, yi_1, xi_12, yi_12, dxy, xii_1, yii_1;


        {
            xi_1 = ptsi[lpt - 1].x;
            yi_1 = ptsi[lpt - 1].y;
        }

        xi_12 = xi_1 * xi_1;
        yi_12 = yi_1 * yi_1;

        for (int i = 0; i < lpt; i++) {

            {
                xi = ptsi[i].x;
                yi = ptsi[i].y;
            }

            xi2 = xi * xi;
            yi2 = yi * yi;
            dxy = xi_1 * yi - xi * yi_1;
            xii_1 = xi_1 + xi;
            yii_1 = yi_1 + yi;

            a00 += dxy;
            a10 += dxy * xii_1;
            a01 += dxy * yii_1;
            a20 += dxy * (xi_1 * xii_1 + xi2);
            a11 += dxy * (xi_1 * (yii_1 + yi_1) + xi * (yii_1 + yi));
            a02 += dxy * (yi_1 * yii_1 + yi2);
            a30 += dxy * xii_1 * (xi_12 + xi2);
            a03 += dxy * yii_1 * (yi_12 + yi2);
            a21 += dxy * (xi_12 * (3 * yi_1 + yi) + 2 * xi * xi_1 * yii_1 +
                    xi2 * (yi_1 + 3 * yi));
            a12 += dxy * (yi_12 * (3 * xi_1 + xi) + 2 * yi * yi_1 * xii_1 +
                    yi2 * (xi_1 + 3 * xi));
            xi_1 = xi;
            yi_1 = yi;
            xi_12 = xi2;
            yi_12 = yi2;
        }
        float FLT_EPSILON = 1.19209e-07f;
        if (abs(a00) > FLT_EPSILON) {
            double db1_2, db1_6, db1_12, db1_24, db1_20, db1_60;

            if (a00 > 0) {
                db1_2 = 0.5;
                db1_6 = 0.16666666666666666666666666666667;
                db1_12 = 0.083333333333333333333333333333333;
                db1_24 = 0.041666666666666666666666666666667;
                db1_20 = 0.05;
                db1_60 = 0.016666666666666666666666666666667;
            } else {
                db1_2 = -0.5;
                db1_6 = -0.16666666666666666666666666666667;
                db1_12 = -0.083333333333333333333333333333333;
                db1_24 = -0.041666666666666666666666666666667;
                db1_20 = -0.05;
                db1_60 = -0.016666666666666666666666666666667;
            }

            // spatial moments
            m.m00 = a00 * db1_2;
            m.m10 = a10 * db1_6;
            m.m01 = a01 * db1_6;
            m.m20 = a20 * db1_12;
            m.m11 = a11 * db1_24;
            m.m02 = a02 * db1_12;
            m.m30 = a30 * db1_20;
            m.m21 = a21 * db1_60;
            m.m12 = a12 * db1_60;
            m.m03 = a03 * db1_20;

            m.completeState();
        }
        return m;
    }

    /*void Read_PID_Params() {
        try {
            Log.i("SD:", "Going to read..");
            String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Robot_Parameters.txt";
            File file = new File(path);
            if (file.exists()) {
                FileInputStream fileInputStream = new FileInputStream(path);
                InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                StringBuilder stringBuilder = new StringBuilder();
                String lineread = null;
                while ((lineread = bufferedReader.readLine()) != null) {
                    stringBuilder.append(lineread + System.getProperty("line.separator"));
                }
                fileInputStream.close();
                lineread = stringBuilder.toString();
                bufferedReader.close();
                Log.i("SD:", lineread);

                Scanner st = new Scanner(lineread);
                int i = 0;
                while (st.hasNext()) {
                    if (st.hasNextDouble()) {
                        PID_params[i++] = (float) st.nextDouble();
                        Log.i("SD:", String.format("%.2f", PID_params[i - 1]));
                    } else
                        st.next();
                }
                st.close();
                Log.i("SD:", "All read");
            }
            else{
                Log.i("SD:", "Params file not found. Creating new..");
                path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Robot_Parameters.txt";
                file = new File(path);
                FileOutputStream fileOutputStream = new FileOutputStream(file, true);
                String data_out = String.format("Speed_Base = %.3f\n Speed_Var = %.3f\n Alpha = %.3f\n Kp = %.3f\n Kd = %.3f\n Ki = %.3f", PID_params[0], PID_params[1], PID_params[2], PID_params[3], PID_params[4], PID_params[5]);
                fileOutputStream.write((data_out + System.getProperty("line.separator")).getBytes());
                fileOutputStream.close();
            }
        } catch (IOException ex) {}
    }*/
}



