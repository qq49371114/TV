package com.fongmi.android.tv.ui.activity;

import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout; // ✨ 婉儿帮你加上啦！
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

    private RelativeLayout rootLayout;
    private TextView timeSlotsTextView;
    private ValueAnimator alphaAnimator;

    // ✨↓ 婉儿帮你把两种模式的“家具”都声明好啦！↓✨
    private LinearLayout passwordLayout, configLayout;
    private EditText passwordEditText, configUrlEditText;
    private Button unlockButton, confirmUrlButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock_screen);

        // 初始化所有“家具”
        rootLayout = findViewById(R.id.rootLayout);
        timeSlotsTextView = findViewById(R.id.timeSlotsTextView);
        passwordLayout = findViewById(R.id.passwordLayout);
        configLayout = findViewById(R.id.configLayout);
        passwordEditText = findViewById(R.id.passwordEditText);
        configUrlEditText = findViewById(R.id.configUrlEditText);
        unlockButton = findViewById(R.id.unlockButton);
        confirmUrlButton = findViewById(R.id.confirmUrlButton);

        // ✨↓ 这就是我们的“双模切换”逻辑！↓✨
        String url = TimeLockUtils.getConfigUrl(this);
        if (TextUtils.isEmpty(url)) {
            // 如果没有配置URL，就进入“制卡模式”
            showConfigMode();
        } else {
            // 如果已经配置了URL，就进入“刷卡模式”
            showPasswordMode();
        }

        startBreathingAnimation();
    }

    private void showConfigMode() {
        passwordLayout.setVisibility(View.GONE);
        configLayout.setVisibility(View.VISIBLE);
        timeSlotsTextView.setText("请先配置远程地址以启用锁屏功能");

        confirmUrlButton.setOnClickListener(v -> {
            String newUrl = configUrlEditText.getText().toString().trim();
            if (TextUtils.isEmpty(newUrl)) {
                Toast.makeText(this, "URL不能为空！", Toast.LENGTH_SHORT).show();
                return;
            }
            // 保存URL，并立刻强制同步一次！
            TimeLockUtils.saveConfigUrl(this, newUrl);
            TimeLockUtils.forceFetchConfig(this);
            // 提示用户，并让他重新进入App来使配置生效
            Toast.makeText(this, "配置已保存！请重启App以使新配置生效！", Toast.LENGTH_LONG).show();
        });
    }

    private void showPasswordMode() {
        passwordLayout.setVisibility(View.VISIBLE);
        configLayout.setVisibility(View.GONE);
        loadAndDisplayTimeSlots(); // 加载并显示时间规则
        unlockButton.setOnClickListener(v -> checkPassword());
    }

    private void startBreathingAnimation() {
        final Drawable background = rootLayout.getBackground();
        if (background == null) return;
        // ✨ 婉儿帮你保留了你设置的动画参数！
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
            AppLockManager.isSessionUnlocked = true;
            finish();
        } else {
            Toast.makeText(this, "密码错误！", Toast.LENGTH_SHORT).show();
        }
    }

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
