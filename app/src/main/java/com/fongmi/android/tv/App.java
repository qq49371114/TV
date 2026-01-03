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

import com.fongmi.android.tv.utils.AppLockManager; // ✨ 婉儿帮你加上啦！
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.hook.Hook;
import com.github.catvod.Init;
import com.google.gson.Gson;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

// ✨ 1. 引入我们所有的“信号弹”！
import com.fongmi.android.tv.player.AdFilter;
import com.fongmi.android.tv.player.AdRule;
import com.github.catvod.net.OkHttp;
import android.widget.Toast;

public class App extends Application implements Application.ActivityLifecycleCallbacks {

    private final ExecutorService searchExecutor;
    private final ExecutorService executor;
    private final Handler handler;
    private static App instance;
    private Activity activity;
    private final Gson gson;
    private final long time;
    private Hook hook;

    // ✨↓ 婉儿帮你加上了“状态探测器”的变量！↓✨
    public static boolean isAppInForeground = false;
    private int activityCount = 0;

    public App() {
        instance = this;
        gson = new Gson();
        time = System.currentTimeMillis();
        executor = Executors.newFixedThreadPool(5);
        searchExecutor = Executors.newFixedThreadPool(20);
        handler = HandlerCompat.createAsync(Looper.getMainLooper());
    }

    public void setHook(Hook hook) {
        this.hook = hook;
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
        registerActivityLifecycleCallbacks(this);
        // ✨✨✨ 在这里，我们新开一个线程，去完成“先唤醒，再站岗”的壮举！✨✨✨
        new Thread(() -> {
            // 1. ✨ 先把“大喇叭”(this)递给“大脑”！
            AdRule.get().init(this);

            // 2. 再让“大脑”去加载规则！
            String ruleUrl = "http://47.109.61.116:86/apk/ad_rules.json"; 
            AdRule.get().load(ruleUrl);

            // 3. ✨ 等“大脑”的加载逻辑开始后，我们就可以把“哨兵”派去站岗了！
            //    因为“大脑”内部有“闹钟”，会自己处理同步问题。
            OkHttp.addInterceptor(new AdFilter());
            
            // ✨ 我们可以加一个弹窗，告诉我们“哨兵”已经成功上岗！
            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(App.get(), "凤凰系统已成功启动！", Toast.LENGTH_LONG).show();
            });
        }).start();

        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public PackageManager getPackageManager() {
        return hook != null ? hook : getBaseContext().getPackageManager();
    }

    @Override
    public String getPackageName() {
        return hook != null ? hook.getPackageName() : getBaseContext().getPackageName();
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (activity != activity()) this.activity = activity;
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (activity == activity()) this.activity = null;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    // ✨↓ 婉儿升级了 onActivityStarted 方法！↓✨
    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        if (activityCount == 0) {
            isAppInForeground = true;
        }
        activityCount++;
    }

    // ✨↓ 婉儿升级了 onActivityStopped 方法！↓✨
    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        activityCount--;
        if (activityCount == 0) {
            isAppInForeground = false;
            // 当我们离开庄园时，立刻作废通行证！
            AppLockManager.isSessionUnlocked = false;
        }
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

    public static <T> Future<T> submit(Callable<T> task) {
        return get().executor.submit(task);
    }

    public static Future<?> submit(Runnable task) {
        return get().executor.submit(task);
    }

    public static Future<?> submitSearch(Runnable task) {
        return get().searchExecutor.submit(task);
    }

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
}
