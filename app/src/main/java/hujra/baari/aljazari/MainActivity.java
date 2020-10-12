package hujra.baari.aljazari;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Example of a call to a native method
        //TextView tv = findViewById(R.id.sample_text);
        //tv.setText(stringFromJNI());
    }

    public void startNightVisionActivity(View view) {
        Intent intent = new Intent(this, NightVisionActivity.class);
        startActivity(intent);
    }
    public void startSingleJoystickController(View view) {
        Intent intent = new Intent(this, SingleJoystickControllerActivity.class);
        startActivity(intent);
    }


    public void startDualJoystickController(View view) {
        Intent intent = new Intent(this, DualJoystickControllerActivity.class);
        startActivity(intent);
    }

    public void startVisionBasedController(View view) {
        Intent intent = new Intent(this, VisionBasedControllerActivity.class);
        startActivity(intent);
    }

    public void startConfigPacket(View view) {
        Intent intent = new Intent(this, ConfigPacketActivity.class);
        startActivity(intent);
    }
//    public native String stringFromJNI();
}
