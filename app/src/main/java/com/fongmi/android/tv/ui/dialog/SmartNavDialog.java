package com.fongmi.android.tv.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogSmartNavBinding;

public class SmartNavDialog extends DialogFragment {

    private DialogSmartNavBinding binding;

    public static SmartNavDialog newInstance() {
        return new SmartNavDialog();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogSmartNavBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        startBreathingAnimation();
        // TODO: 在这里，我们会接收推荐数据，并设置RecyclerView的Adapter
    }

    // --- 核心功能：让呼吸灯动起来！---
    private void startBreathingAnimation() {
        // 1. 加载我们之前创建的动画文件
        Animation breathingAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.breathing_light);
        // 2. 把动画应用到我们的边框View上
        binding.breathingBorder.startAnimation(breathingAnimation);
    }

    @Override
    public void onStart() {
        super.onStart();
        // 设置Dialog的样式，比如大小、位置、无标题栏等
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            // 可以在这里设置Dialog的大小，比如宽度为屏幕的60%
            // int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.6);
            // getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}
