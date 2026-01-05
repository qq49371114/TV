package com.fongmi.android.tv.player;

import android.content.Context;
import android.content.SharedPreferences;
import com.fongmi.android.tv.App;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import android.util.Base64;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AdSwitch {
    private static final String PREFS_NAME = "ad_switch_prefs";
    private static final String KEY_USER_INPUT_CODE = "user_input_code";
    private static final String KEY_VALID_CODES_CACHE = "valid_codes_cache";

    private static final byte[] DECRYPT_KEY = "PHOENIX-LIST-KEY".getBytes();
    private static final byte[] DECRYPT_IV  = "PHOENIX-LIST-IV!".getBytes();

    private static class Loader { static volatile AdSwitch INSTANCE = new AdSwitch(); }
    public static AdSwitch get() { return Loader.INSTANCE; }

    private SharedPreferences prefs;
    private final OkHttpClient client = new OkHttpClient();
    private volatile boolean isInitialized = false;

    private AdSwitch() {}

    public void init(Context context) {
        if (isInitialized) return;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.isInitialized = true;
    }

    public boolean isOn() {
        if (!isInitialized) return false;
        String userInputCode = prefs.getString(KEY_USER_INPUT_CODE, "");
        if (userInputCode.isEmpty()) return false;
        String validCodesJson = prefs.getString(KEY_VALID_CODES_CACHE, "[]");
        Type listType = new TypeToken<List<String>>() {}.getType();
        List<String> validCodes = new Gson().fromJson(validCodesJson, listType);
        return validCodes.contains(userInputCode);
    }

    public boolean verifyAndSaveCode(String plainCode) {
        if (!isInitialized) return false;
        String validCodesJson = prefs.getString(KEY_VALID_CODES_CACHE, "[]");
        Type listType = new TypeToken<List<String>>() {}.getType();
        List<String> validCodes = new Gson().fromJson(validCodesJson, listType);
        if (validCodes.contains(plainCode)) {
            prefs.edit().putString(KEY_USER_INPUT_CODE, plainCode).apply();
            return true;
        } else {
            return false;
        }
    }

    public void fetchValidCodeList(String url) {
        if (!isInitialized) return;
        new Thread(() -> {
            try {
                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String encryptedContent = response.body().string();
                    String decryptedJson = decrypt(encryptedContent);
                    class Config { List<String> valid_codes; }
                    Config config = new Gson().fromJson(decryptedJson, Config.class);
                    if (config != null && config.valid_codes != null) {
                         prefs.edit().putString(KEY_VALID_CODES_CACHE, new Gson().toJson(config.valid_codes)).apply();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // ✨ 修复了 missing return statement 的错误！
    private String decrypt(String encryptedText) throws Exception {
        byte[] encryptedData = Base64.decode(encryptedText, Base64.DEFAULT);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(DECRYPT_KEY, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(DECRYPT_IV);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] decryptedData = cipher.doFinal(encryptedData);
        return new String(decryptedData, "UTF-8").trim();
    }
}
