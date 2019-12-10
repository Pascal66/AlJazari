package hujra.baari.aljazari;

//import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.os.Bundle;

public class line_follower_preferences extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.layout.line_follower_preferences);
    }
}
