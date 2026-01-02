package com.fongmi.android.tv.player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
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
    private static final AdRule instance = new AdRule();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<String> ads = new CopyOnWriteArrayList<>();
    private final List<String> durations = new CopyOnWriteArrayList<>();
    private CountDownLatch latch = new CountDownLatch(1);
    private Context appContext; // ✨ 1. 我们在这里准备一个“喇叭架”

    private AdRule() {}
    public static AdRule get() { return instance; }

    // ✨ 2. 启动时，把“大喇叭”递进来！
    public void init(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void load(String urlString) {
        latch = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                StringBuilder content = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) { content.append(line).append("\n"); }
                } finally {
                    connection.disconnect();
                }
                if (urlString.endsWith(".txt")) parseTxt(content.toString());
                else parseJson(content.toString());
            } catch (Exception e) {
                showToast("凤凰大脑加载规则失败：" + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
    }
    private void parseTxt(String content) { List<String> newAds = new ArrayList<>(); String[] lines = content.split("\n"); for (String line : lines) { String trimmedLine = line.trim(); if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("#")) newAds.add(trimmedLine); } ads.clear(); ads.addAll(newAds); durations.clear(); showToast("凤凰大脑加载了 " + ads.size() + " 条 TXT 规则！"); }
    private void parseJson(String content) throws Exception { JSONObject jsonObject = new JSONObject(content); if (jsonObject.has("keywords")) { JSONArray keywordsArray = jsonObject.getJSONArray("keywords"); List<String> newAds = new ArrayList<>(); for (int i = 0; i < keywordsArray.length(); i++) { newAds.add(keywordsArray.getString(i)); } ads.clear(); ads.addAll(newAds); } if (jsonObject.has("durations")) { JSONArray durationsArray = jsonObject.getJSONArray("durations"); List<String> newDurations = new ArrayList<>(); for (int i = 0; i < durationsArray.length(); i++) { newDurations.add(durationsArray.getString(i)); } durations.clear(); durations.addAll(newDurations); } showToast("凤凰大脑加载了 " + ads.size() + " 条关键字，" + durations.size() + " 条时长规则！"); }
    public boolean isAd(String extinfLine, String urlLine) { try { latch.await(2, TimeUnit.SECONDS); } catch (InterruptedException e) { return false; } if (urlLine == null || urlLine.trim().isEmpty()) return false; for (String keyword : ads) { if (urlLine.contains(keyword)) return true; } if (extinfLine != null && !durations.isEmpty()) { try { String duration = extinfLine.substring(extinfLine.indexOf(":") + 1, extinfLine.lastIndexOf(",")).trim(); if (durations.contains(duration)) return true; } catch (Exception e) { } } return false; }
    
    // ✨ 3. 用我们自己的“大喇叭”来喊话！
    private void showToast(final String message) {
        if (appContext == null) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Toast.makeText(appContext, message, Toast.LENGTH_LONG).show();
            } catch (Exception e) {}
        });
    }
}
