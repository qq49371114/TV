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

    // ================= ▼ 婉儿新增：我们的“信鸽”接口！▼ =================
    public interface ActivationListener {
        void onActivationChanged();
    }
    // ================= ▲ 新增结束 ▲ =================

    private final Activity activity;
    private final ActivationListener listener; // 持有一个“信鸽”
    private AlertDialog dialog;
    private EditText etActivationCode;

    public static ActivationDialog create(Activity activity) {
        // 如果调用者（Activity）本身就是一只“信鸽”，我们就把它记下来
        if (activity instanceof ActivationListener) {
            return new ActivationDialog(activity, (ActivationListener) activity);
        }
        return new ActivationDialog(activity, null);
    }

    private ActivationDialog(Activity activity, ActivationListener listener) {
        this.activity = activity;
        this.listener = listener; // 保存“信鸽”
    }

    public void show() {
        // 视觉统一魔法！强制使用和主界面一样的主题！
        android.util.TypedValue typedValue = new android.util.TypedValue();
        activity.getTheme().resolveAttribute(com.google.android.material.R.attr.materialAlertDialogTheme, typedValue, true);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity, typedValue.resourceId);
        
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
                String activationConfigUrl = "http://47.109.61.116:86/apk/activation_configb.json";
                AdSwitch.get().fetchRemoteCode(activationConfigUrl);
                Toast.makeText(activity, "激活成功！凤凰系统已启动！", Toast.LENGTH_LONG).show();
                
                // ================= ▼ 婉儿新增：放出“信鸽”！▼ =================
                // 告诉设置页面：“我这里完事了，你快更新界面！”
                if (listener != null) {
                    listener.onActivationChanged();
                }
                // ================= ▲ 新增结束 ▲ =================

                dialog.dismiss();
            }
        });

        dialog = builder.create();
        dialog.show();
    }
}
