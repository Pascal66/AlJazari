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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Timer;
import java.util.TimerTask;

import hujra.aljazari.bluetooth.BluetoothRfcommClient;
import hujra.aljazari.bluetooth.OptionsActivity;
import hujra.aljazari.bluetooth.DeviceListActivity;
import hujra.aljazari.bluetooth.BluetoothRfcommClient;
import static android.provider.AlarmClock.EXTRA_MESSAGE;

public class ConfigPacketActivity extends AppCompatActivity  implements SharedPreferences.OnSharedPreferenceChangeListener{

    private TextView mTxtStatus;
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

    // timer task
    private Timer mUpdateTimer;
    private int mTimeoutCounter = 0;
    private int mMaxTimeoutCount; // actual timeout = count * updateperiod
    private long mUpdatePeriod;

    private int mDataFormat;
    // Menu
    private MenuItem mItemConnect;
    private MenuItem mItemOptions;
    private MenuItem mItemAbout;

    // button data
    private String mStrA;
    private String mStrB;
    private String mStrC;
    private String mStrD;

    public float[] cfg_values;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config_packet);
        cfg_values = new float[18];
        ReadConfgPcktPref();
        mTxtStatus = (TextView) findViewById(R.id.textView3);

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

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        // mUpdatePeriod = prefs.getLong( "updates_interval", 200 ); // in milliseconds
        mUpdatePeriod = Long.parseLong(prefs.getString( "updates_interval", "200" ));
        mMaxTimeoutCount = Integer.parseInt(prefs.getString( "maxtimeout_count", "20" ));
        mDataFormat = Integer.parseInt(prefs.getString( "data_format", "7" ));

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

        // if timeout occurred
        if((mTimeoutCounter>=mMaxTimeoutCount && mMaxTimeoutCount>-1) ) {
            byte [] header = new byte[3];
            header[0] = '#';
            header[1] = 'C';
            header[2] = 'G';
            byte [] values = FloatArray2ByteArray(cfg_values);
            byte[] msgbytearray = new byte[header.length + values.length];
            System.arraycopy(header, 0, msgbytearray, 0, header.length);
            System.arraycopy(values, 0, msgbytearray, header.length, values.length);

//            char chksum = (char)((char)(msg[1]) + (char)(msg[2]) + (char)(msg[3]) + (char)(msg[4]));
//            msg[5] = (byte)chksum;
            sendMessagebytes(msgbytearray);

            mTimeoutCounter = 0;
        }
        else{
            if( mMaxTimeoutCount>-1 )
                mTimeoutCounter++;
        }
    }

    public static byte [] float2ByteArray (float value)
    {
        //return ByteBuffer.allocate(4).putFloat(value).array();
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array();
        //return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putFloat(value).array();
    }

    public static byte[] FloatArray2ByteArray(float[] values){
        ByteBuffer buffer = ByteBuffer.allocate(4 * values.length);

        for (float value : values){
            buffer.order(ByteOrder.LITTLE_ENDIAN).putFloat(value);
        }

        return buffer.array();
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

    public void ReadConfgPcktPref(){
        SharedPreferences pref = getApplicationContext().getSharedPreferences("ConfgPcktPref", 0); // 0 - for private mode
        SharedPreferences.Editor editor = pref.edit();

        float temp = pref.getFloat("cfg_v1", 0.0f);
        cfg_values[0] = temp;
        TextView tv1 = findViewById(R.id.editText1);
        tv1.setText(Float.toString(temp));
        temp = pref.getFloat("cfg_v2", 0.0f);
        cfg_values[1] = temp;
        tv1 = findViewById(R.id.editText2);
        tv1.setText(Float.toString(temp));
        temp = pref.getFloat("cfg_v3", 0.0f);
        cfg_values[2] = temp;
        tv1 = findViewById(R.id.editText3);
        tv1.setText(Float.toString(temp));
        temp = pref.getFloat("cfg_v4", 0.0f);
        cfg_values[3] = temp;
        tv1 = findViewById(R.id.editText4);
        tv1.setText(Float.toString(temp));
        temp = pref.getFloat("cfg_v5", 0.0f);
        cfg_values[4] = temp;
        tv1 = findViewById(R.id.editText5);
        tv1.setText(Float.toString(temp));
        temp = pref.getFloat("cfg_v6", 0.0f);
        cfg_values[5] = temp;
        tv1 = findViewById(R.id.editText6);
        tv1.setText(Float.toString(temp));
        temp = pref.getFloat("cfg_v7", 0.0f);
        cfg_values[6] = temp;
        tv1 = findViewById(R.id.editText7);
        tv1.setText(Float.toString(temp));
        temp = pref.getFloat("cfg_v8", 0.0f);
        cfg_values[7] = temp;
        tv1 = findViewById(R.id.editText8);
        tv1.setText(Float.toString(temp));
        temp = pref.getFloat("cfg_v9", 0.0f);
        cfg_values[8] = temp;
        tv1 = findViewById(R.id.editText9);
        tv1.setText(Float.toString(temp));
        temp = pref.getFloat("cfg_v10", 0.0f);
        cfg_values[9] = temp;
        tv1 = findViewById(R.id.editText10);
        tv1.setText(Float.toString(temp));
        temp = pref.getFloat("cfg_v11", 0.0f);
        cfg_values[10] = temp;
        tv1 = findViewById(R.id.editText11);
        tv1.setText(Float.toString(temp));
        temp = pref.getFloat("cfg_v12", 0.0f);
        cfg_values[11] = temp;
        tv1 = findViewById(R.id.editText12);
        tv1.setText(Float.toString(temp));
        temp = pref.getFloat("cfg_v13", 0.0f);
        cfg_values[12] = temp;
        tv1 = findViewById(R.id.editText13);
        tv1.setText(Float.toString(temp));
        temp = pref.getFloat("cfg_v14", 0.0f);
        cfg_values[13] = temp;
        tv1 = findViewById(R.id.editText14);
        tv1.setText(Float.toString(temp));
        temp = pref.getFloat("cfg_v15", 0.0f);
        cfg_values[14] = temp;
        tv1 = findViewById(R.id.editText15);
        tv1.setText(Float.toString(temp));
        temp = pref.getFloat("cfg_v16", 0.0f);
        cfg_values[15] = temp;
        tv1 = findViewById(R.id.editText16);
        tv1.setText(Float.toString(temp));
        temp = pref.getFloat("cfg_v17", 0.0f);
        cfg_values[16] = temp;
        tv1 = findViewById(R.id.editText17);
        tv1.setText(Float.toString(temp));
        temp = pref.getFloat("cfg_v18", 0.0f);
        cfg_values[17] = temp;
        tv1 = findViewById(R.id.editText18);
        tv1.setText(Float.toString(temp));
    }

    public void WriteConfgPcktPref(float sc){
        SharedPreferences pref = getApplicationContext().getSharedPreferences("ConfgPcktPref", 0); // 0 - for private mode
        SharedPreferences.Editor editor = pref.edit();

        EditText et = (EditText) findViewById(R.id.editText1);
        float number = Float.valueOf(et.getText().toString())*sc;
        editor.putFloat("cfg_v1", number);
        et = (EditText) findViewById(R.id.editText2);
        number = Float.valueOf(et.getText().toString())*sc;
        editor.putFloat("cfg_v2", number);
        et = (EditText) findViewById(R.id.editText3);
        number = Float.valueOf(et.getText().toString())*sc;
        editor.putFloat("cfg_v3", number);
        et = (EditText) findViewById(R.id.editText4);
        number = Float.valueOf(et.getText().toString())*sc;
        editor.putFloat("cfg_v4", number);
        et = (EditText) findViewById(R.id.editText5);
        number = Float.valueOf(et.getText().toString())*sc;
        editor.putFloat("cfg_v5", number);
        et = (EditText) findViewById(R.id.editText6);
        number = Float.valueOf(et.getText().toString())*sc;
        editor.putFloat("cfg_v6", number);
        et = (EditText) findViewById(R.id.editText7);
        number = Float.valueOf(et.getText().toString())*sc;
        editor.putFloat("cfg_v7", number);
        et = (EditText) findViewById(R.id.editText8);
        number = Float.valueOf(et.getText().toString())*sc;
        editor.putFloat("cfg_v8", number);
        et = (EditText) findViewById(R.id.editText9);
        number = Float.valueOf(et.getText().toString())*sc;
        editor.putFloat("cfg_v9", number);
        et = (EditText) findViewById(R.id.editText10);
        number = Float.valueOf(et.getText().toString())*sc;
        editor.putFloat("cfg_v10", number);
        et = (EditText) findViewById(R.id.editText11);
        number = Float.valueOf(et.getText().toString())*sc;
        editor.putFloat("cfg_v11", number);
        et = (EditText) findViewById(R.id.editText12);
        number = Float.valueOf(et.getText().toString())*sc;
        editor.putFloat("cfg_v12", number);
        et = (EditText) findViewById(R.id.editText13);
        number = Float.valueOf(et.getText().toString())*sc;
        editor.putFloat("cfg_v13", number);
        et = (EditText) findViewById(R.id.editText14);
        number = Float.valueOf(et.getText().toString())*sc;
        editor.putFloat("cfg_v14", number);
        et = (EditText) findViewById(R.id.editText15);
        number = Float.valueOf(et.getText().toString())*sc;
        editor.putFloat("cfg_v15", number);
        et = (EditText) findViewById(R.id.editText16);
        number = Float.valueOf(et.getText().toString())*sc;
        editor.putFloat("cfg_v16", number);
        et = (EditText) findViewById(R.id.editText17);
        number = Float.valueOf(et.getText().toString())*sc;
        editor.putFloat("cfg_v17", number);
        et = (EditText) findViewById(R.id.editText18);
        number = Float.valueOf(et.getText().toString())*sc;
        editor.putFloat("cfg_v18", number);

        editor.commit();
    }

    public void UpdateCfg(View view) {
        WriteConfgPcktPref(1);
        ReadConfgPcktPref();
    }

    public void ClearCfg(View view) {
        new AlertDialog.Builder(this)
                .setTitle("Configuration Packet")
                .setMessage("Clear all the values?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        WriteConfgPcktPref(0);  //Multiply all values by zero
                        ReadConfgPcktPref();
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }
}

