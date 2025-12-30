package com.fongmi.android.tv.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow; // ✨ 1. 导入“大相框”！
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.bean.Word;
import com.fongmi.android.tv.databinding.DialogSmartNavBinding;
import com.fongmi.android.tv.ui.activity.CollectActivity;
import com.fongmi.android.tv.ui.adapter.BaseDiffCallback;
import com.fongmi.android.tv.ui.custom.CustomRowPresenter;
import com.fongmi.android.tv.ui.custom.CustomSelector;
import com.fongmi.android.tv.ui.presenter.VodPresenter; // ✨ 2. 聘请“王牌工人”！
import com.fongmi.android.tv.utils.SuggestHelper;
import com.fongmi.android.tv.utils.ZhuToPin;
import com.github.catvod.net.OkHttp;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Response;

// ✨ 3. 让我们的弹窗，听“王牌工人”的话！
public class SmartNavDialog extends DialogFragment implements VodPresenter.OnClickListener {

    private DialogSmartNavBinding binding;
    private ArrayObjectAdapter mAdapter; // ✨ 4. 我们只需要一个总的“墙壁”适配器！

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
        setRecyclerView();
        startWorks();
    }

    // ✨ 5. 用你教给我的、最正确的方法来设置“墙壁”！
    private void setRecyclerViews() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        binding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        binding.recycler.setVerticalSpacing(16);
    }

    private void startWorks() {
        String keyword = getArguments().getString("keyword");
        fetchSuggestions(keyword);
        fetchHotWords();
    }

    // ✨ 6. 在获取到数据后，用你教我的“先装相册再挂相框”的方法来显示！
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
                        vod.setVodName(item.getTitle());
                        vod.setVodPic(item.getPic()); // 我们依然假设它有图片地址
                        vodList.add(vod);
                    }
                }

                App.post(() -> {
                    ArrayObjectAdapter listAdapter = new ArrayObjectAdapter(new VodPresenter(SmartNavDialog.this));
                    listAdapter.setItems(vodList, new BaseDiffCallback<>());
                    mAdapter.add(new ListRow(listAdapter)); // 把装好照片的“相框”挂到墙上！
                });
            }
        });
    }

    // ✨ 7. 对“大家都在看”也用同样正确的方法！
    private void fetchHotWords() {
        SuggestHelper.getHot(hotWords -> {
            if (hotWords == null || hotWords.isEmpty()) return;
            
            List<Vod> vodList = new ArrayList<>();
            for (Word.Data item : hotWords) {
                Vod vod = new Vod();
                vod.setVodName(item.getTitle());
                vod.setVodPic(item.getPic());
                vodList.add(vod);
            }

            App.post(() -> {
                ArrayObjectAdapter listAdapter = new ArrayObjectAdapter(new VodPresenter(SmartNavDialog.this));
                listAdapter.setItems(vodList, new BaseDiffCallback<>());
                mAdapter.add(new ListRow(listAdapter)); // 把第二个“相框”也挂到墙上！
            });
        });
    }

    // ✨ 8. 我们的点击事件，现在接收的是“王牌工人”递过来的、正确的“图纸” (Vod)！
    @Override
    public void onItemClick(Vod item) {
        CollectActivity.start(getActivity(), item.getVodName());
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
