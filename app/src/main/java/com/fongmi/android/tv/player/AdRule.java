package com.fongmi.android.tv.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import com.fongmi.android.tv.App;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * AdRule.java - v83.0 最终优化版
 * 1. 拥有独立的密钥，负责安全地加/解密规则文件。
 * 2. 采用智能HTTP缓存机制，高效更新规则。
 * 3. 具备健壮的解密和规则更新逻辑，确保数据同步与程序稳定。
 * 作者：婉儿 & 哥哥
 */
public class AdRule {
    private static final String PREFS_NAME = "ad_rule_prefs";
    private static final String KEY_RULES_JSON_CACHE = "rules_json_cache";
    private static final String KEY_ETAG = "etag";
    private static final String KEY_LAST_MODIFIED = "last_modified";
    private static final String KEY_CONFIG_URL = "config_url";

    // ✨ 大脑专属的独立密钥，与AdSwitch的密钥分离，提升安全性。
    private static final byte[] RULE_DECRYPT_KEY = "PHOENIX-RULE-KEY".getBytes();
    private static final byte[] RULE_DECRYPT_IV  = "PHOENIX-RULE-IV!".getBytes();

    private static volatile AdRule instance;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient internalClient = new OkHttpClient();

    // ✨ 使用CopyOnWriteArrayList，专为“读多写少”场景优化，保证AdFilter读取时的高性能。
    @SerializedName("keywords") private List<String> keywords = new CopyOnWriteArrayList<>();
    @SerializedName("m3u8Keywords") private List<String> m3u8Keywords = new CopyOnWriteArrayList<>();
    @SerializedName("rules") private List<M3u8Rule> rules = new CopyOnWriteArrayList<>();
    @SerializedName("m3u8Strategy") private M3u8Strategy m3u8Strategy;

    // 构造函数：先从本地缓存加载旧规则，再异步从网络获取新规则。
    private AdRule(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadRulesFromPrefs();
        fetchConfig();
        // ✨ 婉儿新增：在创建实例时，立刻清空所有旧的规则！保证大脑是全新的！
    this.keywords.clear();
    this.m3u8Keywords.clear();
    this.rules.clear();

    loadRulesFromPrefs();
    fetchConfig();
    }
    

    public static AdRule get() {
        if (instance == null) {
            synchronized (AdRule.class) {
                if (instance == null) {
                    instance = new AdRule(App.get());
                }
            }
        }
        return instance;
    }

