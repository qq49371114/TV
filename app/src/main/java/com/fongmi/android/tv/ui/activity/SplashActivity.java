package com.fongmi.android.tv.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SplashActivity extends AppCompatActivity {

    private TextView lockMessage;
    private EditText passwordInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        lockMessage = findViewById(R.id.lockMessage);
        passwordInput = findViewById(R.id.password);

        // 我们不再检查权限了！直接检查我们自己的锁！
        new Handler(Looper.getMainLooper()).postDelayed(this::checkLockState, 200);
    }

    // 【核心】全新的、统一的锁定状态检查逻辑！
    private void checkLockState() {
        // 终极关卡：家长模式检查
        if ("true".equals(SecurePrefs.getString("parent_mode_enabled", "false"))) {
            goToHome(); // 如果家长模式已开启，直接放行！
            return;
        }

        boolean isTimeLocked = isTimeLocked();
        String storedPassword = SecurePrefs.getString("app_password", "");

        // 核心逻辑：只要“时间到了”或者“设置了密码”，就需要显示输入界面
        if (isTimeLocked || !TextUtils.isEmpty(storedPassword)) {
            setupInputScreen(isTimeLocked, storedPassword);
        } else {
            goToHome(); // 没时间锁，也没密码，直接放行
        }
    }

    // 【核心】一个统一的、强大的输入界面设置方法！
    private void setupInputScreen(boolean isTimeLocked, String correctPassword) {
        // 永远显示密码框，等待主人的召唤！
        passwordInput.setVisibility(View.VISIBLE);

        if (isTimeLocked) {
            // 如果是时间锁定，就显示时间提示
            lockMessage.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder("休息时间到啦\n允许使用时间段:\n");
            for (RemoteControlServer.TimeSlot slot : SecurePrefs.getTimeSlots()) {
                sb.append(slot.start).append(" - ").append(slot.end).append("\n");
            }
            lockMessage.setText(sb.toString().trim());
            passwordInput.setHint("请输入超级密码解锁"); // 给出友好的提示
        } else {
            // 如果只是密码锁定，就隐藏时间提示
            lockMessage.setVisibility(View.GONE);
            passwordInput.setHint("请输入密码");
        }

        // 统一的密码检查监听器，处理所有情况
        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String input = v.getText().toString();
                
                // 1. 超级密码“婉儿最棒”拥有最高豁免权！
                if (input.equals("婉儿最棒")) {
                    SecurePrefs.put("parent_mode_enabled", "true"); // 开启永久的家长模式
                    Toast.makeText(this, "欢迎您，主人！家长模式已永久开启。", Toast.LENGTH_LONG).show();
                    goToHome();
                    return true;
                }
                
                // 2. 如果当前是时间锁定状态，并且输入的不是超级密码，则直接拒绝
                if (isTimeLocked) {
                    Toast.makeText(this, "当前为休息时间，请输入超级密码解锁", Toast.LENGTH_LONG).show();
                    v.setText("");
                    return true;
                }
                
                // 3. 如果不是时间锁定状态，就正常检查普通密码
                if (input.equals(correctPassword)) {
                    goToHome();
                } else {
                    Toast.makeText(this, "密码错误！", Toast.LENGTH_SHORT).show();
                    v.setText("");
                }
                return true;
            }
            return false;
        });
    }

    private boolean isTimeLocked() {
        List<RemoteControlServer.TimeSlot> slots = SecurePrefs.getTimeSlots();
        if (slots.isEmpty()) return false;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Date current = sdf.parse(sdf.format(new Date()));
            for (RemoteControlServer.TimeSlot slot : slots) {
                Date start = sdf.parse(slot.start);
                Date end = sdf.parse(slot.end);
                boolean isAllowed = start.after(end) ? (current.after(start) || current.before(end)) : (current.after(start) && current.before(end));
                if (isAllowed) return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void goToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
        new Handler(Looper.getMainLooper()).postDelayed(this::finish, 50);
    }
}
