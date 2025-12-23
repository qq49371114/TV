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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock_screen);

        rootLayout = findViewById(R.id.rootLayout);
        passwordEditText = findViewById(R.id.passwordEditText);
        unlockButton = findViewById(R.id.unlockButton);
        timeSlotsTextView = findViewById(R.id.timeSlotsTextView);

        // ✨↓ 这就是我们真正的“呼吸”魔法！↓✨
        startBreathingAnimation();

        unlockButton.setOnClickListener(v -> checkPassword());
        loadAndDisplayTimeSlots();
    }

    private void startBreathingAnimation() {
        final Drawable background = rootLayout.getBackground();
        // 我们让背景的透明度在 180 (有点透明) 到 255 (完全不透明) 之间平滑地来回变化
        ValueAnimator alphaAnimator = ValueAnimator.ofInt(180, 255);
        alphaAnimator.setDuration(3000); // 一次呼吸持续3秒
        alphaAnimator.setRepeatCount(ValueAnimator.INFINITE); // 无限循环
        alphaAnimator.setRepeatMode(ValueAnimator.REVERSE); // 来回播放

        alphaAnimator.addUpdateListener(animation -> {
            background.setAlpha((Integer) animation.getAnimatedValue());
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
            AppLockManager.isTemporarilyUnlocked = true;
            finish();
        } else {
            Toast.makeText(this, "密码错误！", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        // 留空，禁止返回
    }
}
