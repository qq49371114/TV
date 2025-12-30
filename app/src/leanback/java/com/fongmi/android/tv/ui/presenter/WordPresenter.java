package com.fongmi.android.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide; // 引入Glide
import com.fongmi.android.tv.bean.Word;
import com.fongmi.android.tv.databinding.AdapterWordBinding;
import com.fongmi.android.tv.utils.ImgUtil;

public class WordPresenter extends Presenter {

    private final OnClickListener listener;

    public WordPresenter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {
        void onItemClick(Word.Data item);
    }

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return new ViewHolder(AdapterWordBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        Word.Data item = (Word.Data) object;
        ViewHolder holder = (ViewHolder) viewHolder;

        // --- 每次工作前，先打扫战场！---
        holder.binding.image.setImageDrawable(null);

        // --- ✨【核心诊断】用“透视眼镜”检查拿到的名字！✨ ---
        String title = item.getTitle();
        if (TextUtils.isEmpty(title)) {
            // 如果名字是空的，我们就强制显示一个提示，这样就能立刻知道问题所在！
            holder.binding.text.setText("名字丢了!");
        } else {
            // 如果名字正常，就显示名字
            holder.binding.text.setText(title);
        }
        
        // --- 开始加载图片和设置点击事件 ---
        ImgUtil.load(item.getTitle(), item.getPic(), holder.binding.image);
        setOnClickListener(holder, view -> listener.onItemClick(item));
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
        // --- 核心修改2：下班前，把自己的工具都收好！---
        // 当这个“展台”被回收时，告诉Glide停止加载图片并释放资源，防止内存泄漏和图片错位。
        ViewHolder holder = (ViewHolder) viewHolder;
        Glide.with(holder.binding.image.getContext()).clear(holder.binding.image);
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterWordBinding binding;

        public ViewHolder(@NonNull AdapterWordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
