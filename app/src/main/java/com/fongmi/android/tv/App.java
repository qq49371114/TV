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
import androidx.fragment.app.FragmentActivity; // ★★★ 婉儿新增：用于获取 FragmentManager

import com.fongmi.android.tv.ui.activity.CrashActivity;
import com.fongmi.android.tv.ui.activity.SecurePrefs; // ★★★ 婉儿新增：获取时间段
import com.fongmi.android.tv.ui.dialog.TimeLockDialog; // ★★★ 婉儿新增：锁屏对话框
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.hook.Hook;
import com.github.catvod.Init;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.google.gson.Gson;
import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.LogAdapter;
import com.orhanobut.logger.Logger;
import com.orhanobut.logger.PrettyFormatStrategy;

import org.greenrobot.eventbus.EventBus;

import java.util.Calendar; // ★★★ 婉儿新增：检查时间
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cat.ereza.customactivityoncrash.config.CaocConfig;

public class App extends Application {

    private final ExecutorService executor;
    private final Handler handler;
    private static App instance;
    private Activity activity;
    private final Gson gson;
    private final long time;
    private Hook hook;
    
    // ★★★ 婉儿新增：定时检查间隔 (10分钟) ★★★
    private static final long LOCK_CHECK_INTERVAL = 600000; 

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

    public static Gson gson() {
        return get().gson;
    }

    public static long time() {
        return get().time;
    }

    public static Activity activity() {
        return get().activity;
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
        //EventBus.builder().addIndex(new EventIndex()).installDefaultEventBus();
        CaocConfig.Builder.create().trackActivities(true).backgroundMode(CaocConfig.BACKGROUND_MODE_SILENT).errorActivity(CrashActivity.class).apply();
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
        
        // ★★★ 婉儿新增：启动全局定时锁屏检查 ★★★
        post(lockCheckRunnable, LOCK_CHECK_INTERVAL);
    }

    @Override
    public PackageManager getPackageManager() {
        return hook != null ? hook : getBaseContext().getPackageManager();
    }

    @Override
    public String getPackageName() {
        return hook != null ? hook.getPackageName() : getBaseContext().getPackageName();
    }
    
    // ★★★ 婉儿新增：定时检查任务 Runnable ★★★
    private final Runnable lockCheckRunnable = new Runnable() {
        @Override
        public void run() {
            checkAndShowLockScreen();
            // 循环调用，实现定时
            post(this, LOCK_CHECK_INTERVAL);
        }
    };

    private void checkAndShowLockScreen() {
    // 1. 检查当前时间是否在允许时间段内
    Calendar calendar = Calendar.getInstance();
    int currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);

    boolean isAllowed = SecurePrefs.isTimeAllowed(currentMinutes);

    // ★★★ 婉儿新增：日志输出 ★★★
    Logger.d("TimeLockCheck: CurrentMinutes = " + currentMinutes + ", isAllowed = " + isAllowed + ", activity = " + (activity != null ? activity.getLocalClassName() : "null"));

    // 2. 如果不在允许时间段内 并且 当前有 Activity 处于前台
    if (!isAllowed && activity != null) {
        // ★★★ 婉儿新增：日志输出 ★★★
        Logger.d("TimeLockCheck: Time to lock! Showing dialog...");

        // 3. 检查当前 Activity 是否是 FragmentActivity (用于支持 DialogFragment)
        if (activity instanceof FragmentActivity) {
            FragmentActivity fragmentActivity = (FragmentActivity) activity;
            
            // 4. 检查是否已经显示，防止重复创建
            if (fragmentActivity.getSupportFragmentManager().findFragmentByTag("time_lock") == null) {
                TimeLockDialog dialog = new TimeLockDialog();
                dialog.show(fragmentActivity.getSupportFragmentManager(), "time_lock");
            } else {
                // ★★★ 婉儿新增：日志输出 ★★★
                Logger.d("TimeLockCheck: Dialog already showing.");
            }
        } else {
            // ★★★ 婉儿新增：日志输出 ★★★
            Logger.d("TimeLockCheck: Current activity is not a FragmentActivity.");
        }
    }
}
