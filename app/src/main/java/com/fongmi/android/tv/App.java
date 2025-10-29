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
import androidx.fragment.app.FragmentActivity; // 确保导入
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest; // ★★★ 使用周期性请求 ★★★
import androidx.work.WorkManager;

import com.fongmi.android.tv.ui.activity.CrashActivity;
import com.fongmi.android.tv.ui.activity.SecurePrefs; // 确保导入
import com.fongmi.android.tv.ui.dialog.TimeLockDialog; // 确保导入
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

import java.util.Calendar; // 确保导入
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import cat.ereza.customactivityoncrash.config.CaocConfig;

public class App extends Application {

    private final ExecutorService executor;
    private final Handler handler;
    private static App instance;
    private Activity activity;
    private final Gson gson;
    private final long time;
    private Hook hook;

    public App() {
        instance = this;
        executor = Executors.newFixedThreadPool(Constant.THREAD_POOL);
        handler = HandlerCompat.createAsync(Looper.getMainLooper());
        time = System.currentTimeMillis();
        gson = new Gson();
    }

    public static App get() {
        return instance;
    }
    
    public static void execute(Runnable runnable) { get().executor.execute(runnable); }
    public static void post(Runnable runnable) { get().handler.post(runnable); }
    public static void post(Runnable runnable, long delayMillis) { get().handler.postDelayed(runnable, delayMillis); }

    public static Gson gson() { return get().gson; }
    public static long time() { return get().time; }
    public static Activity activity() { return get().activity; }
    
    public void setHook(Hook hook) { this.hook = hook; }
    private void setActivity(Activity activity) { this.activity = activity; }
    
    private LogAdapter getLogAdapter() {
        return new AndroidLogAdapter(PrettyFormatStrategy.newBuilder().methodCount(0).showThreadInfo(false).tag("").build()) {
            @Override
            public boolean isLoggable(int priority, String tag) { return true; }
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
        
        // ★★★ 启动最终的、周期性的锁屏检查 ★★★
        startPeriodicTimeLockCheck();

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) { if (activity != activity()) setActivity(activity); }
            @Override public void onActivityStarted(@NonNull Activity activity) { if (activity != activity()) setActivity(activity); }
            @Override public void onActivityResumed(@NonNull Activity activity) { if (activity != activity()) setActivity(activity); }
            @Override public void onActivityPaused(@NonNull Activity activity) { if (activity == activity()) setActivity(null); }
            @Override public void onActivityStopped(@NonNull Activity activity) { if (activity == activity()) setActivity(null); }
            @Override public void onActivityDestroyed(@NonNull Activity activity) { if (activity == activity()) setActivity(null); }
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) { }
        });
    }

    // ★★★ 婉儿最终版：使用 WorkManager 调度“周期性”任务 ★★★
    private void startPeriodicTimeLockCheck() {
        // 1. 创建一个周期性的工作请求，每 20 分钟运行一次
        PeriodicWorkRequest timeLockRequest =
                new PeriodicWorkRequest.Builder(TimeLockWorker.class, 20, TimeUnit.MINUTES)
                        .build();

        // 2. 将工作请求加入到 WorkManager 的队列中
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "time_lock_worker",
                ExistingPeriodicWorkPolicy.KEEP, // 如果任务已存在，则保留，不重复创建
                timeLockRequest);
    }

    // ★★★ “门卫”和“哨兵”都要使用的公共检查方法 ★★★
    public static void checkTimeLock() {
        Calendar calendar = Calendar.getInstance();
        int currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        boolean isAllowed = SecurePrefs.isTimeAllowed(currentMinutes);

        if (!isAllowed && App.activity() != null) {
            if (App.activity() instanceof FragmentActivity) {
                FragmentActivity fragmentActivity = (FragmentActivity) App.activity();
                if (fragmentActivity.getSupportFragmentManager().findFragmentByTag("time_lock") == null) {
                    TimeLockDialog dialog = new TimeLockDialog();
                    dialog.show(fragmentActivity.getSupportFragmentManager(), "time_lock");
                }
            }
        }
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
