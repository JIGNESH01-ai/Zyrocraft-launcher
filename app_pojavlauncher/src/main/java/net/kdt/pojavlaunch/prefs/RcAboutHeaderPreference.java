package net.kdt.pojavlaunch.prefs;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import net.kdt.pojavlaunch.R;

/** First row of the About tab: the launcher logo and its name. */
public class RcAboutHeaderPreference extends Preference {

    public RcAboutHeaderPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public RcAboutHeaderPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public RcAboutHeaderPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    @SuppressWarnings("unused") public RcAboutHeaderPreference(@NonNull Context context) {
        super(context);
        init();
    }

    private void init() {
        setLayoutResource(R.layout.rc_preference_about_header);
        setSelectable(false);
        setPersistent(false);
    }
}
