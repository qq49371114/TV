package com.fongmi.android.tv.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.fongmi.android.tv.App;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AdRule {
    private static final String PREFS_NAME = "ad_rule_prefs";
    private static final String KEY_RULES_JSON_CACHE = "rules_json_cache";
    private static final String KEY_ETAG = "etag";
    private static final String KEY_LAST_MODIFIED = "last_modified";
    private static final String KEY_CONFIG_URL = "config_url";

    private static final AdRule instance = new AdRule();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<String> ads = new CopyOnWriteArrayList<>();
    private final List<String> durations = new CopyOnWriteArrayList<>();
    private CountDownLatch latch = new CountDownLatch(1);
    private SharedPreferences prefs;

    private int maxAdTsCount = 10;
    private double maxAdDuration = 60.0;

    private AdRule() {}
    public static AdRule get() { return instance; }

    public void init(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadRulesFromPrefs();
        fetchConfig();
    }

    public void fetchConfig() {
        String url = prefs.getString(KEY_CONFIG_URL, "");
        if (url.isEmpty()) return;
        load(url);
    }

    public void load(String urlString) {
        latch = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                String etag = prefs.getString(KEY_ETAG, "");
                String lastModified = prefs.getString(KEY_LAST_MODIFIED, "");
                if (!etag.isEmpty()) connection.setRequestProperty("If-None-Match", etag);
                if (!lastModified.isEmpty()) connection.setRequestProperty("If-Modified-Since", lastModified);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    latch.countDown();
                    return;
                }

                StringBuilder content = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) { content.append(line).append("\n"); }
                } finally {
                    connection.disconnect();
                }

                String newEtag = connection.getHeaderField("ETag");
                String newLastModified = connection.getHeaderField("Last-Modified");
                prefs.edit().putString(KEY_RULES_JSON_CACHE, content.toString()).putString(KEY_ETAG, newEtag != null ? newEtag : "").putString(KEY_LAST_MODIFIED, newLastModified != null ? newLastModified : "").apply();
                
                showToast("云端去广告规则更新成功！");

                if (urlString.endsWith(".txt")) parseTxt(content.toString());
                else parseJson(content.toString());
            } catch (Exception e) {
                showToast("云端去广告规则更新失败！");
            } finally {
                latch.countDown();
            }
        });
    }

    private void loadRulesFromPrefs() { String json = prefs.getString(KEY_RULES_JSON_CACHE, null); if (json != null) { try { String url = prefs.getString(KEY_CONFIG_URL, ""); if (url.endsWith(".txt")) parseTxt(json); else parseJson(json); } catch (Exception e) {} } }
    public static void setConfigUrl(String url) { SharedPreferences p = App.get().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE); p.edit().putString(KEY_CONFIG_URL, url).apply(); get().fetchConfig(); }
    private void parseTxt(String content) { List<String> newAds = new ArrayList<>(); String[] lines = content.split("\n"); for (String line : lines) { String trimmedLine = line.trim(); if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("#")) newAds.add(trimmedLine); } ads.clear(); ads.addAll(newAds); durations.clear(); }
    private void parseJson(String content) throws Exception { JSONObject jsonObject = new JSONObject(content); if (jsonObject.has("keywords")) { JSONArray keywordsArray = jsonObject.getJSONArray("keywords"); List<String> newAds = new ArrayList<>(); for (int i = 0; i < keywordsArray.length(); i++) { newAds.add(keywordsArray.getString(i)); } ads.clear(); ads.addAll(newAds); } if (jsonObject.has("durations")) { JSONArray durationsArray = jsonObject.getJSONArray("durations"); List<String> newDurations = new ArrayList<>(); for (int i = 0; i < durationsArray.length(); i++) { newDurations.add(durationsArray.getString(i)); } durations.clear(); durations.addAll(newDurations); } if (jsonObject.has("maxAdTsCount")) maxAdTsCount = jsonObject.getInt("maxAdTsCount"); if (jsonObject.has("maxAdDuration")) maxAdDuration = jsonObject.getDouble("maxAdDuration"); }
    public boolean isAd(String extinfLine, String urlLine) { try { latch.await(2, TimeUnit.SECONDS); } catch (InterruptedException e) { return false; } if (urlLine != null && !urlLine.trim().isEmpty()) { for (String keyword : ads) { if (urlLine.contains(keyword)) return true; } } if (extinfLine != null && !durations.isEmpty()) { try { String duration = extinfLine.substring(extinfLine.indexOf(":") + 1, extinfLine.lastIndexOf(",")).trim(); if (durations.contains(duration)) return true; } catch (Exception e) { } } return false; }
    public int getMaxAdTsCount() { return maxAdTsCount; }
    public double getMaxAdDuration() { return maxAdDuration; }
    private void showToast(final String message) { Context context = App.get(); if (context == null) return; new Handler(Looper.getMainLooper()).post(() -> { try { Toast.makeText(context, message, Toast.LENGTH_LONG).show(); } catch (Exception e) {} }); }
}
