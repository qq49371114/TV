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

    // --- ✨↓ “借海报”计划的“作战指挥部”又回来了！而且更强大了！↓✨ ---
    private SiteViewModel mSiteViewModel;
    private List<Site> mSites;
    private Queue<Vod> mQueue; // 我们的“任务清单”
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

        // ✨ 监听正确的“result”频道！
        mSiteViewModel.result.observe(getViewLifecycleOwner(), result -> {
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
            Vod currentTask = mQueue.peek();
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

    // ✨ “海报突击队”的“引擎”
    private void processQueue() {
        mQueue.poll();
        if (mQueue.isEmpty()) {
            mRunning = false;
            return;
        }
        Vod nextTask = mQueue.peek();
        if (nextTask == null || TextUtils.isEmpty(nextTask.getName()) || mSites.isEmpty()) {
            processQueue();
        } else {
            // ✨ 命令“海报突击队”，进行“地毯式轰炸”！
            mSiteViewModel.searchContent(mSites, nextTask.getName(), false);
        }
    }

    private void setRecyclerViews() {
        binding.relatedRecycler.setAdapter(new ItemBridgeAdapter(mRelatedAdapter = new ArrayObjectAdapter(new VodPresenter(this))));
        binding.hotRecycler.setAdapter(new ItemBridgeAdapter(mHotAdapter = new ArrayObjectAdapter(new VodPresenter(this))));
    }

    // ✨ 我们让两个列表“同时起跑”，不再搞“接力赛”！
    private void startWorks() {
        String keyword = getArguments().getString("keyword");
        fetchSuggestions(keyword);
        fetchHotWords();
    }

    // ✨ 获取“为你推荐”
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

                App.post(() -> {
                    mRelatedAdapter.setItems(vodList, new BaseDiffCallback<>());
                    mQueue.addAll(vodList); // 把任务加入“任务清单”
                    startProcess(); // ✨ 尝试启动“海报突击队”
                });
            }
        });
    }

    // ✨ 获取“大家都在看”
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

            App.post(() -> {
                mHotAdapter.setItems(vodList, new BaseDiffCallback<>());
                mQueue.addAll(vodList); // 把第二批任务也加入“任务清单”
                startProcess(); // ✨ 再次尝试启动“海报突击队”
            });
        });
    }

    // ✨✨✨【全新的“门禁”系统】✨✨✨
    // 这是一个同步方法，可以防止两个侦察队同时启动突击队！
    private synchronized void startProcess() {
        // 如果“海报突击队”没在行动，并且“任务清单”里有任务，就启动它！
        if (!mRunning && !mQueue.isEmpty()) {
            mRunning = true;
            processQueue();
        }
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
