package com.fongmi.android.tv.player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.AdRule;
//import com.fongmi.android.tv.player.AdSwitch;
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
    // ✨✨✨ 核心改变！我们彻底拆除了那个有问题的ThreadLocal！✨✨✨
    // private static final ThreadLocal<Boolean> hasToast = new ThreadLocal<>();

    @NonNull @Override public Response intercept(@NonNull Chain chain) throws IOException {
        //if (!AdSwitch.get().isActivated()) {
           // return chain.proceed(chain.request());
        //}
        Request request = chain.request();
        String url = request.url().toString();
        if (AdRule.get().isAd(null, url)) {
            // ✨ 我们让“源头扼杀”也每次都弹窗！
            showToast("婉儿的凤凰系统为您拦截一条广告请求！");
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
        if (!m3u8Content.contains("#EXT-X-DISCONTINUITY")) return m3u8Content;
        List<String> finalLines = new ArrayList<>();
        String[] lines = m3u8Content.split("\n");
        List<List<String>> segments = new ArrayList<>();
        List<String> currentSegment = new ArrayList<>();
        
        int bodyStartIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().startsWith("#EXTINF:")) {
                bodyStartIndex = i;
                break;
            }
            finalLines.add(lines[i]);
        }
        if (bodyStartIndex == -1) return m3u8Content;

        for (int i = bodyStartIndex; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().startsWith("#EXT-X-DISCONTINUITY")) {
                if (!currentSegment.isEmpty()) {
                    segments.add(new ArrayList<>(currentSegment));
                    currentSegment.clear();
                }
            } else {
                currentSegment.add(line);
            }
        }
        if (!currentSegment.isEmpty()) segments.add(currentSegment);

        for (List<String> segment : segments) {
            if (isAdSegment(segment, AdRule.get().getMaxAdTsCount(), AdRule.get().getMaxAdDuration())) {
                // ✨✨✨ 核心改变！我们在这里，每次都弹窗报捷！✨✨✨
                showToast("婉儿的凤凰系统为您去掉一个 " + String.format("%.2f", getSegmentDuration(segment)) + " 秒的广告片段！");
            } else {
                if (!finalLines.isEmpty() && finalLines.size() > 1 && !finalLines.get(finalLines.size() - 1).trim().startsWith("#EXT-X-DISCONTINUITY") && !segment.get(0).trim().startsWith("#EXT-X-DISCONTINUITY")) {
                    finalLines.add("#EXT-X-DISCONTINUITY");
                }
                finalLines.addAll(segment);
            }
        }
        StringBuilder cleanedContent = new StringBuilder();
        for (String line : finalLines) { cleanedContent.append(line).append("\n"); }
        if (m3u8Content.contains("#EXT-X-ENDLIST") && !cleanedContent.toString().contains("#EXT-X-ENDLIST")) {
            cleanedContent.append("#EXT-X-ENDLIST\n");
        }
        return fixPaths(cleanedContent.toString(), baseUrl);
    }
    
    // ... 其他所有的方法，都保持我们之前的全功能版不变 ...
    private boolean isAdSegment(List<String> segment, int maxTsCount, double maxDuration) { int tsCount = 0; double totalDuration = 0.0; for (String line : segment) { if (line.trim().startsWith("#EXTINF:")) { try { String durationStr = line.substring(line.indexOf(":") + 1, line.lastIndexOf(",")).trim(); totalDuration += Double.parseDouble(durationStr); } catch (Exception e) {} } else if (line.trim().endsWith(".ts")) { tsCount++; } } if (tsCount > 0 && tsCount < maxTsCount && totalDuration < maxDuration) return true; return false; }
    private double getSegmentDuration(List<String> segment) { double totalDuration = 0.0; for (String line : segment) { if (line.trim().startsWith("#EXTINF:")) { try { String durationStr = line.substring(line.indexOf(":") + 1, line.lastIndexOf(",")).trim(); totalDuration += Double.parseDouble(durationStr); } catch (Exception e) {} } } return totalDuration; }
    private String readResponse(Response response) throws IOException { if (response.body() == null) return ""; InputStream inputStream = response.body().byteStream(); if ("gzip".equalsIgnoreCase(response.header("Content-Encoding"))) { inputStream = new GZIPInputStream(inputStream); } BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)); StringBuilder contentBuilder = new StringBuilder(); String line; while ((line = reader.readLine()) != null) { contentBuilder.append(line).append("\n"); } return contentBuilder.toString(); }
    private void showToast(final String message) { new Handler(Looper.getMainLooper()).post(() -> { try { Context context = App.get(); if (context != null) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show(); } } catch (Exception e) {} }); }
    private String fixPaths(String m3u8Content, String baseUrl) { StringBuilder finalContent = new StringBuilder(); String[] lines = m3u8Content.split("\n"); try { URI baseUri = new URI(baseUrl); for (String line : lines) { if (!line.startsWith("#") && !line.startsWith("http")) { finalContent.append(baseUri.resolve(line).toString()).append("\n"); } else { finalContent.append(line).append("\n"); } } } catch (URISyntaxException e) { return m3u8Content; } return finalContent.toString(); }
}
