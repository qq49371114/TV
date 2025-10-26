package com.fongmi.android.tv.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.text.TextUtils;

import androidx.annotation.NonNull; // <-- 修复：添加导入
import androidx.annotation.Nullable; // <-- 修复：添加导入
import androidx.appcompat.app.AlertDialog;
import androidx.core.splashscreen.SplashScreen;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.Target;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Func;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityHomeBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.ui.adapter.BaseDiffCallback;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomRowPresenter;
import com.fongmi.android.tv.ui.custom.CustomSelector;
import com.fongmi.android.tv.ui.custom.CustomTitleView;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.presenter.FuncPresenter;
import com.fongmi.android.tv.ui.presenter.HeaderPresenter;
import com.fongmi.android.tv.ui.presenter.HistoryPresenter;
import com.fongmi.android.tv.ui.presenter.ProgressPresenter;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.google.common.collect.Lists;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeActivity extends BaseActivity implements CustomTitleView.Listener, VodPresenter.OnClickListener, FuncPresenter.OnClickListener, HistoryPresenter.OnClickListener {

    private ActivityHomeBinding mBinding;
    private ArrayObjectAdapter mHistoryAdapter;
    private ArrayObjectAdapter mFuncAdapter;
    private HistoryPresenter mPresenter;
    private ArrayObjectAdapter mAdapter;
    private SiteViewModel mViewModel;
    private boolean loading;
    private Result mResult;
    private Clock mClock;

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        checkAction(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initView() {
        runGuardCheck();
    }

    private void initSystem() {
        mClock = Clock.create(mBinding.clock);
        mBinding.progressLayout.showProgress();
        Updater.create().start(this);
        mResult = Result.empty();
        Server.get().start();
        setRecyclerView();
        setViewModel();
        setAdapter();
        initConfig();
        setLogo();
    }

    @Override
    protected void initEvent() {
        mBinding.title.setListener(this);
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                mBinding.toolbar.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
                if (mPresenter != null && mPresenter.isDelete()) setHistoryDelete(false);
            }
        });
    }

    private void checkAction(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            VideoActivity.push(this, intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            if ("text/plain".equals(intent.getType()) || UrlUtil.path(intent.getData()).endsWith(".m3u")) {
                loadLive("file:/" + FileChooser.getPathFromUri(this, intent.getData()));
            } else {
                VideoActivity.push(this, intent.getData().toString());
            }
        }
    }

    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(Integer.class, new HeaderPresenter());
        selector.addPresenter(String.class, new ProgressPresenter());
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), FuncPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), HistoryPresenter.class);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.result.observe(this, result -> {
            mAdapter.remove("progress");
            addVideo(mResult = result);
        });
        mViewModel.search.observe(this, result -> {
            VideoActivity.detail(this, result);
        });
    }

    private void setAdapter() {
        mHistoryAdapter = new ArrayObjectAdapter(mPresenter = new HistoryPresenter(this));
        mAdapter.add(new ListRow(mFuncAdapter = new ArrayObjectAdapter(new FuncPresenter(this))));
        mAdapter.add(R.string.home_history);
        mAdapter.add(R.string.home_recommend);
    }

    private void initConfig() {
        if (isLoading()) return;
        WallConfig.get().init();
        LiveConfig.get().init().load();
        VodConfig.get().init().load(getCallback());
        setLoading(true);
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                mBinding.progressLayout.showContent();
                checkAction(getIntent());
                getHistory();
                getVideo();
                setFocus();
                setFunc();
                setLogo();
            }

            @Override
            public void error(String msg) {
                mBinding.progressLayout.showContent();
                mResult = Result.empty();
                Notify.show(msg);
                setFocus();
                setFunc();
            }
        };
    }

    // --- 【婉儿为您植入的、专属的“安防工具箱”】 (已恢复) ---

private void runGuardCheck() {
    // 检查“家长模式”是否已永久开启
    if ("true".equals(SecurePrefs.getString("parent_mode_enabled", "false"))) {
        initSystem();
        return;
    }
    // 检查是否在时间锁定时段或设置了密码
    boolean isTimeLocked = isTimeLocked();
    String storedPassword = SecurePrefs.getString("app_password", "");
    if (isTimeLocked || !TextUtils.isEmpty(storedPassword)) {
        // 如果是，则显示锁定对话框
        showLockDialog(isTimeLocked, storedPassword);
    } else {
        // 否则，直接初始化系统
        initSystem();
    }
}

