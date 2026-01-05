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

    private final Activity activity;
    private AlertDialog dialog;
    private EditText etActivationCode;

    public static ActivationDialog create(Activity activity) {
        return new ActivationDialog(activity);
    }

    private ActivationDialog(Activity activity) {
        this.activity = activity;
    }

    public void show() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setTitle("🔥 凤凰之心・激活系统 🔥");
        
        LayoutInflater inflater = LayoutInflater.from(activity);
        android.view.View view = inflater.inflate(R.layout.dialog_activation, null);
        etActivationCode = view.findViewById(R.id.et_activation_code);
        builder.setView(view);

        builder.setNegativeButton("取消", (dialogInterface, i) -> dialog.dismiss());

        builder.setPositiveButton("激活", (dialogInterface, i) -> {
            String code = etActivationCode.getText().toString().trim();
            if (code.isEmpty()) {
                Toast.makeText(activity, "激活码不能为空！", Toast.LENGTH_SHORT).show();
            } else {
                AdSwitch.get().saveUserCode(code);
                String activationConfigUrl = "http://47.109.61.116:86/apk/activation_config.json";
                AdSwitch.get().fetchRemoteCode(activationConfigUrl);
                Toast.makeText(activity, "激活成功！凤凰系统已启动！", Toast.LENGTH_LONG).show();
                dialog.dismiss();
            }
        });

        dialog = builder.create();
        dialog.show();
    }
}
