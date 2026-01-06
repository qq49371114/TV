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
 * ActivationDialog.java - v72.0 纯粹激活版
 * 1. 移除了所有加密/解密的工具人功能，回归最纯粹的激活使命。
 * 2. 完美对接 v69.0 版的“双引擎” AdSwitch。
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
        android.util.TypedValue typedValue = new android.util.TypedValue();
        activity.getTheme().resolveAttribute(com.google.android.material.R.attr.materialAlertDialogTheme, typedValue, true);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity, typedValue.resourceId);
        
        builder.setTitle("🔥 凤凰之心・授权激活 🔥");
        
        LayoutInflater inflater = LayoutInflater.from(activity);
        android.view.View view = inflater.inflate(R.layout.dialog_activation, null);
        etActivationCode = view.findViewById(R.id.et_activation_code);
        builder.setView(view);

        builder.setNegativeButton("取消", (dialogInterface, i) -> dialog.dismiss());
        builder.setPositiveButton("授权", null);

        dialog = builder.create();
        
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String code = etActivationCode.getText().toString().trim();
                if (code.isEmpty()) {
                    Toast.makeText(activity, "授权码不能为空！", Toast.LENGTH_SHORT).show();
                } else {
                    // ✨ 调用我们新的、强大的“设备绑定”激活方法！
                    AdSwitch.get().activate(activity, code);
                    Toast.makeText(activity, "授权请求已发送，请稍后重启APP查看状态！", Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                }
            });
        });

        dialog.show();
    }
}
