package com.jj.dshpet.lsposed.ui;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.jj.dshpet.R;
import com.jj.dshpet.lsposed.config.ConfigManager;
import com.jj.dshpet.lsposed.constant.Constants;

/**
 * 模块主设置界面 (Material 3 风格)
 * <p>
 * 提供直观美观的卡片式开关控制，各修改项变更后即时生效。
 */
public class MainActivity extends AppCompatActivity {

    private MaterialSwitch mSwitchDoubleTap;
    private MaterialSwitch mSwitchLongPress;
    private MaterialSwitch mSwitchHideIcon;
    private MaterialSwitch mSwitchHideOnCall;
    private MaterialSwitch mSwitchAutoMove;

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
        mSwitchHideIcon = findViewById(R.id.switch_hide_icon);
        mSwitchHideOnCall = findViewById(R.id.switch_hide_on_call);
        mSwitchAutoMove = findViewById(R.id.switch_auto_move);
    }

    private void loadSettings() {
        mSwitchDoubleTap.setChecked(ConfigManager.isDoubleTapChatEnabled(this));
        mSwitchLongPress.setChecked(ConfigManager.isLongPressChatEnabled(this));
        mSwitchHideIcon.setChecked(isLauncherIconHidden());
        mSwitchHideOnCall.setChecked(ConfigManager.isHideOnCallEnabled(this));
        mSwitchAutoMove.setChecked(ConfigManager.isDisableAutoMoveEnabled(this));
    }

    private void setupListeners() {
        // 双击悬浮聊天开关（同时包含常驻及渲染优化）
        mSwitchDoubleTap.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            ConfigManager.putBoolean(this, Constants.KEY_DOUBLE_TAP_CHAT, isChecked);
        });
        findViewById(R.id.layout_double_tap).setOnClickListener(v -> mSwitchDoubleTap.toggle());

        // 长按直接打开聊天页面
        mSwitchLongPress.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            ConfigManager.putBoolean(this, Constants.KEY_LONG_PRESS_CHAT, isChecked);
        });
        findViewById(R.id.layout_long_press).setOnClickListener(v -> mSwitchLongPress.toggle());

        // 隐藏桌面图标开关
        mSwitchHideIcon.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            hideOrShowLauncherIcon(isChecked);
        });
        findViewById(R.id.layout_hide_icon).setOnClickListener(v -> mSwitchHideIcon.toggle());

        // 通话时收起桌宠开关
        mSwitchHideOnCall.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            ConfigManager.putBoolean(this, Constants.KEY_HIDE_ON_CALL, isChecked);
        });
        findViewById(R.id.layout_hide_on_call).setOnClickListener(v -> mSwitchHideOnCall.toggle());

        // 禁止桌宠自动移动开关
        mSwitchAutoMove.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            ConfigManager.putBoolean(this, Constants.KEY_DISABLE_AUTO_MOVE, isChecked);
        });
        findViewById(R.id.layout_auto_move).setOnClickListener(v -> mSwitchAutoMove.toggle());
    }
    private void hideOrShowLauncherIcon(boolean isHide) {
        ComponentName componentName = new ComponentName(getPackageName(), getPackageName() + Constants.ALIAS_HOME);
        getPackageManager().setComponentEnabledSetting(
                componentName,
                isHide ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
        );
    }

    private boolean isLauncherIconHidden() {
        ComponentName componentName = new ComponentName(getPackageName(), getPackageName() + Constants.ALIAS_HOME);
        int state = getPackageManager().getComponentEnabledSetting(componentName);
        return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }
}