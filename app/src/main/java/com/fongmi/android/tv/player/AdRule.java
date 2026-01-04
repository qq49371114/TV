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

public class AdRule {
    private static final String PREFS_NAME = "ad_rule_prefs";
    private static final String KEY_RULES_JSON_CACHE = "rules_json_cache";
    private static final String KEY_ETAG = "etag";
    private static final String KEY_LAST_MODIFIED = "last_modified";
    private static final String KEY_CONFIG_URL = "config_url";

    private static final AdRule instance = new AdRule();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient internalClient = new OkHttpClient();
    //private final List<String> ads = new CopyOnWriteArrayList<>();
    // 婉儿升级：用一个列表来存储所有M3U8规则，代替原来的单个参数
    private final List<M3u8Rule> m3u8Rules = new CopyOnWriteArrayList<>();
    
    private CountDownLatch latch = new CountDownLatch(1);
    private SharedPreferences prefs;

    // M3U8清洗规则参数 (提供默认值)
    private int minAdTsCount = 5;
    private int maxAdTsCount = 10;
    private double adDuration = 20.0;
    private double adTimeTolerance = 2.0;

    private AdRule() {}
    public static AdRule get() { return instance; }

    // 婉儿升级：新增一个内部类，用于封装单条M3U8广告规则
    public static class M3u8Rule {
        public final String name;
        public final int minAdTsCount;
        public final int maxAdTsCount;
        public final double adDuration;
        public final double adTimeTolerance;

    public M3u8Rule(String name, int min, int max, double duration, double tolerance) {
            this.name = name;
            this.minAdTsCount = min;
            this.maxAdTsCount = max;
            this.adDuration = duration;
            this.adTimeTolerance = tolerance;
        }
    }

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
                    throw new IOException("Failed to fetch rules: " + response.code());
                }
                String content = response.body().string();
                String newEtag = response.header("ETag");
                String newLastModified = response.header("Last-Modified");
                prefs.edit()
                        .putString(KEY_RULES_JSON_CACHE, content)
                        .putString(KEY_ETAG, newEtag != null ? newEtag : "")
                        .putString(KEY_LAST_MODIFIED, newLastModified != null ? newLastModified : "")
                        .apply();
                showToast("凤凰系统云端规则更新成功！");
                parseJson(content);
            } catch (Exception e) {
                showToast("凤凰系统云端规则更新失败！");
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });
    }

    private void loadRulesFromPrefs() {
        String json = prefs.getString(KEY_RULES_JSON_CACHE, null);
        if (json != null) {
            try {
                parseJson(json);
            } catch (Exception e) {
                prefs.edit().remove(KEY_RULES_JSON_CACHE).apply();
                e.printStackTrace();
            }
        }
    }


    public static void setConfigUrl(String url) {
        SharedPreferences p = App.get().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        p.edit().putString(KEY_CONFIG_URL, url).apply();
        get().fetchConfig();
    }

    private void parseJson(String content) throws Exception {
        JSONObject jsonObject = new JSONObject(content);
        
        // 解析 keywords (这部分不变)
        if (jsonObject.has("keywords")) {
            JSONArray keywordsArray = jsonObject.getJSONArray("keywords");
            List<String> newAds = new ArrayList<>();
            for (int i = 0; i < keywordsArray.length(); i++) {
                newAds.add(keywordsArray.getString(i));
            }
            ads.clear();
            ads.addAll(newAds);
        }

        // 核心升级：解析 m3u8_rules 列表
        if (jsonObject.has("m3u8_rules")) {
            List<M3u8Rule> newRules = new ArrayList<>();
            JSONArray rulesArray = jsonObject.getJSONArray("m3u8_rules");
            for (int i = 0; i < rulesArray.length(); i++) {
                JSONObject ruleObj = rulesArray.getJSONObject(i);
                String name = ruleObj.optString("name", "未命名规则");
                int minCount = ruleObj.optInt("minAdTsCount", 1);
                int maxCount = ruleObj.optInt("maxAdTsCount", 1);
                double duration = ruleObj.optDouble("adDuration", 0.0);
                double tolerance = ruleObj.optDouble("adTimeTolerance", 0.5);
                newRules.add(new M3u8Rule(name, minCount, maxCount, duration, tolerance));
            }
            this.m3u8Rules.clear();
            this.m3u8Rules.addAll(newRules);
        }
    }

    public boolean isAd(String urlLine) {
        try {
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


    // 婉儿升级：提供新的getter方法，并删除旧的getter
    public List<M3u8Rule> getM3u8Rules() {
        return m3u8Rules;
    }

    
    //public int getMinAdTsCount() { return minAdTsCount; }
    //public int getMaxAdTsCount() { return maxAdTsCount; }
    //public double getAdDuration() { return adDuration; }
    //public double getAdTimeTolerance() { return adTimeTolerance; }

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
