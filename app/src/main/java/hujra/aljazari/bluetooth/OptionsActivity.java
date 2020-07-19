package hujra.aljazari.bluetooth;

import android.preference.PreferenceActivity;
import android.os.Bundle;

import hujra.aljazari.R;

public class OptionsActivity extends PreferenceActivity {

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.layout.preferences);
    }

}
