package com.fongmi.android.tv.service;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.fongmi.android.tv.utils.TimeLockUtils;

public class TimeLockService extends Service {

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable checkTimeRunnable;
    public static final String ACTION_SHOW_LOCK_SCREEN = "com.fongmi.android.tv.ACTION_SHOW_LOCK_SCREEN";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        checkTimeRunnable = new Runnable() {
            @Override
            public void run() {
                if (!TimeLockUtils.isAllowedTime(getApplicationContext())) {
                    sendBroadcast(new Intent(ACTION_SHOW_LOCK_SCREEN));
                }
                // ✨↓ 婉儿的修改在这里！我们把 60 * 1000 改成了 10 * 60 * 1000！↓✨
                handler.postDelayed(this, 10 * 60 * 1000); // 10分钟检查一次
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
