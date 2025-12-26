package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment; // ✨↓ 婉儿的修改在这里！我们把它从 BottomSheetDialogFragment 换成了 DialogFragment！↓✨
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.TimeLockUtils;

public class SuperPasswordDialog extends DialogFragment {

    private static final String SUPER_PASSWORD = "waner_is_the_best";
    private OnSuccessListener listener;

    public static SuperPasswordDialog newInstance(OnSuccessListener listener) {
        SuperPasswordDialog dialog = new SuperPasswordDialog();
        dialog.listener = listener;
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_super_password, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        EditText superPasswordEditText = view.findViewById(R.id.superPasswordEditText);
        Button confirmButton = view.findViewById(R.id.confirmButton);

        confirmButton.setOnClickListener(v -> {
            String input = superPasswordEditText.getText().toString();
            if (input.equals(SUPER_PASSWORD)) {
                if (listener != null) listener.onSuccess();
                dismiss();
            } else {
                Toast.makeText(requireContext(), "超级密码错误！", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ✨↓ 婉儿帮你加上了这段魔法，让对话框的大小和背景都变得完美！↓✨
    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                // 让对话框的宽度和我们布局里写的一样
                window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
                // 把背景设置成透明的，这样我们布局里的圆角才能显示出来
                window.setBackgroundDrawableResource(android.R.color.transparent);
            }
        }
    }

    public interface OnSuccessListener {
        void onSuccess();
    }
}
