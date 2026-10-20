package net.kdt.pojavlaunch.prefs.screens;

import android.os.Bundle;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.prefs.CustomSeekBarPreference;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

/** Gamepad tab: controller mapping and the controller deadzone. */
public class ZyroPreferenceGamepadFragment extends LauncherPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_gamepad);

        CustomSeekBarPreference deadzoneSeek = requirePreference("gamepad_deadzone_scale",
                CustomSeekBarPreference.class);
        deadzoneSeek.setValue((int) (LauncherPreferences.PREF_DEADZONE_SCALE * 100f));
        deadzoneSeek.setSuffix(" %");
    }
}
