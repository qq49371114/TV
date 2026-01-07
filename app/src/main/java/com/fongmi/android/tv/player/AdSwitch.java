package com.fongmi.android.tv.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import android.util.Base64;

import com.fongmi.android.tv.App;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * AdSwitch.java - v82.0 最终稳定版
 * 1. 激活逻辑改为本地验证，激活码列表通过远程加密文件获取。
 * 2. 内置“超级密码”后门，并拥有超健壮的解密能力。
 * 3. 彻底移除旧的、不安全的远程激活方式。
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

    // ✨ 婉儿新增的内存激活码名单，用线程安全的Set来存储，查询超快！
    private final Set<String> validCodes = Collections.synchronizedSet(new HashSet<>());

    private AdSwitch(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // ✨ 婉儿新增：在创建实例时，立刻清空上一次的激活码列表！保证一个干净的开始！
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

    public boolean isOn() {
        String activatedCode = prefs.getString(KEY_ACTIVATED_CODE, "");
        return !activatedCode.isEmpty();
    }

    // ✨ App启动时调用的方法，负责获取并加载激活码列表
    public void fetchValidCodeList(String url) {
        try {
            Request request = new Request.Builder().url(url).build();
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful() || response.body() == null) {
                System.err.println("婉儿获取激活名单失败了 T_T，响应码: " + response.code());
                return;
            }

            String encryptedBody = response.body().string();
            // ✨ 调用我们超级健壮的解密方法！
            String decryptedJson = decrypt(encryptedBody);

            // ✨ 必须检查解密是否成功！
            if (decryptedJson != null) {
                Type listType = new TypeToken<List<String>>() {}.getType();
                List<String> codes = new Gson().fromJson(decryptedJson, listType);

                if (codes != null) {
                    validCodes.clear();
                    validCodes.addAll(codes);
                    System.out.println("报告哥哥，婉儿的激活名单已更新，共 " + codes.size() + " 个有效名额！");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("婉儿在处理激活名单时遇到异常了...");
        }
    }

    // ✨ 最终版激活入口，完全本地验证！
    public void activate(Context context, String activationCode) {
        // 第一重检查：是不是我们的“超级密码”？
        if (MASTER_KEY.equals(activationCode)) {
            prefs.edit().putString(KEY_ACTIVATED_CODE, activationCode).apply();
            showToast("超级权限已激活！");
            return;
        }

        // 第二重检查：直接查询内存里的激活名单，瞬间完成！
        if (validCodes.contains(activationCode)) {
            prefs.edit().putString(KEY_ACTIVATED_CODE, activationCode).apply();
            showToast("激活成功！");
        } else {
            showToast("激活失败：无效的激活码。");
        }
    }

// 第二部分：最终版 AdSwitch.java (工具方法)

    // 加密工具方法 (保持不变)
    public String encrypt(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(API_CRYPT_KEY, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(API_CRYPT_IV);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encryptedData = cipher.doFinal(plainText.getBytes("UTF-8"));
        return Base64.encodeToString(encryptedData, Base64.NO_WRAP);
    }

    // ✨ 婉儿升级版解密方法，增加了“金钟罩”，能抵抗任何无效密文！
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
            // 捕获所有解密相关的异常，比如密文截断、格式错误等
            System.err.println("婉儿解密失败，密文可能被截断或格式不正确: " + e.getMessage());
            // 优雅地返回null，而不是让程序崩溃
            return null;
        }
    }

    // UI线程Toast提示工具 (保持不变)
    private void showToast(final String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(App.get(), message, Toast.LENGTH_LONG).show();
        });
    }
}
