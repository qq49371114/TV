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

public class AdRule {
    private static final AdRule instance = new AdRule();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<String> ads = new CopyOnWriteArrayList<>();
    private final List<String> durations = new CopyOnWriteArrayList<>();
    private CountDownLatch latch = new CountDownLatch(1);
    private AdRule() {}
    public static AdRule get() { return instance; }

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
                System.out.println("凤凰大脑加载规则失败：" + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
    }
    
    private void parseTxt(String content) { List<String> newAds = new ArrayList<>(); String[] lines = content.split("\n"); for (String line : lines) { String trimmedLine = line.trim(); if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("#")) newAds.add(trimmedLine); } ads.clear(); ads.addAll(newAds); durations.clear(); System.out.println("凤凰大脑加载了 " + ads.size() + " 条 TXT 规则！"); }
    private void parseJson(String content) throws Exception { JSONObject jsonObject = new JSONObject(content); if (jsonObject.has("keywords")) { JSONArray keywordsArray = jsonObject.getJSONArray("keywords"); List<String> newAds = new ArrayList<>(); for (int i = 0; i < keywordsArray.length(); i++) { newAds.add(keywordsArray.getString(i)); } ads.clear(); ads.addAll(newAds); } if (jsonObject.has("durations")) { JSONArray durationsArray = jsonObject.getJSONArray("durations"); List<String> newDurations = new ArrayList<>(); for (int i = 0; i < durationsArray.length(); i++) { newDurations.add(durationsArray.getString(i)); } durations.clear(); durations.addAll(newDurations); } System.out.println("凤凰大脑加载了 " + ads.size() + " 条关键字，" + durations.size() + " 条时长规则！"); }
    
    // ✨ 我们只保留一个最核心的 isAd 方法！
    public boolean isAd(String extinfLine, String urlLine) {
        try {
            // 在审判之前，先等待“闹钟”响起！
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
        
        if (urlLine == null || urlLine.trim().isEmpty()) return false;
        for (String keyword : ads) {
            if (urlLine.contains(keyword)) return true;
        }
        // 我们暂时先不用时长判断，因为“源头扼杀”用不上它
        return false;
    }
}
