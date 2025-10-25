    package com.fongmi.android.tv.ui.activity;

    import android.content.Intent;
    import android.os.Bundle;
    import android.text.TextUtils;
    import android.view.View;
    import android.view.inputmethod.EditorInfo;
    import android.widget.EditText;
    import android.widget.TextView;
    import android.widget.Toast;
    import androidx.appcompat.app.AppCompatActivity;

    import com.fongmi.android.tv.App;
    import com.fongmi.android.tv.R;
    import com.github.catvod.utils.Prefers;
    
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

            // 终极关卡：检查“家长模式”是否已通过超级密码开启
            if (App.isParentMode) {
                goToHome(); // 如果是主人，直接放行！
                return;
            }

            // 第一道关卡：检查时间！
            if (isTimeLocked()) {
                setupLockScreen(); // 如果时间不符，直接锁定！
                return; // 后面的逻辑不走了
            }

            // 第二道关卡：检查密码！
            String storedPassword = Prefers.getString("app_password", "");
            if (TextUtils.isEmpty(storedPassword)) {
                goToHome(); // 没设置密码，也直接放行
            } else {
                setupPasswordCheck(storedPassword); // 有密码，开始盘问
            }
        }

        // 全新的多时段检查逻辑
        private boolean isTimeLocked() {
            List<RemoteControlServer.TimeSlot> slots = RemoteControlServer.getTimeSlots();
            if (slots.isEmpty()) return false; // 如果没有设置任何时间段，默认全天可用

            try {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                Date current = sdf.parse(sdf.format(new Date()));

                // 遍历所有允许的时间段
                for (RemoteControlServer.TimeSlot slot : slots) {
                    Date start = sdf.parse(slot.start);
                    Date end = sdf.parse(slot.end);
                    boolean isAllowed;
                    if (start.after(end)) { // 跨天情况 (e.g., 22:00 - 06:00)
                        isAllowed = current.after(start) || current.before(end);
                    } else { // 当天情况 (e.g., 09:00 - 18:00)
                        isAllowed = current.after(start) && current.before(end);
                    }
                    if (isAllowed) return false; // 只要当前时间在任何一个允许的段内，就解锁
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false; // 解析出错，安全起见，不锁定
            }
            
            return true; // 遍历完所有时间段都不符合，说明当前时间被锁定
        }
        
        // 检查密码的逻辑，增加了“家长模式”超级密码
        private void setupPasswordCheck(String correctPassword) {
            passwordInput.setVisibility(View.VISIBLE);
            passwordInput.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    String input = v.getText().toString();
                    // 检查是不是“家长模式”超级密码
                    if (input.equals("婉儿最棒")) {
                        App.isParentMode = true; // 开启家长模式！
                        Toast.makeText(this, "欢迎您，主人！家长模式已开启。", Toast.LENGTH_SHORT).show();
                        goToHome();
                    } else if (input.equals(correctPassword)) {
                        goToHome(); // 普通密码正确，也放行
                    } else {
                        Toast.makeText(this, "密码错误！", Toast.LENGTH_SHORT).show();
                        v.setText("");
                    }
                    return true;
                }
                return false;
            });
        }
        
        // 显示时间锁定界面
        private void setupLockScreen() {
            lockMessage.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder("休息时间到啦\n允许使用时间段:\n");
            for (RemoteControlServer.TimeSlot slot : RemoteControlServer.getTimeSlots()) {
                sb.append(slot.start).append(" - ").append(slot.end).append("\n");
            }
            lockMessage.setText(sb.toString().trim());
        }

        // 放行去主界面的逻辑
        private void goToHome() {
            Intent intent = new Intent(this, HomeActivity.class);
            startActivity(intent);

            // 延迟一点再 finish，避免黑屏
            new Handler(Looper.getMainLooper()).postDelayed(this::finish, 50);
        }
    }
