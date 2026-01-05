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

import com.fongmi.android.tv.utils.Notify;
import com.fongmi.hook.Hook;
import com.github.catvod.Init;
import com.google.gson.Gson;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fongmi.android.tv.player.AdFilter;
import com.fongmi.android.tv.player.AdRule;
import com.fongmi.android.tv.player.AdSwitch;
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

    // ✨✨✨ 2. 在后台线程中，初始化并启动我们的整个“凤凰系统”！ ✨✨✨
    
    new Thread(() -> {
        // a. 初始化“大脑”，让它准备好加载规则
        AdRule.get().init(this);
        // b. 为“大脑”设置加密规则文件的地址
        AdRule.setConfigUrl("http://47.109.61.116:86/apk/ad_rulesa.json");

        // c. 初始化“心脏”，让它准备好进行激活验证
        AdSwitch.get().init(this);
        // d. 让“心脏”去云端获取最新的“远程钥匙” (activation_code)
        String activationConfigUrl = "http://47.109.61.116:86/apk/activation_configb.json"; 
        AdSwitch.get().fetchRemoteCode(activationConfigUrl);

        // e. 把我们的“哨兵”，通过“外挂接口”，插到“发动机”上！
        OkHttp.addInterceptor(new AdFilter());
    }).start();

    // ... 你其他的原始启动代码 ...
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

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
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
