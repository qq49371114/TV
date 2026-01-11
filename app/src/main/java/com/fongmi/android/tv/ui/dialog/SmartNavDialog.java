package com.fongmi.android.tv.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.ApiConfig;
import com.fongmi.android.tv.bean.Hot;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterNavBinding;
import com.fongmi.android.tv.databinding.AdapterNavVodBinding; // 我们需要一个新的海报项布局
import com.fongmi.android.tv.databinding.DialogSmartNavBinding;
import com.fongmi.android.tv.ui.adapter.VodAdapter; // 我们会借用它的 ViewHolder
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;
import java.util.ArrayList;
import java.util.List;

public class SmartNavDialog extends DialogFragment {

    private Listener listener;
    private HybridAdapter mAdapter;

    public static SmartNavDialog newInstance(List<Vod> recommendations) {
        SmartNavDialog dialog = new SmartNavDialog();
        Bundle args = new Bundle();
        if (recommendations != null && !recommendations.isEmpty()) {
            args.putParcelableArrayList("recs", new ArrayList<>(recommendations));
        }
        dialog.setArguments(args);
        return dialog;
    }

    public SmartNavDialog listener(Listener listener) {
        this.listener = listener;
        return this;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 这里的 dialog_smart_nav.xml 就是我们公共的那个，里面只有一个 RecyclerView
        DialogSmartNavBinding binding = DialogSmartNavBinding.inflate(inflater, container, false);
        setupRecyclerView(binding.recycler); // 假设列表的id是recycler
        return binding.getRoot();
    }

    private void setupRecyclerView(RecyclerView recyclerView) {
        List<Object> items = new ArrayList<>();
        List<Vod> recs = getArguments().getParcelableArrayList("recs");

        // 1. 准备好所有要显示的数据，按顺序放进一个大列表
        if (recs != null && !recs.isEmpty()) {
            items.add(ResUtil.getString(R.string.nav_recommend)); // "为你推荐" 标题
            items.addAll(recs); // 所有推荐影片
        }
        if (ApiConfig.get() != null && !ApiConfig.get().getSites().isEmpty()) {
            items.add(ResUtil.getString(R.string.nav_site)); // "站点导航" 标题
            items.addAll(ApiConfig.get().getSites());
        }
        // ... 如果还要加热搜，也可以加进来 ...

        mAdapter = new HybridAdapter(items);
        
        // 2. ⭐最关键的魔法：GridLayoutManager + SpanSizeLookup⭐
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 5); // 假设一行最多5个海报
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                // 如果是影片海报，占1个格子；如果是标题或站点，占满一行（5个格子）
                return mAdapter.isVod(position) ? 1 : 5;
            }
        });
        
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(mAdapter);
    }

    // ... 省略了 Listener 接口定义 ...
    public interface Listener {
        void onVodClick(Vod item);
        void onSiteClick(Site item);
        // ...
    }

    // 3. ⭐一个能显示所有东西的“超级适配器”⭐
    class HybridAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_TITLE = 0;
        private static final int TYPE_VOD = 1;
        private static final int TYPE_SITE = 2;

        private final List<Object> mItems;

        public HybridAdapter(List<Object> items) {
            this.mItems = items;
        }

        public boolean isVod(int position) {
            return mItems.get(position) instanceof Vod;
        }

        @Override
        public int getItemViewType(int position) {
            Object item = mItems.get(position);
            if (item instanceof String) return TYPE_TITLE;
            if (item instanceof Vod) return TYPE_VOD;
            if (item instanceof Site) return TYPE_SITE;
            return super.getItemViewType(position);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_VOD) {
                // 使用我们为海报创建的新布局
                return new VodHolder(AdapterNavVodBinding.inflate(inflater, parent, false));
            } else { // 标题和站点都用同一种简单的文本布局
                return new TextHolder(AdapterNavBinding.inflate(inflater, parent, false));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object item = mItems.get(position);
            if (holder.getItemViewType() == TYPE_VOD) {
                ((VodHolder) holder).bind((Vod) item);
            } else if (holder.getItemViewType() == TYPE_TITLE) {
                ((TextHolder) holder).bindTitle((String) item);
            } else if (holder.getItemViewType() == TYPE_SITE) {
                ((TextHolder) holder).bindSite((Site) item);
            }
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        // 海报的 ViewHolder
        class VodHolder extends RecyclerView.ViewHolder {
            private final AdapterNavVodBinding binding;
            VodHolder(AdapterNavVodBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
                binding.getRoot().setOnClickListener(v -> {
                    if (listener != null) listener.onVodClick((Vod) mItems.get(getAdapterPosition()));
                    dismiss();
                });
            }
            void bind(Vod item) {
                ImgUtil.load(item.getVodPic(), binding.poster);
            }
        }

        // 文字的 ViewHolder
        class TextHolder extends RecyclerView.ViewHolder {
            private final AdapterNavBinding binding;
            TextHolder(AdapterNavBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
            void bindTitle(String title) {
                binding.text.setText(title);
                itemView.setFocusable(false);
                itemView.setClickable(false);
            }
            void bindSite(Site site) {
                binding.text.setText(site.getName());
                itemView.setOnClickListener(v -> {
                    if (listener != null) listener.onSiteClick(site);
                    dismiss();
                });
            }
        }
    }
}
