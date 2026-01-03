package com.fongmi.android.tv.player;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.fongmi.android.tv.App; // ✨ 引入App，让我们可以弹窗和复制
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
    private static boolean hasCopied = false; // ✨ 加一个标志位，确保只复制一次

    @NonNull @Override public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        String url = request.url().toString();

        // ✨ 我们暂时把“源头扼杀”关掉，确保能拿到完整的M3U8文件
        // if (AdRule.get().isAd(null, url)) { ... }

        if (!url.contains(".m3u8")) {
            return chain.proceed(request);
        }

        Response response = chain.proceed(request);
        if (!response.isSuccessful() || response.body() == null) return response;

        try {
            String m3u8Content = readResponse(response);

            // ✨✨✨ 终极情报获取行动！只要是M3U8，就自动复制到剪贴板！ ✨✨✨
            if (!hasCopied) {
                copyToClipboardAndToast(m3u8Content);
                hasCopied = true; // 确保只复制一次，避免干扰
            }

            // 我们暂时不过滤，直接返回原始M3U8，保证能播放
            ResponseBody originalBody = ResponseBody.create(m3u8Content, response.body().contentType());
            return response.newBuilder().body(originalBody).build();
        } catch (Exception e) {
            return response;
        }
    }

    // ✨ 新增一个方法，专门用来在主线程复制和弹窗
    private void copyToClipboardAndToast(final String text) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Context context = App.get();
                if (context == null) return; // 安全检查
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("M3U8 Content", text);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, "婉儿已将M3U8内容复制到剪贴板！", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                // 如果发生异常，我们就不弹窗了，避免干扰
            }
        });
    }

    private String readResponse(Response response) throws IOException { if (response.body() == null) return ""; InputStream inputStream = response.body().byteStream(); if ("gzip".equalsIgnoreCase(response.header("Content-Encoding"))) { inputStream = new GZIPInputStream(inputStream); } BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)); StringBuilder contentBuilder = new StringBuilder(); String line; while ((line = reader.readLine()) != null) { contentBuilder.append(line).append("\n"); } return contentBuilder.toString(); }
}
