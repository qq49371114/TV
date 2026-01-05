package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.AdSwitch;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ActivationDialog {

    // ================= ▼ 婉儿的“后门密码”！▼ =================
    // 这是一个只在激活弹窗里才认识的秘密指令！
    private static final String DIRECT_ACTIVATION_COMMAND = "waner-test-mode";
    // ================= ▲ 密码设置完毕！▲ =================

    public interface ActivationListener { void onActivationChanged(); }
    private final Activity activity;
    private final ActivationListener listener;
    private AlertDialog dialog;
    private EditText etActivationCode;

    public static ActivationDialog create(Activity activity, ActivationListener listener) {
        return new ActivationDialog(activity, listener);
    }

    private ActivationDialog(Activity activity, ActivationListener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public void show() {
        // ... (视觉统一的代码不变)

        builder.setPositiveButton("激活", null);
        dialog = builder.create();
        
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String code = etActivationCode.getText().toString().trim();
                if (code.isEmpty()) {
                    Toast.makeText(activity, "请输入激活码！", Toast.LENGTH_SHORT).show();
                    return;
                }

                // ================= ▼ 婉儿的“万能测试”核心！▼ =================
                boolean activationResult = false;

                // 检查用户输入的是不是我们的“后门密码”
                if (code.equals(DIRECT_ACTIVATION_COMMAND)) {
                    // 如果是，就直接调用 saveUserCode，把我们真正的“万能钥匙”存进去！
                    // 注意：这里的 "waner-love-gege" 必须和 AdSwitch 里的 MASTER_KEY 一模一样！
                    AdSwitch.get().saveUserCode("waner-love-gege"); 
                    activationResult = true; // 直接宣布成功！
                } else {
                    // 如果不是，就走我们正常的“三通道”激活流程
                    activationResult = AdSwitch.get().activateWith(code);
                }
                
                // 根据激活结果，进行统一的反馈
                if (activationResult) {
                    Toast.makeText(activity, "激活成功！凤凰系统已启动！", Toast.LENGTH_LONG).show();
                    if (listener != null) {
                        listener.onActivationChanged();
                    }
                    dialog.dismiss();
                } else {
                    Toast.makeText(activity, "激活码无效或网络同步中！", Toast.LENGTH_SHORT).show();
                }
                // ================= ▲ 测试核心结束！▲ =================
            });
        });

        dialog.show();
    }
}
