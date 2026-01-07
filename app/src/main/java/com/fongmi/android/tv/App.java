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

    // ================= ▼ 婉儿的终极“发射程序”！▼ =================
    // 我们在一个后台线程里，完成所有初始化和配置任务，保证APP启动流畅！
    new Thread(() -> {
        
        // ✨ 第一步：配置“大脑” (AdRule)，告诉它去哪里取“规则文件”
        // ✨ 注意：这里的地址，必须和 AdFilter 白名单里的 RULE_CONFIG_URL 一模一样！
        AdRule.setConfigUrl("http://47.109.61.116:86/apk/ad_rulesa.json");

        // ✨ 第二步：配置“心脏” (AdSwitch)，告诉它去哪里取“激活名单”
        // ✨ 注意：这里的地址，也必须和 AdFilter 白名单里的 ACTIVATION_CONFIG_URL 一模一样！
        String validCodesUrl = "http://47.109.61.116:86/apk/activation_configb.json";
        AdSwitch.get().fetchValidCodeList(validCodesUrl);

    }).start();
    
    // ✨ 第三步：把我们的“哨兵” (AdFilter)，安装到网络引擎上！
    // ✨ 这一步必须在主线程、并且尽早执行，才能拦截到所有请求！
    OkHttp.addInterceptor(new AdFilter());
    // ================= ▲ 发射程序部署完毕！▲ =================
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
