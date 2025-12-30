package com.fongmi.android.tv.ui.dialog;

import android.os.Bundle;
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
import com.fongmi.android.tv.ui.activity.CollectActivity;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.presenter.WordPresenter;
import java.util.ArrayList;
import java.util.List;

public class SmartNavDialog extends DialogFragment implements WordPresenter.OnClickListener {

    private DialogSmartNavBinding binding;
    private ArrayObjectAdapter mRelatedAdapter;
    private ArrayObjectAdapter mHotAdapter;

    // ✨ 它又变回了接收两个完美列表的构造方法！
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

    // ✨ 它只负责显示传进来的数据！
    private void setData() {
        if (getArguments() == null) return;
        List<Word.Data> relatedWords = getArguments().getParcelableArrayList("related");
        List<Word.Data> hotWords = getArguments().getParcelableArrayList("hot");

        if (relatedWords != null && !relatedWords.isEmpty()) {
            mRelatedAdapter.setItems(relatedWords, null);
        } else {
            binding.relatedRecycler.setVisibility(View.GONE);
        }

        if (hotWords != null && !hotWords.isEmpty()) {
            mHotAdapter.setItems(hotWords, null);
        } else {
            binding.hotRecycler.setVisibility(View.GONE);
        }
    }
    
    // ✨ 点击事件的逻辑是正确的，我们保留它！
    @Override
    public void onItemClick(Word.Data item) {
        CollectActivity.start(getActivity(), item.getTitle());
        dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }
}
