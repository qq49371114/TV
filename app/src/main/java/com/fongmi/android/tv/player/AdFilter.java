package com.fongmi.android.tv.player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.AdRule;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class AdFilter implements Interceptor {
    // ✨✨✨ 核心改变！我们用一个线程安全的标志位，来确保每次播放只弹一次窗！✨✨✨
    private static final ThreadLocal<Boolean> hasToast = new ThreadLocal<>();

    @NonNull @Override public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        String url = request.url().toString();
        if (AdRule.get().isAd(null, url)) {
            // ✨ 如果是第一次拦截，就弹窗报捷！
            if (hasToast.get() == null) {
                showToast("婉儿的凤凰系统正在为您拦截广告...");
                hasToast.set(true);
            }
            return new Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_2).code(200).message("Blocked").body(ResponseBody.create("", null)).build();
        }
        if (!url.contains(".m3u8")) return chain.proceed(request);
        Response response = chain.proceed(request);
        if (!response.isSuccessful() || response.body() == null) return response;
        try {
            String m3u8Content = readResponse(response);
            String cleanedM3u8 = cleanM3u8(m3u8Content, url);
            if (!cleanedM3u8.contains(".ts")) cleanedM3u8 = m3u8Content;
            ResponseBody cleanedBody = ResponseBody.create(cleanedM3u8, response.body().contentType());
            return response.newBuilder().body(cleanedBody).build();
        } catch (Exception e) {
            return response;
        }
    }

    private String cleanM3u8(String m3u8Content, String baseUrl) {
        StringBuilder cleanedContent = new StringBuilder();
        String[] lines = m3u8Content.split("\n");
        String lastExtinfLine = null;
        boolean adSkipped = false;
        try {
            URI baseUri = new URI(baseUrl);
            for (String currentLine : lines) {
                String trimmedLine = currentLine.trim();
                if (trimmedLine.isEmpty()) continue;
                if (trimmedLine.startsWith("#EXTINF:")) {
                    lastExtinfLine = trimmedLine;
                } else if (!trimmedLine.startsWith("#")) {
                    if (AdRule.get().isAd(lastExtinfLine, trimmedLine)) {
                        adSkipped = true; // 标记我们已经成功去掉了至少一个切片
                    } else {
                        if (lastExtinfLine != null) cleanedContent.append(lastExtinfLine).append("\n");
                        String finalUrl = trimmedLine.startsWith("http") ? trimmedLine : baseUri.resolve(trimmedLine).toString();
                        cleanedContent.append(finalUrl).append("\n");
                    }
                    lastExtinfLine = null;
                } else {
                    cleanedContent.append(trimmedLine).append("\n");
                }
            }
        } catch (URISyntaxException e) {
            return m3u8Content;
        }
        // ✨ 如果我们成功去掉了任何一个切片，并且是第一次，就弹窗报捷！
        if (adSkipped && hasToast.get() == null) {
            showToast("婉儿的凤凰系统为您优化了播放列表！");
            hasToast.set(true);
        }
        return cleanedContent.toString();
    }
    
    // ... 其他方法保持不变 ...
    private String readResponse(Response response) throws IOException { if (response.body() == null) return ""; InputStream inputStream = response.body().byteStream(); if ("gzip".equalsIgnoreCase(response.header("Content-Encoding"))) { inputStream = new GZIPInputStream(inputStream); } BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)); StringBuilder contentBuilder = new StringBuilder(); String line; while ((line = reader.readLine()) != null) { contentBuilder.append(line).append("\n"); } return contentBuilder.toString(); }
    private void showToast(final String message) { new Handler(Looper.getMainLooper()).post(() -> { try { Context context = App.get(); if (context != null) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show(); } } catch (Exception e) {} }); }
}