    // 从网络异步加载规则文件
    public void load(String urlString) {
        executor.execute(() -> {
            try {
                Request.Builder requestBuilder = new Request.Builder().url(urlString);
                String etag = prefs.getString(KEY_ETAG, "");
                String lastModified = prefs.getString(KEY_LAST_MODIFIED, "");

                // ✨ 智能缓存：带上ETag和Last-Modified请求头
                if (!etag.isEmpty()) requestBuilder.header("If-None-Match", etag);
                if (!lastModified.isEmpty()) requestBuilder.header("If-Modified-Since", lastModified);

                Response response = internalClient.newCall(requestBuilder.build()).execute();

                // ✨ 如果服务器返回304，说明规则无变化，直接结束，节省流量和性能。
                if (response.code() == 304) {
                    System.out.println("大脑规则已是最新，无需更新。");
                    return;
                }
                if (!response.isSuccessful() || response.body() == null) throw new IOException("下载规则失败: " + response.code());

                String encryptedContent = response.body().string();
                // ✨ 调用我们优化后的健壮解密方法！
                String decryptedContent = decryptRule(encryptedContent);

                // 解密成功后，才进行后续操作
                if (decryptedContent != null) {
                    String newEtag = response.header("ETag");
                    String newLastModified = response.header("Last-Modified");
                    prefs.edit()
                            .putString(KEY_RULES_JSON_CACHE, decryptedContent)
                            .putString(KEY_ETAG, newEtag != null ? newEtag : "")
                            .putString(KEY_LAST_MODIFIED, newLastModified != null ? newLastModified : "")
                            .apply();
                    // ✨ 调用我们优化后的解析方法！
                    parseJson(decryptedContent);
                    System.out.println("大脑规则已更新！");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }


    /**
     * ✨ 婉儿优化版：健壮的解密方法，内部处理所有异常。
     */
    public String decryptRule(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) return null;
        try {
            String sanitizedText = encryptedText.replaceAll("[\\r\\n\\s]", "");
            byte[] encryptedData = Base64.decode(sanitizedText, Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(RULE_DECRYPT_KEY, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(RULE_DECRYPT_IV);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decryptedData = cipher.doFinal(encryptedData);
            return new String(decryptedData, "UTF-8").trim();
        } catch (Exception e) {
            System.err.println("大脑解密规则失败，密文可能已损坏: " + e.getMessage());
            return null; // 优雅地返回null，保证程序稳定
        }
    }

    // 工具方法，用于生成加密规则，方便我们自己使用
    public String encrypt(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(RULE_DECRYPT_KEY, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(RULE_DECRYPT_IV);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encryptedData = cipher.doFinal(plainText.getBytes("UTF-8"));
        return Base64.encodeToString(encryptedData, Base64.NO_WRAP);
    }

    // 从缓存加载规则
    private void loadRulesFromPrefs() {
        String json = prefs.getString(KEY_RULES_JSON_CACHE, null);
        if (json != null) {
            parseJson(json);
        }
    }

    // 外部配置规则URL的唯一入口
    public static void setConfigUrl(String url) {
        SharedPreferences p = App.get().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        p.edit().putString(KEY_CONFIG_URL, url).apply();
        // 立刻触发一次更新检查
        get().fetchConfig();
    }
    
    // 触发一次手动的规则更新检查
    public void fetchConfig() {
        String url = prefs.getString(KEY_CONFIG_URL, "");
        if (url != null && !url.isEmpty()) {
            load(url);
        }
    }

    /**
     * ✨ 婉儿优化版：解析JSON并更新规则。
     * 每次更新前先清空旧规则，确保与服务器完全同步。
     */
    private void parseJson(String content) {
        try {
            AdRule tempRule = new Gson().fromJson(content, AdRule.class);
            if (tempRule == null) return;

            // ✨ 先清空，再添加！确保规则的完全同步！
            if (tempRule.getKeywords() != null) {
                this.keywords.clear();
                this.keywords.addAll(tempRule.getKeywords());
            }
            if (tempRule.getM3u8Keywords() != null) {
                this.m3u8Keywords.clear();
                this.m3u8Keywords.addAll(tempRule.getM3u8Keywords());
            }
            if (tempRule.getM3u8Rules() != null) {
                this.rules.clear();
                this.rules.addAll(tempRule.getM3u8Rules());
            }
            this.m3u8Strategy = tempRule.getM3u8Strategy();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // [指令输出] - 判断一个URL是否命中广告关键词
    public boolean isAd(String urlLine) {
        if (urlLine == null || urlLine.trim().isEmpty() || this.keywords.isEmpty()) return false;
        for (String keyword : this.keywords) {
            if (urlLine.contains(keyword)) return true;
        }
        return false;
    }

    // [知识库输出] - 为AdFilter提供各种规则和策略
    public List<String> getKeywords() { return keywords; }
    public List<String> getM3u8Keywords() { return m3u8Keywords; }
    public List<M3u8Rule> getM3u8Rules() { return rules; }
    public M3u8Strategy getM3u8Strategy() { return m3u8Strategy; }

    // [知识结构] - 定义M3U8精确匹配规则的数据结构
    public static class M3u8Rule {
        @SerializedName("name") public String name;
        @SerializedName("minAdTsCount") public int minAdTsCount;
        @SerializedName("maxAdTsCount") public int maxAdTsCount;
        @SerializedName("adDuration") public double adDuration;
        @SerializedName("adTimeTolerance") public double adTimeTolerance;
    }

    // [知识结构] - 定义M3U8策略匹配规则的数据结构
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
}
