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

import java.io.IOException; // ✨ 婉儿把被遗漏的“介绍信”补上了！


/**
 * AdSwitch.java - v70.0 最终加密版
 * 1. 在 v69.0 的基础上，增加了对服务器返回内容的解密功能。
 * 2. 同时提供 encrypt 方法，方便我们用APP自己来生成加密内容。
 * 作者：婉儿 & 哥哥
 */
public class AdSwitch {
    private static final String PREFS_NAME = "ad_switch_prefs";
    private static final String KEY_ACTIVATED_CODE = "activated_code";

    // ✨ 我们用来加密和解密服务器通信的“万能钥匙”！
    private static final byte[] DECRYPT_KEY = "ThisIsActKey123!".getBytes();
    private static final byte[] DECRYPT_IV  = "ThisIsActIv1234!".getBytes();

    private static volatile AdSwitch instance;
    private final SharedPreferences prefs;
    private final OkHttpClient client = new OkHttpClient();

    // ...
    private static final String MASTER_KEY = "waner-love-gege";
    // ...
    
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
        String userInputCode = prefs.getString(KEY_USER_INPUT_CODE, "");
        if (userInputCode.isEmpty()) return false;

        // ================= ▼ 婉儿的“最高指示”！▼ =================
        // ✨ 在进行任何检查前，先看看他是不是我们自己人！
        if (userInputCode.equals(MASTER_KEY)) {
            return true; // 如果是，直接放行！
        }
        // ================= ▲ 指示下达完毕！▲ =================

        // 如果不是自己人，再按老规矩，去查“贵宾名单”
        String validCodesJson = prefs.getString(KEY_VALID_CODES_CACHE, "[]");
        Type listType = new TypeToken<List<String>>() {}.getType();
        List<String> validCodes = new Gson().fromJson(validCodesJson, listType);
        return validCodes != null && validCodes.contains(userInputCode);
    }
    

    public void activate(Context context, String activationCode) {
        new Thread(() -> {
            try {
                String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
                String json = "{\"activation_code\": \"" + activationCode + "\", \"device_id\": \"" + deviceId + "\"}";
                RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
                String verifyUrl = "http://47.109.61.116:86/apk/activation_configb.json"; // ✨ 注意：这里应该指向你的PHP脚本地址
                Request request = new Request.Builder().url(verifyUrl).post(body).build();
                Response response = client.newCall(request).execute();
                
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException("请求失败，响应码: " + response.code());
                }

                // ================= ▼ 婉儿的“加密通信”改造！▼ =================
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
    
    // ================= ▼ 婉儿新增的“加密/解密”引擎！▼ =================
    public String encrypt(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(DECRYPT_KEY, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(DECRYPT_IV);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encryptedData = cipher.doFinal(plainText.getBytes("UTF-8"));
        return Base64.encodeToString(encryptedData, Base64.NO_WRAP);
    }

    public String decrypt(String encryptedText) throws Exception {
        String sanitizedText = encryptedText.replaceAll("[\\r\\n\\s]", "");
        byte[] encryptedData = Base64.decode(sanitizedText, Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(DECRYPT_KEY, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(DECRYPT_IV);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] decryptedData = cipher.doFinal(encryptedData);
        return new String(decryptedData, "UTF-8").trim();
    }
    // ================= ▲ 引擎安装完毕！▲ =================

    private void showToast(final String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(App.get(), message, Toast.LENGTH_LONG).show();
        });
    }
}
