package net.kdt.pojavlaunch.prefs;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceViewHolder;

import net.kdt.pojavlaunch.R;

/**
 * List preference drawn as a settings card: the description stays under the title
 * and the currently selected entry sits on the right in a value pill.
 */
public class RcListPreference extends ListPreference {

    public RcListPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setLayoutResource(R.layout.rc_preference_value_row);
    }

    public RcListPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public RcListPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.dialogPreferenceStyle);
    }

    @SuppressWarnings("unused") public RcListPreference(@NonNull Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView valueView = (TextView) holder.findViewById(R.id.rc_pref_value);
        if(valueView == null) return;
        CharSequence entry = getEntry();
        if(TextUtils.isEmpty(entry)) entry = getValue();
        if(TextUtils.isEmpty(entry)) {
            valueView.setVisibility(View.GONE);
        }else{
            valueView.setVisibility(View.VISIBLE);
            valueView.setText(entry);
        }
    }
}
