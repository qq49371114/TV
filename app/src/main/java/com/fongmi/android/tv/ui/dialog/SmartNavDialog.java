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
import com.google.android.material.tabs.TabLayout;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Response;

public class SmartNavDialog extends DialogFragment implements VodPresenter.OnClickListener {

    private DialogSmartNavBinding binding;
    private ArrayObjectAdapter mRelatedAdapter;
    private ArrayObjectAdapter mHotAdapter;

    private static List<Vod> sRealVods;
    
    public static SmartNavDialog newInstance(List<Vod> vods) {
        // 把数据存到静态变量里，供后面使用
        sRealVods = vods; 
        
        // 创建 Fragment 实例
        SmartNavDialog fragment = new SmartNavDialog();
        
        // 不需要再传 Bundle keyword 了，因为数据都在 sRealVods 里了
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
        initTabs(); // 💖 增加一个初始化Tab的方法 💖
        startWorks();
    }

    private void setRecyclerViews() {
        binding.relatedRecycler.setAdapter(new ItemBridgeAdapter(mRelatedAdapter = new ArrayObjectAdapter(new VodPresenter(this))));
        binding.hotRecycler.setAdapter(new ItemBridgeAdapter(mHotAdapter = new ArrayObjectAdapter(new VodPresenter(this))));
    }

    // 💖 最终的、最简单的解决方案！ 💖
    private void initTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("为你推荐"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("大家都在看"));
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    binding.relatedRecycler.setVisibility(View.VISIBLE);
                    binding.hotRecycler.setVisibility(View.GONE);
                } else {
                    binding.relatedRecycler.setVisibility(View.GONE);
                    binding.hotRecycler.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }
    private void startWorks() {
        // 1. 如果有真数据，直接显示！(海报这就来了🖼️)
        if (sRealVods != null && !sRealVods.isEmpty()) {
            mRelatedAdapter.setItems(sRealVods, new BaseDiffCallback<>());
        }
        
        // 2. 右边的热词还是照常显示
        fetchHotWords();
    }

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