private void showLockDialog(boolean isTimeLocked, String correctPassword) {
    LayoutInflater inflater = LayoutInflater.from(this);
    // 注意：这里需要您的项目中有一个名为 "dialog_lock.xml" 的布局文件
    View dialogView = inflater.inflate(R.layout.dialog_lock, null);
    TextView lockMessage = dialogView.findViewById(R.id.lockMessage);
    EditText passwordInput = dialogView.findViewById(R.id.password);

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setView(dialogView);
    builder.setCancelable(false);
    AlertDialog dialog = builder.create();

    if (isTimeLocked) {
        lockMessage.setVisibility(View.VISIBLE);
        StringBuilder sb = new StringBuilder("休息时间到啦\n允许使用时间段:\n");
        // 注意：这里需要您的项目中有 "SecurePrefs" 这个类和它的内部类 "TimeSlot"
        for (SecurePrefs.TimeSlot slot : SecurePrefs.getTimeSlots()) {
            sb.append(slot.start).append(" - ").append(slot.end).append("\n");
        }
        lockMessage.setText(sb.toString().trim());
        passwordInput.setHint("请输入超级密码解锁");
    } else {
        lockMessage.setVisibility(View.GONE);
        passwordInput.setHint("请输入密码");
    }

    passwordInput.setOnEditorActionListener((v, actionId, event) -> {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            String input = v.getText().toString();
            // 超级密码，可永久开启家长模式
            if (input.equals("婉儿最棒")) {
                SecurePrefs.put("parent_mode_enabled", "true");
                Toast.makeText(this, "欢迎您，主人！家长模式已永久开启。", Toast.LENGTH_LONG).show();
                dialog.dismiss();
                initSystem();
            // 如果是时间锁定状态，普通密码无效
            } else if (isTimeLocked) {
                Toast.makeText(this, "当前为休息时间，请输入超级密码解锁", Toast.LENGTH_LONG).show();
                v.setText("");
            // 检查普通密码
            } else if (input.equals(correctPassword)) {
                dialog.dismiss();
                initSystem();
            } else {
                Toast.makeText(this, "密码错误！", Toast.LENGTH_SHORT).show();
                v.setText("");
            }
            return true;
        }
        return false;
    });
    dialog.show();
}

