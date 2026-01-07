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

/**
 * AdFilter.java - 终极完整版
 * 1. 增加了对两个静态JSON配置请求的“白名单”逻辑，彻底解决了“死锁”问题。
 * 2. 搭载了 v17 版“梯次进攻”核心算法。
 * 3. 与最新的 AdSwitch 和 AdRule 完美兼容，并移除了 await() 调用。
 * 作者：婉儿 & 哥哥
 */
public class AdFilter implements Interceptor {

    // ✨ “运粮车”的车牌号，必须和 App.java 里设置的地址完全一样！
    private static final String RULE_CONFIG_URL = "http://47.109.61.116:86/apk/ad_rules.json";
    private static final String ACTIVATION_CONFIG_URL = "http://47.109.61.116:86/apk/valid_codes_list.json";

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

        // 哨兵的第一道检查：看车牌！如果是我们自己的运粮车，直接敬礼放行！
        if (url.equals(RULE_CONFIG_URL) || url.equals(ACTIVATION_CONFIG_URL)) {
            return chain.proceed(request);
        }

        // 总开关！如果AdSwitch没有被激活，AdFilter将完全“休眠”！
        if (!AdSwitch.get().isOn()) {
            return chain.proceed(request);
        }

        // 域名拦截
        if (AdRule.get().isAd(url)) {
            showToast("凤凰系统为您拦截一条广告请求！");
            return new Response.Builder().request(request).protocol(Protocol.HTTP_2).code(200).message("Blocked by Waner-Phoenix Keyword Rule").body(ResponseBody.create("", null)).build();
        }

        // M3U8处理
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
            if (cleanedM3u8.equals(m3u8Content)) return response;
            ResponseBody cleanedBody = ResponseBody.create(cleanedM3u8, response.body().contentType());
            return response.newBuilder().body(cleanedBody).build();
        } catch (Exception e) {
            e.printStackTrace();
            return response;
        }
    }

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
        if (!itemsToRemove.isEmpty()) {
            return buildCleanedM3u8(m3u8Content, items, itemsToRemove, baseUrl);
        }

        List<Integer> discontinuityIndices = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof String && ((String) items.get(i)).equals("#EXT-X-DISCONTINUITY")) {
                discontinuityIndices.add(i);
            }
        }

        if (!discontinuityIndices.isEmpty()) {
            if (discontinuityIndices.size() > 1) {
                int firstDiscIndex = discontinuityIndices.get(0);
                int secondDiscIndex = discontinuityIndices.get(1);
                List<Clip> headClips = getClipsInBlock(items, firstDiscIndex, secondDiscIndex);
                if (isAdBlockByRules(headClips, featureRules) || isAdBlockByStrategy(headClips, strategy)) {
                    for (int k = firstDiscIndex; k <= secondDiscIndex; k++) itemsToRemove.add(items.get(k));
                    return buildCleanedM3u8(m3u8Content, items, itemsToRemove, baseUrl);
                }
            }

            int lastDiscIndex = discontinuityIndices.get(discontinuityIndices.size() - 1);
            if (discontinuityIndices.size() == 1 || (discontinuityIndices.size() > 1 && lastDiscIndex != discontinuityIndices.get(0))) {
                List<Clip> tailClips = new ArrayList<>();
                for (int i = lastDiscIndex + 1; i < items.size(); i++) {
                    if (items.get(i) instanceof Clip) tailClips.add((Clip) items.get(i));
                    else if (items.get(i) instanceof String && ((String) items.get(i)).startsWith("#EXT")) break;
                }
                if (!tailClips.isEmpty()) {
                    if (isAdBlockByRules(tailClips, featureRules) || isAdBlockByStrategy(tailClips, strategy)) {
                        itemsToRemove.add(items.get(lastDiscIndex));
                        itemsToRemove.addAll(tailClips);
                        return buildCleanedM3u8(m3u8Content, items, itemsToRemove, baseUrl);
                    }
                }
            }
        }

        for (int i = 1; i < discontinuityIndices.size() - 1; i++) {
            int startIndex = discontinuityIndices.get(i);
            int endIndex = discontinuityIndices.get(i + 1);
            List<Clip> middleClips = getClipsInBlock(items, startIndex, endIndex);
            if (isAdBlockByRules(middleClips, featureRules) || isAdBlockByStrategy(middleClips, strategy)) {
                 for (int k = startIndex; k <= endIndex; k++) itemsToRemove.add(items.get(k));
                 return buildCleanedM3u8(m3u8Content, items, itemsToRemove, baseUrl);
            }
        }

        return m3u8Content;
    }

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

    private boolean isAdBlockByRules(List<Clip> clips, List<AdRule.M3u8Rule> rules) {
        if (rules == null || rules.isEmpty() || clips.isEmpty()) return false;
        double totalDuration = 0;
        for (Clip clip : clips) { totalDuration += clip.duration; }
        for (AdRule.M3u8Rule rule : rules) {
            boolean countMatch = clips.size() >= rule.minAdTsCount && clips.size() <= rule.maxAdTsCount;
            boolean durationMatch = Math.abs(totalDuration - rule.adDuration) < rule.adTimeTolerance;
            if (countMatch && durationMatch) return true;
        }
        return false;
    }

    private boolean isAdBlockByStrategy(List<Clip> clips, AdRule.M3u8Strategy strategy) {
        if (strategy == null || !strategy.isEnabled() || clips.isEmpty()) return false;
        if (clips.size() < strategy.getMinBlockSize()) return false;
        double totalDuration = 0;
        for (Clip clip : clips) { totalDuration += clip.duration; }
        if (totalDuration >= strategy.getMaxTotalDuration()) return false;
        double averageDuration = totalDuration / clips.size();
        return averageDuration < strategy.getMaxAvgDuration();
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
        ResponseBody body = response.peekBody(Long.MAX_VALUE);
        InputStream inputStream = body.byteStream();
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
