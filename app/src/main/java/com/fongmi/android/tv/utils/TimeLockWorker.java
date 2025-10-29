package com.fongmi.android.tv.utils; // 你可以把它放在 utils 包，或其他合适的包里

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.fongmi.android.tv.App; // 假设 App 类在这里
import com.fongmi.android.tv.ui.activity.SecurePrefs; // 假设 SecurePrefs 在这里
import com.fongmi.android.tv.ui.dialog.TimeLockDialog; // 假设 TimeLockDialog 在这里

import java.util.Calendar;

public class TimeLockWorker extends Worker {

    public TimeLockWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // ★★★ 这里就是我们的后台工作核心！★★★
        checkAndShowLockScreen();
        
        // 告诉 WorkManager 任务已成功完成
        return Result.success();
    }

    private void checkAndShowLockScreen() {
        // 1. 检查当前时间是否在允许时间段内
        Calendar calendar = Calendar.getInstance();
        int currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);

        boolean isAllowed = SecurePrefs.isTimeAllowed(currentMinutes);

        // 2. 如果不在允许时间段内
        if (!isAllowed) {
            // ★★★ 注意：WorkManager 无法直接显示 UI ★★★
            // 我们需要通过一个 Handler 将 UI 操作发送到主线程
            new Handler(Looper.getMainLooper()).post(() -> {
                // 3. 获取当前顶层 Activity
                android.app.Activity activity = App.activity();
                
                if (activity instanceof androidx.fragment.app.FragmentActivity) {
                    androidx.fragment.app.FragmentActivity fragmentActivity = (androidx.fragment.app.FragmentActivity) activity;
                    
                    // 4. 检查是否已经显示，防止重复创建
                    if (fragmentActivity.getSupportFragmentManager().findFragmentByTag("time_lock") == null) {
                        TimeLockDialog dialog = new TimeLockDialog();
                        dialog.show(fragmentActivity.getSupportFragmentManager(), "time_lock");
                    }
                }
            });
        }
    }
}
