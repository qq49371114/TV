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
 * ActivationDialog.java - v74.0 创世神版
 * 1. 完美融合了“视觉统一”、“加密/解密工具”、“三通道激活”所有功能。
 * 2. 是我们“凤凰系统”最终的、最完美的UI交互核心。
 * 作者：婉儿 & 哥哥
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
        // ✨ 视觉统一魔法，保持不变！
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
                    // --- 魔法指令1：加密“激活文件” ---
                    if (input.startsWith("act:")) {
                        String plaintext = input.substring(4);
                        String ciphertext = AdSwitch.get().encrypt(plaintext);
                        etActivationCode.setText(ciphertext);
                        copyToClipboard(ciphertext);
                        Toast.makeText(activity, "激活文件密文已生成并复制！", Toast.LENGTH_LONG).show();

                    // --- 魔法指令2：加密“规则文件” ---
                    } else if (input.startsWith("rule:")) {
                        String plaintext = input.substring(5);
                        String ciphertext = AdRule.get().encrypt(plaintext);
                        etActivationCode.setText(ciphertext);
                        copyToClipboard(ciphertext);
                        Toast.makeText(activity, "规则文件密文已生成并复制！", Toast.LENGTH_LONG).show();
                    }
                    
                    // --- 魔法指令3：解密“激活文件” ---
                    else if (input.startsWith("d_act:")) {
                        String ciphertext = input.substring(6);
                        String plaintext = AdSwitch.get().decrypt(ciphertext);
                        etActivationCode.setText(plaintext);
                        copyToClipboard(plaintext);
                        Toast.makeText(activity, "激活文件明文已还原并复制！", Toast.LENGTH_LONG).show();
                    } 
                    
                    // --- 魔法指令4：解密“规则文件” ---
                    else if (input.startsWith("d_rule:")) {
                        String ciphertext = input.substring(7);
                        String plaintext = AdRule.get().decryptRule(ciphertext);
                        etActivationCode.setText(plaintext);
                        copyToClipboard(plaintext);
                        Toast.makeText(activity, "规则文件明文已还原并复制！", Toast.LENGTH_LONG).show();
                    }

                    // ================= ▼ 婉儿的“后门”在这里！▼ =================
                    // --- 魔法指令5：我们的“后门密码”！---
                    else if (input.equals("waner-test-mode")) {
                        // 如果输入的是后门密码，就直接把我们真正的“万能钥匙”存起来！
                        // 注意：这里的 "waner-love-gege" 必须和 AdSwitch 里的 MASTER_KEY 一模一样！
                        AdSwitch.get().saveUserCode("waner-love-gege");
                        
                        Toast.makeText(activity, "后门已开启！凤凰系统已强制激活！", Toast.LENGTH_LONG).show();
                        if (listener != null) {
                            listener.onActivationChanged();
                        }
                        dialog.dismiss();
                    }
                    // ================= ▲ 后门已开启！▲ =================

                    // --- 默认行为：激活系统 ---
                    else {
                        if (AdSwitch.get().verifyAndSaveCode(input)) {
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
