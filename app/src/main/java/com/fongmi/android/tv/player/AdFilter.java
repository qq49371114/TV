package com.fongmi.android.tv.player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.fongmi.android.tv.App;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class AdFilter implements Interceptor {
    @NonNull @Override public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        String url = request.url().toString();
        if (AdRule.get().isAd(null, url)) {
            // ✨✨✨ 在这里，弹窗报捷！ ✨✨✨
            showToast("婉儿的凤凰智能ai系统为您拦截一条广告请求！");
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
        try {
            URI baseUri = new URI(baseUrl);
            for (String currentLine : lines) {
                String trimmedLine = currentLine.trim();
                if (trimmedLine.isEmpty()) continue;
                if (trimmedLine.startsWith("#EXTINF:")) {
                    lastExtinfLine = trimmedLine;
                } else if (!trimmedLine.startsWith("#")) {
                    if (AdRule.get().isAd(lastExtinfLine, trimmedLine)) {
                        // ✨✨✨ 在这里，弹窗报捷！ ✨✨✨
                        showToast("婉儿的智能ai凤凰系统为您去掉一条广告切片！");
                    } else {
                        if (lastExtinfLine != null) {
                            cleanedContent.append(lastExtinfLine).append("\n");
                        }
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
        return cleanedContent.toString();
    }

    private String readResponse(Response response) throws IOException { if (response.body() == null) return ""; InputStream inputStream = response.body().byteStream(); if ("gzip".equalsIgnoreCase(response.header("Content-Encoding"))) { inputStream = new GZIPInputStream(inputStream); } BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)); StringBuilder contentBuilder = new StringBuilder(); String line; while ((line = reader.readLine()) != null) { contentBuilder.append(line).append("\n"); } return contentBuilder.toString(); }

    // ✨ 一个用来在主线程弹窗的方法
    private void showToast(final String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Context context = App.get();
                if (context != null) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                // 忽略异常
            }
        });
    }
}
