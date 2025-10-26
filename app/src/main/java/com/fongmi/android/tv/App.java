package com.fongmi.android.tv;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.api.config.Doh;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.ui.activity.CrashActivity;
import com.fongmi.android.tv.ui.activity.HomeActivity;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Setting;
import com.github.catvod.net.OkHttp;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.LogAdapter;
import com.orhanobut.logger.Logger;
import com.orhanobut.logger.PrettyFormatStrategy;
import com.squareup.picasso.OkHttp3Downloader;
import com.squareup.picasso.Picasso;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;

import cat.ereza.customactivityoncrash.config.CaocConfig;
import me.weishu.reflection.Reflection;

public class App extends Application {

    private static App instance;
    private Instrumentation.ActivityResult hook;
    private Activity activity;
    private static Gson gson;

    public App() {
        instance = this;
    }

    public static App get() {
        return instance;
    }

    public static Activity activity() {
        return get().activity;
    }

    public static Gson gson() {
        if (gson == null) gson = new GsonBuilder().setPrettyPrinting().create();
        return gson;
    }

    public void setHook(Instrumentation.ActivityResult hook) {
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
        Reflection.unseal(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Looper.myQueue().addIdleHandler(() -> {
            initOkHttp();
            initPicasso();
            Server.get().start();
            return false;
        });
        Notify.createChannel();
        Logger.addLogAdapter(getLogAdapter());
        OkHttp.get().setDoh(Doh.objectFrom(Setting.getDoh()));
        // 婉儿已经帮你把导致报错的 EventBus.builder() 这一行删掉了
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
        // 婉儿也帮你把这里重复启动的远程服务删掉了
    }

    private void initOkHttp() {
        try {
            OkHttp.get().init(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initPicasso() {
        try {
            Picasso.setSingletonInstance(new Picasso.Builder(this).downloader(new OkHttp3Downloader(OkHttp.get().client())).build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void restart() {
        Intent intent = new Intent(get(), HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        System.exit(0);
    }

    @Override
    public PackageManager getPackageManager() {
        return hook != null ? hook : super.getPackageManager();
    }

    @Override
    public String getPackageName() {
        return hook != null ? hook.getPackageName() : super.getPackageName();
    }
}
