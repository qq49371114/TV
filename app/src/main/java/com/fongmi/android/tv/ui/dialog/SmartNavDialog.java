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

    // --- ✨↓ 故事里的“工具”都在这里 ↓✨ ---
    private SiteViewModel mSiteViewModel; // 我们的“小笨手”帮工
    private List<Site> mSites; // 帮工知道的“商店”列表
    private Queue<Word.Data> mQueue; // 我们的“总任务单”
    private boolean mRunning; // 一个标记，表示“流水线”是否正在运行

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
        initViewModel(); // 准备好“帮工”和“对讲机”
        setRecyclerViews(); // 准备好界面
        startWorks(); // 开始工作！
    }

    // --- ✨↓ 准备我们的“帮工”和“对讲机” ↓✨ ---
    private void initViewModel() {
        mSiteViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mSites = VodConfig.get().getSites().stream().filter(Site::isSearchable).collect(Collectors.toList());
        mQueue = new LinkedList<>(); // 准备一个空的“总任务单”

        // ✨✨✨ 最核心的地方：监听“对讲机”！✨✨✨
        mSiteViewModel.search.observe(getViewLifecycleOwner(), vods -> {
            // “对讲机”响了，我们收到了帮工找回来的东西 (vods)
            if (vods.isEmpty()) { // 如果帮工说没找到
                processQueue(); // 我们就直接处理下一个任务
                return;
            }

            String poster = findPoster(vods); // 从帮工拿回来的东西里，找一张最好的海报
            if (poster.isEmpty()) { // 如果还是没找到海报
                processQueue(); // 我们也直接处理下一个任务
                return;
            }

            // ✨ 成功！我们拿到了海报！✨
            Word.Data currentTask = mQueue.peek(); // 看看当前正在处理的任务是哪个
            if (currentTask != null) {
                currentTask.setPic(poster); // 把海报给它
                updateAdapter(currentTask); // 通知界面，赶紧把这张海报显示出来！
            }
            processQueue(); // 全部搞定，处理下一个任务！
        });
    }

    // ✨ 一个小工具：从一堆结果里找海报
    private String findPoster(List<Vod> vods) {
        for (Vod vod : vods) {
            if (!TextUtils.isEmpty(vod.getPic())) {
                return vod.getPic();
            }
        }
        return "";
    }

    // ✨ 一个小工具：更新界面上某个项目的显示
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

    // ✨↓ “流水线”的发动机，负责处理“总任务单”上的任务 ↓✨
    private void processQueue() {
        mQueue.poll(); // 先把已经完成的任务从单子上划掉

        if (mQueue.isEmpty()) { // 如果单子空了
            mRunning = false; // 就关闭流水线，收工！
            return;
        }

        Word.Data nextTask = mQueue.peek(); // 看看下一个任务是什么
        if (nextTask == null || TextUtils.isEmpty(nextTask.getTitle())) {
            processQueue(); // 如果这个任务有问题，跳过，处理再下一个
        } else {
            // ✨ 一切正常，命令“帮工”去搜索下一个任务的海报！
            mSiteViewModel.searchContent(mSites.get(0), nextTask.getTitle());
        }
    }

    private void setRecyclerViews() {
        binding.relatedRecycler.setHasFixedSize(true);
        binding.relatedRecycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        binding.relatedRecycler.setAdapter(mRelatedAdapter = new ArrayObjectAdapter(new WordPresenter(this)));
        binding.hotRecycler.setHasFixedSize(true);
        binding.hotRecycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        binding.hotRecycler.setAdapter(mHotAdapter = new ArrayObjectAdapter(new WordPresenter(this)));
    }

    private void startWorks() {
        String keyword = getArguments().getString("keyword");
        if (TextUtils.isEmpty(keyword)) return;
        fetchSuggestions(keyword); // 获取“为你推荐”
        fetchHotWords(); // 获取“大家都在看”
    }

    // ✨↓ 获取“为你推荐”的电影，并把它们放到“总任务单”上 ↓✨
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
                    mRelatedAdapter.setItems(filtered, null); // 1. 先把文字显示出来
                    mQueue.addAll(filtered); // 2. 把任务加到“总任务单”
                    if (!mRunning) { // 3. 如果流水线没在运行，就启动它！
                        mRunning = true;
                        processQueue();
                    }
                });
            }
        });
    }

    // ✨↓ 获取“大家都在看”的电影，也把它们放到“总任务单”上 ↓✨
    private void fetchHotWords() {
        SuggestHelper.getHot(hotWords -> {
            if (hotWords == null || hotWords.isEmpty()) return;
            App.post(() -> {
                mHotAdapter.setItems(hotWords, null); // 1. 先显示文字
                mQueue.addAll(hotWords); // 2. 把任务加到“总任务单”
                if (!mRunning) { // 3. 如果流水线没在运行，就启动它！
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
