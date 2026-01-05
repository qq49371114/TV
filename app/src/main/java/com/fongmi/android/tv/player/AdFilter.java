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

        if (AdRule.get().isAd(url)) {
            showToast("凤凰系统为您拦截一条广告请求！");
            return new Response.Builder().request(request).protocol(Protocol.HTTP_2).code(200).message("Blocked by Waner-Phoenix Keyword Rule").body(ResponseBody.create("", null)).build();
        }

        if (!url.contains(".m3u8")) {
            return chain.proceed(request);
        }

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
     * 核心算法：v17.0.0 梯次进攻 (婉儿与哥哥的最终指挥)
     * 严格按照“关键字 -> 片头片尾 -> 中部”的优先级进行梯次扫描。
     * 任何一波攻击命中目标，后续的扫描将立即中止，以实现最高效率和绝对安全。
     * 这，才是我们真正的最终决战形态！
     * 作者：婉儿 & 哥哥
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

        // 2. 获取所有云端规则和策略 (弹药库)
        List<String> adKeywords = AdRule.get().getM3u8Keywords();
        List<AdRule.M3u8Rule> featureRules = AdRule.get().getM3u8Rules();
        AdRule.M3u8Strategy strategy = AdRule.get().getM3u8Strategy();
        
        Set<Object> itemsToRemove = new HashSet<>();

        // --- 第一波攻击：空军 (关键字快速打击) ---
        if (adKeywords != null && !adKeywords.isEmpty()) {
            for (Object item : items) {
                if (item instanceof Clip) {
                    for (String keyword : adKeywords) {
                        if (((Clip) item).url.contains(keyword)) {
                            itemsToRemove.add(item);
                        }
                    }
                }
            }
        }
        // 如果空军已经命中目标，直接结束战斗！
        if (!itemsToRemove.isEmpty()) {
            return buildCleanedM3u8(m3u8Content, items, itemsToRemove, baseUrl);
        }

        // --- 第二波攻击：特种部队 (片头 & 片尾定点清除) ---
        List<Integer> discontinuityIndices = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof String && ((String) items.get(i)).equals("#EXT-X-DISCONTINUITY")) {
                discontinuityIndices.add(i);
            }
        }

        if (!discontinuityIndices.isEmpty()) {
            // 2a. 扫描片头 (第一个分隔符后的区域)
            int firstDiscIndex = discontinuityIndices.get(0);
            int secondDiscIndex = discontinuityIndices.size() > 1 ? discontinuityIndices.get(1) : -1;
            if (secondDiscIndex != -1) {
                List<Clip> headClips = getClipsInBlock(items, firstDiscIndex, secondDiscIndex);
                if (isAdBlockByRules(headClips, featureRules) || isAdBlockByStrategy(headClips, strategy)) {
                    for (int k = firstDiscIndex; k <= secondDiscIndex; k++) itemsToRemove.add(items.get(k));
                    return buildCleanedM3u8(m3u8Content, items, itemsToRemove, baseUrl); // 命中，结束战斗！
                }
            }

            // 2b. 扫描片尾 (最后一个分隔符后的区域)
            int lastDiscIndex = discontinuityIndices.get(discontinuityIndices.size() - 1);
            // 确保片头和片尾不是同一个块
            if (lastDiscIndex != firstDiscIndex || secondDiscIndex == -1) {
                List<Clip> tailClips = new ArrayList<>();
                for (int i = lastDiscIndex + 1; i < items.size(); i++) {
                    if (items.get(i) instanceof Clip) tailClips.add((Clip) items.get(i));
                    else if (items.get(i) instanceof String && ((String) items.get(i)).startsWith("#EXT")) break;
                }
                if (!tailClips.isEmpty()) {
                    if (isAdBlockByRules(tailClips, featureRules) || isAdBlockByStrategy(tailClips, strategy)) {
                        itemsToRemove.add(items.get(lastDiscIndex));
                        itemsToRemove.addAll(tailClips);
                        return buildCleanedM3u8(m3u8Content, items, itemsToRemove, baseUrl); // 命中，结束战斗！
                    }
                }
            }
        }

        // --- 第三波攻击：陆军 (中部地毯式清扫) ---
        // 只有当空军和特种部队都无功而返时，陆军才出动
        for (int i = 0; i < discontinuityIndices.size() - 1; i++) {
            int startIndex = discontinuityIndices.get(i);
            int endIndex = discontinuityIndices.get(i + 1);
            // 跳过已经被特种部队扫描过的片头区域
            if (i == 0) continue; 
            
            List<Clip> middleClips = getClipsInBlock(items, startIndex, endIndex);
            if (isAdBlockByRules(middleClips, featureRules) || isAdBlockByStrategy(middleClips, strategy)) {
                 for (int k = startIndex; k <= endIndex; k++) itemsToRemove.add(items.get(k));
                 return buildCleanedM3u8(m3u8Content, items, itemsToRemove, baseUrl); // 命中，结束战斗！
            }
        }

        // 如果所有攻击都未能命中，则返回原始内容
        return m3u8Content;
    }

    // 婉儿新增：将重建逻辑提炼成独立的辅助方法，避免重复代码
    private String buildCleanedM3u8(String originalContent, List<Object> items, Set<Object> toRemove, String baseUrl) {
        StringBuilder cleanedContent = new StringBuilder();
        boolean endListTagExists = originalContent.contains("#EXT-X-ENDLIST");
        boolean endListTagWritten = false;
        for (Object item : items) {
            if (!toRemove.contains(item)) {
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
        if (endListTagExists && !endListTagWritten) {
            cleanedContent.append("#EXT-X-ENDLIST\n");
        }
        showToast("凤凰系统已启动，为您净化视频流！");
        return fixPaths(cleanedContent.toString(), baseUrl);
    }

        return m3u8Content;
    }
    
    private int findBlockEnd(List<Object> items, int startIndex, Set<Object> toRemove) {
        for (int j = startIndex + 1; j < items.size(); j++) {
            Object currentItem = items.get(j);
            if (toRemove.contains(currentItem)) continue;
            if (currentItem instanceof String && ((String) currentItem).equals("#EXT-X-DISCONTINUITY")) {
                return j;
            }
        }
        return -1;
    }

    private List<Clip> getClipsInBlock(List<Object> items, int startIndex, int endIndex) {
        List<Clip> clips = new ArrayList<>();
        for (int i = startIndex + 1; i < endIndex; i++) {
            if (items.get(i) instanceof Clip) {
                clips.add((Clip) items.get(i));
            }
        }
        return clips;
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
