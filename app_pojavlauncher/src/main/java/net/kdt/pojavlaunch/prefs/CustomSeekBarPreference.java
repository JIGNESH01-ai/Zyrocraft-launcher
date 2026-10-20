package net.kdt.pojavlaunch.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SeekBarPreference;

import net.kdt.pojavlaunch.R;

public class CustomSeekBarPreference extends SeekBarPreference {

    /** The suffix displayed */
    private String mSuffix = "";
    /** Custom minimum value to provide the same behavior as the usual setMin */
    private int mMin;
    /** The textview associated by default to the preference */
    private TextView mTextView;
    /** Seekbar increment in case the max gets set */
    private final int mIncrement;


    @SuppressLint("PrivateResource")
    public CustomSeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setLayoutResource(R.layout.zyro_preference_seekbar);
        try (TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.SeekBarPreference, defStyleAttr, defStyleRes)) {
            mMin = a.getInt(R.styleable.SeekBarPreference_min, 0);
            mIncrement = a.getInt(R.styleable.SeekBarPreference_seekBarIncrement, 0);
        }
    }

    public CustomSeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public CustomSeekBarPreference(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.seekBarPreferenceStyle);
    }

    @SuppressWarnings("unused") public CustomSeekBarPreference(Context context) {
        this(context, null);
    }

    @Override
    public void setMin(int min) {
        //Note: since the max (setMax is a final function) is not taken into account properly, setting the min over the max may produce funky results
        super.setMin(min);
        if (min != mMin) mMin = min;
    }


    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder view) {
        super.onBindViewHolder(view);
        TextView titleTextView = (TextView) view.findViewById(android.R.id.title);
        titleTextView.setTextColor(Color.WHITE);

        mTextView = (TextView) view.findViewById(R.id.seekbar_value);
        mTextView.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        SeekBar seekBar = (SeekBar) view.findViewById(R.id.seekbar);

        View stepDown = view.findViewById(R.id.zyro_seek_step_down);
        View stepUp = view.findViewById(R.id.zyro_seek_step_up);
        if(stepDown != null && stepUp != null) {
            stepDown.setOnClickListener(v -> stepValue(seekBar, -1));
            stepUp.setOnClickListener(v -> stepValue(seekBar, 1));
        }

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progress += mMin;
                progress = progress / getSeekBarIncrement();
                progress = progress * getSeekBarIncrement();
                progress -= mMin;

                mTextView.setText(String.valueOf(progress + mMin));
                updateTextViewWithSuffix();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

                int progress = seekBar.getProgress() + mMin;
                progress /= getSeekBarIncrement();
                progress *= getSeekBarIncrement();
                progress -= mMin;

                setValue(progress + mMin);
                updateTextViewWithSuffix();
            }
        });

        updateTextViewWithSuffix();
    }

    /**
     * Set a suffix to be appended on the TextView associated to the value
     * @param suffix The suffix to append as a String
     */
    public void setSuffix(String suffix) {
        this.mSuffix = suffix;
    }

    /**
     * Convenience function to set both min and max at the same time.
     * @param min The minimum value
     * @param max The maximum value
     */
    public void setRange(int min, int max){
        setMin(min);
        setMaxKeepIncrement(max);
    }

    public void setMaxKeepIncrement(int max) {
        super.setMax(max);
        setSeekBarIncrement(mIncrement);
    }


    /** Moves the slider by one increment, the way the step arrows do */
    private void stepValue(SeekBar seekBar, int direction) {
        int increment = Math.max(1, getSeekBarIncrement());
        int progress = seekBar.getProgress() + direction * increment;
        progress = Math.max(0, Math.min(seekBar.getMax(), progress));
        if(progress == seekBar.getProgress()) return;
        seekBar.setProgress(progress);
        int value = progress + mMin;
        if(increment > 1) value = value / increment * increment;
        setValue(value);
    }

    private void updateTextViewWithSuffix(){
        if(mTextView == null) return;
        if(!mTextView.getText().toString().endsWith(mSuffix)){
            mTextView.setText(String.format("%s%s", mTextView.getText(), mSuffix));
        }
    }
}
