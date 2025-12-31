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
import com.fongmi.android.tv.R; // ✨【重要】导入资源文件
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.bean.Word;
import com.fongmi.android.tv.databinding.DialogSmartNavBinding;
import com.fongmi.android.tv.ui.activity.CollectActivity;
import com.fongmi.android.tv.ui.adapter.BaseDiffCallback;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.SuggestHelper;
import com.fongmi.android.tv.utils.ZhuToPin;
import com.github.catvod.net.OkHttp;
import com.google.android.material.tabs.TabLayout; // ✨【重要】导入 TabLayout

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Response;

public class SmartNavDialog extends DialogFragment implements VodPresenter.OnClickListener {

    private DialogSmartNavBinding binding;
    private ArrayObjectAdapter mAdapter; // ✨ 我们现在只需要一个 Adapter！

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
        initView(); // ✨ 我们把初始化逻辑都放在一个方法里
    }

    private void initView() {
        // 1. 设置我们唯一的“大展柜”
        binding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(new VodPresenter(this))));

        // 2. 初始化“标签栏”
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("为你推荐"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("大家都在看"));

        // 3. 设置“标签栏”的监听器，这是所有魔法的核心！
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                // 当一个标签被选中时，根据它的位置去加载不同的数据！
                if (tab.getPosition() == 0) {
                    fetchSuggestions(getArguments().getString("keyword"));
                } else {
                    fetchHotWords();
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        // 4. 默认加载第一个标签的内容
        fetchSuggestions(getArguments().getString("keyword"));
    }

    // ✨ “为你推荐”的方法，现在只负责获取数据，并更新我们唯一的 Adapter！
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
                App.post(() -> mAdapter.setItems(vodList, new BaseDiffCallback<>()));
            }
        });
    }

    // ✨ “大家都在看”的方法，也只负责获取数据，并更新我们唯一的 Adapter！
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
            App.post(() -> mAdapter.setItems(vodList, new BaseDiffCallback<>()));
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
