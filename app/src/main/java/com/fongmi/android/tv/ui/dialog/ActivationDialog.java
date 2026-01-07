package com.fongmi.android.tv.ui.dialog; // (哥哥，这里的包名请根据你的项目结构调整哦)

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.fongmi.android.tv.R; // (哥哥，这里的R文件路径也需要你确认下哦)
import com.fongmi.android.tv.player.AdRule;
import com.fongmi.android.tv.player.AdSwitch;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ActivationDialog {

    private final Activity activity;
    private EditText etActivationCode;
    private AlertDialog dialog;

    public ActivationDialog(Activity activity) {
        this.activity = activity;
    }

    public void show() {
        // 创建对话框
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setTitle("🔥 凤凰系统");
        builder.setView(LayoutInflater.from(activity).inflate(R.layout.dialog_activation, null));
        builder.setNegativeButton("取消", null);
        builder.setPositiveButton("执行", null); // 先设置为null，我们自己接管点击事件

        dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            // 在对话框显示后，获取输入框实例
            etActivationCode = dialog.findViewById(R.id.et_activation_code);

            // ✨ 核心逻辑：我们自己接管“执行”按钮的点击事件
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
        String input = etActivationCode.getText().toString().trim();
        if (input.isEmpty()) {
        Toast.makeText(activity, "输入不能为空！", Toast.LENGTH_SHORT).show();
        return;
    }
    
    // ✨ 只保留最纯粹的激活功能！
    AdSwitch.get().activate(activity, input);
    dialog.dismiss();
});
// ================= ▲ 修改结束 ▲ =================

                // --- 魔法指令区：我们的瑞士军刀 ---
                try {
                    // ✨ 指令1：加密“激活文件”
                    if (input.startsWith("act:")) {
                        String plaintext = input.substring(4);
                        String ciphertext = AdSwitch.get().encrypt(plaintext);
                        etActivationCode.setText(ciphertext);
                        copyToClipboard(ciphertext);
                        Toast.makeText(activity, "【激活】密文已生成并复制！", Toast.LENGTH_LONG).show();
                    } 
                    
                    // ✨ 指令2：加密“规则文件”
                    else if (input.startsWith("rule:")) {
                        String plaintext = input.substring(5);
                        String ciphertext = AdRule.get().encrypt(plaintext);
                        etActivationCode.setText(ciphertext);
                        copyToClipboard(ciphertext);
                        Toast.makeText(activity, "【规则】密文已生成并复制！", Toast.LENGTH_LONG).show();
                    }
                    
                    // ✨ 指令3：解密“激活文件”
                    else if (input.startsWith("d_act:")) {
                        String ciphertext = input.substring(6);
                        String plaintext = AdSwitch.get().decrypt(ciphertext);
                        // ✨ 婉儿优化：必须检查解密是否成功！
                        if (plaintext != null) {
                            etActivationCode.setText(plaintext);
                            copyToClipboard(plaintext);
                            Toast.makeText(activity, "【激活】明文已还原并复制！", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(activity, "解密失败，密文可能已损坏！", Toast.LENGTH_SHORT).show();
                        }
                    } 
                    
                    // ✨ 指令4：解密“规则文件”
                    else if (input.startsWith("d_rule:")) {
                        String ciphertext = input.substring(7);
                        String plaintext = AdRule.get().decryptRule(ciphertext);
                        // ✨ 婉儿优化：必须检查解密是否成功！
                        if (plaintext != null) {
                            etActivationCode.setText(plaintext);
                            copyToClipboard(plaintext);
                            Toast.makeText(activity, "【规则】明文已还原并复制！", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(activity, "解密失败，密文可能已损坏！", Toast.LENGTH_SHORT).show();
                        }
                    }

                    // --- 默认行为：面向用户的激活流程 ---
                    else {
                        AdSwitch.get().activate(activity, input);
                        Toast.makeText(activity, "授权请求已发送...", Toast.LENGTH_SHORT).show();
                        // ✨ 婉儿优化：激活是最终操作，完成后关闭对话框
                        dialog.dismiss(); 
                    }
                } catch (Exception e) {
                    // 只捕获加密时可能出现的异常
                    Toast.makeText(activity, "操作失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
            });
        });

        dialog.show();
    }


    private void copyToClipboard(String text) {
        if (text == null || text.isEmpty()) return;
        try {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("phoenix_result", text);
            clipboard.setPrimaryClip(clip);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(activity, "复制到剪贴板失败", Toast.LENGTH_SHORT).show();
        }
    }
}
