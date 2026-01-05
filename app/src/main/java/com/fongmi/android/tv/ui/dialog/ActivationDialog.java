package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.AdRule;
import com.fongmi.android.tv.player.AdSwitch;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * ActivationDialog.java - v39.0 创世神版
 * 1. 内置了“加密模式”，可以通过魔法指令触发。
 * 2. 使用APP自身的加密逻辑来生成密文，100%保证兼容性。
 * 作者：婉儿 (根据哥哥的最终指示)
 */
public class ActivationDialog {

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

        builder.setPositiveButton("执行", null); // 按钮文字改成“执行”
        dialog = builder.create();
        
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String input = etActivationCode.getText().toString().trim();
                if (input.isEmpty()) {
                    Toast.makeText(activity, "输入不能为空！", Toast.LENGTH_SHORT).show();
                    return;
                }

                // ================= ▼ 婉儿的“创世神力”核心！▼ =================
                try {
                    // --- 魔法指令1：加密“激活文件” ---
                    if (input.startsWith("act:")) {
                        String plaintext = input.substring(4); // 截取 "act:" 后面的内容
                        String ciphertext = AdSwitch.get().encrypt(plaintext);
                        etActivationCode.setText(ciphertext); // 将结果显示在输入框
                        copyToClipboard(ciphertext);
                        Toast.makeText(activity, "激活文件密文已生成并复制！", Toast.LENGTH_LONG).show();

                    // --- 魔法指令2：加密“规则文件” ---
                    } else if (input.startsWith("rule:")) {
                        String plaintext = input.substring(5); // 截取 "rule:" 后面的内容
                        String ciphertext = AdRule.get().encrypt(plaintext);
                        etActivationCode.setText(ciphertext);
                        copyToClipboard(ciphertext);
                        Toast.makeText(activity, "规则文件密文已生成并复制！", Toast.LENGTH_LONG).show();

                    // --- 默认行为：激活系统 ---
                    } else {
                        if (AdSwitch.get().activateWith(input)) {
                            Toast.makeText(activity, "激活成功！凤凰系统已启动！", Toast.LENGTH_LONG).show();
                            if (listener != null) {
                                listener.onActivationChanged();
                            }
                            dialog.dismiss();
                        } else {
                            Toast.makeText(activity, "激活码无效或网络同步中！", Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (Exception e) {
                    Toast.makeText(activity, "操作失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
                // ================= ▲ 神力施展完毕！▲ =================
            });
        });

        dialog.show();
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("phoenix_ciphertext", text);
        clipboard.setPrimaryClip(clip);
    }
}
