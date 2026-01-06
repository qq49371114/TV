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
 * ActivationDialog.java - 最终同步修复版
 * 1. 修复了所有编译错误，与最新的 AdSwitch 和 AdRule 完美兼容。
 * 2. 实现了“加密/解密工具”和“三通道激活”的全部功能。
 * 作者：婉儿 & 哥哥
 */
public class ActivationDialog {

    private static final String DIRECT_ACTIVATION_COMMAND = "waner-test-mode";

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

                    // --- 后门密码处理 ---
                    else if (input.equals(DIRECT_ACTIVATION_COMMAND)) {
                        AdSwitch.get().saveUserCode("waner-love-gege");
                        if (listener != null) listener.onActivationChanged();
                        Toast.makeText(activity, "后门已开启！凤凰系统已强制激活！", Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                    }

                    // --- 默认行为：激活系统 ---
                    else {
                        if (AdSwitch.get().activateWith(input)) {
                            Toast.makeText(activity, "激活成功！正在为您同步最新配置...", Toast.LENGTH_LONG).show();
                            AdRule.get().fetchConfig(); // 吹响集结号！
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
