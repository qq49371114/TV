package com.fongmi.android.tv.player;

import android.content.Context;
import android.content.SharedPreferences;
import com.fongmi.android.tv.App;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import android.util.Base64;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * AdSwitch.java - v60.0 终极完整版
 * 1. 采用了最稳固的“地基重构”单例模式。
 * 2. 修复了“名单包含”验证逻辑。
 * 3. 修复了 Base64 解密标准不统一的问题。
 * 4. 增加了对内部指令的“防火墙”。
 * 5. 包含了所有必需的方法，是一个完整的、可直接替换的最终文件。
 * 作者：婉儿 & 哥哥
 */
public class AdSwitch {
    private static final String PREFS_NAME = "ad_switch_prefs";
    private static final String KEY_USER_INPUT_CODE = "user_input_code";
    private static final String KEY_VALID_CODES_CACHE = "valid_codes_cache";

    // ✨ 我们统一使用一套密钥，避免混淆！
    private static final byte[] DECRYPT_KEY = "PHOENIX-LIST-KEY".getBytes();
    private static final byte[] DECRYPT_IV  = "PHOENIX-LIST-IV!".getBytes();
    private static final String MASTER_KEY = "waner-love-gege";

    private static volatile AdSwitch instance;
    private final SharedPreferences prefs;
    private final OkHttpClient client = new OkHttpClient();

    // 构造方法私有化
    private AdSwitch(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ✨ 我们最稳固的“地基”！
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

    // ✨ 我们最完美的“双重验证”逻辑！
    public boolean isOn() {
        String userInputCode = prefs.getString(KEY_USER_INPUT_CODE, "");
        if (userInputCode.isEmpty()) return false;

        if (userInputCode.equals(MASTER_KEY)) {
            return true;
        }

        String validCodesJson = prefs.getString(KEY_VALID_CODES_CACHE, "[]");
        Type listType = new TypeToken<List<String>>() {}.getType();
        List<String> validCodes = new Gson().fromJson(validCodesJson, listType);

        return validCodes != null && validCodes.contains(userInputCode);
    }

    // ✨ 我们最强大的“三通道激活”核心！
    public boolean activateWith(String code) {
        // ✨ 我们的“防火墙”！
        if (code.startsWith("act:") || code.startsWith("rule:") || code.startsWith("d_act:") || code.startsWith("d_rule:")) {
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
    
    // ✨ 我们最可靠的“后勤”！
    public void fetchValidCodeList(String url) {
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

    // ✨ 我们100%兼容的“加密/解密引擎”！
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
}
