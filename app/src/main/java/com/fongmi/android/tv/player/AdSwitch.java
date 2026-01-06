package com.fongmi.android.tv.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;
import com.fongmi.android.tv.App;
import com.google.gson.Gson;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AdSwitch {
    private static final String PREFS_NAME = "ad_switch_prefs";
    private static final String KEY_ACTIVATED_CODE = "activated_code";

    private static volatile AdSwitch instance;
    private final SharedPreferences prefs;
    private final OkHttpClient client = new OkHttpClient();

    private AdSwitch(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static AdSwitch get() {
        if (instance == null) {
            synchronized (AdSwitch.class) {
                if (instance == null) {
                    instance = new AdSwitch(App.get());
                }
            }
        }
        return instance;
    }

    public boolean isOn() {
        String activatedCode = prefs.getString(KEY_ACTIVATED_CODE, "");
        return !activatedCode.isEmpty();
    }

    public void activate(Context context, String activationCode) {
        new Thread(() -> {
            try {
                String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
                String json = "{\"activation_code\": \"" + activationCode + "\", \"device_id\": \"" + deviceId + "\"}";
                RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
                String verifyUrl = "http://47.109.61.116:86/apk/activation_configb.json"; // 换成你的验证服务器地址
                Request request = new Request.Builder().url(verifyUrl).post(body).build();
                Response response = client.newCall(request).execute();
                String responseBody = response.body() != null ? response.body().string() : "";
                class ServerResponse { String status; String message; }
                ServerResponse serverResponse = new Gson().fromJson(responseBody, ServerResponse.class);

                if (response.isSuccessful() && serverResponse != null && "activated".equals(serverResponse.status)) {
                    prefs.edit().putString(KEY_ACTIVATED_CODE, activationCode).apply();
                    showToast("激活成功！" + serverResponse.message);
                } else {
                    String errorMessage = serverResponse != null ? serverResponse.message : "未知错误";
                    showToast("激活失败：" + errorMessage);
                }
            } catch (Exception e) {
                showToast("激活失败：网络或服务器异常。");
                e.printStackTrace();
            }
        }).start();
    }
    
    private void showToast(final String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(App.get(), message, Toast.LENGTH_LONG).show();
        });
    }
}
