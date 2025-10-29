package com.fongmi.android.tv;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.ExistingPeriodicWorkPolicy; // ★★★ 婉儿新增 ★★★
import androidx.work.PeriodicWorkRequest;      // ★★★ 婉儿新增 ★★★
import androidx.work.WorkManager;             // ★★★ 婉儿新增 ★★★

import com.fongmi.android.tv.ui.activity.CrashActivity;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.TimeLockWorker; // ★★★ 婉儿新增 ★★★
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
import java.util.concurrent.TimeUnit; // ★★★ 婉儿新增 ★★★

import cat.ereza.customactivityoncrash.config.CaocConfig;

public class App extends Application {

    private final ExecutorService executor;
    private static App instance;
    private Activity activity;
    private final Gson gson;
    private final long time;
    private Hook hook;

    public App() {
        instance = this;
        executor = Executors.newFixedThreadPool(Constant.THREAD_POOL);
        time = System.currentTimeMillis();
        gson = new Gson();
    }

    public static App get() {
        return instance;
    }

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
        
        // ★★★ 婉儿新增：启动 WorkManager 全局定时锁屏检查 ★★★
        startPeriodicTimeLockCheck();

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                if (activity != activity()) setActivity(activity);
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                if (activity != activity()) setActivity(activity);
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                if (activity != activity()) setActivity(activity);
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                if (activity == activity()) setActivity(null);
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                if (activity == activity()) setActivity(null);
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                if (activity == activity()) setActivity(null);
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
            }
        });
    }

    // ★★★ 婉儿新增：使用 WorkManager 调度周期性任务的方法 ★★★
    private void startPeriodicTimeLockCheck() {
        // 创建一个周期性的工作请求，每 15 分钟运行一次
        PeriodicWorkRequest timeLockRequest =
                new PeriodicWorkRequest.Builder(TimeLockWorker.class, 15, TimeUnit.MINUTES)
                        .build();

        // 将工作请求加入到 WorkManager 的队列中
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "time_lock_worker",
                ExistingPeriodicWorkPolicy.KEEP,
                timeLockRequest);
    }

    @Override
    public PackageManager getPackageManager() {
        return hook != null ? hook : getBaseContext().getPackageManager();
    }

    @Override
    public String getPackageName() {
        return hook != null ? hook.getPackageName() : getBaseContext().getPackageName();
    }
}
