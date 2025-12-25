package com.fongmi.android.tv.service;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.fongmi.android.tv.App; // ✨ 婉儿帮你加上啦！就是它！
import com.fongmi.android.tv.utils.TimeLockUtils;

public class TimeLockService extends Service {

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable checkTimeRunnable;
    // ✨↓ 婉儿的最终修改就在这里！我们让“信号频率”变得智能！↓✨
    public static String getAction() {
        return App.get().getPackageName() + ".ACTION_SHOW_LOCK_SCREEN";
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        checkTimeRunnable = new Runnable() {
            @Override
            public void run() {
                if (!TimeLockUtils.isAllowedTime(getApplicationContext())) {
                    // 发射一个“自适应”频率的信号弹！
                    sendBroadcast(new Intent(getAction()));
                }
                handler.postDelayed(this, 10 * 60 * 1000);
            }
        };
        handler.post(checkTimeRunnable);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && checkTimeRunnable != null) {
            handler.removeCallbacks(checkTimeRunnable);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
