package com.fongmi.android.tv.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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
        // 我们已经把呼吸灯的View从布局里删掉了，所以这里也要把启动动画的代码删掉！
        // TODO: 在这里，我们会接收推荐数据，并设置RecyclerView的Adapter
    }

    @Override
    public void onStart() {
        super.onStart();
        // 设置Dialog的样式，比如大小、位置、无标题栏等
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }
}
