package com.fongmi.android.tv.player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.fongmi.android.tv.App;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Response;

public class SignalFilter implements Interceptor {
    private static boolean hasFired = false;
    @NonNull @Override public Response intercept(@NonNull Chain chain) throws IOException {
        if (!hasFired) {
            hasFired = true;
            showToast("哥哥！“信号弹哨兵”已成功上岗！");
        }
        return chain.proceed(chain.request());
    }
    private void showToast(final String message) { new Handler(Looper.getMainLooper()).post(() -> { try { Context context = App.get(); if (context != null) { Toast.makeText(context, message, Toast.LENGTH_LONG).show(); } } catch (Exception e) {} }); }
}
