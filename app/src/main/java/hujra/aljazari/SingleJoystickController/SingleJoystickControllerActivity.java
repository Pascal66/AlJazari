package hujra.aljazari.SingleJoystickController;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

import static java.lang.Math.abs;

import hujra.aljazari.R;
import hujra.aljazari.joystick.JoystickMovedListener;
import hujra.aljazari.bluetooth.DeviceListActivity;
import hujra.aljazari.bluetooth.BluetoothRfcommClient;
import hujra.aljazari.joystick.SingleJoystickView;


public class SingleJoystickControllerActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private int connection_type = -1;    //0=WiFi(TCP), 1=WiFi(ROS) 2=BT(Serial)

    private final boolean D = false;
    private static final String TAG = SingleJoystickControllerActivity.class.getSimpleName();

    private DataOutputStream socket_link;
    private WiFiCommLinkSetup wifi_link;
    private String server_ip;
    private int server_port;

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
    SingleJoystickView mSingleJoystick;
    private TextView mTxtStatus;
    private TextView mTVjoystickreadings;

    // Menu
    private MenuItem mItemConnect_WiFi;
    private MenuItem mItemConnect_BT;
    private MenuItem mItemOptions;

    // polar coordinates
    private double mRadiusJS = 0;
    private double mAngleJS = 0;
    private boolean mCenter = true;
    private int mDataFormat;
    private float lspeed = 0;
    private float rspeed = 0;
    private float lrdir = 0;
    private byte[] msg_buffer = new byte[] {0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0};
    private int msg_len = 10;

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
        setContentView(R.layout.activity_single_joystick);

        mSingleJoystick = (SingleJoystickView)findViewById(R.id.singlejoystickView);
        mSingleJoystick.setOnJostickMovedListener(_listenerLeft);

        mTxtStatus = (TextView) findViewById(R.id.txt_status);
        mTVjoystickreadings = (TextView) findViewById(R.id.txt_dataL);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        // mUpdatePeriod = prefs.getLong( "updates_interval", 200 ); // in milliseconds
        mUpdatePeriod = Long.parseLong(prefs.getString( "sj_updates_interval", "200" ));
        mMaxTimeoutCount = Integer.parseInt(prefs.getString( "sj_maxtimeout_count", "20" ));
        mDataFormat = Integer.parseInt(prefs.getString( "sj_data_format", "1" ));

        server_ip = prefs.getString("IP_data", "192.168.4.1");
        server_port = Integer.parseInt(prefs.getString("Port_data", "5000"));

        mStrA = prefs.getString( "btnA_data", "A" );
        mStrB = prefs.getString( "btnB_data", "B" );
        mStrC = prefs.getString( "btnC_data", "C" );
        mStrD = prefs.getString( "btnD_data", "D" );

        /*mButtonA = (Button) findViewById(R.id.button_A);
        mButtonA.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                sendMessage( mStrA );
            }
        });

        mButtonB = (Button) findViewById(R.id.button_B);
        mButtonB.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                sendMessage( mStrB );
            }
        });

        mButtonC = (Button) findViewById(R.id.button_C);
        mButtonC.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                sendMessage( mStrC );
            }
        });

        mButtonD = (Button) findViewById(R.id.button_D);
        mButtonD.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                sendMessage( mStrD );
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
        mItemConnect_WiFi = menu.add("Connect (WiFi)");
        mItemConnect_BT = menu.add("Connect (Bluetooth)");
        mItemOptions = menu.add("Options");
        return (super.onCreateOptionsMenu(menu));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if ( item == mItemConnect_WiFi ) {
            connection_type = 0;
            wifi_link = new WiFiCommLinkSetup(server_ip, server_port);
            wifi_link.start();
            mTxtStatus.setText("Connected to :" + server_ip + String.valueOf(server_port));

        } else if ( item == mItemConnect_BT ) {
            connection_type = 2;
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            // If the adapter is null, then Bluetooth is not supported
           /* if (mBluetoothAdapter == null) {
                Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
                finish();
                return;
            }*/
            if (!mBluetoothAdapter.isEnabled()){//Turn on BT if not already
                mBluetoothAdapter.enable();
                //Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                //startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            }
            // Initialize the BluetoothRfcommClient to perform bluetooth connections
            mRfcommClient = new BluetoothRfcommClient(this, mHandler);
            Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);

        } else if ( item == mItemOptions ) {
            startActivity( new Intent(this, SingleJoystickOptionsActivity.class) );
            /*
            SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
String strUserName = SP.getString("username", "NA");
boolean bAppUpdates = SP.getBoolean("applicationUpdates",false);
String downloadType = SP.getString("downloadType","1");
             */
        }
        return super.onOptionsItemSelected(item);
    }

    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if ( key.equals("sj_updates_interval") ) {
            // reschedule task
            mUpdateTimer.cancel();
            mUpdateTimer.purge();
            mUpdatePeriod = Long.parseLong(prefs.getString( "sj_updates_interval", "200" ));
            mUpdateTimer = new Timer();
            mUpdateTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    UpdateMethod();
                }
            }, mUpdatePeriod, mUpdatePeriod);
        }else if( key.equals("sj_maxtimeout_count") ){
            mMaxTimeoutCount = Integer.parseInt(prefs.getString( "sj_maxtimeout_count", "20" ));
        }else if( key.equals("sj_data_format") ){
            mDataFormat = Integer.parseInt(prefs.getString( "sj_data_format", "1" ));
        }else if( key.equals("IP_data") ){
            server_ip = prefs.getString( "IP_data", "192.168.4.1" );
        }else if( key.equals("Port_data") ){
            server_port = Integer.parseInt(prefs.getString( "Port_data", "5000" ));

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
        if(wifi_link != null) {
            wifi_link.running = false;
/*            if (wifi_link.client.isConnected()) {
                try {
                    wifi_link.client.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //wifi_link = null;
            }*/
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle("Single Joystick Controller")
                .setMessage("Close this controller?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }

    private JoystickMovedListener _listenerLeft = new JoystickMovedListener() {

        public void OnMoved(int pan, int tilt) {
            mRadiusJS = Math.min(Math.sqrt((pan*pan) + (tilt*tilt))*2.01,255);
            mAngleJS = Math.atan2(tilt,pan);
            mTVjoystickreadings.setText(String.format("(%.2f,%.2f)", mRadiusJS, mAngleJS*57.295779513082320876798154814105));
            //if (wifi_link.isConnected())
            //mTxtStatus.setText(R.string.title_connected_to + "192.168.1.4" + "5000");
            mCenter = false;
        }

        public void OnReleased() {
            //
        }

        public void OnReturnedToCenter() {
            mRadiusJS = mAngleJS = 0;
            UpdateMethod();
            mCenter = true;
        }
    };

    private void sendMessage(String message){
        if(connection_type==0) {//WiFi
            if (wifi_link.isConnected()) {
                try {
                    socket_link.write(msg_buffer, 0, msg_len);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to send data! ");
                    try {
                        wifi_link.client.close();
                        Log.e(TAG, "Socket closed");
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    wifi_link.connected = false;
                }
            }
        }else if(connection_type==2) {//Bluetooth
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
    }

    private void UpdateMethod() {
        // if joystick is not on the center, or timeout occurred
        if(!mCenter || (mTimeoutCounter>=mMaxTimeoutCount && mMaxTimeoutCount>-1) ) {
            if( mDataFormat==1 ) {
                double strspeed = abs(Math.cos(mAngleJS)) * mRadiusJS;
                byte dir1,dir2;
                if(mAngleJS<0)
                    dir1 = 3;
                else
                    dir1 = 2;
                if(abs(mAngleJS)<1.571)
                    dir2 = 3;
                else
                    dir2 = 2;
                msg_len = 8;
                msg_buffer[0] = '$';
                msg_buffer[1] = 0x02;
                msg_buffer[2] = (byte)mRadiusJS;
                msg_buffer[3] = dir1;
                msg_buffer[4] = (byte)mRadiusJS;
                msg_buffer[5] = dir1;
                msg_buffer[6] = (byte)strspeed;
                msg_buffer[7] = dir2;
                sendMessage("");
            }
            else if( mDataFormat==1 ) {

            }

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

    private class WiFiCommLinkSetup extends Thread {
        private String serverName;
        private int port;
        private boolean connected;
        private Socket client;
        volatile boolean running = true;

        public WiFiCommLinkSetup(String serverName, int port) {
            this.serverName = serverName;
            this.port = port;
        }

        @Override
        public void run() {
            connected = false;
            while (!connected) {
                if (!running) return;
                try {
                    Log.i(TAG, "Trying to connect to " + serverName + ":" + port + "...");
                    client = new Socket(serverName, port);
                    Log.i(TAG, "Connected to " + client.getRemoteSocketAddress());

                    OutputStream outToServer = client.getOutputStream();
                    socket_link = new DataOutputStream(outToServer);

                    /*InputStream inFromServer = client.getInputStream();
                    in = new DataInputStream(inFromServer);
                    connected = true;
                    CommLinkRxconnected = true;*/
                    Log.i(TAG, "Connected ?: " + client.getRemoteSocketAddress());
                    connected = true;
                } catch (IOException e) {
                    Log.e(TAG, "Connection failed, retrying ...");
                    try {
                        sleep(100);
                    } catch (InterruptedException ignored) {
                    }
                    connected = false;
                }
            }
        }

        public boolean isConnected() {
            return connected;
        }

        public Socket getClient() {
            return client;
        }
    }
}


