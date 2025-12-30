package com.fongmi.android.tv.ui.dialog;

// --- ✨↓ 婉儿把所有需要的“身份证”，都帮你写在这里了！↓✨ ---
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
import com.fongmi.android.tv.api.Api;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.bean.Word;
import com.fongmi.android.tv.databinding.DialogSmartNavBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.net.OkHttp;
import com.fongmi.android.tv.ui.activity.CollectActivity;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.presenter.WordPresenter;
import com.fongmi.android.tv.utils.SuggestHelper;
import com.fongmi.android.tv.utils.ZhuToPin;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
// --- ✨↑ “身份证”补全完毕！↑✨ ---

public class SmartNavDialog extends DialogFragment implements WordPresenter.OnClickListener {

    private DialogSmartNavBinding binding;
    private ArrayObjectAdapter mRelatedAdapter;
    private ArrayObjectAdapter mHotAdapter;
    
    // --- ✨↓ 我们为它装上了“大脑”和“通讯录”！↓✨ ---
    private SiteViewModel mSiteViewModel;
    private List<Site> mSites;

    // --- ✨↓ 改造 newInstance，只接收一个关键词！↓✨ ---
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
        // ✨ 1. 初始化我们新加的“器官”
        initViewModel();
        // ✨ 2. 初始化界面
        setRecyclerViews();
        // ✨ 3. 开始干活！
        startWorks();
    }

    // --- ✨↓ 新增的初始化方法 ↓✨ ---
    private void initViewModel() {
        mSiteViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mSites = VodConfig.get().getSites().stream().filter(Site::isSearchable).collect(Collectors.toList());
    }
    
    private void setRecyclerViews() {
        binding.relatedRecycler.setHasFixedSize(true);
        binding.relatedRecycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        binding.relatedRecycler.setAdapter(new ItemBridgeAdapter(mRelatedAdapter = new ArrayObjectAdapter(new WordPresenter(this))));
        binding.hotRecycler.setHasFixedSize(true);
        binding.hotRecycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        binding.hotRecycler.setAdapter(new ItemBridgeAdapter(mHotAdapter = new ArrayObjectAdapter(new WordPresenter(this))));
    }

    // --- ✨↓ 核心工作方法，取代了旧的 setData() ↓✨ ---
    private void startWorks() {
        String keyword = getArguments().getString("keyword");
        if (TextUtils.isEmpty(keyword)) return;

        // ✨ a. 自己去获取“为你推荐”
        fetchSuggestions(keyword);
        // ✨ b. 自己去获取“大家都在看”
        fetchHotWords();
    }

    private void fetchSuggestions(String keyword) {
        OkHttp.newCall("https://suggest.video.iqiyi.com/?if=mobile&key=" + URLEncoder.encode(ZhuToPin.get(keyword))).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {}

            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) throws IOException {
                List<Word.Data> suggestions = Word.objectFrom(response.body().string()).getData();
                if (suggestions.isEmpty()) return;
                
                // 为推荐列表“借”海报
                borrowPictures(suggestions, () -> App.post(() -> {
                    // 过滤掉和当前视频名字一样的
                    List<Word.Data> filtered = new ArrayList<>();
                    for (Word.Data item : suggestions) {
                        if (!item.getTitle().equals(keyword)) {
                            filtered.add(item);
                        }
                    }
                    mRelatedAdapter.setItems(filtered, null);
                }));
            }
        });
    }

    private void fetchHotWords() {
        SuggestHelper.getHot(hotWords -> {
            if (hotWords == null || hotWords.isEmpty()) return;
            // 为热搜列表“借”海报
            borrowPictures(hotWords, () -> App.post(() -> mHotAdapter.setItems(hotWords, null)));
        });
    }

    // --- ✨↓ 我们把“借海报”的逻辑，也封装成了一个方法！↓✨ ---
    private void borrowPictures(List<Word.Data> list, Runnable onCompleted) {
        AtomicInteger counter = new AtomicInteger(list.size());
        if (counter.get() == 0) {
            onCompleted.run();
            return;
        }
        for (Word.Data item : list) {
            mSiteViewModel.searchContent(mSites, item.getTitle(), new Api.Callback<Vod>() {
                @Override
                public void onResponse(List<Vod> items) {
                    if (items != null && !items.isEmpty()) {
                        for(Vod vod : items) {
                            if(!TextUtils.isEmpty(vod.getPic())) {
                                item.setPic(vod.getPic());
                                break;
                            }
                        }
                    }
                    if (counter.decrementAndGet() == 0) onCompleted.run();
                }
                @Override
                public void onError(Throwable e) {
                    if (counter.decrementAndGet() == 0) onCompleted.run();
                }
            });
        }
    }

    // --- ✨↓ 点击事件的逻辑是完全正确的，我们保留它！↓✨ ---
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
