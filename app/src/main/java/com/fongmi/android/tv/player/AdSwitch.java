package com.fongmi.android.tv.player;

import android.content.Context;
import android.content.SharedPreferences;
import com.fongmi.android.tv.App;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import android.util.Base64;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AdSwitch {
    private static final String PREFS_NAME = "ad_switch_prefs";
    private static final String KEY_USER_INPUT_CODE = "user_input_code";
    private static final String KEY_REMOTE_PASSWORD_CACHE = "remote_password_cache";

    // --- 密钥1：专门用来解密“激活文件”，必须和你在工具里用的一样！---
    private static final byte[] ACT_DECRYPT_KEY = "ThisIsActKey123!".getBytes();
    private static final byte[] ACT_DECRYPT_IV  = "ThisIsActIv1234!".getBytes();

    private static class Loader { static volatile AdSwitch INSTANCE = new AdSwitch(); }
    public static AdSwitch get() { return Loader.INSTANCE; }

    private final SharedPreferences prefs;
    private final OkHttpClient client = new OkHttpClient();

    private AdSwitch() {
        this.prefs = App.get().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isOn() {
        String userInputCode = prefs.getString(KEY_USER_INPUT_CODE, "");
        String remoteCode = prefs.getString(KEY_REMOTE_PASSWORD_CACHE, "");
        return !userInputCode.isEmpty() && userInputCode.equals(remoteCode);
    }

    public void saveUserCode(String activationCode) {
        prefs.edit().putString(KEY_USER_INPUT_CODE, activationCode).apply();
    }

    public void fetchRemoteCode(String url) {
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { e.printStackTrace(); }
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String encryptedContent = response.body().string();
                        String decryptedJson = decrypt(encryptedContent);
                        class Config { String activation_code; }
                        Config config = new Gson().fromJson(decryptedJson, Config.class);
                        if (config != null && config.activation_code != null) {
                            prefs.edit().putString(KEY_REMOTE_PASSWORD_CACHE, config.activation_code).apply();
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
        });
    }

    private String decrypt(String encryptedText) throws Exception {
        byte[] encryptedData = Base64.decode(encryptedText, Base64.DEFAULT);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(ACT_DECRYPT_KEY, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(ACT_DECRYPT_IV);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] decryptedData = cipher.doFinal(encryptedData);
        return new String(decryptedData, "UTF-8").trim();
    }

    public void deactivate() {
        prefs.edit().remove(KEY_USER_INPUT_CODE).apply();
    }
}
