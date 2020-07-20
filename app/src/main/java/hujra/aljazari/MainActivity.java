package hujra.aljazari;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import hujra.aljazari.SingleJoystickController.SingleJoystickControllerActivity;

public class MainActivity extends AppCompatActivity {
    private RadioGroup radioCtrlGroup;
    private RadioButton radioCtrlType;

    private TCPclient mTcpClient;

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        radioCtrlGroup=(RadioGroup)findViewById(R.id.radioGroup);
    }

    public void startManualController(View view) {
        Intent intent;
        int selectedId=radioCtrlGroup.getCheckedRadioButtonId();
        radioCtrlType=(RadioButton)findViewById(selectedId);
        switch (radioCtrlGroup.getCheckedRadioButtonId()) {
            case R.id.rb_single_joystick:
                Toast.makeText(MainActivity.this,"single joystick",Toast.LENGTH_SHORT).show();
                intent = new Intent(this, SingleJoystickControllerActivity.class);
                startActivity(intent);
                break;
            case R.id.rb_double_joystick:
                Toast.makeText(MainActivity.this,"double joystick",Toast.LENGTH_SHORT).show();
                intent = new Intent(this, DualJoystickControllerActivity.class);
                startActivity(intent);
                break;
        }
    }

    public void AboutButton(View view) {
        String msg = "AlJazari\nhttps://sites.google.com/view/4mbilal/";
        final SpannableString s = new SpannableString(msg); // msg should have url to enable clicking
        Linkify.addLinks(s, Linkify.ALL);

        AlertDialog about = new AlertDialog.Builder(this).create();
        about.setCancelable(false);
        about.setMessage(s);
        about.setButton(DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
            }
        });
        about.show();
        ((TextView)about.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
    }

    //Native methods definitions
    public native String stringFromJNI();
}
