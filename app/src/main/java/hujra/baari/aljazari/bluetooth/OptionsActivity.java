package hujra.baari.aljazari.bluetooth;

import android.preference.PreferenceActivity;
import android.os.Bundle;

import hujra.baari.aljazari.R;

public class OptionsActivity extends PreferenceActivity {

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.layout.preferences);
    }

}
