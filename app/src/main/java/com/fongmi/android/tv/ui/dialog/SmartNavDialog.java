package com.fongmi.android.tv.ui.dialog;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogSmartNavBinding;
import com.fongmi.android.tv.ui.activity.SearchActivity;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.presenter.WordPresenter; // 我们借用一下现成的Presenter

import java.util.ArrayList;

public class SmartNavDialog extends DialogFragment implements WordPresenter.OnClickListener {

    private DialogSmartNavBinding binding;
    private ArrayObjectAdapter mRelatedAdapter;
    private ArrayObjectAdapter mHotAdapter;

    // --- 核心修改1：newInstance方法现在可以接收数据了 ---
    public static SmartNavDialog newInstance(ArrayList<String> relatedWords, ArrayList<String> hotWords) {
        Bundle args = new Bundle();
        args.putStringArrayList("related", relatedWords);
        args.putStringArrayList("hot", hotWords);
        SmartNavDialog fragment = new SmartNavDialog();
        fragment.setArguments(args);
        return fragment;
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
        // 我们不再在这里启动动画，因为布局文件里已经把它删掉了
        // startBreathingAnimation(); 
        
        // --- 核心修改2：设置RecyclerView ---
        setRecyclerViews();
        setData();
    }

    private void setRecyclerViews() {
        // 设置相关推荐列表
        binding.relatedRecycler.setHasFixedSize(true);
        binding.relatedRecycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        binding.relatedRecycler.setAdapter(new ItemBridgeAdapter(mRelatedAdapter = new ArrayObjectAdapter(new WordPresenter(this))));

        // 设置热门推荐列表
        binding.hotRecycler.setHasFixedSize(true);
        binding.hotRecycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        binding.hotRecycler.setAdapter(new ItemBridgeAdapter(mHotAdapter = new ArrayObjectAdapter(new WordPresenter(this))));
    }

    private void setData() {
        if (getArguments() == null) return;

        ArrayList<String> relatedWords = getArguments().getStringArrayList("related");
        ArrayList<String> hotWords = getArguments().getStringArrayList("hot");

        if (relatedWords != null && !relatedWords.isEmpty()) {
            mRelatedAdapter.setItems(relatedWords, null);
        } else {
            // 如果没有相关推荐，可以隐藏这部分
            binding.relatedRecycler.setVisibility(View.GONE);
            // 也可以显示一个提示，比如 "暂无相关推荐"
        }

        if (hotWords != null && !hotWords.isEmpty()) {
            mHotAdapter.setItems(hotWords, null);
        } else {
            binding.hotRecycler.setVisibility(View.GONE);
        }
    }
    
    // --- 核心修改3：实现点击事件，启动一键搜索！---
    @Override
    public void onItemClick(String text) {
        // 当任何一个推荐词被点击时，就启动搜索！
        SearchActivity.start(getActivity(), text);
        // 然后关闭自己
        dismiss();
    }

    // --- 核心修改4：实现按键自动消失的功能 ---
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 这里我们先用一个简单的方式，按返回键关闭
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            dismiss();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }
}
