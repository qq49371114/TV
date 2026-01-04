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
    private final List<String> ads = new CopyOnWriteArrayList<>();
    private CountDownLatch latch = new CountDownLatch(1);
    private SharedPreferences prefs;

    // M3U8清洗规则参数 (提供默认值)
    private int minAdTsCount = 5;
    private int maxAdTsCount = 10;
    private double adDuration = 20.0;
    private double adTimeTolerance = 2.0;

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
        if (jsonObject.has("keywords")) {
            JSONArray keywordsArray = jsonObject.getJSONArray("keywords");
            List<String> newAds = new ArrayList<>();
            for (int i = 0; i < keywordsArray.length(); i++) {
                newAds.add(keywordsArray.getString(i));
            }
            ads.clear();
            ads.addAll(newAds);
        }
        // 解析v4.0算法所需的新参数，如果JSON中没有则使用默认值
        minAdTsCount = jsonObject.optInt("minAdTsCount", this.minAdTsCount);
        maxAdTsCount = jsonObject.optInt("maxAdTsCount", this.maxAdTsCount);
        adDuration = jsonObject.optDouble("adDuration", this.adDuration);
        adTimeTolerance = jsonObject.optDouble("adTimeTolerance", this.adTimeTolerance);
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
    public int getMinAdTsCount() { return minAdTsCount; }
    public int getMaxAdTsCount() { return maxAdTsCount; }
    public double getAdDuration() { return adDuration; }
    public double getAdTimeTolerance() { return adTimeTolerance; }

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
