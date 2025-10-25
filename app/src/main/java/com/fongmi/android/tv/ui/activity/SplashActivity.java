package com.fongmi.android.tv.ui.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SplashActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 999;
    private TextView lockMessage;
    private EditText passwordInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        lockMessage = findViewById(R.id.lockMessage);
        passwordInput = findViewById(R.id.password);

        new Handler(Looper.getMainLooper()).postDelayed(this::checkAll, 200);
    }

    private void checkAll() {
        if (hasPermission()) {
            checkLockState();
        } else {
            requestPermission();
        }
    }

    private void checkLockState() {
        // ▼▼▼ 核心修改！从“加密保险库”里读取永久的家长模式状态！▼▼▼
        if ("true".equals(SecurePrefs.getString("parent_mode_enabled", "false"))) {
            goToHome(); // 如果家长模式已开启，直接放行！
            return;
        }

        boolean isTimeLocked = isTimeLocked();
        String storedPassword = SecurePrefs.getString("app_password", "");

        if (isTimeLocked || !TextUtils.isEmpty(storedPassword)) {
            setupInputScreen(isTimeLocked, storedPassword);
        } else {
            goToHome();
        }
    }

    private void setupInputScreen(boolean isTimeLocked, String correctPassword) {
        passwordInput.setVisibility(View.VISIBLE);

        if (isTimeLocked) {
            lockMessage.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder("休息时间到啦\n允许使用时间段:\n");
            for (RemoteControlServer.TimeSlot slot : SecurePrefs.getTimeSlots()) {
                sb.append(slot.start).append(" - ").append(slot.end).append("\n");
            }
            lockMessage.setText(sb.toString().trim());
            passwordInput.setHint("请输入超级密码解锁");
        } else {
            lockMessage.setVisibility(View.GONE);
            passwordInput.setHint("请输入密码");
        }

        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String input = v.getText().toString();
                
                // ▼▼▼ 核心修改！“婉儿最棒”现在是用来开启【永久】的家长模式！▼▼▼
                if (input.equals("婉儿最棒")) {
                    SecurePrefs.put("parent_mode_enabled", "true"); // 把开关状态写入“加密保险库”！
                    Toast.makeText(this, "欢迎您，主人！家长模式已永久开启。", Toast.LENGTH_LONG).show();
                    goToHome();
                    return true;
                }
                
                if (isTimeLocked) {
                    Toast.makeText(this, "当前为休息时间，请输入超级密码解锁", Toast.LENGTH_LONG).show();
                    v.setText("");
                    return true;
                }
                
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

    // --- (下面的所有其他方法，如权限请求、isTimeLocked、goToHome 等，都保持不变) ---

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(android.net.Uri.fromParts("package", getPackageName(), null));
                startActivityForResult(intent, PERMISSION_REQUEST_CODE);
            } catch (Exception e) {
                e.printStackTrace();
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, PERMISSION_REQUEST_CODE);
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            new Handler(Looper.getMainLooper()).postDelayed(this::checkAll, 200);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            new Handler(Looper.getMainLooper()).postDelayed(this::checkAll, 200);
        }
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
