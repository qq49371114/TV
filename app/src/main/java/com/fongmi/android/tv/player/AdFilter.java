package com.fongmi.android.tv.player;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.fongmi.android.tv.App;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class AdFilter implements Interceptor {
    // ✨✨✨ 核心改变！我们不再需要那个 static 的 hasCopied 标志位了！✨✨✨
    // private static boolean hasCopied = false;

    @NonNull @Override public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        String url = request.url().toString();

        if (!url.contains(".m3u8")) {
            return chain.proceed(request);
        }

        Response response = chain.proceed(request);
        if (!response.isSuccessful() || response.body() == null) return response;

        try {
            String m3u8Content = readResponse(response);

            // ✨✨✨ 终极情报获取行动！现在每次遇到M3U8都会尝试复制！ ✨✨✨
            if (m3u8Content.contains(".ts")) {
                copyToClipboardAndToast(m3u8Content);
            }

            // 我们暂时不过滤，直接返回原始M3U8，保证能播放
            ResponseBody originalBody = ResponseBody.create(m3u8Content, response.body().contentType());
            return response.newBuilder().body(originalBody).build();
        } catch (Exception e) {
            return response;
        }
    }

    private void copyToClipboardAndToast(final String text) { new Handler(Looper.getMainLooper()).post(() -> { try { Context context = App.get(); if (context == null) return; ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE); ClipData clip = ClipData.newPlainText("M3U8 Content", text); clipboard.setPrimaryClip(clip); Toast.makeText(context, "婉儿已将真正的M3U8内容复制到剪贴板！", Toast.LENGTH_LONG).show(); } catch (Exception e) {} }); }
    private String readResponse(Response response) throws IOException { if (response.body() == null) return ""; InputStream inputStream = response.body().byteStream(); if ("gzip".equalsIgnoreCase(response.header("Content-Encoding"))) { inputStream = new GZIPInputStream(inputStream); } BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)); StringBuilder contentBuilder = new StringBuilder(); String line; while ((line = reader.readLine()) != null) { contentBuilder.append(line).append("\n"); } return contentBuilder.toString(); }
}
