package net.kdt.pojavlaunch.prefs.screens;

import android.os.Bundle;

import androidx.preference.Preference;

import net.kdt.pojavlaunch.BuildConfig;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

/** About screen: version information and project links. */
public class LauncherPreferenceAboutFragment extends LauncherPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_about);

        Preference version = findPreference("about_version");
        if (version != null) {
            version.setSummary(getString(R.string.zyro_about_text, BuildConfig.VERSION_NAME));
        }
        Preference source = findPreference("about_source");
        if (source != null) {
            source.setOnPreferenceClickListener(p -> {
                Tools.openURL(requireActivity(), "https://github.com/JIGNESH01-ai/Zyrocraft-launcher");
                return true;
            });
        }
    }
}
