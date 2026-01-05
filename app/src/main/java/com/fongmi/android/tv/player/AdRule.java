package com.fongmi.android.tv.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.fongmi.android.tv.App;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import android.util.Base64;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AdRule {
    private static final String PREFS_NAME = "ad_rule_prefs";
    private static final String KEY_RULES_JSON_CACHE = "rules_json_cache";
    private static final String KEY_ETAG = "etag";
    private static final String KEY_LAST_MODIFIED = "last_modified";
    private static final String KEY_CONFIG_URL = "config_url";

    private static final byte[] RULE_DECRYPT_KEY = "PHOENIX-RULE-KEY".getBytes();
    private static final byte[] RULE_DECRYPT_IV  = "PHOENIX-RULE-IV!".getBytes();

    private static final AdRule instance = new AdRule();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient internalClient = new OkHttpClient();
    private CountDownLatch latch = new CountDownLatch(1);
    private SharedPreferences prefs;

    @SerializedName("keywords") private List<String> keywords = new CopyOnWriteArrayList<>();
    @SerializedName("m3u8Keywords") private List<String> m3u8Keywords = new CopyOnWriteArrayList<>();
    @SerializedName("rules") private List<M3u8Rule> rules = new CopyOnWriteArrayList<>();
    @SerializedName("m3u8Strategy") private M3u8Strategy m3u8Strategy;

    public static class M3u8Rule {
        @SerializedName("name") public String name;
        @SerializedName("minAdTsCount") public int minAdTsCount;
        @SerializedName("maxAdTsCount") public int maxAdTsCount;
        @SerializedName("adDuration") public double adDuration;
        @SerializedName("adTimeTolerance") public double adTimeTolerance;
    }

    public static class M3u8Strategy {
        @SerializedName("enabled") private boolean enabled;
        @SerializedName("minBlockSize") private int minBlockSize;
        @SerializedName("maxAvgDuration") private double maxAvgDuration;
        @SerializedName("maxTotalDuration") private double maxTotalDuration;

        public boolean isEnabled() { return enabled; }
        public int getMinBlockSize() { return minBlockSize; }
        public double getMaxAvgDuration() { return maxAvgDuration; }
        public double getMaxTotalDuration() { return maxTotalDuration; }
    }

    private AdRule() {}
    public static AdRule get() { return instance; }

    public void init(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadRulesFromPrefs();
        fetchConfig();
    }

    public void fetchConfig() {
        String url = prefs.getString(KEY_CONFIG_URL, "");
        if (url != null && !url.isEmpty()) {
            load(url);
        }
    }

    public void load(String urlString) {
        latch = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                Request.Builder requestBuilder = new Request.Builder().url(urlString);
                String etag = prefs.getString(KEY_ETAG, "");
                String lastModified = prefs.getString(KEY_LAST_MODIFIED, "");
                if (!etag.isEmpty()) requestBuilder.header("If-None-Match", etag);
                if (!lastModified.isEmpty()) requestBuilder.header("If-Modified-Since", lastModified);
                Response response = internalClient.newCall(requestBuilder.build()).execute();
                if (response.code() == 304) {
                    latch.countDown();
                    return;
                }
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException("下载规则失败: " + response.code());
                }
                String encryptedContent = response.body().string();
                String decryptedContent = decryptRule(encryptedContent);
                String newEtag = response.header("ETag");
                String newLastModified = response.header("Last-Modified");
                prefs.edit()
                        .putString(KEY_RULES_JSON_CACHE, decryptedContent)
                        .putString(KEY_ETAG, newEtag != null ? newEtag : "")
                        .putString(KEY_LAST_MODIFIED, newLastModified != null ? newLastModified : "")
                        .apply();
                parseJson(decryptedContent);
            } catch (Exception e) {
                showToast("凤凰系统云端规则更新失败：" + e.getMessage());
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });
    }

    public String encrypt(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(RULE_DECRYPT_KEY, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(RULE_DECRYPT_IV);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encryptedData = cipher.doFinal(plainText.getBytes("UTF-8"));
        return Base64.encodeToString(encryptedData, Base64.NO_WRAP);
    }

    public String decryptRule(String encryptedText) throws Exception {
        byte[] encryptedData = Base64.decode(encryptedText, Base64.DEFAULT);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(RULE_DECRYPT_KEY, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(RULE_DECRYPT_IV);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] decryptedData = cipher.doFinal(encryptedData);
        return new String(decryptedData, "UTF-8").trim();
    }

    private void loadRulesFromPrefs() {
        String json = prefs.getString(KEY_RULES_JSON_CACHE, null);
        if (json != null) {
            parseJson(json);
        }
    }

    public static void setConfigUrl(String url) {
        SharedPreferences p = App.get().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        p.edit().putString(KEY_CONFIG_URL, url).apply();
        get().fetchConfig();
    }

    private void parseJson(String content) {
        try {
            AdRule tempRule = new Gson().fromJson(content, AdRule.class);
            if (tempRule == null) throw new Exception("JSON格式不正确");
            if (tempRule.getKeywords() != null) this.keywords.addAll(tempRule.getKeywords());
            if (tempRule.getM3u8Keywords() != null) this.m3u8Keywords.addAll(tempRule.getM3u8Keywords());
            if (tempRule.getM3u8Rules() != null) this.rules.addAll(tempRule.getM3u8Rules());
            this.m3u8Strategy = tempRule.getM3u8Strategy();
            showToast("凤凰系统规则解析成功！");
        } catch (Exception e) {
            e.printStackTrace();
            showToast("凤凰系统规则解析失败：" + e.getMessage());
        }
    }

    public boolean isAd(String urlLine) {
        await();
        if (urlLine == null || urlLine.trim().isEmpty() || this.keywords == null || this.keywords.isEmpty()) return false;
        for (String keyword : this.keywords) {
            if (urlLine.contains(keyword)) return true;
        }
        return false;
    }

    public void await() {
        try {
            latch.await(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public List<String> getKeywords() { return keywords; }
    public List<String> getM3u8Keywords() { return m3u8Keywords; }
    public List<M3u8Rule> getM3u8Rules() { return rules; }
    public M3u8Strategy getM3u8Strategy() { return m3u8Strategy; }

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
