package hujra.aljazari;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;

import static java.lang.Math.abs;
import hujra.aljazari.bluetooth.OptionsActivity;
import hujra.aljazari.joystick.DualJoystickView;
import hujra.aljazari.joystick.JoystickMovedListener;
import hujra.aljazari.bluetooth.DeviceListActivity;
import hujra.aljazari.bluetooth.BluetoothRfcommClient;


public class DualJoystickControllerActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    // debug / logs
    private final boolean D = false;
    private static final String TAG = DualJoystickControllerActivity.class.getSimpleName();

    // Message types sent from the BluetoothRfcommClient Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

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

    // Layout View
    DualJoystickView mDualJoystick;
    //SingleJoystickView mSingleJoystick;
    //private Button mButtonA;
    private TextView mTxtStatus;
    private TextView mTxtDataL;
    private TextView mTxtDataR;

    // Menu
    private MenuItem mItemConnect;
    private MenuItem mItemOptions;
    private MenuItem mItemConfig;
    private MenuItem mItemAbout;

    // polar coordinates
    private double mRadiusL = 0, mRadiusR = 0;
    private double mAngleL = 0, mAngleR = 0;
    private boolean mCenterL = true, mCenterR = true;
    private int mDataFormat;
    private byte thr = 0;
    private byte yaw = 0;
    private byte pitch = 0;
    private byte roll = 0;

    // button data
    private String mStrA;
    private String mStrB;
    private String mStrC;
    private String mStrD;

    // timer task
    private Timer mUpdateTimer;
    private int mTimeoutCounter = 0;
    private int mMaxTimeoutCount; // actual timeout = count * updateperiod
    private long mUpdatePeriod;


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dualjoysticklayout);

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

        mDualJoystick = (DualJoystickView)findViewById(R.id.dualjoystickView);
        mDualJoystick.setOnJostickMovedListener(_listenerLeft, _listenerRight);
        mDualJoystick.setAutoReturnToCenter(false,true);    //Only right is enabled, left is for throttle. Also return to zero in only y is disabled. x always returns to zero. See code JoyStickView
//        mSingleJoystick = (SingleJoystickView)findViewById(R.id.singlejoystickView);
//        mSingleJoystick.setOnJostickMovedListener(_listenerLeft);

        // mDualJoystick.setYAxisInverted(false, false);

        mTxtStatus = (TextView) findViewById(R.id.txt_status);
        mTxtDataL = (TextView) findViewById(R.id.txt_dataL);
        // mTxtDataR = (TextView) findViewById(R.id.txt_dataR);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        // mUpdatePeriod = prefs.getLong( "updates_interval", 200 ); // in milliseconds
        mUpdatePeriod = Long.parseLong(prefs.getString( "updates_interval", "200" ));
        mMaxTimeoutCount = Integer.parseInt(prefs.getString( "maxtimeout_count", "20" ));
        mDataFormat = Integer.parseInt(prefs.getString( "data_format", "7" ));

        /*mStrA = prefs.getString( "btnA_data", "A" );

        mButtonA = (Button) findViewById(R.id.button_A);
        mButtonA.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                sendMessage( mStrA );
            }
        });*/

        // fix me: use Runnable class instead
        mUpdateTimer = new Timer();
        mUpdateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                UpdateMethod();
            }
        }, 2000, mUpdatePeriod);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mItemConnect = menu.add("Connect");
        mItemOptions = menu.add("Options");
        mItemConfig = menu.add("Send Configuration");
        mItemAbout = menu.add("About");
        return (super.onCreateOptionsMenu(menu));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if ( item == mItemConnect ) {
            Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
        } else if ( item == mItemOptions ) {
            startActivity( new Intent(this, OptionsActivity.class) );
        } else if ( item == mItemAbout ) {
            AlertDialog about = new AlertDialog.Builder(this).create();
            about.setCancelable(false);
            about.setMessage("Binkamaat v1.0\nhttp://sites.google.com/view/4mbilal");
            about.setButton(DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                }
            });
            about.show();
        } else if ( item == mItemConfig) {
            Intent intent = new Intent(this, ConfigPacketActivity.class);
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if ( key.equals("updates_interval") ) {
            // reschedule task
            mUpdateTimer.cancel();
            mUpdateTimer.purge();
            mUpdatePeriod = Long.parseLong(prefs.getString( "updates_interval", "200" ));
            mUpdateTimer = new Timer();
            mUpdateTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    UpdateMethod();
                }
            }, mUpdatePeriod, mUpdatePeriod);
        }else if( key.equals("maxtimeout_count") ){
            mMaxTimeoutCount = Integer.parseInt(prefs.getString( "maxtimeout_count", "20" ));
        }else if( key.equals("data_format") ){
            mDataFormat = Integer.parseInt(prefs.getString( "data_format", "7" ));
        }else if( key.equals("btnA_data") ){
            mStrA = prefs.getString( "btnA_data", "A" );
        }else if( key.equals("btnB_data") ){
            mStrB = prefs.getString( "btnB_data", "B" );
        }else if( key.equals("btnC_data") ){
            mStrC = prefs.getString( "btnC_data", "C" );
        }else if( key.equals("btnD_data") ){
            mStrD = prefs.getString( "btnD_data", "D" );
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
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
        mUpdateTimer.cancel();
        // Stop the Bluetooth RFCOMM services
        if (mRfcommClient != null) mRfcommClient.stop();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle("Bluetooth Joystick")
                .setMessage("Close this controller?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }




    private JoystickMovedListener _listenerRight = new JoystickMovedListener() {

        public void OnMoved(int pan, int tilt) {
            mTxtDataL.setText(String.format("(%d,%d)", pan,tilt));
            roll = (byte)pan;
            pitch = (byte)tilt;
            mCenterL = false;
        }

        public void OnReleased() {
            //
        }

        public void OnReturnedToCenter() {
            UpdateMethod();
            mCenterL = true;
        }
    };
    private JoystickMovedListener _listenerLeft = new JoystickMovedListener() {

        public void OnMoved(int pan, int tilt) {
            mTxtDataL.setText(String.format("(%d,%d)", pan,tilt));
            yaw = (byte)pan;
            thr = (byte)tilt;
            mCenterL = false;
        }
        public void OnReleased() {
            //
        }

        public void OnReturnedToCenter() {
            UpdateMethod();
            mCenterL = true;
        }
    };
    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private void sendMessage(String message){
        // Check that we're actually connected before trying anything
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

    private void sendMessagebytes(byte[] send){
        // Check that we're actually connected before trying anything
        if (mRfcommClient.getState() != BluetoothRfcommClient.STATE_CONNECTED) {
            // Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }
            mRfcommClient.write(send);
    }

    private void UpdateMethod() {

        // if either of the joysticks is not on the center, or timeout occurred
        if(!mCenterL || !mCenterR || (mTimeoutCounter>=mMaxTimeoutCount && mMaxTimeoutCount>-1) ) {
            byte [] msg = new byte[6];
            msg[0] = '$';
            msg[1] = (byte)(thr);
            msg[2] = (byte)yaw;
            msg[3] = (byte)pitch;
            msg[4] = (byte)roll;
            char chksum = (char)((char)(msg[1]) + (char)(msg[2]) + (char)(msg[3]) + (char)(msg[4]));
            msg[5] = (byte)chksum;
            sendMessagebytes(msg);

            mTimeoutCounter = 0;
        }
        else{
            if( mMaxTimeoutCount>-1 )
                mTimeoutCounter++;
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode){
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
            }
        }
    };
}


