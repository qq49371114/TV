package com.fongmi.android.tv.player;

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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;

public class AdRule {
    private static final AdRule instance = new AdRule();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<String> ads = new CopyOnWriteArrayList<>();
    private final List<String> durations = new CopyOnWriteArrayList<>();
    private CountDownLatch latch = new CountDownLatch(1);
    private final OkHttpClient internalClient = new OkHttpClient();

    // ✨✨✨ 在这里，我们为“智能参数”准备好了变量！ ✨✨✨
    private int maxAdTsCount = 10; // 默认值：ts文件少于10个
    private double maxAdDuration = 60.0; // 默认值：总时长小于60秒

    private AdRule() {}
    public static AdRule get() { return instance; }

    public void load(String urlString) {
        latch = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                Request request = new Request.Builder().url(urlString).build();
                Response response = internalClient.newCall(request).execute();
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException("Failed to fetch rules: " + response.code());
                }
                String content = response.body().string();
                if (urlString.endsWith(".txt")) parseTxt(content);
                else parseJson(content);
            } catch (Exception e) {
                System.out.println("凤凰大脑加载规则失败：" + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
    }
    
    private void parseTxt(String content) { List<String> newAds = new ArrayList<>(); String[] lines = content.split("\n"); for (String line : lines) { String trimmedLine = line.trim(); if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("#")) newAds.add(trimmedLine); } ads.clear(); ads.addAll(newAds); durations.clear(); System.out.println("凤凰大脑加载了 " + ads.size() + " 条 TXT 规则！"); }
    
    // ✨✨✨ 核心改变！让它能从JSON里，读取我们新的“智能参数”！ ✨✨✨
    private void parseJson(String content) throws Exception {
        JSONObject jsonObject = new JSONObject(content);
        if (jsonObject.has("keywords")) {
            JSONArray keywordsArray = jsonObject.getJSONArray("keywords");
            List<String> newAds = new ArrayList<>();
            for (int i = 0; i < keywordsArray.length(); i++) { newAds.add(keywordsArray.getString(i)); }
            ads.clear();
            ads.addAll(newAds);
        }
        if (jsonObject.has("durations")) {
            JSONArray durationsArray = jsonObject.getJSONArray("durations");
            List<String> newDurations = new ArrayList<>();
            for (int i = 0; i < durationsArray.length(); i++) { newDurations.add(durationsArray.getString(i)); }
            durations.clear();
            durations.addAll(newDurations);
        }
        if (jsonObject.has("maxAdTsCount")) maxAdTsCount = jsonObject.getInt("maxAdTsCount");
        if (jsonObject.has("maxAdDuration")) maxAdDuration = jsonObject.getDouble("maxAdDuration");
        System.out.println("凤凰大脑加载了 " + ads.size() + " 条关键字，" + durations.size() + " 条时长规则，智能参数已更新！");
    }
    
    public boolean isAd(String extinfLine, String urlLine) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
        if (urlLine != null && !urlLine.trim().isEmpty()) {
            for (String keyword : ads) {
                if (urlLine.contains(keyword)) return true;
            }
        }
        if (extinfLine != null && !durations.isEmpty()) {
            try {
                String duration = extinfLine.substring(extinfLine.indexOf(":") + 1, extinfLine.lastIndexOf(",")).trim();
                if (durations.contains(duration)) return true;
            } catch (Exception e) { }
        }
        return false;
    }
    
    // ✨✨✨ 核心改变！把我们的“智能参数”提供给“哨兵”使用！ ✨✨✨
    public int getMaxAdTsCount() { return maxAdTsCount; }
    public double getMaxAdDuration() { return maxAdDuration; }
}
