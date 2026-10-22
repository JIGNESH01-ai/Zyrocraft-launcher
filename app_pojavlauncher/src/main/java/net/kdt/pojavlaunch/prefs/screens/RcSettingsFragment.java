package net.kdt.pojavlaunch.prefs.screens;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;

/**
 * Settings host: a single scrollable row of pill tabs on top and the matching
 * preference page below it. Every tab opens a preference screen that already
 * exists in the launcher, nothing here is a placeholder.
 */
public class RcSettingsFragment extends Fragment {
    private static final String STATE_TAB = "rc_settings_tab";
    private static final String PAGE_TAG = "rc_settings_page";

    private static final int TAB_RENDERER = 0;
    private static final int TAB_GAME = 1;
    private static final int TAB_JAVA = 2;
    private static final int TAB_LAUNCHER = 3;
    private static final int TAB_CONTROLS = 4;
    private static final int TAB_GAMEPAD = 5;
    private static final int TAB_EXPERIMENTAL = 6;
    private static final int TAB_ABOUT = 7;

    private static final int[] TAB_IDS = {
            R.id.stab_renderer,
            R.id.stab_game,
            R.id.stab_java,
            R.id.stab_launcher,
            R.id.stab_controls,
            R.id.stab_gamepad,
            R.id.stab_experimental,
            R.id.stab_about
    };

    private int mSelectedTab = TAB_RENDERER;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null) mSelectedTab = savedInstanceState.getInt(STATE_TAB, TAB_RENDERER);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rc_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        for(int i = 0; i < TAB_IDS.length; i++) {
            final int index = i;
            view.findViewById(TAB_IDS[i]).setOnClickListener(v -> selectTab(index));
        }
        refreshTabSelection();
        if(getChildFragmentManager().findFragmentById(R.id.rc_settings_container) == null) {
            showPage(mSelectedTab);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, mSelectedTab);
    }

    private void selectTab(int index) {
        if(index == mSelectedTab && getChildFragmentManager().findFragmentById(R.id.rc_settings_container) != null) {
            refreshTabSelection();
            return;
        }
        mSelectedTab = index;
        refreshTabSelection();
        showPage(index);
    }

    private void refreshTabSelection() {
        View view = getView();
        if(view == null) return;
        for(int i = 0; i < TAB_IDS.length; i++) {
            view.findViewById(TAB_IDS[i]).setSelected(i == mSelectedTab);
        }
    }

    private void showPage(int index) {
        getChildFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.rc_settings_container, createPage(index), PAGE_TAG)
                .commit();
    }

    private Fragment createPage(int index) {
        switch(index) {
            case TAB_GAME:
                return new RcPreferenceGameFragment();
            case TAB_JAVA:
                return new LauncherPreferenceJavaFragment();
            case TAB_LAUNCHER:
                return new RcPreferenceLauncherFragment();
            case TAB_CONTROLS:
                return new LauncherPreferenceControlFragment();
            case TAB_GAMEPAD:
                return new RcPreferenceGamepadFragment();
            case TAB_EXPERIMENTAL:
                return new LauncherPreferenceExperimentalFragment();
            case TAB_ABOUT:
                return new LauncherPreferenceAboutFragment();
            case TAB_RENDERER:
            default:
                return new LauncherPreferenceVideoFragment();
        }
    }
}
