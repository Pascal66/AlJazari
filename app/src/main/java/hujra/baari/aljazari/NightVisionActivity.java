package hujra.baari.aljazari;

import androidx.appcompat.app.AppCompatActivity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import static org.opencv.imgproc.Imgproc.FONT_HERSHEY_PLAIN;

public class NightVisionActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static final String TAG = "NightVision";
    private CameraBridgeViewBase mOpenCvCameraView;
    private Mat mRgba;
    private Mat mGray;
    long prev_time;
    float fps;

    //static{ System.loadLibrary("opencv_java4"); }//This doesn't seem to be necessary. OpenCVLoader.initDebug(); does the job in onCreate()

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);   //Fullscreen mode
        setContentView(R.layout.activity_night_vision);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.nightvision_activity_surface_view);
        mOpenCvCameraView.setCameraIndex(0);    //Front or Back Camera
        mOpenCvCameraView.setCvCameraViewListener(this);
        OpenCVLoader.initDebug();
        mOpenCvCameraView.enableView();
        mOpenCvCameraView.setCameraPermissionGranted();
        fps = 0;
        prev_time = SystemClock.elapsedRealtime();
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
            Log.d(TAG, "OpenCV library not found!");
        } else {
            Log.d(TAG, "OpenCV library found inside package!");
            mOpenCvCameraView.enableView();
            //System.loadLibrary("opencv_java4");   //Equivalent to OpenCVLoader.initDebug()
        }
    }

    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();

    }

    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();
//        Size s_orig = new Size(mRgba.width(), mRgba.height());
//        Imgproc.resize(mRgba, mRgba, s_orig);

        NativeCanny(mGray.getNativeObjAddr(), 2000);

        long cur_time = SystemClock.elapsedRealtime();
        fps = (float)0.99*fps+(float)0.01*(1000/(float)(cur_time-prev_time));
        prev_time = cur_time;

        Imgproc.putText(mGray, String.format("%d, %d, %.2f fps",mRgba.width(),mRgba.height(),fps), new org.opencv.core.Point(50, 50), FONT_HERSHEY_PLAIN, 2.0, new Scalar(255, 0, 25, 255), 3);
        return mGray;
    }

    public native void NativeCanny(long matAddrGray, int nbrElem);
}
