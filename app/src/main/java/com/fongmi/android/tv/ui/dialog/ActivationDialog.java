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
 * ActivationDialog.java - v49.0 双锁双待版
 * 1. 严格区分 act: 和 rule: 指令，调用不同的加密/解密方法。
 * 2. 100%保证了激活文件和规则文件的密钥隔离。
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
        android.util.TypedValue typedValue = new android.util.TypedValue();
        activity.getTheme().resolveAttribute(com.google.android.material.R.attr.materialAlertDialogTheme, typedValue, true);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity, typedValue.resourceId);
        
        builder.setTitle("🔥 凤凰之心・激活/工具 🔥");
        
        LayoutInflater inflater = LayoutInflater.from(activity);
        android.view.View view = inflater.inflate(R.layout.dialog_activation, null);
        etActivationCode = view.findViewById(R.id.et_activation_code);
        builder.setView(view);

        builder.setNegativeButton("取消", (dialogInterface, i) -> dialog.dismiss());
        builder.setPositiveButton("执行", null);
        dialog = builder.create();
        
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String input = etActivationCode.getText().toString().trim();
                if (input.isEmpty()) {
                    Toast.makeText(activity, "输入不能为空！", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    // --- 激活文件处理 ---
                    if (input.startsWith("act:")) {
                        String plaintext = input.substring(4);
                        String ciphertext = AdSwitch.get().encrypt(plaintext);
                        etActivationCode.setText(ciphertext);
                        copyToClipboard(ciphertext);
                        Toast.makeText(activity, "激活文件密文已生成并复制！", Toast.LENGTH_LONG).show();
                    } else if (input.startsWith("d_act:")) {
                        String ciphertext = input.substring(6);
                        String plaintext = AdSwitch.get().decrypt(ciphertext);
                        etActivationCode.setText(plaintext);
                        copyToClipboard(plaintext);
                        Toast.makeText(activity, "激活文件明文已还原并复制！", Toast.LENGTH_LONG).show();
                    }
                    
                    // --- 规则文件处理 ---
                    else if (input.startsWith("rule:")) {
                        String plaintext = input.substring(5);
                        String ciphertext = AdRule.get().encrypt(plaintext);
                        etActivationCode.setText(ciphertext);
                        copyToClipboard(ciphertext);
                        Toast.makeText(activity, "规则文件密文已生成并复制！", Toast.LENGTH_LONG).show();
                    } else if (input.startsWith("d_rule:")) {
                        String ciphertext = input.substring(7);
                        String plaintext = AdRule.get().decryptRule(ciphertext);
                        etActivationCode.setText(plaintext);
                        copyToClipboard(plaintext);
                        Toast.makeText(activity, "规则文件明文已还原并复制！", Toast.LENGTH_LONG).show();
                    }

                    // --- 默认行为：激活系统 ---
                    else {
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
            });
        });

        dialog.show();
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("phoenix_result", text);
        clipboard.setPrimaryClip(clip);
    }
}
