package com.fongmi.android.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.bean.Word;
import com.fongmi.android.tv.databinding.AdapterWordBinding;
import com.fongmi.android.tv.utils.ImgUtil;
import com.bumptech.glide.Glide;

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
        holder.binding.text.setText(item.getTitle());
        ImgUtil.load(item.getTitle(), item.getPic(), holder.binding.image);
        setOnClickListener(holder, view -> listener.onItemClick(item));
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
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
