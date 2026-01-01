package com.fongmi.android.tv.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.bean.Word;
import com.fongmi.android.tv.databinding.DialogSmartNavBinding;
import com.fongmi.android.tv.databinding.FragmentRecommendPageBinding;
import com.fongmi.android.tv.ui.activity.CollectActivity;
import com.fongmi.android.tv.ui.adapter.BaseDiffCallback;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.SuggestHelper;
import com.fongmi.android.tv.utils.ZhuToPin;
import com.github.catvod.net.OkHttp;
import com.google.android.material.tabs.TabLayoutMediator;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import okhttp3.Call;
import okhttp3.Response;

public class SmartNavDialog extends DialogFragment implements VodPresenter.OnClickListener {

    private DialogSmartNavBinding binding;
    private String keyword;
    private final List<String> titles = Arrays.asList("为你推荐", "大家都在看");

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
        keyword = getArguments().getString("keyword");
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViewPager();
    }

    private void initViewPager() {
        PageAdapter adapter = new PageAdapter(getActivity());
        binding.viewPager.setAdapter(adapter);
        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> tab.setText(titles.get(position))).attach();
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

    // --- Adapter for ViewPager ---
    public class PageAdapter extends FragmentStateAdapter {

        public PageAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return RecommendFragment.newInstance(position, keyword, SmartNavDialog.this);
        }

        @Override
        public int getItemCount() {
            return titles.size();
        }
    }

    // --- Fragment for each page ---
    public static class RecommendFragment extends Fragment {

        private FragmentRecommendPageBinding binding;
        private ArrayObjectAdapter mAdapter;
        private VodPresenter.OnClickListener mListener;
        private int position;
        private String keyword;

        public static RecommendFragment newInstance(int position, String keyword, VodPresenter.OnClickListener listener) {
            Bundle args = new Bundle();
            args.putInt("position", position);
            args.putString("keyword", keyword);
            RecommendFragment fragment = new RecommendFragment();
            fragment.setArguments(args);
            fragment.mListener = listener;
            return fragment;
        }


        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            // 注意：这里需要你手动创建一个 binding 类，或者直接用 findViewById
            // 假设你已经创建了 FragmentRecommendPageBinding
            View view = inflater.inflate(R.layout.fragment_recommend_page, container, false);
            binding = FragmentRecommendPageBinding.bind(view);
            position = getArguments().getInt("position");
            keyword = getArguments().getString("keyword");
            return view;
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            setRecyclerView();
            fetchData();
        }

        private void setRecyclerView() {
            binding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(new VodPresenter(mListener))));
        }

        private void fetchData() {
            if (position == 0) {
                fetchSuggestions();
            } else {
                fetchHotWords();
            }
        }

        private void fetchSuggestions() {
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
    }
}
