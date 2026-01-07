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
 * AdSwitch.java - v80.0 终极版
 * 1. 激活逻辑为向服务器发送“激活码+设备ID”，由服务器进行最终裁决。
 * 2. 内置了“超级密码”作为最高权限后门。
 * 3. 提供了加密/解密工具方法。
 * 作者：婉儿 & 哥哥
 */
public class AdSwitch {
    private static final String PREFS_NAME = "ad_switch_prefs";
    private static final String KEY_ACTIVATED_CODE = "activated_code";

    private static final byte[] API_CRYPT_KEY = "PHOENIX-API-KEY!".getBytes();
    private static final byte[] API_CRYPT_IV  = "PHOENIX-API-IV!!".getBytes();
    private static final String MASTER_KEY = "waner-love-gege";

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

    // ✨ 我们全新的、唯一的激活入口！
    public void activate(Context context, String activationCode) {
        // ✨ 第一重检查：是不是我们的“超级密码”？
        if (activationCode.equals(MASTER_KEY)) {
            prefs.edit().putString(KEY_ACTIVATED_CODE, activationCode).apply();
            showToast("超级权限已激活！");
            return;
        }

        // ✨ 如果不是，就去走远程验证流程
        activateRemotely(context, activationCode);
    }

    private void activateRemotely(Context context, String activationCode) {
        new Thread(() -> {
            try {
                String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
                String json = "{\"activation_code\": \"" + activationCode + "\", \"device_id\": \"" + deviceId + "\"}";
                RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
                String verifyUrl = "https://your-server.com/api/phoenix/activate.php"; // 换成你的验证服务器地址
                Request request = new Request.Builder().url(verifyUrl).post(body).build();
                Response response = client.newCall(request).execute();
                
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException("请求失败: " + response.code());
                }

                String responseBody = response.body().string();
                class ServerResponse { String status; String message; }
                ServerResponse serverResponse = new Gson().fromJson(responseBody, ServerResponse.class);

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
    
    public String encrypt(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(API_CRYPT_KEY, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(API_CRYPT_IV);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encryptedData = cipher.doFinal(plainText.getBytes("UTF-8"));
        return Base64.encodeToString(encryptedData, Base64.NO_WRAP);
    }

    public String decrypt(String encryptedText) throws Exception {
        String sanitizedText = encryptedText.replaceAll("[\\r\\n\\s]", "");
        byte[] encryptedData = Base64.decode(sanitizedText, Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(API_CRYPT_KEY, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(API_CRYPT_IV);
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
