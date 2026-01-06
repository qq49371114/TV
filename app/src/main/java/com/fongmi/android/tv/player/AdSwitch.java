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

public class AdSwitch {
    private static final String PREFS_NAME = "ad_switch_prefs";
    private static final String KEY_USER_INPUT_CODE = "user_input_code";
    private static final String KEY_VALID_CODES_CACHE = "valid_codes_cache";

    private static final byte[] DECRYPT_KEY = "PHOENIX-LIST-KEY".getBytes();
    private static final byte[] DECRYPT_IV  = "PHOENIX-LIST-IV!".getBytes();
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
        String userInputCode = prefs.getString(KEY_USER_INPUT_CODE, "");
        if (userInputCode.isEmpty()) return false;
        if (userInputCode.equals(MASTER_KEY)) return true;
        String validCodesJson = prefs.getString(KEY_VALID_CODES_CACHE, "[]");
        Type listType = new TypeToken<List<String>>() {}.getType();
        List<String> validCodes = new Gson().fromJson(validCodesJson, listType);
        return validCodes.contains(userInputCode);
    }

    public boolean activateWith(String code) {
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
        if (validCodes.contains(plainCode)) {
            prefs.edit().putString(KEY_USER_INPUT_CODE, plainCode).apply();
            return true;
        } else {
            return false;
        }
    }
    
    public void saveUserCode(String activationCode) {
        prefs.edit().putString(KEY_USER_INPUT_CODE, activationCode).apply();
    }

    // 在 AdSwitch.java 文件中
    public void fetchValidCodeList(String url) {
        new Thread(() -> {
            try {
                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String encryptedContent = response.body().string();
                    String decryptedJson = decrypt(encryptedContent);
                    
                    // ================= ▼ 婉儿的“双格式”兼容核心！▼ =================
                    
                    // ✨ 先尝试解析成“名片”格式
                    class SingleCodeConfig { String activation_code; }
                    try {
                        SingleCodeConfig singleConfig = new Gson().fromJson(decryptedJson, SingleCodeConfig.class);
                        if (singleConfig != null && singleConfig.activation_code != null) {
                            // 如果成功从“名片”上读到了名字，就包装成名单存起来
                            String singleCodeListJson = "[\"" + singleConfig.activation_code + "\"]";
                            prefs.edit().putString(KEY_VALID_CODES_CACHE, singleCodeListJson).apply();
                            return; // ✨ 处理完毕，直接结束！
                        }
                    } catch (Exception e) {
                        // 解析成“名片”失败，没关系，我们继续往下尝试
                    }

                    // ✨ 如果上面解析“名片”失败了，再尝试解析成“花名册”格式
                    class ListConfig { List<String> valid_codes; }
                    try {
                        ListConfig listConfig = new Gson().fromJson(decryptedJson, ListConfig.class);
                        if (listConfig != null && listConfig.valid_codes != null) {
                             prefs.edit().putString(KEY_VALID_CODES_CACHE, new Gson().toJson(listConfig.valid_codes)).apply();
                        }
                    } catch (Exception e) {
                        // 如果两种格式都解析失败，那也没办法了
                    }
                    // ================= ▲ 升级结束！▲ =================
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
        byte[] encryptedData = Base64.decode(encryptedText, Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(DECRYPT_KEY, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(DECRYPT_IV);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] decryptedData = cipher.doFinal(encryptedData);
        return new String(decryptedData, "UTF-8").trim();
    }
}
