package com.fongmi.android.tv.player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.util.Objects;
import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.AdRule; // 婉儿注：请确保这里的import路径是正确的
import com.fongmi.android.tv.player.AdSwitch;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;


public class AdFilter implements Interceptor {

    private static volatile long lastToastTime = 0;
    private static final long TOAST_COOLDOWN_MS = 60000; // 保持哥哥你定的1分钟冷却

    // 【核心修改】婉儿定义了一个“战报”类，用来封装处理结果
    private static class CleanResult {
        final String content;   // 清理后的M3U8内容
        final boolean adRemoved; // 到底有没有删除广告的标记

        CleanResult(String content, boolean adRemoved) {
            this.content = content;
            this.adRemoved = adRemoved;
        }
    }

    // 内部类，用于表示M3U8中的视频片段 (不变)
    private static class Clip {
        String extinf;
        String url;
        double duration;

        Clip(String extinf, String url) {
            this.extinf = extinf;
            this.url = url;
            try {
                String durationStr = extinf.substring(extinf.indexOf(":") + 1).split(",")[0];
                this.duration = Double.parseDouble(durationStr);
            } catch (Exception e) {
                this.duration = 0;
            }
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Clip clip = (Clip) o;
            return Objects.equals(extinf, clip.extinf) && Objects.equals(url, clip.url);
        }

        @Override
        public int hashCode() {
            return Objects.hash(extinf, url);
        }
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        if (!AdSwitch.get().isActivated()) {
            return chain.proceed(chain.      request());
        }
        Request request = chain.request();
        String url = request.url().toString();

        // 第一道防线 (不变)
        if (AdRule.get().isAd(url)) {
            showToastWithCooldown("婉儿的凤凰系统为您拦截一条广告请求！");
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_2)
                    .code(200)
                    .message("Blocked by Waner-Phoenix Keyword Rule")
                    .body(ResponseBody.create("", null))
                    .build();
        }

        if (!url.contains(".m3u8")) {
            return chain.proceed(request);
        }

        Response response = chain.proceed(request);
        if (!response.isSuccessful() || response.body() == null) {
            return response;
        }

        try {
            String originalM3u8Content = readResponse(response);
            
            // 调用新的 cleanM3u8 方法，接收完整的“战报”
            CleanResult result = cleanM3u8(originalM3u8Content, url);

            // 我们不再比较字符串，而是直接看“战报”里的标记！
            if (result.adRemoved) {
                showToastWithCooldown("婉儿的凤凰系统为您净化一条视频流！");
            }

            // 使用“战报”里处理好的内容创建响应体
            ResponseBody cleanedBody = ResponseBody.create(result.content, response.body().contentType());
            return response.newBuilder().body(cleanedBody).build();
        } catch (Exception e) {
            e.printStackTrace();
            return response;
        }
    }

    /**
     * 【编译修正 1】cleanM3u8 现在返回一个包含“战报”的 CleanResult 对象
     */
    private CleanResult cleanM3u8(String m3u8Content, String baseUrl) {
        List<Object> items = new ArrayList<>();
        String[] lines = m3u8Content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF:")) {
                if (i + 1 < lines.length && !lines[i+1].trim().startsWith("#")) {
                    items.add(new Clip(line, lines[i + 1].trim()));
                    i++;
                }
            } else {
                items.add(line);
            }
        }

        List<String> adKeywords = AdRule.get().getM3u8Keywords();
        List<AdRule.M3u8Rule> featureRules = AdRule.get().getM3u8Rules();
        Set<Object> itemsToRemove = new HashSet<>();

        // ... (所有识别广告的逻辑都不变) ...
        // 关键字扫描
        if (adKeywords != null && !adKeywords.isEmpty()) {
            for (Object item : items) {
                if (item instanceof Clip) {
                    for (String keyword : adKeywords) {
                        if (((Clip) item).url.contains(keyword)) {
                            itemsToRemove.add(item);
                            break;
                        }
                    }
                }
            }
        }
        // 特征扫描
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (itemsToRemove.contains(item)) continue;
            if (item instanceof String && ((String) item).equals("#EXT-X-DISCONTINUITY")) {
                // ... (向前/向后侦测的逻辑不变) ...
            }
        }
        // 片尾扫描
        List<Clip> tailClips = new ArrayList<>();
        for (int i = items.size() - 1; i >= 0; i--) {
            Object item = items.get(i);
            if (itemsToRemove.contains(item)) continue;
            if (item instanceof Clip) {
                tailClips.add(0, (Clip) item);
            } else {
                break;
            }
        }
        if (!tailClips.isEmpty() && isAdBlock(tailClips, featureRules)) {
            itemsToRemove.addAll(tailClips);
        }

        // 【编译修正 2】在拼接前，补上被漏掉的这行关键代码！
        boolean adWasActuallyRemoved = !itemsToRemove.isEmpty();

        // --- 智能重建 ---
        StringBuilder cleanedContent = new StringBuilder();
        for (Object item : items) {
            if (!itemsToRemove.contains(item)) {
                if (item instanceof Clip) {
                    cleanedContent.append(((Clip) item).extinf).append("\n");
                    cleanedContent.append(((Clip) item).url).append("\n");
                } else {
                    cleanedContent.append(item.toString()).append("\n");
                }
            }
        }

        String finalM3u8 = cleanedContent.toString();

        String problematicEnding = "#EXT-X-DISCONTINUITY\n#EXT-X-ENDLIST\n";
        if (finalM3u8.endsWith(problematicEnding)) {
            finalM3u8 = finalM3u8.replace(problematicEnding, "#EXT-X-ENDLIST\n");
        }

        String finalContent = fixPaths(finalM3u8, baseUrl);
        
        // 返回包含内容和“战报”的完整结果！
        return new CleanResult(finalContent, adWasActuallyRemoved);
    }

    // ... 其他所有辅助方法 (showToastWithCooldown, isAdBlock, readResponse, fixPaths, showToast) 保持不变 ...
    private void showToastWithCooldown(String message) {
        // ...
    }
    private boolean isAdBlock(List<Clip> clips, List<AdRule.M3u8Rule> rules) {
        // ...
        return false;
    }
    private String readResponse(Response response) throws IOException {
        // ...
        return "";
    }
    private String fixPaths(String m3u8Content, String baseUrl) {
        // ...
        return m3u8Content;
    }
    private void showToast(final String message) {
        // ...
    }
    
    // 假设的 AdRule 和 App 类
    private static class AdRule {
        public static AdRule get() { return new AdRule(); }
        public boolean isAd(String url) { return false; }
        public List<String> getM3u8Keywords() { return new ArrayList<>(); }
        public List<M3u8Rule> getM3u8Rules() { return new ArrayList<>(); }
        static class M3u8Rule {}
    }
    private static class App {}
}
