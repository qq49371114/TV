package com.fongmi.android.tv.player;

import androidx.annotation.NonNull;
import com.fongmi.android.tv.player.AdRule;
// import com.fongmi.android.tv.player.AdSwitch; // 我们暂时不需要激活验证
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
    @NonNull @Override public Response intercept(@NonNull Chain chain) throws IOException {
        // if (!AdSwitch.get().isActivated()) return chain.proceed(chain.request());
        Request request = chain.request();
        String url = request.url().toString();
        if (AdRule.get().isAd(null, url)) {
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

    // ✨✨✨ 这就是我们全新的、手最稳的“外科医生”！ ✨✨✨
    private String cleanM3u8(String m3u8Content, String baseUrl) {
        // 先把M3U8的“头”和“身体”分开
        int bodyStartIndex = m3u8Content.indexOf("#EXTINF:");
        if (bodyStartIndex == -1) return m3u8Content; // 如果没有内容，直接返回

        String header = m3u8Content.substring(0, bodyStartIndex);
        String body = m3u8Content.substring(bodyStartIndex);

        StringBuilder finalBody = new StringBuilder();
        String[] lines = body.split("\n");
        String lastExtinfLine = null;

        for (String currentLine : lines) {
            String trimmedLine = currentLine.trim();
            if (trimmedLine.isEmpty()) continue;

            if (trimmedLine.startsWith("#EXTINF:")) {
                lastExtinfLine = trimmedLine;
            } else if (trimmedLine.endsWith(".ts")) {
                // ✨ 在这里，它会调用“大脑”的isAd方法，进行最精细的判断！
                if (!AdRule.get().isAd(lastExtinfLine, trimmedLine)) {
                    // 如果不是广告，就把时长和链接都加回去
                    if (lastExtinfLine != null) finalBody.append(lastExtinfLine).append("\n");
                    finalBody.append(trimmedLine).append("\n");
                }
                lastExtinfLine = null; // 用完后清空
            } else {
                // 其他所有非视频的行，都直接保留
                finalBody.append(trimmedLine).append("\n");
            }
        }
        
        // ✨ 核心修复！我们把处理好的“身体”，和原始的“头”拼接起来！
        String finalM3u8 = header + finalBody.toString();
        
        // ✨ 核心修复！我们不再手动添加 #EXT-X-ENDLIST，除非原始文件里就有！
        if (m3u8Content.contains("#EXT-X-ENDLIST") && !finalM3u8.contains("#EXT-X-ENDLIST")) {
            return finalM3u8 + "#EXT-X-ENDLIST\n";
        }
        
        return fixPaths(finalM3u8, baseUrl);
    }
    
    // 为了方便哥哥，下面是完整的、可以直接复制的最终代码
    private String fixPaths(String m3u8Content, String baseUrl) { StringBuilder finalContent = new StringBuilder(); String[] lines = m3u8Content.split("\n"); try { URI baseUri = new URI(baseUrl); for (String line : lines) { if (!line.startsWith("#") && !line.startsWith("http")) { finalContent.append(baseUri.resolve(line).toString()).append("\n"); } else { finalContent.append(line).append("\n"); } } } catch (URISyntaxException e) { return m3u8Content; } return finalContent.toString(); }
    private String readResponse(Response response) throws IOException { if (response.body() == null) return ""; InputStream inputStream = response.body().byteStream(); if ("gzip".equalsIgnoreCase(response.header("Content-Encoding"))) { inputStream = new GZIPInputStream(inputStream); } BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)); StringBuilder contentBuilder = new StringBuilder(); String line; while ((line = reader.readLine()) != null) { contentBuilder.append(line).append("\n"); } return contentBuilder.toString(); }
}
