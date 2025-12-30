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
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.bean.Word;
import com.fongmi.android.tv.databinding.DialogSmartNavBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.activity.CollectActivity;
import com.fongmi.android.tv.ui.adapter.BaseDiffCallback;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
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

public class SmartNavDialog extends DialogFragment implements VodPresenter.OnClickListener {

    private DialogSmartNavBinding binding;
    private ArrayObjectAdapter mRelatedAdapter;
    private ArrayObjectAdapter mHotAdapter;

    // --- ✨↓ “借海报”计划的“作战指挥部” ↓✨ ---
    private SiteViewModel mSiteViewModel;
    private List<Site> mSites;
    private Queue<Vod> mQueue; // 我们的“任务清单”，这次里面直接放 Vod 对象
    private boolean mRunning;  // “海报突击队”是否正在行动

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

    // ✨ 1. 准备我们的“海报突击队”和“对讲机”
    private void initViewModel() {
        mSiteViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mSites = VodConfig.get().getSites().stream().filter(Site::isSearchable).collect(Collectors.toList());
        mQueue = new LinkedList<>();

        // ✨ 监听“对讲机”！
        mSiteViewModel.search.observe(getViewLifecycleOwner(), result -> {
            List<Vod> vods = result.getList();
            if (vods == null || vods.isEmpty()) {
                processQueue(); // 没搜到，处理下一个
                return;
            }
            // 从返回的结果里，找一张最好的海报
            String poster = findPoster(vods);
            if (poster.isEmpty()) {
                processQueue(); // 还是没找到，处理下一个
                return;
            }
            // ✨ 成功！我们拿到了海报！✨
            Vod currentTask = mQueue.peek(); // 看看当前正在处理的任务是哪个
            if (currentTask != null) {
                currentTask.setPic(poster); // 把“借”来的海报地址，给我们的任务对象
                updateAdapter(currentTask); // 通知界面，赶紧把这张海报显示出来！
            }
            processQueue(); // 全部搞定，处理下一个任务！
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

    private void updateAdapter(Vod item) {
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

    // ✨ “海报突击队”的“引擎”，负责处理“任务清单”
    private void processQueue() {
        mQueue.poll(); // 先把已经完成的任务从单子上划掉
        if (mQueue.isEmpty()) {
            mRunning = false; // 任务都完成了，收队！
            return;
        }
        Vod nextTask = mQueue.peek();
        if (nextTask == null || TextUtils.isEmpty(nextTask.getName())) {
            processQueue(); // 这个任务有问题，跳过
        } else {
            // ✨ 命令“海报突击队”，去搜索下一个任务的海报！
            mSiteViewModel.searchContent(mSites, nextTask.getName(), false);
        }
    }

    private void setRecyclerViews() {
        binding.relatedRecycler.setAdapter(new ItemBridgeAdapter(mRelatedAdapter = new ArrayObjectAdapter(new VodPresenter(this))));
        binding.hotRecycler.setAdapter(new ItemBridgeAdapter(mHotAdapter = new ArrayObjectAdapter(new VodPresenter(this))));
    }

    private void startWorks() {
        String keyword = getArguments().getString("keyword");
        // ✨ 我们用一个安全的“链式调用”，来保证两个列表不会打架！
        fetchSuggestions(keyword, () -> fetchHotWords());
    }

    // ✨ 2. 获取“为你推荐”，完成后，再触发下一步
    private void fetchSuggestions(String keyword, Runnable callback) {
        OkHttp.newCall("https://suggest.video.iqiyi.com/?if=mobile&key=" + URLEncoder.encode(ZhuToPin.get(keyword))).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) { callback.run(); }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                List<Word.Data> suggestions = Word.objectFrom(response.body().string()).getData();
                List<Vod> vodList = new ArrayList<>();
                if (!suggestions.isEmpty()) {
                    for (Word.Data item : suggestions) {
                        if (!item.getTitle().equals(keyword)) {
                            Vod vod = new Vod();
                            vod.setName(item.getTitle());
                            vod.setPic(item.getPic()); // 此时的 pic 很可能是空的
                            vodList.add(vod);
                        }
                    }
                }
                App.post(() -> {
                    mRelatedAdapter.setItems(vodList, new BaseDiffCallback<>());
                    mQueue.addAll(vodList); // 把任务加入“任务清单”
                    callback.run(); // ✨ 通知“链条”，可以进行下一步了！
                });
            }
        });
    }

    // ✨ 3. 获取“大家都在看”，完成后，启动“海报突击队”！
    private void fetchHotWords() {
        SuggestHelper.getHot(hotWords -> {
            List<Vod> vodList = new ArrayList<>();
            if (hotWords != null && !hotWords.isEmpty()) {
                for (Word.Data item : hotWords) {
                    Vod vod = new Vod();
                    vod.setName(item.getTitle());
                    vod.setPic(item.getPic());
                    vodList.add(vod);
                }
            }
            App.post(() -> {
                mHotAdapter.setItems(vodList, new BaseDiffCallback<>());
                mQueue.addAll(vodList); // 把第二批任务也加入“任务清单”
                // ✨✨✨ 所有“步兵”都已就位，启动“海报突击队”！✨✨✨
                if (!mRunning && !mQueue.isEmpty()) {
                    mRunning = true;
                    processQueue();
                }
            });
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
