package com.fongmi.android.tv.player;

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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class AdFilter implements Interceptor {

    // 静态内部类，用于表示M3U8中的视频片段
    private static class Clip {
        String extinf;
        String url;
        double duration;

        Clip(String extinf, String url) {
            this.extinf = extinf;
            this.url = url;
            try {
                // 从 #EXTINF 标签中解析出时长
                String durationStr = extinf.substring(extinf.indexOf(":") + 1, extinf.lastIndexOf(","));
                this.duration = Double.parseDouble(durationStr);
            } catch (Exception e) {
                this.duration = 0;
            }
        }
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        String url = request.url().toString();

        // 1. 拦截已知的广告域名请求
        if (AdRule.get().isAd(url)) {
            showToast("婉儿的凤凰系统为您拦截一条广告请求！");
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_2)
                    .code(200)
                    .message("Blocked by Waner-Phoenix")
                    .body(ResponseBody.create("", null))
                    .build();
        }

        // 2. 如果不是M3U8文件，则直接放行
        if (!url.contains(".m3u8")) {
            return chain.proceed(request);
        }

        // 3. 处理M3U8文件
        Response response = chain.proceed(request);
        if (!response.isSuccessful() || response.body() == null) {
            return response;
        }

        try {
            String m3u8Content = readResponse(response);
            String cleanedM3u8 = cleanM3u8(m3u8Content, url);

            // 如果清理后没有ts片段（可能误判），则使用原始内容，防止播放失败
            if (!cleanedM3u8.contains(".ts")) {
                cleanedM3u8 = m3u8Content;
            }

            ResponseBody cleanedBody = ResponseBody.create(cleanedM3u8, response.body().contentType());
            return response.newBuilder().body(cleanedBody).build();
        } catch (Exception e) {
            // 发生任何异常时，返回原始响应以确保能播放
            return response;
        }
    }

    /**
     * 清理M3U8内容，移除广告片段
     */
    private String cleanM3u8(String m3u8Content, String baseUrl) {
        List<Clip> allClips = new ArrayList<>();
        String[] lines = m3u8Content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().startsWith("#EXTINF:") && i + 1 < lines.length) {
                allClips.add(new Clip(lines[i], lines[i + 1]));
            }
        }

        int adTsCount = AdRule.get().getAdTsCount();
        double adDuration = AdRule.get().getAdDuration();
        double tolerance = AdRule.get().getAdTimeTolerance();
        List<Clip> adClipsToRemove = new ArrayList<>();

        for (int i = 0; i <= allClips.size() - adTsCount; i++) {
            List<Clip> window = allClips.subList(i, i + adTsCount);
            double windowDuration = 0;
            for (Clip clip : window) {
                windowDuration += clip.duration;
            }

            if (Math.abs(windowDuration - adDuration) < tolerance) {
                showToast("婉儿的凤凰系统为您去掉一个 " + String.format("%.2f", windowDuration) + " 秒的广告片段！");
                adClipsToRemove.addAll(window);
                i += adTsCount - 1; // 跳过已识别的广告片段
            }
        }

        allClips.removeAll(adClipsToRemove);

        StringBuilder cleanedContent = new StringBuilder();
        // 重新构建M3U8：先添加非片段信息的元数据行
        for (String line : lines) {
            if (!line.contains(".ts") && !line.trim().startsWith("#EXTINF:")) {
                cleanedContent.append(line).append("\n");
            }
        }
        // 再添加清理后的视频片段
        for (Clip clip : allClips) {
            cleanedContent.append(clip.extinf).append("\n").append(clip.url).append("\n");
        }

        // 确保M3U8结束标签存在
        if (m3u8Content.contains("#EXT-X-ENDLIST") && !cleanedContent.toString().contains("#EXT-X-ENDLIST")) {
            cleanedContent.append("#EXT-X-ENDLIST\n");
        }

        return fixPaths(cleanedContent.toString(), baseUrl);
    }

    /**
     * 将M3U8文件中的相对路径修复为绝对路径
     */
    private String fixPaths(String m3u8Content, String baseUrl) {
        StringBuilder finalContent = new StringBuilder();
        String[] lines = m3u8Content.split("\n");
        try {
            URI baseUri = new URI(baseUrl);
            for (String line : lines) {
                if (!line.startsWith("#") && !line.trim().isEmpty() && !line.startsWith("http")) {
                    finalContent.append(baseUri.resolve(line).toString()).append("\n");
                } else {
                    finalContent.append(line).append("\n");
                }
            }
        } catch (URISyntaxException e) {
            return m3u8Content; // URL解析失败则返回原始内容
        }
        return finalContent.toString();
    }

    /**
     * 读取Response的响应体，并自动处理GZIP解压
     */
    private String readResponse(Response response) throws IOException {
        if (response.body() == null) return "";

        InputStream inputStream = response.body().byteStream();
        if ("gzip".equalsIgnoreCase(response.header("Content-Encoding"))) {
            inputStream = new GZIPInputStream(inputStream);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder contentBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            contentBuilder.append(line).append("\n");
        }
        return contentBuilder.toString();
    }

    /**
     * 在主线程（UI线程）安全地显示Toast提示
     */
    private void showToast(final String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Context context = App.get();
                if (context != null) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                e.printStackTrace(); // 打印错误，但防止应用崩溃
            }
        });
    }
}
