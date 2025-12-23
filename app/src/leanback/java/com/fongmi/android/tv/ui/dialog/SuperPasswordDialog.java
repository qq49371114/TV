package com.fongmi.android.tv.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.fongmi.android.tv.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class SuperPasswordDialog extends BottomSheetDialogFragment {

    // ✨↓ 这是我们写死的“超级密码”，只有我们知道！↓✨
    private static final String SUPER_PASSWORD = "waner_666";
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

    // 这是一个“回调”接口，用来通知设置页面“密码正确啦！”
    public interface OnSuccessListener {
        void onSuccess();
    }
}
