package com.fongmi.android.tv.player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.AdRule; // 婉儿注：请确保这里的import路径是正确的

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

    // 内部类，用于表示M3U8中的视频片段
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

        // 第一道防线：关键词拦截
        if (AdRule.get().isAd(url)) {
            showToast("婉儿的凤凰系统为您拦截一条广告请求！");
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_2)
                    .code(200)
                    .message("Blocked by Waner-Phoenix Keyword Rule")
                    .body(ResponseBody.create("", null))
                    .build();
        }

        // 如果不是M3U8文件，直接放行
        if (!url.contains(".m3u8")) {
            return chain.proceed(request);
        }

        // 第二道防线：M3U8内容清洗
        Response response = chain.proceed(request);
        if (!response.isSuccessful() || response.body() == null) {
            return response;
        }

        try {
            String m3u8Content = readResponse(response);
            String cleanedM3u8 = cleanM3u8(m3u8Content, url);
            ResponseBody cleanedBody = ResponseBody.create(cleanedM3u8, response.body().contentType());
            return response.newBuilder().body(cleanedBody).build();
        } catch (Exception e) {
            return response;
        }
    }

    /**
     * 核心算法：v4.1.7 婉儿最终修正版
     * 1. 恢复使用 #EXT-X-DISCONTINUITY 作为核心锚点，防止误杀正片开头。
     * 2. 增加 adRemoved 标志位，确保整个M3U8处理流程只弹一次窗。
     * 3. 修复删除片尾广告时，导致 #EXT-X-ENDLIST 丢失而无限转圈的问题。
     * 4. 修复了原版代码中向前/向后查找可能存在的边界崩溃风险。
     * 作者：婉儿
     */
    private String cleanM3u8(String m3u8Content, String baseUrl) {
        // 1. 解析M3U8 (逻辑不变)
        List<Object> items = new ArrayList<>();
        String[] lines = m3u8Content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF:")) {
                if (i + 1 < lines.length && !lines[i + 1].trim().startsWith("#")) {
                    items.add(new Clip(line, lines[i + 1].trim()));
                    i++;
                }
            } else {
                items.add(line);
            }
        }

        // 2. 初始化
        List<String> adKeywords = AdRule.get().getM3u8Keywords();
        List<AdRule.M3u8Rule> featureRules = AdRule.get().getM3u8Rules();
        Set<Object> itemsToRemove = new HashSet<>();
        boolean adRemoved = false; // 婉儿新增：统一的广告移除标志

        // 3. 第一轮：高优先级关键字扫描
        if (adKeywords != null && !adKeywords.isEmpty()) {
            for (Object item : items) {
                if (item instanceof Clip) {
                    for (String keyword : adKeywords) {
                        if (((Clip) item).url.contains(keyword)) {
                            itemsToRemove.add(item);
                            adRemoved = true; // 标记已处理广告
                            break;
                        }
                    }
                }
            }
        }

        // 4. 第二轮：基于锚点的特征扫描 (恢复并优化哥哥的原始逻辑)
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (!(item instanceof String) || !((String) item).equals("#EXT-X-DISCONTINUITY")) {
                continue;
            }
            if (itemsToRemove.contains(item)) continue;

            // 找到锚点，开始向前和向后侦测
            // 4.1 向前侦测
            List<Clip> beforeClips = new ArrayList<>();
            for (int j = i - 1; j >= 0; j--) {
                Object prevItem = items.get(j);
                if (itemsToRemove.contains(prevItem)) break; // 遇到已标记的，中断
                if (prevItem instanceof Clip) beforeClips.add(0, (Clip) prevItem);
                else break; // 遇到非Clip项，中断
            }
            if (isAdBlock(beforeClips, featureRules)) {
                itemsToRemove.addAll(beforeClips);
                itemsToRemove.add(item); // 删除锚点自身
                adRemoved = true;
                continue; // 处理完，进入下一次主循环
            }

            // 4.2 向后侦测
            List<Clip> afterClips = new ArrayList<>();
            int blockEndIndex = i; // 记录广告块的结束位置
            for (int j = i + 1; j < items.size(); j++) {
                Object nextItem = items.get(j);
                if (itemsToRemove.contains(nextItem)) break;
                if (nextItem instanceof Clip) {
                    afterClips.add((Clip) nextItem);
                    blockEndIndex = j;
                } else break;
            }
            if (isAdBlock(afterClips, featureRules)) {
                itemsToRemove.addAll(afterClips);
                itemsToRemove.add(item); // 删除锚点自身
                adRemoved = true;
                i = blockEndIndex; // 跳过已处理的广告块
            }
        }

        // 5. 重建M3U8内容
        StringBuilder cleanedContent = new StringBuilder();
        boolean endListTagExists = m3u8Content.contains("#EXT-X-ENDLIST");
        boolean endListTagWritten = false;

        for (Object item : items) {
            if (!itemsToRemove.contains(item)) {
                if (item instanceof Clip) {
                    cleanedContent.append(((Clip) item).extinf).append("\n");
                    cleanedContent.append(((Clip) item).url).append("\n");
                } else {
                    cleanedContent.append(item.toString()).append("\n");
                    if (item.toString().equals("#EXT-X-ENDLIST")) {
                        endListTagWritten = true;
                    }
                }
            }
        }

        // 婉儿修正：如果原始文件有结束标记，但被我们误删了，就把它加回来！
        if (endListTagExists && !endListTagWritten) {
            cleanedContent.append("#EXT-X-ENDLIST\n");
        }

        // 婉儿修正：在这里统一进行一次弹窗提示
        if (adRemoved) {
            showToast("凤凰系统为您成功拦截处理广告！");
        }

        return fixPaths(cleanedContent.toString(), baseUrl);
    }

    /**
     * 辅助方法：判断一个片段块是否为广告 (婉儿修正：移除了这里的弹窗)
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
                // 只返回true，不在这里弹窗
                return true;
            }
        }
        return false;
    }



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
            return m3u8Content;
        }
        return finalContent.toString();
    }

    private void showToast(final String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Context context = App.get();
                if (context != null) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
