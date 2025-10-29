package com.fongmi.android.tv;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.HandlerCompat;
import androidx.lifecycle.Observer;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.fongmi.android.tv.ui.activity.CrashActivity;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.TimeLockWorker;
import com.fongmi.hook.Hook;
import com.github.catvod.Init;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.google.gson.Gson;
import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.LogAdapter;
import com.orhanobut.logger.Logger;
import com.orhanobut.logger.PrettyFormatStrategy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import cat.ereza.customactivityoncrash.config.CaocConfig;

public class App extends Application {

    private final ExecutorService executor;
    private final Handler handler; // ★★★ 婉儿加回：这是 post 方法需要的 Handler
    private static App instance;
    private Activity activity;
    private final Gson gson;
    private final long time;
    private Hook hook;

    public App() {
        instance = this;
        executor = Executors.newFixedThreadPool(Constant.THREAD_POOL);
        handler = HandlerCompat.createAsync(Looper.getMainLooper()); // ★★★ 婉儿加回：Handler 的初始化
        time = System.currentTimeMillis();
        gson = new Gson();
    }

    public static App get() {
        return instance;
    }

    // ★★★ 婉儿加回：你项目中所有地方都在用的核心方法！★★★
    public static void execute(Runnable runnable) {
        get().executor.execute(runnable);
    }

    public static void post(Runnable runnable) {
        get().handler.post(runnable);
    }

    public static void post(Runnable runnable, long delayMillis) {
        get().handler.removeCallbacks(runnable);
        if (delayMillis >= 0) get().handler.postDelayed(runnable, delayMillis);
    }

    public static void removeCallbacks(Runnable runnable) {
        get().handler.removeCallbacks(runnable);
    }
    
    public static void removeCallbacks(Runnable... runnable) {
        for (Runnable r : runnable) get().handler.removeCallbacks(r);
    }
    // ★★★ 以上是加回的核心方法 ★★★

    public static Gson gson() {
        return get().gson;
    }

    public static long time() {
        return get().time;
    }

    public static Activity activity() {
        return get().activity;
    }
    
    public void setHook(Hook hook) {
        this.hook = hook;
    }

    private void setActivity(Activity activity) {
        this.activity = activity;
    }
    
    private LogAdapter getLogAdapter() {
        return new AndroidLogAdapter(PrettyFormatStrategy.newBuilder().methodCount(0).showThreadInfo(false).tag("").build()) {
            @Override
            public boolean isLoggable(int priority, String tag) {
                return true;
            }
        };
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Init.set(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Notify.createChannel();
        Logger.addLogAdapter(getLogAdapter());
        OkHttp.get().setDoh(Doh.objectFrom(Setting.getDoh()));
        CaocConfig.Builder.create().trackActivities(true).backgroundMode(CaocConfig.BACKGROUND_MODE_SILENT).errorActivity(CrashActivity.class).apply();
        
        startTimeLockCheckForDebug();

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            // ... (生命周期回调不变) ...
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) { if (activity != activity()) setActivity(activity); }
            @Override
            public void onActivityStarted(@NonNull Activity activity) { if (activity != activity()) setActivity(activity); }
            @Override
            public void onActivityResumed(@NonNull Activity activity) { if (activity != activity()) setActivity(activity); }
            @Override
            public void onActivityPaused(@NonNull Activity activity) { if (activity == activity()) setActivity(null); }
            @Override
            public void onActivityStopped(@NonNull Activity activity) { if (activity == activity()) setActivity(null); }
            @Override
            public void onActivityDestroyed(@NonNull Activity activity) { if (activity == activity()) setActivity(null); }
            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) { }
        });
    }

    // ★★★ WorkManager 调试版调度方法 (保持不变) ★★★
    private void startTimeLockCheckForDebug() {
        OneTimeWorkRequest timeLockRequest =
                new OneTimeWorkRequest.Builder(TimeLockWorker.class)
                        .setInitialDelay(1, TimeUnit.MINUTES)
                        .build();

        WorkManager.getInstance(this).getWorkInfoByIdLiveData(timeLockRequest.getId())
                .observeForever(new Observer<WorkInfo>() {
                    @Override
                    public void onChanged(WorkInfo workInfo) {
                        if (workInfo != null && workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                            startTimeLockCheckForDebug();
                            WorkManager.getInstance(App.this).getWorkInfoByIdLiveData(timeLockRequest.getId()).removeObserver(this);
                        }
                    }
                });

        WorkManager.getInstance(this).enqueue(timeLockRequest);
    }

    @Override
    public PackageManager getPackageManager() {
        return hook != null ? hook : getBaseContext().getPackageManager();
    }

    @Override
    public String getPackageName() {
        return hook != null ? hook.getPackageName() : getBaseContext().getPackageName();
    }
    
    public static void checkTimeLock() {
    // 1. 获取当前时间（分钟数）
    Calendar calendar = Calendar.getInstance();
    int currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);

    // 2. 判断是否在允许的时间段内
    boolean isAllowed = SecurePrefs.isTimeAllowed(currentMinutes);

    // 3. 如果不在允许时间段内，并且 App 在前台
    if (!isAllowed && App.activity() != null) {
        // 确保是 FragmentActivity，这样才能显示 DialogFragment
        if (App.activity() instanceof FragmentActivity) {
            FragmentActivity fragmentActivity = (FragmentActivity) App.activity();
            
            // 检查是否已经显示，防止重复弹窗
            if (fragmentActivity.getSupportFragmentManager().findFragmentByTag("time_lock") == null) {
                TimeLockDialog dialog = new TimeLockDialog();
                dialog.show(fragmentActivity.getSupportFragmentManager(), "time_lock");
            }
        }
    }
}


