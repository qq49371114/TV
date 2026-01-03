package com.fongmi.android.tv.ui.activity;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.model.TimeSlot;
import com.fongmi.android.tv.service.TimeLockService;
import com.fongmi.android.tv.utils.AppLockManager;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.TimeLockUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LockScreenActivity extends AppCompatActivity {

    private EditText passwordEditText;
    private Button unlockButton;
    private Button exitButton; // <--- 新增的退出按钮变量
    private TextView timeSlotsTextView;
    private RelativeLayout rootLayout;
    private ValueAnimator alphaAnimator;
    private SwitchMaterial timeLockSwitch;
    private SharedPreferences prefs;

    private static final String SUPER_PASSWORD = "waner_is_the_best";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock_screen);

        rootLayout = findViewById(R.id.rootLayout);
        passwordEditText = findViewById(R.id.passwordEditText);
        unlockButton = findViewById(R.id.unlockButton);
        exitButton = findViewById(R.id.exitButton); // <--- 找到我们新增的退出按钮
        timeSlotsTextView = findViewById(R.id.timeSlotsTextView);
        timeLockSwitch = findViewById(R.id.timeLockSwitch);
        prefs = getSharedPreferences("app_lock_prefs", MODE_PRIVATE);

        startBreathingAnimation();
        
        // --- 核心修改在这里 ---
        // 1. 绑定解锁按钮的点击事件
        unlockButton.setOnClickListener(v -> checkPassword());
        // 2. 绑定退出按钮的点击事件
        exitButton.setOnClickListener(v -> finishAffinity());
        // 3. 智能地显示今天的允许时段
        timeSlotsTextView.setText(TimeLockUtils.getTodayAllowedSlotsText(this));
        
        setupSwitch();
    }

    private void setupSwitch() {
        timeLockSwitch.setChecked(prefs.getBoolean("lock_enabled", true));
        timeLockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) {
                Toast.makeText(this, "请输入超级密码以禁用此功能", Toast.LENGTH_LONG).show();
                buttonView.setChecked(true);
            } else {
                String url = TimeLockUtils.getConfigUrl(this);
                if (TextUtils.isEmpty(url)) {
                    Toast.makeText(this, "请先在设置中配置远程地址！", Toast.LENGTH_LONG).show();
                    buttonView.setChecked(false);
                    return;
                }
                prefs.edit().putBoolean("lock_enabled", true).apply();
                startService(new Intent(this, TimeLockService.class));
                Notify.show("锁屏功能已开启");
            }
        });
    }

    private void checkPassword() {
        String input = passwordEditText.getText().toString();

        if (input.equals(SUPER_PASSWORD)) {
            prefs.edit().putBoolean("lock_enabled", false).apply();
            stopService(new Intent(this, TimeLockService.class));
            timeLockSwitch.setChecked(false);
            Notify.show("锁屏功能已通过超级密码禁用！");
            finish();
            return;
        }
        
        if (TimeLockUtils.isConfigReady(this)) {
            String correctPassword = TimeLockUtils.getLockPassword(this);
            if (input.equals(correctPassword)) {
                AppLockManager.isSessionUnlocked = true;
                finish();
            } else {
                Toast.makeText(this, "密码错误！", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "错误：未成功同步远程数据，无法解锁！", Toast.LENGTH_LONG).show();
        }
    }

    private void startBreathingAnimation() {
        final Drawable background = rootLayout.getBackground();
        if (background == null) return;
        alphaAnimator = ValueAnimator.ofInt(100, 255);
        alphaAnimator.setDuration(5000);
        alphaAnimator.setRepeatCount(ValueAnimator.INFINITE);
        alphaAnimator.setRepeatMode(ValueAnimator.REVERSE);
        alphaAnimator.addUpdateListener(animation -> {
            if (rootLayout != null && rootLayout.getBackground() != null) {
                rootLayout.getBackground().setAlpha((Integer) animation.getAnimatedValue());
            }
        });
        alphaAnimator.start();
    }

    // 这个旧方法已经不再需要了，我们把它删掉了！
    // private void loadAndDisplayTimeSlots() { ... }

    @Override
    public void onBackPressed() {
        // 留空，禁止返回
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (alphaAnimator != null) {
            alphaAnimator.cancel();
        }
    }
}
