package com.fongmi.android.tv.ui.activity;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.AppLockManager;

/**
 * 锁屏页面
 * @author 婉儿
 */
public class LockScreenActivity extends AppCompatActivity {

    private EditText passwordEditText;
    private Button unlockButton;

    // ✨ 我们先把密码写死在这里，以后可以改成从设置里读取
    private static final String CORRECT_PASSWORD = "waner666";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock_screen);

        passwordEditText = findViewById(R.id.passwordEditText);
        unlockButton = findViewById(R.id.unlockButton);

        unlockButton.setOnClickListener(v -> checkPassword());
    }

    private void checkPassword() {
        String input = passwordEditText.getText().toString();
        if (input.equals(CORRECT_PASSWORD)) {
            // 密码正确！
            // 1. 发放“临时通行证”
            AppLockManager.isTemporarilyUnlocked = true;
            // 2. 关闭自己
            finish();
        } else {
            // 密码错误
            Toast.makeText(this, "密码错误！", Toast.LENGTH_SHORT).show();
        }
    }

    // ✨ 最重要的一步：禁止用户按返回键跳过锁屏！
    @Override
    public void onBackPressed() {
        // 留空，这样按返回键就没反应啦
    }
}
