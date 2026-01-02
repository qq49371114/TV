package com.fongmi.android.tv.player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.fongmi.android.tv.App;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class SignalRule {
    private static final SignalRule instance = new SignalRule();
    private SignalRule() {}
    public static SignalRule get() { return instance; }

    public void load(String urlString) {
        new Thread(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                StringBuilder content = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) { content.append(line); }
                } finally {
                    connection.disconnect();
                }
                // ✨✨✨ 只要能走到这里，就说明规则读取成功了！✨✨✨
                showToast("哥哥！“信号弹大脑”已成功读取云端规则！内容长度：" + content.length());
            } catch (Exception e) {
                showToast("哥哥！“信号弹大脑”读取规则失败：" + e.getMessage());
            }
        }).start();
    }
    private void showToast(final String message) { new Handler(Looper.getMainLooper()).post(() -> { try { Context context = App.get(); if (context != null) { Toast.makeText(context, message, Toast.LENGTH_LONG).show(); } } catch (Exception e) {} }); }
}
