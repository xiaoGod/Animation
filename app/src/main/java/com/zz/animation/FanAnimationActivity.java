package com.zz.animation;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class FanAnimationActivity extends Activity {
    private AirFlowView airFlowView;
    private SeekBar levelSeek;
    private TextView levelLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF101418);

        airFlowView = new AirFlowView(this);
        root.addView(airFlowView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        ScrollView scrollView = new ScrollView(this);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(24, 16, 24, 24);
        scrollView.addView(controls);

        addTitle(controls, "Android View Air Flow Demo");
        levelSeek = addSeek(controls, "风量", 1, 7, 4, new SeekValueListener() {
            @Override
            public void onValueChanged(int value) {
                airFlowView.setLevel(value);
            }
        });
        levelLabel = (TextView) levelSeek.getTag();
        addSeek(controls, "透明度", 20, 100, 72, new SeekValueListener() {
            @Override
            public void onValueChanged(int value) {
                airFlowView.setOpacity(value / 100f);
            }
        });
        addSeek(controls, "扩散宽度", 55, 150, 100, new SeekValueListener() {
            @Override
            public void onValueChanged(int value) {
                airFlowView.setSpread(value / 100f);
            }
        });
        addSeek(controls, "速度", 35, 180, 100, new SeekValueListener() {
            @Override
            public void onValueChanged(int value) {
                airFlowView.setSpeed(value / 100f);
            }
        });
        addSeek(controls, "蓝白强度", 35, 125, 82, new SeekValueListener() {
            @Override
            public void onValueChanged(int value) {
                airFlowView.setCoolness(value / 100f);
            }
        });
        addSeek(controls, "Flow 强度", 30, 160, 85, new SeekValueListener() {
            @Override
            public void onValueChanged(int value) {
                airFlowView.setFlowAlpha(value / 100f);
            }
        });
        addSeek(controls, "Flow 宽度", 50, 180, 100, new SeekValueListener() {
            @Override
            public void onValueChanged(int value) {
                airFlowView.setFlowWidth(value / 100f);
            }
        });
        addSeek(controls, "Flow 长度", 60, 180, 100, new SeekValueListener() {
            @Override
            public void onValueChanged(int value) {
                airFlowView.setFlowLength(value / 100f);
            }
        });
        addSeek(controls, "Flow 数量", 1, 8, 5, new SeekValueListener() {
            @Override
            public void onValueChanged(int value) {
                airFlowView.setFlowCount(value);
            }
        });
        addSeek(controls, "Flow 速度", 40, 220, 100, new SeekValueListener() {
            @Override
            public void onValueChanged(int value) {
                airFlowView.setFlowSpeed(value / 100f);
            }
        });

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setGravity(Gravity.CENTER_VERTICAL);
        Button minButton = new Button(this);
        minButton.setText("最小风量");
        Button maxButton = new Button(this);
        maxButton.setText("最大风量");
        minButton.setOnClickListener(v -> updateLevel(1));
        maxButton.setOnClickListener(v -> updateLevel(7));
        buttonRow.addView(minButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        buttonRow.addView(maxButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        controls.addView(buttonRow);

        CheckBox backgroundCheck = new CheckBox(this);
        backgroundCheck.setText("黑色背景");
        backgroundCheck.setTextColor(0xFFEAF6FF);
        backgroundCheck.setChecked(true);
        backgroundCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                airFlowView.setBlackBackground(isChecked);
            }
        });
        controls.addView(backgroundCheck);

        CheckBox bodyCheck = addCheck(controls, "Body 主体", true);
        CheckBox flowCheck = addCheck(controls, "Flow 流纹", true);
        bodyCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                airFlowView.setBodyVisible(isChecked);
            }
        });
        flowCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                airFlowView.setFlowVisible(isChecked);
            }
        });

        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(260)
        ));

        setContentView(root);
    }

    private void addTitle(LinearLayout parent, String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(0xFFEAF6FF);
        title.setTextSize(18f);
        parent.addView(title);
    }

    private SeekBar addSeek(LinearLayout parent, String label, int min, int max, int value, SeekValueListener listener) {
        TextView textView = new TextView(this);
        textView.setText(label + ": " + value);
        textView.setTextColor(0xFFEAF6FF);
        parent.addView(textView);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max - min);
        seekBar.setProgress(value - min);
        seekBar.setTag(textView);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int next = min + progress;
                textView.setText(label + ": " + next);
                listener.onValueChanged(next);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        parent.addView(seekBar);
        return seekBar;
    }

    private void updateLevel(int level) {
        airFlowView.setLevel(level);
        levelSeek.setProgress(level - 1);
        levelLabel.setText("风量: " + level);
    }

    private CheckBox addCheck(LinearLayout parent, String label, boolean checked) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(label);
        checkBox.setTextColor(0xFFEAF6FF);
        checkBox.setChecked(checked);
        parent.addView(checkBox);
        return checkBox;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface SeekValueListener {
        void onValueChanged(int value);
    }
}
