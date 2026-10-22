package net.kdt.pojavlaunch.prefs;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreferenceCompat;

import net.kdt.pojavlaunch.R;

/**
 * Switch preference drawn as a settings card with a pill switch on the right.
 * The pill shows a check when on and a cross when off; the whole card toggles it.
 */
public class RcSwitchPreference extends SwitchPreferenceCompat {

    public RcSwitchPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public RcSwitchPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public RcSwitchPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RcSwitchPreference(@NonNull Context context) {
        super(context);
        init();
    }

    private void init() {
        setLayoutResource(R.layout.rc_preference_switch);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        SwitchCompat switchWidget = (SwitchCompat) holder.findViewById(R.id.rc_pref_switch);
        if(switchWidget == null) return;
        // The pill is decorative: the card handles the click, so the widget only mirrors the state
        switchWidget.setOnCheckedChangeListener(null);
        switchWidget.setChecked(isChecked());
        switchWidget.jumpDrawablesToCurrentState();
    }
}
