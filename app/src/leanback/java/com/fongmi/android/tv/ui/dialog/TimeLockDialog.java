package com.fongmi.android.tv.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.fongmi.android.tv.databinding.DialogTimeLockBinding;
import com.fongmi.android.tv.ui.activity.SecurePrefs;

import java.util.List; // ▼▼▼ 婉儿把这行最重要的“引路”代码加上啦！▼▼▼

public class TimeLockDialog extends DialogFragment {

    private DialogTimeLockBinding mBinding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_FRAME, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = DialogTimeLockBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initView();
        initEvent();
    }

    private void initView() {
        setCancelable(false);

        List<SecurePrefs.TimeSlot> slots = SecurePrefs.getTimeSlots();
        if (slots.isEmpty()) {
            mBinding.timeRangeText.setText("当前未设置任何允许使用的时间段");
        } else {
            StringBuilder sb = new StringBuilder("允许使用时间段:\n");
            for (SecurePrefs.TimeSlot slot : slots) {
                sb.append(slot.start).append(" - ").append(slot.end).append("\n");
            }
            mBinding.timeRangeText.setText(sb.toString().trim());
        }
    }

    private void initEvent() {
        mBinding.exitButton.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        mBinding.unlockButton.setOnClickListener(v -> {
            String password = mBinding.passwordInput.getText().toString().trim();
            if (password.equals("waner666")) {
                SecurePrefs.put("parent_mode_enabled", "true");
                Toast.makeText(getContext(), "欢迎您，主人！家长模式已永久开启。", Toast.LENGTH_LONG).show();
                dismiss();
            } else {
                Toast.makeText(getContext(), "超级密码错误！", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
