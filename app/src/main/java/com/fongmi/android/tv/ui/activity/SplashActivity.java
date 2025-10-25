    package com.fongmi.android.tv.ui.activity;

    import android.content.Intent;
    import android.os.Bundle;
    import android.text.TextUtils;
    import android.view.View;
    import android.view.inputmethod.EditorInfo;
    import android.widget.EditText;
    import android.widget.Toast;
    import androidx.appcompat.app.AppCompatActivity;
    import com.fongmi.android.tv.R;
    import com.github.catvod.utils.Prefers;
    
    public class SplashActivity extends AppCompatActivity {
    
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
//            setContentView(R.layout.activity_splash);
    
//            String storedPassword = Prefers.getString("app_password", "");
    
            if (TextUtils.isEmpty(storedPassword)) {
                goToHome();
            } else {
                setupPasswordCheck(storedPassword);
            }
        }
    
        private void setupPasswordCheck(String correctPassword) {
//            EditText passwordInput = findViewById(R.id.password);
//            passwordInput.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    if (v.getText().toString().equals(correctPassword)) {
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
            finish();
        }
    }
