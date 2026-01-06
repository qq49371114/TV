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
 * ActivationDialog.java - v73.1 视觉统一版
 * 1. 强制对话框使用与Activity相同的主题，解决了背景和点击效果不协调的问题。
 * 2. 保留了 v72.0 版最纯粹的激活逻辑。
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
        // ================= ▼ 婉儿的视觉统一魔法！▼ =================
        // 我们不再直接用 new MaterialAlertDialogBuilder(activity)，
        // 而是先获取到当前界面的主题，然后再用这个主题来创建对话框！
        
        // 1. 获取当前界面的主题ID
        android.util.TypedValue typedValue = new android.util.TypedValue();
        activity.getTheme().resolveAttribute(com.google.android.material.R.attr.materialAlertDialogTheme, typedValue, true);
        int themeResId = typedValue.resourceId;

        // 2. 使用这个主题ID来创建我们的对话框
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity, themeResId);
        // ================= ▲ 魔法施展完毕！▲ =================
        
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
