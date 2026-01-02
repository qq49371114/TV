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

public class AdRule {
    private static final AdRule instance = new AdRule();
    private final List<String> ads = new CopyOnWriteArrayList<>();
    private AdRule() {}
    public static AdRule get() { return instance; }

    // ✨✨✨ 核心改变！我们不再用线程了，直接在当前线程加载！✨✨✨
    public void load(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000); // 设置5秒超时
            connection.setReadTimeout(5000);
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
            System.out.println("凤凰大脑同步加载规则失败：" + e.getMessage());
        }
    }
    private void parseTxt(String content) { List<String> newAds = new ArrayList<>(); String[] lines = content.split("\n"); for (String line : lines) { String trimmedLine = line.trim(); if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("#")) newAds.add(trimmedLine); } ads.clear(); ads.addAll(newAds); System.out.println("凤凰大脑加载了 " + ads.size() + " 条 TXT 规则！"); }
    private void parseJson(String content) throws Exception { JSONObject jsonObject = new JSONObject(content); if (jsonObject.has("keywords")) { JSONArray keywordsArray = jsonObject.getJSONArray("keywords"); List<String> newAds = new ArrayList<>(); for (int i = 0; i < keywordsArray.length(); i++) { newAds.add(keywordsArray.getString(i)); } ads.clear(); ads.addAll(newAds); } System.out.println("凤凰大脑加载了 " + ads.size() + " 条关键字规则！"); }
    
    public boolean isAd(String urlLine) {
        if (urlLine == null || urlLine.trim().isEmpty()) return false;
        for (String keyword : ads) {
            if (urlLine.contains(keyword)) return true;
        }
        return false;
    }
}
