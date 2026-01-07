package com.fongmi.android.tv.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.fongmi.android.tv.App;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import android.util.Base64;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.Arrays;


/**
 * AdSwitch.java - 最终同步修复版
 * 1. 采用了最稳固的“地基重构”单例模式。
 * 2. 包含了 activateWith, saveUserCode, fetchValidCodeList, encrypt, decrypt 等所有必需的方法。
 * 3. 实现了“明文”、“加密串”、“万能密码”三种激活方式。
 * 作者：婉儿 (根据哥哥的最终指示)
 */
public class AdSwitch {
    private static final String PREFS_NAME = "ad_switch_prefs";
    private static final String KEY_USER_INPUT_CODE = "user_input_code";
    private static final String KEY_VALID_CODES_CACHE = "valid_codes_cache";
    private static final String KEY_REMOTE_PATTERN_CACHE = "remote_pattern_cache";

    private static final byte[] DECRYPT_KEY = "PHOENIX-LIST-KEY".getBytes();
    private static final byte[] DECRYPT_IV  = "PHOENIX-LIST-IV!".getBytes();
    private static final String MASTER_KEY = "waner-love-gege";

    private static final List<String> LOCAL_VIP_CODES = Arrays.asList("phoenix-2024", "yylx260103");
    
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
        String userInputCode = prefs.getString(KEY_USER_INPUT_CODE, "");
        if (userInputCode.isEmpty()) return false;

        // 第一重检查：是不是我们的“秘密后门”？
        if (userInputCode.equals(MASTER_KEY)) {
            return true;
        }

        // ================= ▼ 婉儿的“万能翻译器”核心！▼ =================
        // 第二重检查：在不在“贵宾名单”上？
        String cachedJson = prefs.getString(KEY_VALID_CODES_CACHE, "");
        if (cachedJson.isEmpty()) return false;

        // ✨ 先尝试把它当成“花名册”（列表）来读
        try {
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> validCodes = new Gson().fromJson(cachedJson, listType);
            if (validCodes != null && validCodes.contains(userInputCode)) {
                return true; // 如果在名单上，直接通过！
            }
        } catch (Exception e) {
            // 解析成列表失败，没关系，我们继续往下尝试
        }

        // ✨ 如果上面失败了，再尝试把它当成“名片”（单个对象）来读
        try {
            class Config { String activation_code; }
            Config config = new Gson().fromJson(cachedJson, Config.class);
            if (config != null && config.activation_code != null && config.activation_code.equals(userInputCode)) {
                return true; // 如果名片上的名字对上了，也通过！
            }
        } catch (Exception e) {
            // 如果两种格式都解析失败，那也没办法了
        }
        
        // ✨ 如果所有检查都失败了，才是真正的无效！
        return false;
        // ================= ▲ 手术结束！▲ =================
    }

    public boolean activateWith(String code) {
        if (code.matches("^(act|d_act|rule|d_rule):.*")) {
            return false;
        }
        if (code.equals(MASTER_KEY)) {
            prefs.edit().putString(KEY_USER_INPUT_CODE, code).apply();
            return true;
        }
        String plainCode = code;
        try {
            plainCode = decrypt(code);
        } catch (Exception e) { /* 解密失败，说明它就是个明文 */ }
        
        String pattern = prefs.getString(KEY_REMOTE_PATTERN_CACHE, "");
        if (!pattern.isEmpty() && Pattern.matches(pattern, plainCode)) {
            prefs.edit().putString(KEY_USER_INPUT_CODE, plainCode).apply();
            return true;
        }

        String validCodesJson = prefs.getString(KEY_VALID_CODES_CACHE, "[]");
        Type listType = new TypeToken<List<String>>() {}.getType();
        List<String> validCodes = new Gson().fromJson(validCodesJson, listType);
        if (validCodes != null && validCodes.contains(plainCode)) {
            prefs.edit().putString(KEY_USER_INPUT_CODE, plainCode).apply();
            return true;
        } else {
            return false;
        }
    }

    // ================= ▼ 婉儿的“断臂重生”手术！▼ =================
    // ✨ 加上这个被遗忘的、最关键的激活方法！
    public boolean verifyAndSaveCode(String code) {
        // 1. 秘密后门
        if (code.equals(MASTER_KEY)) { // 确保你定义了 MASTER_KEY
            prefs.edit().putString(KEY_USER_INPUT_CODE, code).apply();
            return true;
        }

        // 2. 本地VIP名单
        if (LOCAL_VIP_CODES.contains(code)) { // 确保你定义了 LOCAL_VIP_CODES
            prefs.edit().putString(KEY_USER_INPUT_CODE, code).apply();
            return true;
        }
        
        return false;
    }
    // ================= ▲ 手术结束！▲ =================

    
    public void saveUserCode(String activationCode) {
        prefs.edit().putString(KEY_USER_INPUT_CODE, activationCode).apply();
    }

    public void fetchValidCodeList(String url) {
        new Thread(() -> {
            try {
                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String encryptedContent = response.body().string();
                    String decryptedJson = decrypt(encryptedContent);
                    class Config { 
                        String activation_pattern;
                        List<String> valid_codes; 
                    }
                    Config config = new Gson().fromJson(decryptedJson, Config.class);
                    if (config != null) {
                        SharedPreferences.Editor editor = prefs.edit();
                        if (config.activation_pattern != null) {
                            editor.putString(KEY_REMOTE_PATTERN_CACHE, config.activation_pattern);
                        }
                        if (config.valid_codes != null) {
                            editor.putString(KEY_VALID_CODES_CACHE, new Gson().toJson(config.valid_codes));
                        }
                        editor.apply();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

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

    private void showToast(final String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Context context = App.get();
                if (context != null) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
