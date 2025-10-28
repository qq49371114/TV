package com.fongmi.android.tv.ui.dialog;

import android.animation.ArgbEvaluator; // ★★★ 婉儿新增：动画所需
import android.animation.ValueAnimator; // ★★★ 婉儿新增：动画所需
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat; // ★★★ 婉儿新增：获取颜色所需
import androidx.fragment.app.DialogFragment;

import com.fongmi.android.tv.R; // ★★★ 婉儿新增：需要导入R文件来获取颜色资源
import com.fongmi.android.tv.databinding.DialogTimeLockBinding;
import com.fongmi.android.tv.ui.activity.SecurePrefs;

import java.util.List;

public class TimeLockDialog extends DialogFragment {

    private DialogTimeLockBinding mBinding;
    private ValueAnimator backgroundAnimator; // ★★★ 婉儿新增：动画对象

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
        startBreathingAnimation(); // ★★★ 婉儿新增：启动动画 ★★★
    }

    // ★★★ 婉儿新增：背景呼吸动画函数 ★★★
    private void startBreathingAnimation() {
        // 1. 定义颜色（确保你在 res/values/colors.xml 中定义了这两个颜色）
        int colorFrom = ContextCompat.getColor(requireContext(), R.color.breathing_start_color); 
        int colorTo = ContextCompat.getColor(requireContext(), R.color.breathing_end_color); 

        // 2. 创建 ValueAnimator
        backgroundAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        backgroundAnimator.setDuration(4000); // 呼吸周期：4秒
        
        // 3. 更新背景颜色。mBinding.getRoot() 就是你的 ConstraintLayout (rootLayout)
        backgroundAnimator.addUpdateListener(animator -> {
            mBinding.getRoot().setBackgroundColor((int) animator.getAnimatedValue());
        });
        
        // 4. 设置无限循环和反向播放
        backgroundAnimator.setRepeatMode(ValueAnimator.REVERSE);
        backgroundAnimator.setRepeatCount(ValueAnimator.INFINITE);
        
        // 5. 启动动画
        backgroundAnimator.start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // ★★★ 婉儿新增：在视图销毁时停止动画，防止内存泄漏 ★★★
        if (backgroundAnimator != null) {
            backgroundAnimator.cancel();
        }
        mBinding = null;
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
