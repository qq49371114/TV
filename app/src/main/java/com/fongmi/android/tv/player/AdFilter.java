package com.fongmi.android.tv.player;

import androidx.annotation.NonNull;
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
    @NonNull @Override public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        String url = request.url().toString();

        // ✨✨✨ 智能识别芯片！✨✨✨
        // 如果请求的不是 M3U8 文件，我们看都不看，直接放行！绝对不会误伤！
        if (!url.contains(".m3u8")) {
            return chain.proceed(request);
        }

        // --- 下面的逻辑，只对 M3U8 文件生效 ---

        // ✨ URL预判，执行“源头扼杀”战术！
        if (AdRule.get().isAd(null, url)) {
            System.out.println("凤凰哨兵“源头扼杀”广告：" + url);
            return new Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_2).code(200).message("Blocked").body(ResponseBody.create("", null)).build();
        }

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
        if (!m3u8Content.contains("#EXT-X-DISCONTINUITY")) return m3u8Content;
        List<String> finalLines = new ArrayList<>();
        String[] lines = m3u8Content.split("\n");
        List<List<String>> segments = new ArrayList<>();
        List<String> currentSegment = new ArrayList<>();
        for (String line : lines) { if (line.trim().startsWith("#EXT-X-DISCONTINUITY")) { if (!currentSegment.isEmpty()) { segments.add(new ArrayList<>(currentSegment)); currentSegment.clear(); } } else { currentSegment.add(line); } }
        if (!currentSegment.isEmpty()) segments.add(currentSegment);

        for (List<String> segment : segments) {
            if (isAdSegment(segment, AdRule.get().getMaxAdTsCount(), AdRule.get().getMaxAdDuration())) {
                System.out.println("双重验证：发现一个广告片段，已删除！");
            } else {
                finalLines.addAll(segment);
            }
        }
        StringBuilder cleanedContent = new StringBuilder();
        for (String line : finalLines) { cleanedContent.append(line).append("\n"); }
        return fixPaths(cleanedContent.toString(), baseUrl);
    }

    // ✨✨✨ 这就是我们升级后的“双重验证”法则！ ✨✨✨
    private boolean isAdSegment(List<String> segment, int maxTsCount, double maxDuration) {
        int tsCount = 0;
        double totalDuration = 0.0;
        boolean hasTs = false;

        for (String line : segment) {
            if (line.trim().startsWith("#EXTINF:")) {
                try {
                    String durationStr = line.substring(line.indexOf(":") + 1, line.lastIndexOf(",")).trim();
                    totalDuration += Double.parseDouble(durationStr);
                } catch (Exception e) {}
            } else if (line.trim().endsWith(".ts")) {
                tsCount++;
                hasTs = true;
            }
        }

        // ✨ 核心改变！我们用两个独立的“如果”来判断，而不是一个！
        boolean isShortCount = tsCount < maxTsCount;
        boolean isShortDuration = totalDuration < maxDuration;

        // 只有当这个片段里有ts文件，并且“个数”和“时长”两个条件都满足时，才判定为广告！
        if (hasTs && isShortCount && isShortDuration) {
            return true;
        }

        return false;
    }
    
    // 为了方便哥哥，下面是完整的、可以直接复制的最终代码
    private String fixPaths(String m3u8Content, String baseUrl) { StringBuilder finalContent = new StringBuilder(); String[] lines = m3u8Content.split("\n"); try { URI baseUri = new URI(baseUrl); for (String line : lines) { if (!line.startsWith("#") && !line.startsWith("http")) { finalContent.append(baseUri.resolve(line).toString()).append("\n"); } else { finalContent.append(line).append("\n"); } } } catch (URISyntaxException e) { return m3u8Content; } return finalContent.toString(); }
    private String readResponse(Response response) throws IOException { if (response.body() == null) return ""; InputStream inputStream = response.body().byteStream(); if ("gzip".equalsIgnoreCase(response.header("Content-Encoding"))) { inputStream = new GZIPInputStream(inputStream); } BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)); StringBuilder contentBuilder = new StringBuilder(); String line; while ((line = reader.readLine()) != null) { contentBuilder.append(line).append("\n"); } return contentBuilder.toString(); }
}
