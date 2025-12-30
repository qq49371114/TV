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
import com.fongmi.android.tv.ui.presenter.VodPresenter; // ✨ 1. 聘请你找到的“王牌工人”
import com.fongmi.android.tv.utils.SuggestHelper;
import com.fongmi.android.tv.utils.ZhuToPin;
import com.github.catvod.net.OkHttp;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Response;

// ✨ 2. 让我们的弹窗，听从“王牌工人”的合同！
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

    // ✨ 3. 使用我们最开始的、稳定的“双列表”布局，并为它们都聘请“王牌工人”！
    private void setRecyclerViews() {
        binding.relatedRecycler.setAdapter(new ItemBridgeAdapter(mRelatedAdapter = new ArrayObjectAdapter(new VodPresenter(this))));
        binding.hotRecycler.setAdapter(new ItemBridgeAdapter(mHotAdapter = new ArrayObjectAdapter(new VodPresenter(this))));
    }

    private void startWorks() {
        String keyword = getArguments().getString("keyword");
        fetchSuggestions(keyword);
        fetchHotWords();
    }

    // ✨ 4. 在获取到数据后，把正确的“图纸”(Vod)交给“王牌工人”！
    private void fetchSuggestions(String keyword) {
        OkHttp.newCall("https://suggest.video.iqiyi.com/?if=mobile&key=" + URLEncoder.encode(ZhuToPin.get(keyword))).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {}

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                List<Word.Data> suggestions = Word.objectFrom(response.body().string()).getData();
                if (suggestions.isEmpty()) return;

                List<Vod> vodList = new ArrayList<>();
                for (Word.Data item : suggestions) {
                    if (!item.getTitle().equals(keyword)) {
                        Vod vod = new Vod();
                        vod.setName(item.getTitle()); // ✨【最终修正】使用你找到的、正确的 setName 方法！
                        vod.setPic(item.getPic());   // ✨【最终修正】使用你找到的、正确的 setPic 方法！
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
                vod.setName(item.getTitle()); // ✨【最终修正】使用你找到的、正确的 setName 方法！
                vod.setPic(item.getPic());   // ✨【最终修正】使用你找到的、正确的 setPic 方法！
                vodList.add(vod);
            }

            App.post(() -> mHotAdapter.setItems(vodList, new BaseDiffCallback<>()));
        });
    }

    @Override
    public void onItemClick(Vod item) {
        CollectActivity.start(getActivity(), item.getName()); // ✨【最终修正】使用你找到的、正确的 getName 方法！
        dismiss();
    }

    // ✨ 5. 补上了“王牌工人”合同里要求的“长按”方法！
    @Override
    public boolean onLongClick(Vod item) {
        return false; // 我们暂时用不到它，但必须有！
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }
}
