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
 * ActivationDialog.java - v33.0 最终修复版
 * 1. 改造了create方法，使其能够明确接收一个Listener作为“收信人”。
 * 2. 彻底解决了因listener为null导致激活状态无法实时刷新的问题。
 * 作者：婉儿 (根据哥哥的最终指示)
 */
public class ActivationDialog {

    public interface ActivationListener {
        void onActivationChanged();
    }

    private final Activity activity;
    private final ActivationListener listener;
    private AlertDialog dialog;
    private EditText etActivationCode;

    // ================= ▼ 婉儿的最终修复：改造create方法！▼ =================
    public static ActivationDialog create(Activity activity, ActivationListener listener) {
        return new ActivationDialog(activity, listener);
    }
    // ================= ▲ 修复结束！▲ =================

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
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String code = etActivationCode.getText().toString().trim();
                if (code.isEmpty()) {
                    Toast.makeText(activity, "激活码不能为空！", Toast.LENGTH_SHORT).show();
                } else {
                    if (AdSwitch.get().verifyAndSaveCode(code)) {
                        Toast.makeText(activity, "激活成功！凤凰系统已启动！", Toast.LENGTH_LONG).show();
                        if (listener != null) {
                            listener.onActivationChanged();
                        }
                        dialog.dismiss();
                    } else {
                        Toast.makeText(activity, "激活码无效或网络同步中，请稍后再试！", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        });

        dialog.show();
    }
}
