package net.kdt.pojavlaunch.prefs;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import net.kdt.pojavlaunch.R;

/**
 * Preference drawn as a settings card whose right side is a short accent label,
 * the way the reference settings screens show "Open" or "Reset".
 */
public class ZyroActionPreference extends Preference {
    private CharSequence mActionText;

    public ZyroActionPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs, defStyleAttr, defStyleRes);
    }

    public ZyroActionPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ZyroActionPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.preferenceStyle);
    }

    @SuppressWarnings("unused") public ZyroActionPreference(@NonNull Context context) {
        this(context, null);
    }

    private void init(AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        setLayoutResource(R.layout.zyro_preference_action_row);
        if(attrs == null) return;
        try(android.content.res.TypedArray array = getContext().obtainStyledAttributes(
                attrs, R.styleable.ZyroActionPreference, defStyleAttr, defStyleRes)) {
            mActionText = array.getText(R.styleable.ZyroActionPreference_zyroActionText);
        }
    }

    /** Sets the accent label on the right side of the card */
    public void setActionText(CharSequence actionText) {
        mActionText = actionText;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView actionView = (TextView) holder.findViewById(R.id.zyro_pref_action);
        if(actionView == null) return;
        if(mActionText == null || mActionText.length() == 0) {
            actionView.setVisibility(View.GONE);
        }else{
            actionView.setVisibility(View.VISIBLE);
            actionView.setText(mActionText);
        }
    }
}
