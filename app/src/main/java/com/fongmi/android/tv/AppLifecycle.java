package com.fongmi.tv;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
//import com.fongmi.android.tv.ui.overlay.TimeLockOverlay;
import com.fongmi.android.tv.ui.dialog.TimeLockDialog;



/**
 * 全局生命周期监听 → 统一锁屏计时器
 * 写入 waner 分支，leanback & mobile 双端通用
 */
public class AppLifecycle implements Application.ActivityLifecycleCallbacks {

    private static final long LOCK_DELAY = 5 * 60 * 1000;   // 5 分钟无操作即锁
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable lockTask;

    @Override
    public void onActivityResumed(Activity activity) {
        cancelLock();           // 用户回到前台，取消倒计时
    }

    @Override
    public void onActivityPaused(Activity activity) {
        scheduleLock(activity); // 用户离开前台，启动倒计时
    }

    /* 以下空实现即可 */
    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
    @Override public void onActivityDestroyed(Activity activity) {}

    private void scheduleLock(Activity activity) {
        lockTask = () -> TimeLockDialog.show(activity);
        handler.postDelayed(lockTask, LOCK_DELAY);
    }

    private void cancelLock() {
        if (lockTask != null) {
            handler.removeCallbacks(lockTask);
            lockTask = null;
        }
    }
}
