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

        // 加一个短暂的延迟，确保界面完全渲染出来，避免闪烁
        new Handler(Looper.getMainLooper()).postDelayed(this::checkAll, 200);
    }

    // 统一的检查入口，我们的“三道关卡”
    private void checkAll() {
        // 第一关：检查最关键的文件访问权限！
        if (hasPermission()) {
            // 权限没问题，再进入我们自己的“婉儿守护”检查流程
            checkTimeAndPassword();
        } else {
            // 权限有问题，就去请求权限！
            requestPermission();
        }
    }

    // “婉儿守护”检查流程
    private void checkTimeAndPassword() {
        // 终极关卡：家长模式检查
        if (App.isParentMode) {
            goToHome(); // 如果是主人，直接放行！
            return;
        }
        // 时间锁检查
        if (isTimeLocked()) {
            setupLockScreen(); // 如果时间不符，锁定！
            return;
        }
        // 密码锁检查
        String storedPassword = SecurePrefs.getString("app_password", "");
        if (TextUtils.isEmpty(storedPassword)) {
            goToHome(); // 没设置密码，也放行
        } else {
            setupPasswordCheck(storedPassword); // 有密码，开始盘问
        }
    }

    // --- 权限检查与请求逻辑 ---

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager(); // Android 11+
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED; // Android 10-
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

    // --- 处理权限请求返回的结果 ---

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
    
    // (下面的 isTimeLocked, setupLockScreen, setupPasswordCheck, goToHome 方法和我们之前设计的一样，现在它们是最终形态)
    
    private boolean isTimeLocked() {
        List<SecurePrefs.TimeSlot> slots = SecurePrefs.getTimeSlots();
        if (slots.isEmpty()) return false;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Date current = sdf.parse(sdf.format(new Date()));
            for (SecurePrefs.TimeSlot slot : slots) {
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

    private void setupLockScreen() {
        passwordInput.setVisibility(View.GONE);
        lockMessage.setVisibility(View.VISIBLE);
        StringBuilder sb = new StringBuilder("休息时间到啦\n允许使用时间段:\n");
        for (SecurePrefs.TimeSlot slot : SecurePrefs.getTimeSlots()) {
            sb.append(slot.start).append(" - ").append(slot.end).append("\n");
        }
        lockMessage.setText(sb.toString().trim());
    }

    private void setupPasswordCheck(String correctPassword) {
        lockMessage.setVisibility(View.GONE);
        passwordInput.setVisibility(View.VISIBLE);
        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String input = v.getText().toString();
                if (input.equals("婉儿最棒")) {
                    App.isParentMode = true;
                    Toast.makeText(this, "欢迎您，主人！家长模式已开启。", Toast.LENGTH_SHORT).show();
                    goToHome();
                } else if (input.equals(correctPassword)) {
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

    private void goToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
        new Handler(Looper.getMainLooper()).postDelayed(this::finish, 50);
    }
}
