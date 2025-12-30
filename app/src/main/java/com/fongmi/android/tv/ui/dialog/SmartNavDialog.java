package com.fongmi.android.tv.ui.dialog;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result; // ✨【修正】导入“包裹”类
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.bean.Word;
import com.fongmi.android.tv.databinding.DialogSmartNavBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.activity.CollectActivity;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.presenter.WordPresenter;
import com.fongmi.android.tv.utils.SuggestHelper;
import com.fongmi.android.tv.utils.ZhuToPin;
import com.github.catvod.net.OkHttp;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

import okhttp3.Call;
import okhttp3.Response;

public class SmartNavDialog extends DialogFragment implements WordPresenter.OnClickListener {

    private DialogSmartNavBinding binding;
    private ArrayObjectAdapter mRelatedAdapter;
    private ArrayObjectAdapter mHotAdapter;

    private SiteViewModel mSiteViewModel;
    private List<Site> mSites;
    private Queue<Word.Data> mQueue;
    private boolean mRunning;

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
        initViewModel();
        setRecyclerViews();
        startWorks();
    }

    private void initViewModel() {
        mSiteViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mSites = VodConfig.get().getSites().stream().filter(Site::isSearchable).collect(Collectors.toList());
        mQueue = new LinkedList<>();

        // ✨【修正】监听“对讲机”，接收一个叫 Result 的“包裹”
        mSiteViewModel.search.observe(getViewLifecycleOwner(), result -> {
            // ✨【修正】先“拆开包裹”，拿出里面的东西
            List<Vod> vods = result.getList();
            if (vods == null || vods.isEmpty()) {
                processQueue();
                return;
            }

            String poster = findPoster(vods);
            if (poster.isEmpty()) {
                processQueue();
                return;
            }

            Word.Data currentTask = mQueue.peek();
            if (currentTask != null) {
                currentTask.setPic(poster);
                updateAdapter(currentTask);
            }
            processQueue();
        });
    }

    private String findPoster(List<Vod> vods) {
        for (Vod vod : vods) {
            if (!TextUtils.isEmpty(vod.getPic())) {
                return vod.getPic();
            }
        }
        return "";
    }

    private void updateAdapter(Word.Data item) {
        int index = mRelatedAdapter.indexOf(item);
        if (index != -1) {
            mRelatedAdapter.notifyArrayItemRangeChanged(index, 1);
            return;
        }
        index = mHotAdapter.indexOf(item);
        if (index != -1) {
            mHotAdapter.notifyArrayItemRangeChanged(index, 1);
        }
    }

    private void processQueue() {
        mQueue.poll();

        if (mQueue.isEmpty()) {
            mRunning = false;
            return;
        }

        Word.Data nextTask = mQueue.peek();
        if (nextTask == null || TextUtils.isEmpty(nextTask.getTitle())) {
            processQueue();
        } else {
            // ✨【修正】使用正确的命令：(一堆商店, 一个电影名, false)
            mSiteViewModel.searchContent(mSites, nextTask.getTitle(), false);
        }
    }

    private void setRecyclerViews() {
        // ✨【修正】使用“转接头”ItemBridgeAdapter 来设置列表
        mRelatedAdapter = new ArrayObjectAdapter(new WordPresenter(this));
        binding.relatedRecycler.setHasFixedSize(true);
        binding.relatedRecycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        binding.relatedRecycler.setAdapter(new ItemBridgeAdapter(mRelatedAdapter));

        mHotAdapter = new ArrayObjectAdapter(new WordPresenter(this));
        binding.hotRecycler.setHasFixedSize(true);
        binding.hotRecycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        binding.hotRecycler.setAdapter(new ItemBridgeAdapter(mHotAdapter));
    }

    private void startWorks() {
        String keyword = getArguments().getString("keyword");
        if (TextUtils.isEmpty(keyword)) return;
        fetchSuggestions(keyword);
        fetchHotWords();
    }

    private void fetchSuggestions(String keyword) {
        OkHttp.newCall("https://suggest.video.iqiyi.com/?if=mobile&key=" + URLEncoder.encode(ZhuToPin.get(keyword))).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {}

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                List<Word.Data> suggestions = Word.objectFrom(response.body().string()).getData();
                if (suggestions.isEmpty()) return;

                App.post(() -> {
                    List<Word.Data> filtered = new ArrayList<>();
                    for (Word.Data item : suggestions) {
                        if (!item.getTitle().equals(keyword)) {
                            filtered.add(item);
                        }
                    }
                    mRelatedAdapter.setItems(filtered, null);
                    mQueue.addAll(filtered);
                    if (!mRunning) {
                        mRunning = true;
                        processQueue();
                    }
                });
            }
        });
    }

    private void fetchHotWords() {
        SuggestHelper.getHot(hotWords -> {
            if (hotWords == null || hotWords.isEmpty()) return;
            App.post(() -> {
                mHotAdapter.setItems(hotWords, null);
                mQueue.addAll(hotWords);
                if (!mRunning) {
                    mRunning = true;
                    processQueue();
                }
            });
        });
    }

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
