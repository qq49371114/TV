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
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.bean.Word;
import com.fongmi.android.tv.databinding.DialogSmartNavBinding;
import com.fongmi.android.tv.ui.activity.CollectActivity;
import com.fongmi.android.tv.ui.adapter.BaseDiffCallback;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.SuggestHelper;
import com.fongmi.android.tv.utils.ZhuToPin;
import com.github.catvod.net.OkHttp;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Response;

// ✨ 我们回归到那个最简单、最稳定的版本！✨
public class SmartNavDialog extends DialogFragment implements VodPresenter.OnClickListener {

    private DialogSmartNavBinding binding;
    private ArrayObjectAdapter mRelatedAdapter;
    private ArrayObjectAdapter mHotAdapter;

    public static SmartNavDialog newInstance(String keyword) {
        Bundle args = new Bundle();
        args.putString("keyword", keyword);
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
        startWorks();
    }

    // ✨✨✨【最终的答案】我们回归到最稳定的“双列表”方案！✨✨✨
    private void setRecyclerViews() {
        // 大脑现在认识 relatedRecycler 了！
        binding.relatedRecycler.setAdapter(new ItemBridgeAdapter(mRelatedAdapter = new ArrayObjectAdapter(new VodPresenter(this))));
        // 大脑现在也认识 hotRecycler 了！
        binding.hotRecycler.setAdapter(new ItemBridgeAdapter(mHotAdapter = new ArrayObjectAdapter(new VodPresenter(this))));
    }

    private void startWorks() {
        String keyword = getArguments().getString("keyword");
        fetchSuggestions(keyword);
        fetchHotWords();
    }

    // ✨ “为你推荐”的选手，只负责更新自己的列表 mRelatedAdapter！
    private void fetchSuggestions(String keyword) {
        OkHttp.newCall("https://suggest.video.iqiyi.com/?if=mobile&key=" + URLEncoder.encode(ZhuToPin.get(keyword))).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {}

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                List<Word.Data> suggestions = Word.objectFrom(response.body().string()).getData();
                if (suggestions == null || suggestions.isEmpty()) return;

                List<Vod> vodList = new ArrayList<>();
                for (Word.Data item : suggestions) {
                    if (!item.getTitle().equals(keyword)) {
                        Vod vod = new Vod();
                        vod.setName(item.getTitle());
                        vod.setPic(item.getPic());
                        vodList.add(vod);
                    }
                }

                App.post(() -> mRelatedAdapter.setItems(vodList, new BaseDiffCallback<>()));
            }
        });
    }

    // ✨ “大家都在看”的选手，也只负责更新自己的列表 mHotAdapter！
    private void fetchHotWords() {
        SuggestHelper.getHot(hotWords -> {
            if (hotWords == null || hotWords.isEmpty()) return;
            
            List<Vod> vodList = new ArrayList<>();
            for (Word.Data item : hotWords) {
                Vod vod = new Vod();
                vod.setName(item.getTitle());
                vod.setPic(item.getPic());
                vodList.add(vod);
            }

            App.post(() -> mHotAdapter.setItems(vodList, new BaseDiffCallback<>()));
        });
    }

    @Override
    public void onItemClick(Vod item) {
        CollectActivity.start(getActivity(), item.getName());
        dismiss();
    }

    @Override
    public boolean onLongClick(Vod item) {
        return false;
    }

    @Override
    public void onStart() {
        super.onStart();
        android.view.Window window = getDialog().getWindow();
        if (window == null) return;
        int screenWidth = com.fongmi.android.tv.utils.ResUtil.getScreenWidth();
        int width = (int) (screenWidth * 0.8f);
        window.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setBackgroundDrawableResource(android.R.color.transparent);
    }
}
