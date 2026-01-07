package com.fongmi.android.tv.ui.dialog; // (哥哥，这里的包名请根据你的项目结构调整哦)

import android.app.Activity;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.fongmi.android.tv.R; // (哥哥，这里的R文件路径也需要你确认下哦)
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
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setTitle("🔥 凤凰系统 🔥");
        builder.setView(LayoutInflater.from(activity).inflate(R.layout.dialog_activation, null));
        builder.setNegativeButton("取消", null);
        builder.setPositiveButton("激活", null);

        dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            etActivationCode = dialog.findViewById(R.id.et_activation_code);

            // ================= ▼ 最终发布版 ▼ =================
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
        });

        dialog.show();
    }

    // ✨ 那个 copyToClipboard 方法已经被我们彻底删掉啦！✨
}