private boolean isTimeLocked() {
    // 注意：这里需要您的项目中有 "SecurePrefs" 这个类
    List<SecurePrefs.TimeSlot> slots = SecurePrefs.getTimeSlots();
    if (slots.isEmpty()) return false;
    try {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        Date current = sdf.parse(sdf.format(new Date()));
        for (SecurePrefs.TimeSlot slot : slots) { // 使用已获取的 slots 列表
            Date start = sdf.parse(slot.start);
            Date end = sdf.parse(slot.end);
            // 检查是否在允许的时间段内（支持跨天设置）
            boolean isAllowed = start.after(end) ? (current.after(start) || current.before(end)) : (current.after(start) && current.before(end));
            if (isAllowed) return false; // 在允许的时间段内，未被锁定
        }
    } catch (Exception e) {
        e.printStackTrace();
        return false; // 出现异常则不锁定
    }
    return true; // 不在任何允许的时间段内，已被锁定
}

    private void setFunc() {
        List<Func> items = new ArrayList<>();
        items.add(Func.create(R.string.home_vod));
        if (LiveConfig.hasUrl()) items.add(Func.create(R.string.home_live));
        items.add(Func.create(R.string.home_search));
        items.add(Func.create(R.string.home_keep));
        items.add(Func.create(R.string.home_push));
        items.add(Func.create(R.string.home_cast));
        items.add(Func.create(R.string.home_setting));
        mFuncAdapter.setItems(items, new BaseDiffCallback<Func>());
    }

    private void getHistory() {
        getHistory(false);
    }

    private void getHistory(boolean renew) {
        List<History> items = History.get();
        int historyIndex = getHistoryIndex();
        int recommendIndex = getRecommendIndex();
        boolean exist = recommendIndex - historyIndex == 2;
        if (renew) mHistoryAdapter = new ArrayObjectAdapter(mPresenter = new HistoryPresenter(this));
        if ((items.isEmpty() && exist) || (renew && exist)) mAdapter.removeItems(historyIndex, 1);
        if ((!items.isEmpty() && !exist) || (renew && exist)) mAdapter.add(historyIndex, new ListRow(mHistoryAdapter));
        mHistoryAdapter.setItems(items, new BaseDiffCallback<History>());
    }

    private void setHistoryDelete(boolean delete) {
        mPresenter.setDelete(delete);
        mHistoryAdapter.notifyArrayItemRangeChanged(0, mHistoryAdapter.size());
    }

    private void clearHistory() {
        mAdapter.removeItems(getHistoryIndex(), 1);
        History.delete(VodConfig.getCid());
        mPresenter.setDelete(false);
        mHistoryAdapter.clear();
    }

    private int getHistoryIndex() {
        return mAdapter.indexOf(R.string.home_history) + 1;
    }

    private int getRecommendIndex() {
        return mAdapter.indexOf(R.string.home_recommend) + 1;
    }

    private boolean isLoading() {
        return loading;
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
    }

    private void setLogo() {
        Glide.with(this).load(UrlUtil.convert(VodConfig.get().getConfig().getLogo())).circleCrop().override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL).error(R.drawable.ic_logo).into(mBinding.logo);
    }

    // --- 修复：补充缺失的方法 ---

    private void addVideo(Result result) {
        if (result.getList().isEmpty()) return;
        List<ListRow> rows = new ArrayList<>();
        for (List<Vod> items : Lists.partition(result.getList(), Product.getColumn())) {
            ArrayObjectAdapter adapter = new ArrayObjectAdapter(new VodPresenter(this, Style.rect()));
            adapter.setItems(items, new BaseDiffCallback<>());
            rows.add(new ListRow(adapter));
        }
        mAdapter.addAll(getRecommendIndex(), rows);
    }
    
    private void getVideo() {
        mResult.getList().clear();
        mAdapter.removeItems(getRecommendIndex(), mAdapter.size() - getRecommendIndex());
        if (getHome().getKey().isEmpty()) return;
        mAdapter.add("progress");
        mViewModel.homeContent(getHome());
    }

    private void setFocus() {
        mBinding.recycler.post(() -> mBinding.recycler.scrollToPosition(0));
    }

    private void loadLive(String path) {
        LiveActivity.start(this, LiveConfig.get().find(path));
    }


    // --- 接口实现 ---

    @Override
    public void onLogoClick() {
        new SiteDialog(this).show();
    }

    @Override
    public void onSearchClick() {
        SearchActivity.start(this);
    }

    @Override
    public void onRefreshClick() {
        initConfig();
    }

    @Override
    public void onItemClick(Func item) {
        switch (item.getResId()) {
            case R.string.home_vod:
                VodActivity.start(this, mResult.clear());
                break;
            case R.string.home_live:
                LiveActivity.start(this);
                break;
            case R.string.home_search:
                SearchActivity.start(this);
                break;
            case R.string.home_keep:
                KeepActivity.start(this);
                break;
            case R.string.home_push:
                PushActivity.start(this);
                break;
            case R.string.home_cast:
                CastActivity.start(this);
                break;
            case R.string.home_setting:
                SettingActivity.start(this);
                break;
        }
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.getVodId().startsWith("msearch:")) {
            mViewModel.searchContent(item.getVodId().substring(8));
        } else {
            VideoActivity.detail(this, item.getVodId());
        }
    }

    @Override
    public boolean onLongClick(Vod item) {
        // 在这里实现长按逻辑，如果需要的话
        return true;
    }

    @Override
    public void onItemClick(History item) {
        VideoActivity.detail(this, item.getVodId(), item.getVodPart(), item.getVodName());
    }

    @Override
    public boolean onLongClick(History item) {
        setHistoryDelete(!mPresenter.isDelete());
        return true;
    }

    @Override
    public void onDeleteClick(History item) {
        if (mPresenter.isDelete()) {
            mHistoryAdapter.remove(item);
            History.delete(item.getKey());
            if (mHistoryAdapter.size() > 0) return;
            mAdapter.removeItems(getHistoryIndex(), 1);
        } else {
            clearHistory();
        }
    }
    
    // --- 事件订阅 ---

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case CONFIG:
                setFunc();
                setLogo();
                break;
            case VIDEO:
                getVideo();
                break;
            case HISTORY:
                getHistory();
                break;
            case SIZE:
                getVideo();
                getHistory(true);
                break;
        }
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        switch (event.getType()) {
            case SEARCH:
                CollectActivity.start(this, event.getText());
                break;
            case PUSH:
                VideoActivity.push(this, event.getText());
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        VodConfig.load(event.getConfig(), getCallback(event));
    }

    private Callback getCallback(CastEvent event) {
        return new Callback() {
            @Override
            public void success() {
                RefreshEvent.history();
                RefreshEvent.config();
                VideoActivity.cast(HomeActivity.this, event.getHistory().save());
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) onLongClick(null);
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (mPresenter != null && mPresenter.isDelete()) {
            setHistoryDelete(false);
        } else {
            super.onBackPressed();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        WallConfig.get().clear();
        VodConfig.get().clear();
        Server.get().stop();
        mClock.release();
    }
}
