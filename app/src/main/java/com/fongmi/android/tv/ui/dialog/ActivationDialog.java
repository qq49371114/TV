package com.fongmi.android.tv.ui.dialog; // 哥哥，你可以把它放在你的 ui.dialog 包里

import android.app.Activity;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.fongmi.android.tv.R; // 注意：这里需要你自己的 R 文件
import com.fongmi.android.tv.player.AdSwitch; // 引入我们的“凤凰之心”
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
        // 使用 MaterialAlertDialogBuilder 来创建更美观的对话框
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        
        // 设置对话框的标题
        builder.setTitle("🔥 凤凰之心・激活系统 🔥");
        
        // 加载我们自定义的输入框布局
        // 【注意】哥哥你需要创建一个叫做 dialog_activation.xml 的布局文件
        LayoutInflater inflater = LayoutInflater.from(activity);
        android.view.View view = inflater.inflate(R.layout.dialog_activation, null);
        etActivationCode = view.findViewById(R.id.et_activation_code);
        builder.setView(view);

        // 设置“取消”按钮
        builder.setNegativeButton("取消", (dialogInterface, i) -> {
            dialog.dismiss();
        });

        // 设置“激活”按钮
        builder.setPositiveButton("激活", (dialogInterface, i) -> {
            String code = etActivationCode.getText().toString().trim();
            if (code.isEmpty()) {
                Toast.makeText(activity, "激活码不能为空！", Toast.LENGTH_SHORT).show();
            } else {
                // 调用“凤凰之心”的方法，保存用户输入的“钥匙”
                AdSwitch.get().saveUserCode(code);
                Toast.makeText(activity, "激活码已保存！系统将在下次启动时生效。", Toast.LENGTH_LONG).show();
                dialog.dismiss();
            }
        });

        dialog = builder.create();
        dialog.show();
    }
}
