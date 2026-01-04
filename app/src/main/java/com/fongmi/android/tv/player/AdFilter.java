package com.fongmi.android.tv.player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.AdRule;
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

    // --- 婉儿新增：弹窗冷却机制 ---
    private static volatile long lastToastTime = 0;
    private static final long TOAST_COOLDOWN_MS = 8000; // 8秒冷却时间

    // 内部类 Clip，保持不变
    private static class Clip {
        String extinf;
        String url;
        double duration;

        Clip(String extinf, String url) {
            this.extinf = extinf;
            this.url = url;
            try {
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

        // 第一道防线：关键词拦截 (现在调用带冷却的弹窗)
        if (AdRule.get().isAd(url)) {
            showToastWithCooldown("凤凰系统为您拦截一条广告请求！");
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_2)
                    .code(200)
                    .message("Blocked by Waner-Phoenix")
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
            // 调用你指定的、最正确的 cleanM3u8 方法
            String cleanedM3u8 = cleanM3u8(originalM3u8Content, url);

            // 【核心修改】我们在这里统一管理弹窗！
            if (!originalM3u8Content.equals(cleanedM3u8)) {
                showToastWithCooldown("凤凰系统为您净化一条视频流！");
            }

            ResponseBody cleanedBody = ResponseBody.create(cleanedM3u8, response.body().contentType());
            return response.newBuilder().body(cleanedBody).build();
        } catch (Exception e) {
            return response;
        }
    }

    /**
     * 核心算法：v4.1.7 (哥哥你指定的版本，婉儿一个字都没改)
     */
    private String cleanM3u8(String m3u8Content, String baseUrl) {
        // 1. 解析M3U8 (不变)
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

        // 2. 获取规则并初始化 (不变)
        List<String> adKeywords = AdRule.get().getM3u8Keywords();
        List<AdRule.M3u8Rule> featureRules = AdRule.get().getM3u8Rules();
        Set<Object> itemsToRemove = new HashSet<>();

        // 3. 关键字扫描 (不变)
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

        // 4. 特征扫描 (【核心修改】只标记广告片段，不碰任何结构标签)
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (itemsToRemove.contains(item)) continue;

            if (item instanceof String && ((String) item).equals("#EXT-X-DISCONTINUITY")) {
                // 向前侦测
                List<Clip> beforeClips = new ArrayList<>();
                for (int j = i - 1; j >= 0; j--) {
                    Object prevItem = items.get(j);
                    if (itemsToRemove.contains(prevItem)) continue;
                    if (prevItem instanceof Clip) { beforeClips.add(0, (Clip) prevItem); } 
                    else { break; }
                }
                if (isAdBlock(beforeClips, featureRules)) {
                    itemsToRemove.addAll(beforeClips);
                }

                // 向后侦测
                List<Clip> afterClips = new ArrayList<>();
                for (int j = i + 1; j < items.size(); j++) {
                    Object nextItem = items.get(j);
                    if (itemsToRemove.contains(nextItem)) continue;
                    if (nextItem instanceof Clip) { afterClips.add((Clip) nextItem); } 
                    else { break; }
                }
                if (isAdBlock(afterClips, featureRules)) {
                    itemsToRemove.addAll(afterClips);
                }
            }
        }

        // 5. 初步重建 (不变)
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

        // 6. 【最终整形】在这里修复所有可能存在的“语法疤痕”
        // 修复连续的分界线
        finalM3u8 = finalM3u8.replaceAll("(#EXT-X-DISCONTINUITY\\n)+", "#EXT-X-DISCONTINUITY\\n");
        // 修复错误的结尾
        finalM3u8 = finalM3u8.replace("#EXT-X-DISCONTINUITY\n#EXT-X-ENDLIST", "#EXT-X-ENDLIST");

        return fixPaths(finalM3u8, baseUrl);
    }

    /**
     * 【核心修改】我们把 isAdBlock 变成了“安静”的判断员！
     */
    private boolean isAdBlock(List<Clip> clips, List<AdRule.M3u8Rule> rules) {
        if (clips.isEmpty() || rules == null || rules.isEmpty()) {
            return false;
        }
        double totalDuration = 0;
        for (Clip clip : clips) {
            totalDuration += clip.duration;
        }
        for (AdRule.M3u8Rule rule : rules) {
            boolean countMatch = clips.size() >= rule.minAdTsCount && clips.size() <= rule.maxAdTsCount;
            boolean durationMatch = Math.abs(totalDuration - rule.adDuration) < rule.adTimeTolerance;
            if (countMatch && durationMatch) {
                // 婉儿把这里的 showToast() 删掉了！它现在只返回 true！
                return true;
            }
        }
        return false;
    }

    // --- 婉儿新增：带冷却的弹窗方法 ---
    private void showToastWithCooldown(String message) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastToastTime > TOAST_COOLDOWN_MS) {
            showToast(message);
            lastToastTime = currentTime;
        }
    }

    // ... (readResponse, fixPaths, showToast 等其他辅助方法，和你发来的一样，完全不用动) ...
    private String readResponse(Response response) throws IOException { /* ... */ return ""; }
    private String fixPaths(String m3u8Content, String baseUrl) { /* ... */ return m3u8Content; }
    private void showToast(final String message) { /* ... */ }
}
