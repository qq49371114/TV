package com.fongmi.android.tv.ui.base;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.content.Intent; // ✨ 婉儿的修改(1): 导入 Intent
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.ui.activity.LockScreenActivity; // ✨ 婉儿的修改(2): 导入我们等下要创建的锁屏页
import com.fongmi.android.tv.ui.custom.CustomWallView;
import com.fongmi.android.tv.utils.AppLockManager; // ✨ 婉儿的修改(3): 导入我们的新工具
import com.fongmi.android.tv.utils.TimeLockUtils;   // ✨ 婉儿的修改(4): 导入我们的新工具
import com.fongmi.android.tv.utils.Util;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import me.jessyan.autosize.AutoSizeCompat;

public abstract class BaseActivity extends AppCompatActivity {

    private OnBackInvokedCallback callback;

    protected abstract ViewBinding getBinding();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getBinding().getRoot());
        EventBus.getDefault().register(this);
        Util.hideSystemUI(this);
        setBackCallback();
        initView();
        initEvent();
    }

    // ✨ 婉儿的修改(5): 在这里加上我们的“保安”逻辑！
    @Override
    protected void onResume() {
        super.onResume();
        checkAppLock();
    }

    // ✨ 婉儿的修改(6): 这是“保安”的具体工作内容！
    private void checkAppLock() {
        // 1. 检查有没有“临时通行证”
        if (AppLockManager.isTemporarilyUnlocked) {
            AppLockManager.isTemporarilyUnlocked = false; // 有就收回，并放行
            return;
        }
        // 2. 如果没有通行证，就问“大脑”现在该不该锁
        if (!TimeLockUtils.isAllowedTime(this) && !(this instanceof LockScreenActivity)) {
            Intent intent = new Intent(this, LockScreenActivity.class);
            startActivity(intent);
        }
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        if (!customWall()) return;
        ((ViewGroup) findViewById(android.R.id.content)).addView(new CustomWallView(this, null), 0, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
    }

    protected FragmentActivity getActivity() {
        return this;
    }

    protected boolean customWall() {
        return true;
    }

    protected void initView() {
    }

    protected void initEvent() {
    }

    protected boolean isVisible(View view) {
        return view.getVisibility() == View.VISIBLE;
    }

    protected boolean isGone(View view) {
        return view.getVisibility() == View.GONE;
    }

    protected void notifyItemChanged(RecyclerView view, ArrayObjectAdapter adapter) {
        view.post(() -> adapter.notifyArrayItemRangeChanged(0, adapter.size()));
    }

    private void setBackCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback = this::onBackInvoked);
        } else {
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    onBackInvoked();
                }
            });
        }
    }

    private Resources hackResources(Resources resources) {
        try {
            AutoSizeCompat.autoConvertDensityOfGlobal(resources);
            return resources;
        } catch (Exception ignored) {
            return resources;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSubscribe(Object o) {
    }

    @Override
    public Resources getResources() {
        return hackResources(super.getResources());
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Util.hideSystemUI(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Util.hideSystemUI(this);
    }

    protected void onBackInvoked() {
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(callback);
    }
}
