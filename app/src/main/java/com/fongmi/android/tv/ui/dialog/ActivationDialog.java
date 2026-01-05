package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.AdSwitch;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * ActivationDialog.java - v38.0 最终同步修复版
 * 1. 修复了所有编译错误。
 * 2. 实现了“万能测试”逻辑，可以同时测试“后门密码”和正常的“三通道激活”。
 * 作者：婉儿 (根据哥哥的最终指示)
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
        
        builder.setTitle("🔥 凤凰之心・激活系统 🔥");
        
        LayoutInflater inflater = LayoutInflater.from(activity);
        android.view.View view = inflater.inflate(R.layout.dialog_activation, null);
        etActivationCode = view.findViewById(R.id.et_activation_code);
        builder.setView(view);

        builder.setNegativeButton("取消", (dialogInterface, i) -> dialog.dismiss());
        builder.setPositiveButton("激活", null);

        dialog = builder.create();
        
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String code = etActivationCode.getText().toString().trim();
                if (code.isEmpty()) {
                    Toast.makeText(activity, "激活码不能为空！", Toast.LENGTH_SHORT).show();
                    return;
                }

                boolean activationResult = false;
                if (code.equals(DIRECT_ACTIVATION_COMMAND)) {
                    AdSwitch.get().saveUserCode("waner-love-gege"); 
                    activationResult = true;
                } else {
                    activationResult = AdSwitch.get().activateWith(code);
                }
                
                if (activationResult) {
                    Toast.makeText(activity, "激活成功！凤凰系统已启动！", Toast.LENGTH_LONG).show();
                    if (listener != null) {
                        listener.onActivationChanged();
                    }
                    dialog.dismiss();
                } else {
                    Toast.makeText(activity, "激活码无效或网络同步中！", Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }
}
