package com.fongmi.android.tv.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import android.util.Base64;

import com.fongmi.android.tv.App;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * AdSwitch.java - v83.0 激活有效期版
 * 1. 激活逻辑改为本地验证，支持带“保质期”的激活码。
 * 2. 内置“超级密码”后门，永不过期。
 * 3. App自己就能判断激活是否过期，无需服务器支持。
 * 作者：婉儿 & 哥哥
 */
public class AdSwitch {
    private static final String PREFS_NAME = "ad_switch_prefs";
    private static final String KEY_ACTIVATED_CODE = "activated_code";
    // ✨ 婉儿新增：用来保存“过期时间戳”的键
    private static final String KEY_EXPIRES_AT = "expires_at";

    private static final byte[] API_CRYPT_KEY = "PHOENIX-API-KEY!".getBytes();
    private static final byte[] API_CRYPT_IV  = "PHOENIX-API-IV!!".getBytes();
    private static final String MASTER_KEY = "waner-love-gege";

    private static volatile AdSwitch instance;
    private final SharedPreferences prefs;
    private final OkHttpClient client = new OkHttpClient();

    // ✨ 婉儿升级：内存名单不再是简单的字符串，而是能存放“保质期”的ActivationCode对象！
    private final List<ActivationCode> validCodes = new CopyOnWriteArrayList<>();

    // ✨ 婉儿新增：定义我们新的“激活码”对象结构
    private static class ActivationCode {
        @SerializedName("code")
        String code;
        @SerializedName("expires_in")
        long expiresIn; // 单位是秒
    }

    private AdSwitch(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.validCodes.clear(); 
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

    /**
     * ✨ 婉儿升级：自己当保安，检查“月卡”有没有过期！
     */
    public boolean isOn() {
        String activatedCode = prefs.getString(KEY_ACTIVATED_CODE, "");
        if (activatedCode.isEmpty()) {
            return false;
        }
        
        // ✨ 超级密码，永不过期！
        if (activatedCode.equals(MASTER_KEY)) {
            return true;
        }

        // ✨ 核心改动：自己检查月卡有效期！
        long expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0);
        long currentTime = System.currentTimeMillis() / 1000;

        if (expiresAt > 0 && currentTime > expiresAt) {
            // 月卡过期了！自己把自己变回未激活状态！
            prefs.edit().remove(KEY_ACTIVATED_CODE).remove(KEY_EXPIRES_AT).apply();
            showToast("您的激活已过期，请重新激活。");
            // ✨ 发送一个广播，通知UI刷新！
            org.greenrobot.eventbus.EventBus.getDefault().post(new ActivationEvent());
            return false; // 返回“未激活”
        }

        return true; // 如果没过期，就返回“已激活”
    }

    /**
     * ✨ 婉儿升级：加载新的、带“保质期”的激活名单！
     */
    public void fetchValidCodeList(String url) {
        try {
            Request request = new Request.Builder().url(url).build();
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful() || response.body() == null) {
                return;
            }

            String encryptedBody = response.body().string();
            String decryptedJson = decrypt(encryptedBody);

            if (decryptedJson != null) {
                // ✨ 核心改动：用Gson把JSON解析成我们新的ActivationCode对象列表！
                Type listType = new TypeToken<List<ActivationCode>>() {}.getType();
                List<ActivationCode> codes = new Gson().fromJson(decryptedJson, listType);

                if (codes != null) {
                    validCodes.clear();
                    validCodes.addAll(codes);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * ✨ 婉儿升级：激活时，计算并保存“过期时间戳”！
     */
    public void activate(Context context, String activationCode) {
        // 第一重检查：超级密码
        if (MASTER_KEY.equals(activationCode)) {
            prefs.edit().putString(KEY_ACTIVATED_CODE, activationCode).apply();
            // ✨ 超级密码永不过期，所以要清除掉可能存在的旧的过期时间
            prefs.edit().remove(KEY_EXPIRES_AT).apply();
            showToast("超级权限已激活！");
            org.greenrobot.eventbus.EventBus.getDefault().post(new ActivationEvent());
            return;
        }

        // ✨ 核心改动：在新的名单里查找激活码！
        ActivationCode foundCode = null;
        for (ActivationCode ac : validCodes) {
            if (ac.code.equals(activationCode)) {
                foundCode = ac;
                break;
            }
        }

        if (foundCode != null) { // 如果找到了
            prefs.edit().putString(KEY_ACTIVATED_CODE, activationCode).apply();
            
            // ✨ 计算并保存“过期时间戳”！
            if (foundCode.expiresIn > 0) {
                long expiresAt = (System.currentTimeMillis() / 1000) + foundCode.expiresIn;
                prefs.edit().putLong(KEY_EXPIRES_AT, expiresAt).apply();
            } else {
                // 如果是永不过期的码，就清除掉旧的过期时间
                prefs.edit().remove(KEY_EXPIRES_AT).apply();
            }
            
            showToast("激活成功！");
            org.greenrobot.eventbus.EventBus.getDefault().post(new ActivationEvent());
        } else {
            showToast("激活失败：无效的激活码。");
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return null;
        }
        try {
            String sanitizedText = encryptedText.replaceAll("[\\r\\n\\s]", "");
            byte[] encryptedData = Base64.decode(sanitizedText, Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(API_CRYPT_KEY, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(API_CRYPT_IV);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decryptedData = cipher.doFinal(encryptedData);
            return new String(decryptedData, "UTF-8").trim();
        } catch (Exception e) {
            System.err.println("婉儿解密失败，密文可能被截断或格式不正确: " + e.getMessage());
            return null;
        }
    }

    private void showToast(final String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(App.get(), message, Toast.LENGTH_LONG).show();
        });
    }
}
