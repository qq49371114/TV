package com.fongmi.android.tv.ui.dialog;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;

import com.fongmi.android.tv.bean.Word;
import com.fongmi.android.tv.databinding.DialogSmartNavBinding;
import com.fongmi.android.tv.ui.activity.SearchActivity;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.presenter.WordPresenter;
import com.fongmi.android.tv.ui.activity.CollectActivity;

import java.util.ArrayList;
import java.util.List;

public class SmartNavDialog extends DialogFragment implements WordPresenter.OnClickListener {

    private DialogSmartNavBinding binding;
    private ArrayObjectAdapter mRelatedAdapter;
    private ArrayObjectAdapter mHotAdapter;

    public static SmartNavDialog newInstance(String currentVideo, ArrayList<Word.Data> related, ArrayList<Word.Data> hots) {
        Bundle args = new Bundle();
        args.putString("current_video", currentVideo);
        args.putParcelableArrayList("related", related);
        args.putParcelableArrayList("hot", hots);
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
        setRecyclerViews();
        setData();
    }

    private void setRecyclerViews() {
        binding.relatedRecycler.setHasFixedSize(true);
        binding.relatedRecycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        binding.relatedRecycler.setAdapter(new ItemBridgeAdapter(mRelatedAdapter = new ArrayObjectAdapter(new WordPresenter(this))));
        binding.hotRecycler.setHasFixedSize(true);
        binding.hotRecycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        binding.hotRecycler.setAdapter(new ItemBridgeAdapter(mHotAdapter = new ArrayObjectAdapter(new WordPresenter(this))));
    }

    private void setData() {
        if (getArguments() == null) return;

        String currentVideoName = getArguments().getString("current_video");
        List<Word.Data> relatedWords = getArguments().getParcelableArrayList("related");
        List<Word.Data> hotWords = getArguments().getParcelableArrayList("hot");

        if (relatedWords != null && !relatedWords.isEmpty()) {
            List<Word.Data> filteredList = new ArrayList<>();
            for (Word.Data item : relatedWords) {
                if (!item.getTitle().equals(currentVideoName)) {
                    filteredList.add(item);
                }
            }
            mRelatedAdapter.setItems(filteredList, null);
        } else {
            binding.relatedRecycler.setVisibility(View.GONE);
        }

        if (hotWords != null && !hotWords.isEmpty()) {
            mHotAdapter.setItems(hotWords, null);
        } else {
            binding.hotRecycler.setVisibility(View.GONE);
        }
    }
    
    @Override
    public void onItemClick(Word.Data item) {
        CollectActivity.start(getActivity(), item.getTitle());
    // 然后关闭自己
        dismiss();
        }

    // 这里可以添加更完善的按键处理逻辑
    // @Override
    // public boolean onKeyDown(int keyCode, KeyEvent event) { ... }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }
}
