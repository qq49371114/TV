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

/**
 * AdSwitch.java - v32.0 即时验卡版
 * 1. 核心逻辑改为“当场验证”：提供一个方法，接收明文码，直接返回是否在贵宾名单中。
 * 2. 不再需要 saveEncryptedCode，验证和保存在一步完成。
 * 作者：婉儿 (根据哥哥的最终指示)
 */
public class AdSwitch {
    private static final String PREFS_NAME = "ad_switch_prefs";
    private static final String KEY_USER_INPUT_CODE = "user_input_code"; // ✨ 我们现在只保存验证通过的“明文码”
    private static final String KEY_VALID_CODES_CACHE = "valid_codes_cache";

    private static final byte[] LIST_DECRYPT_KEY = "PHOENIX-LIST-KEY".getBytes();
    private static final byte[] LIST_DECRYPT_IV  = "PHOENIX-LIST-IV!".getBytes();

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

    // 最终的“贵宾名单”验证法！
    public boolean isOn() {
        if (!isInitialized) return false;
        String userInputCode = prefs.getString(KEY_USER_INPUT_CODE, "");
        if (userInputCode.isEmpty()) return false;

        String validCodesJson = prefs.getString(KEY_VALID_CODES_CACHE, "[]");
        Type listType = new TypeToken<List<String>>() {}.getType();
        List<String> validCodes = new Gson().fromJson(validCodesJson, listType);

        return validCodes.contains(userInputCode);
    }

    // ✨✨✨ 这就是我们全新的“当场验卡”核心方法！✨✨✨
    public boolean verifyAndSaveCode(String plainCode) {
        if (!isInitialized) return false;
        
        String validCodesJson = prefs.getString(KEY_VALID_CODES_CACHE, "[]");
        Type listType = new TypeToken<List<String>>() {}.getType();
        List<String> validCodes = new Gson().fromJson(validCodesJson, listType);

        if (validCodes.contains(plainCode)) {
            // 验证通过！把这个有效的明文码存起来！
            prefs.edit().putString(KEY_USER_INPUT_CODE, plainCode).apply();
            return true;
        } else {
            // 验证失败！
            return false;
        }
    }

    // 下载并缓存“贵宾名单”
    public void fetchValidCodeList(String url) {
        // ... (这个方法保持不变)
    }

    private String decrypt(String encryptedText) throws Exception {
        // ... (这个方法保持不变)
    }
}
