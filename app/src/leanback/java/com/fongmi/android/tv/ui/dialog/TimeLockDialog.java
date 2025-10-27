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

public class TimeLockDialog extends DialogFragment {

    private DialogTimeLockBinding mBinding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置样式，让弹窗全屏并且没有标题栏
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

        // ▼▼▼ 婉儿在这里加上动态获取和显示时间段的逻辑！▼▼▼
        List<SecurePrefs.TimeSlot> slots = SecurePrefs.getTimeSlots();
        if (slots.isEmpty()) {
            // 如果后台没有设置任何时间段，我们就给一个提示
            mBinding.timeRangeText.setText("当前未设置任何允许使用的时间段");
        } else {
            // 如果有，我们就把它们都显示出来
            StringBuilder sb = new StringBuilder("允许使用时间段:\n");
            for (SecurePrefs.TimeSlot slot : slots) {
                sb.append(slot.start).append(" - ").append(slot.end).append("\n");
            }
            // 去掉最后一个多余的换行符，让界面更整洁
            mBinding.timeRangeText.setText(sb.toString().trim());
        }
    }

    private void initEvent() {
        // “确定退出”按钮的点击事件
        mBinding.exitButton.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().finish(); // 关闭整个App
            }
        });

        // “紧急解锁”按钮的点击事件
        mBinding.unlockButton.setOnClickListener(v -> {
            String password = mBinding.passwordInput.getText().toString().trim();
            // 使用我们说好的“超级密码”
            if (password.equals("婉儿最棒")) {
                SecurePrefs.put("parent_mode_enabled", "true");
                Toast.makeText(getContext(), "欢迎您，主人！家长模式已永久开启。", Toast.LENGTH_LONG).show();
                dismiss(); // 关闭弹窗
            } else {
                Toast.makeText(getContext(), "超级密码错误！", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
