package com.fongmi.android.tv.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.fongmi.android.tv.App;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 广告规则管理中心 (单例)
 * 负责从云端获取、缓存和提供广告规则。
 * 被称为“凤凰大脑”，为 AdFilter 提供决策支持。
 * by 婉儿
 */
public class AdRule {
    // SharedPreferences 相关常量
    private static final String PREFS_NAME = "ad_rule_prefs";
    private static final String KEY_RULES_JSON_CACHE = "rules_json_cache";
    private static final String KEY_ETAG = "etag";
    private static final String KEY_LAST_MODIFIED = "last_modified";
    private static final String KEY_CONFIG_URL = "config_url";

    // 单例实例
    private static final AdRule instance = new AdRule();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient internalClient = new OkHttpClient();
    private final List<String> ads = new CopyOnWriteArrayList<>(); // 线程安全的广告关键词列表
    private CountDownLatch latch = new CountDownLatch(1);
    private SharedPreferences prefs;

    // M3U8内嵌广告的识别参数 (可由云端配置动态更新)
    private int adTsCount = 8;
    private double adDuration = 17.0;
    private double adTimeTolerance = 2.0;

    // 私有构造函数，防止外部实例化
    private AdRule() {}

    // 获取单例实例的唯一方法
    public static AdRule get() {
        return instance;
    }

    /**
     * 初始化，在Application启动时调用
     */
    public void init(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadRulesFromPrefs(); // 先从本地缓存加载规则
        fetchConfig();        // 再尝试从云端更新规则
    }

    /**
     * 从已保存的URL配置中获取最新规则
     */
    public void fetchConfig() {
        String url = prefs.getString(KEY_CONFIG_URL, "");
        if (url != null && !url.isEmpty()) {
            load(url);
        }
    }

    /**
     * 从指定URL加载规则，支持HTTP缓存
     */
    public void load(String urlString) {
        latch = new CountDownLatch(1); // 重置latch，表示新的加载任务开始
        executor.execute(() -> {
            try {
                Request.Builder requestBuilder = new Request.Builder().url(urlString);
                String etag = prefs.getString(KEY_ETAG, "");
                String lastModified = prefs.getString(KEY_LAST_MODIFIED, "");

                // 添加HTTP缓存头，如果规则未改变，服务器会返回304
                if (!etag.isEmpty()) requestBuilder.header("If-None-Match", etag);
                if (!lastModified.isEmpty()) requestBuilder.header("If-Modified-Since", lastModified);

                Response response = internalClient.newCall(requestBuilder.build()).execute();

                // 规则未修改，无需处理
                if (response.code() == 304) {
                    latch.countDown();
                    return;
                }

                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException("Failed to fetch rules: " + response.code());
                }

                String content = response.body().string();
                String newEtag = response.header("ETag");
                String newLastModified = response.header("Last-Modified");

                // 缓存新规则和缓存头
                prefs.edit()
                        .putString(KEY_RULES_JSON_CACHE, content)
                        .putString(KEY_ETAG, newEtag != null ? newEtag : "")
                        .putString(KEY_LAST_MODIFIED, newLastModified != null ? newLastModified : "")
                        .apply();

                showToast("云端去广告规则更新成功！");
                parseJson(content);
            } catch (Exception e) {
                showToast("云端去广告规则更新失败！");
                e.printStackTrace();
            } finally {
                latch.countDown(); // 任务完成，释放latch
            }
        });
    }

    /**
     * 从本地SharedPreferences加载缓存的规则
     */
    private void loadRulesFromPrefs() {
        String json = prefs.getString(KEY_RULES_JSON_CACHE, null);
        if (json != null) {
            try {
                parseJson(json);
            } catch (Exception e) {
                // 解析失败则清空缓存，避免下次启动时再次失败
                prefs.edit().remove(KEY_RULES_JSON_CACHE).apply();
                e.printStackTrace();
            }
        }
    }

    /**
     * 供外部设置云端规则的URL地址
     */
    public static void setConfigUrl(String url) {
        SharedPreferences p = App.get().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        p.edit().putString(KEY_CONFIG_URL, url).apply();
        get().fetchConfig(); // 设置后立即尝试获取
    }

    /**
     * 解析JSON规则内容，并更新到内存中
     */
    private void parseJson(String content) throws Exception {
        JSONObject jsonObject = new JSONObject(content);
        if (jsonObject.has("keywords")) {
            JSONArray keywordsArray = jsonObject.getJSONArray("keywords");
            List<String> newAds = new ArrayList<>();
            for (int i = 0; i < keywordsArray.length(); i++) {
                newAds.add(keywordsArray.getString(i));
            }
            ads.clear();
            ads.addAll(newAds);
        }
        // 动态更新M3U8去广告参数
        if (jsonObject.has("adTsCount")) adTsCount = jsonObject.getInt("adTsCount");
        if (jsonObject.has("adDuration")) adDuration = jsonObject.getDouble("adDuration");
        if (jsonObject.has("adTimeTolerance")) adTimeTolerance = jsonObject.getDouble("adTimeTolerance");
    }

    /**
     * 判断给定的URL是否为广告链接
     * @return true如果是广告，false如果不是
     */
    public boolean isAd(String urlLine) {
        try {
            // 等待最多2秒，确保初始化加载完成
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        if (urlLine == null || urlLine.trim().isEmpty()) return false;

        for (String keyword : ads) {
            if (urlLine.contains(keyword)) return true;
        }
        return false;
    }

    // === 提供给AdFilter的参数接口 ===
    public int getAdTsCount() { return adTsCount; }
    public double getAdDuration() { return adDuration; }
    public double getAdTimeTolerance() { return adTimeTolerance; }

    /**
     * 在主线程安全地显示Toast
     */
    private void showToast(final String message) {
        Context context = App.get();
        if (context == null) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
