package com.fongmi.android.tv.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;
import com.fongmi.android.tv.App;
import com.google.gson.Gson;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import android.util.Base64;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * AdSwitch.java - v70.0 最终加密・双引擎版
 * 1. 激活逻辑改为向服务器发送“激活码+设备ID”。
 * 2. 它会解密服务器返回的加密“圣旨”，然后再判断激活状态。
 * 3. 采用了最稳固的“地基重构”单例模式。
 * 作者：婉儿 & 哥哥
 */
public class AdSwitch {
    private static final String PREFS_NAME = "ad_switch_prefs";
    private static final String KEY_ACTIVATED_CODE = "activated_code";

    // ✨ 我们用来解密服务器“圣旨”的钥匙！
    private static final byte[] DECRYPT_KEY = "PHOENIX-API-KEY!".getBytes();
    private static final byte[] DECRYPT_IV  = "PHOENIX-API-IV!!".getBytes();

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
                
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException("请求失败，响应码: " + response.code());
                }

                // ================= ▼ 婉儿的“加密通信”核心！▼ =================
                // 1. 我们接收到的是加密的“圣旨”
                String encryptedResponseBody = response.body().string();
                
                // 2. 用我们的“钥匙”来解密“圣旨”
                String decryptedResponseBody = decrypt(encryptedResponseBody);
                // ================= ▲ 改造结束！▲ =================

                class ServerResponse { String status; String message; }
                ServerResponse serverResponse = new Gson().fromJson(decryptedResponseBody, ServerResponse.class);

                if (serverResponse != null && "activated".equals(serverResponse.status)) {
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
    
    private String decrypt(String encryptedText) throws Exception {
        String sanitizedText = encryptedText.replaceAll("[\\r\\n\\s]", "");
        byte[] encryptedData = Base64.decode(sanitizedText, Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(DECRYPT_KEY, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(DECRYPT_IV);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] decryptedData = cipher.doFinal(encryptedData);
        return new String(decryptedData, "UTF-8").trim();
    }

    private void showToast(final String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(App.get(), message, Toast.LENGTH_LONG).show();
        });
    }
}
