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
     * 核心算法：v13.0.0 双重扫描与安全隔离 (婉儿与哥哥的智慧巅峰)
     * 采用两轮完全独立的扫描机制，实现高精度与高安全的完美统一。
     * 第一轮扫描 (高精度打击):
     *   - 仅使用关键字和精确规则(rules)进行扫描。
     *   - 如果命中任何广告，则任务结束，绝不启动第二轮，防止任何误伤可能。
     * 第二轮扫描 (启发式补充):
     *   - 当且仅当第一轮扫描未发现任何广告时，才会启动。
     *   - 使用带安全锁的平均时长策略(m3u8Strategy)进行补充侦测。
     * 这，是我们的最终、也是最安全可靠的杰作！
     * 作者：婉儿 & 哥哥
     */
    private String cleanM3u8(String m3u8Content, String baseUrl) {
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

        List<String> adKeywords = AdRule.get().getM3u8Keywords();
        List<AdRule.M3u8Rule> featureRules = AdRule.get().getM3u8Rules();
        AdRule.M3u8Strategy strategy = AdRule.get().getM3u8Strategy();
        
        Set<Object> itemsToRemove = new HashSet<>();
        boolean adRemoved = false;

        // --- 第一轮扫描：高精度打击 ---
        if (adKeywords != null && !adKeywords.isEmpty()) {
            for (Object item : items) {
                if (item instanceof Clip) {
                    for (String keyword : adKeywords) {
                        if (((Clip) item).url.contains(keyword)) {
                            itemsToRemove.add(item);
                            adRemoved = true;
                            break;
                        }
                    }
                }
            }
        }
        if (featureRules != null && !featureRules.isEmpty()) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) instanceof String && ((String) items.get(i)).equals("#EXT-X-DISCONTINUITY")) {
                    int blockEndIndex = findBlockEnd(items, i, itemsToRemove);
                    if (blockEndIndex != -1) {
                        List<Clip> candidateClips = getClipsInBlock(items, i, blockEndIndex);
                        double totalDuration = 0;
                        for (Clip clip : candidateClips) { totalDuration += clip.duration; }

                        for (AdRule.M3u8Rule rule : featureRules) {
                            boolean countMatch = candidateClips.size() >= rule.minAdTsCount && candidateClips.size() <= rule.maxAdTsCount;
                            boolean durationMatch = Math.abs(totalDuration - rule.adDuration) < rule.adTimeTolerance;
                            if (countMatch && durationMatch) {
                                for (int k = i; k <= blockEndIndex; k++) itemsToRemove.add(items.get(k));
                                adRemoved = true;
                                i = blockEndIndex;
                                break;
                            }
                        }
                    }
                }
            }
        }

        // --- 安全隔离带 ---
        if (!adRemoved && strategy != null && strategy.isEnabled()) {
            // --- 第二轮扫描：启发式补充 ---
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) instanceof String && ((String) items.get(i)).equals("#EXT-X-DISCONTINUITY")) {
                    int blockEndIndex = findBlockEnd(items, i, itemsToRemove);
                    if (blockEndIndex != -1) {
                        List<Clip> candidateClips = getClipsInBlock(items, i, blockEndIndex);
                        double totalDuration = 0;
                        for (Clip clip : candidateClips) { totalDuration += clip.duration; }
                        
                        if (totalDuration < strategy.getMaxTotalDuration()) {
                            if (candidateClips.size() >= strategy.getMinBlockSize()) {
                                double averageDuration = candidateClips.isEmpty() ? 0 : totalDuration / candidateClips.size();
                                if (averageDuration < strategy.getMaxAvgDuration()) {
                                    for (int k = i; k <= blockEndIndex; k++) itemsToRemove.add(items.get(k));
                                    adRemoved = true;
                                    i = blockEndIndex;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (adRemoved) {
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
