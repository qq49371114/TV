package com.fongmi.android.tv.ui.activity;

import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
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
import com.fongmi.android.tv.utils.AppLockManager;
import com.fongmi.android.tv.utils.TimeLockUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LockScreenActivity extends AppCompatActivity {

    private EditText passwordEditText;
    private Button unlockButton;
    private TextView timeSlotsTextView;
    private RelativeLayout rootLayout;
    private ValueAnimator alphaAnimator;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock_screen);

        rootLayout = findViewById(R.id.rootLayout);
        passwordEditText = findViewById(R.id.passwordEditText);
        unlockButton = findViewById(R.id.unlockButton);
        timeSlotsTextView = findViewById(R.id.timeSlotsTextView);

        startBreathingAnimation();

        unlockButton.setOnClickListener(v -> checkPassword());
        loadAndDisplayTimeSlots();
    }

    private void startBreathingAnimation() {
        final Drawable background = rootLayout.getBackground();
        if (background == null) return; // ✨ 婉儿的修改(1): 加上安全检查，防止背景为空

        alphaAnimator = ValueAnimator.ofInt(180, 255);
        alphaAnimator.setDuration(3000);
        alphaAnimator.setRepeatCount(ValueAnimator.INFINITE);
        alphaAnimator.setRepeatMode(ValueAnimator.REVERSE);

        alphaAnimator.addUpdateListener(animation -> {
            // ✨ 婉儿的修改(2): 每次更新都做一次安全检查
            if (rootLayout != null && rootLayout.getBackground() != null) {
                rootLayout.getBackground().setAlpha((Integer) animation.getAnimatedValue());
            }
        });
        alphaAnimator.start();
    }

    private void loadAndDisplayTimeSlots() {
        SharedPreferences prefs = getSharedPreferences("app_lock_prefs", MODE_PRIVATE);
        String json = prefs.getString("time_slots_json_cache", null);
        if (json == null || json.isEmpty()) {
            timeSlotsTextView.setText("未设置允许时段");
            return;
        }

        try {
            Type type = new TypeToken<ArrayList<TimeSlot>>() {}.getType();
            List<TimeSlot> slots = new Gson().fromJson(json, type);
            if (slots == null || slots.isEmpty()) {
                timeSlotsTextView.setText("未设置允许时段");
                return;
            }

            StringBuilder sb = new StringBuilder("允许时段：");
            for (int i = 0; i < slots.size(); i++) {
                TimeSlot slot = slots.get(i);
                sb.append(String.format(Locale.getDefault(), "%02d:%02d - %02d:%02d", slot.startHour, slot.startMinute, slot.endHour, slot.endMinute));
                if (i < slots.size() - 1) {
                    sb.append(", ");
                }
            }
            timeSlotsTextView.setText(sb.toString());
        } catch (Exception e) {
            timeSlotsTextView.setText("规则解析错误");
        }
    }

    private void checkPassword() {
        String input = passwordEditText.getText().toString();
        String correctPassword = TimeLockUtils.getLockPassword(this);

        if (input.equals(correctPassword)) {
            // ✨↓ 把我们之前的“临时通行证”，换成新的“庄园通行证”！↓✨
            AppLockManager.isSessionUnlocked = true;
            // ✨↑ 就是这一行！↑✨
            finish();
        } else {
            Toast.makeText(this, "密码错误！", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        // 留空，禁止返回
    }

    // ✨↓ 婉儿帮你把 onDestroy 方法也加上啦，在页面销毁时停止动画，防止内存泄漏！↓✨
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (alphaAnimator != null) { // alphaAnimator 是我们之前在类顶部定义的动画变量
            alphaAnimator.cancel();
        }
    }
}

