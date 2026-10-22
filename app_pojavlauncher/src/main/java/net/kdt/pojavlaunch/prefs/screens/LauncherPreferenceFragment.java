package net.kdt.pojavlaunch.prefs.screens;


import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

/**
 * Base class of every settings page. Each page loads one preference screen and is
 * hosted inside {@link RcSettingsFragment}, which draws the pill tab bar.
 */
public class LauncherPreferenceFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    protected Runnable mVisibilityUpdater = () -> {};

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // The settings pages sit on the launcher background, so the list stays transparent
        getListView().setBackgroundColor(Color.TRANSPARENT);
        int padding = getResources().getDimensionPixelSize(R.dimen.padding_medium) * 2;
        getListView().setPadding(padding, 0, padding, padding);
        getListView().setClipToPadding(false);
    }

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        mVisibilityUpdater = this::updateVisibility;
    }

    protected void updateVisibility() {
    }

    protected void setupNotificationRequestPreference() {
        Preference requestNotificationPermission = findPreference("notification_permission_request");
        if(requestNotificationPermission == null) return;
        Activity activity = getActivity();
        if(activity instanceof LauncherActivity) {
            requestNotificationPermission.setOnPreferenceClickListener(preference -> {
                ((LauncherActivity) activity).askForPermission(33, Manifest.permission.POST_NOTIFICATIONS);
                return true;
            });
        }else{
            requestNotificationPermission.setVisible(false);
        }
        updateNotificationVisibility();
    }

    protected void updateNotificationVisibility() {
        Activity activity = getActivity();
        Preference requestNotificationPermission = findPreference("notification_permission_request");
        if(requestNotificationPermission == null || !(activity instanceof LauncherActivity)) return;
        requestNotificationPermission.setVisible(
                !((LauncherActivity) activity).checkForPermission(33, Manifest.permission.POST_NOTIFICATIONS));
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if(sharedPreferences != null) sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        mVisibilityUpdater.run();
    }

    @Override
    public void onPause() {
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if(sharedPreferences != null) sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        LauncherPreferences.loadPreferences(getContext());
    }

    protected Preference requirePreference(CharSequence key) {
        Preference preference = findPreference(key);
        if(preference != null) return preference;
        throw new IllegalStateException("Preference "+key+" is null");
    }
    @SuppressWarnings("unchecked")
    protected <T extends Preference> T requirePreference(CharSequence key, Class<T> preferenceClass) {
        Preference preference = requirePreference(key);
        if(preferenceClass.isInstance(preference)) return (T)preference;
        throw new IllegalStateException("Preference "+key+" is not an instance of "+preferenceClass.getSimpleName());
    }
    protected LauncherActivity getLauncherActivity(){
        return ((LauncherActivity) getActivity());
    }
}
