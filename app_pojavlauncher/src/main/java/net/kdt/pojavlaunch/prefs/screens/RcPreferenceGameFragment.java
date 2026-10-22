package net.kdt.pojavlaunch.prefs.screens;

import android.os.Bundle;

import net.kdt.pojavlaunch.R;

/** Game tab: checks run before a game starts, and file verification settings. */
public class RcPreferenceGameFragment extends LauncherPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_game);
    }
}
