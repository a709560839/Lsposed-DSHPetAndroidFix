package com.jj.dshpet.lsposed.ui;

import android.os.Bundle;
import android.view.HapticFeedbackConstants;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.jj.dshpet.R;
import com.jj.dshpet.lsposed.config.ConfigManager;

/**
 * 模块主设置界面 (Material 3 风格)
 * <p>
 * 提供直观美观的卡片式开关控制，各修改项变更后即时生效。
 */
public class MainActivity extends AppCompatActivity {

    private MaterialSwitch mSwitchDoubleTap;
    private MaterialSwitch mSwitchLongPress;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        loadSettings();
        setupListeners();
    }

    private void initViews() {
        mSwitchDoubleTap = findViewById(R.id.switch_double_tap);
        mSwitchLongPress = findViewById(R.id.switch_long_press);
    }

    private void loadSettings() {
        mSwitchDoubleTap.setChecked(ConfigManager.isDoubleTapChatEnabled(this));
        mSwitchLongPress.setChecked(ConfigManager.isLongPressChatEnabled(this));
    }

    private void setupListeners() {
        // 双击悬浮聊天开关（同时包含常驻及渲染优化）
        mSwitchDoubleTap.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            ConfigManager.putBoolean(this, ConfigManager.KEY_DOUBLE_TAP_CHAT, isChecked);
        });
        findViewById(R.id.layout_double_tap).setOnClickListener(v -> mSwitchDoubleTap.toggle());

        // 长按直接打开聊天页面
        mSwitchLongPress.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            ConfigManager.putBoolean(this, ConfigManager.KEY_LONG_PRESS_CHAT, isChecked);
        });
        findViewById(R.id.layout_long_press).setOnClickListener(v -> mSwitchLongPress.toggle());
    }
}
