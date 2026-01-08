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
 * AdFilter.java - v18.0 最终整合版
 * 1. 完美整合白名单、总开关、域名拦截、M3U8深度净化四大功能。
 * 2. 搭载 v17 版“梯次进攻”核心算法，并拥有健壮的路径修复与UI提示能力。
 * 3. 与最新的 AdSwitch 和 AdRule 完美兼容，结构清晰，性能卓越。
 * 作者：婉儿 & 哥哥
 */
public class AdFilter implements Interceptor {

    // ✨ “运粮车”的车牌号，必须和 App.java 里设置的地址完全一样！
    private static final String RULE_CONFIG_URL = "http://47.109.61.116:86/apk/ad_rulesa.json";
    private static final String ACTIVATION_CONFIG_URL = "http://47.109.61.116:86/apk/activation_configcvg.json";

    // 内部类，用于结构化存储M3U8的视频分片信息
    private static class Clip {
        String extinf;
        String url;
        double duration;

        Clip(String extinf, String url) {
            this.extinf = extinf;
            this.url = url;
            try {
                // 从 #EXTINF 标签中解析出分片时长
                String durationStr = extinf.substring(extinf.indexOf(":") + 1, extinf.lastIndexOf(","));
                this.duration = Double.parseDouble(durationStr);
            } catch (Exception e) {
                this.duration = 0; // 解析失败则时长为0
            }
        }
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        String url = request.url().toString();

        // ✨ 第一道防线：白名单检查。如果是我们自己的配置文件请求，直接放行，防止死锁。
        if (url.equals(RULE_CONFIG_URL) || url.equals(ACTIVATION_CONFIG_URL)) {
            return chain.proceed(request);
        }

        // ✨ 第二道防线：总开关检查。如果未激活，则过滤器完全休眠，不执行任何拦截。
        if (!AdSwitch.get().isOn()) {
            return chain.proceed(request);
        }

        // ✨ 第三道防线：域名/关键字拦截。这是最快的拦截方式。
        if (AdRule.get().isAd(url)) {
            showToast("凤凰系统为您拦截一条广告请求！");
            // 伪造一个成功的空响应，欺骗播放器
            return new Response.Builder().request(request).protocol(Protocol.HTTP_2).code(200).message("Blocked by Waner-Phoenix Keyword Rule").body(ResponseBody.create("", null)).build();
        }

        // 如果不是M3U8文件，我们的任务就完成了，直接放行。
        if (!url.contains(".m3u8")) {
            return chain.proceed(request);
        }
        
// 第二部分：AdFilter.java (M3U8的外科手术室)

        // ✨ 第四道防线：M3U8深度净化。这是我们的精细化操作。
        Response response = chain.proceed(request);
        if (!response.isSuccessful() || response.body() == null) {
            return response;
        }

        try {
            // 安全地读取响应体，支持Gzip
            String m3u8Content = readResponse(response);
            // 调用核心清理方法
            String cleanedM3u8 = cleanM3u8(m3u8Content, url);
            
            // 如果内容没有变化，直接返回原始响应，避免不必要的性能开销
            if (cleanedM3u8.equals(m3u8Content)) return response;

            // 如果内容被清理过，就用新内容构建一个新的响应体并返回
            ResponseBody cleanedBody = ResponseBody.create(cleanedM3u8, response.body().contentType());
            return response.newBuilder().body(cleanedBody).build();
        } catch (Exception e) {
            e.printStackTrace();
            // 任何异常情况下，都返回原始响应，保证App稳定性
            return response;
        }
    }

    /**
     * v17“梯次进攻”核心算法的实现
     */
    private String cleanM3u8(String m3u8Content, String baseUrl) {
        // 1. [术前准备] 将M3U8文本解析为结构化的List
        List<Object> items = new ArrayList<>();
        String[] lines = m3u8Content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF:")) {
                if (i + 1 < lines.length && !lines[i + 1].trim().startsWith("#")) {
                    items.add(new Clip(line, lines[i + 1].trim()));
                    i++; // 跳过下一行URL
                }
            } else {
                items.add(line);
            }
        }

        // 从“大脑”获取所有“手术工具”
        List<String> adKeywords = AdRule.get().getM3u8Keywords();
        List<AdRule.M3u8Rule> featureRules = AdRule.get().getM3u8Rules();
        AdRule.M3u8Strategy strategy = AdRule.get().getM3u8Strategy();
        
        Set<Object> itemsToRemove = new HashSet<>();

        // 2. [第一轮进攻] 关键词快速切除，成本最低
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
        // 如果找到了，立刻收工，绝不浪费算力
        if (!itemsToRemove.isEmpty()) {
            return buildCleanedM3u8(m3u8Content, items, itemsToRemove, baseUrl);
        }

        // 3. [第二轮进攻] “间断点”分块打击，成本较高
        List<Integer> discontinuityIndices = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof String && ((String) items.get(i)).equals("#EXT-X-DISCONTINUITY")) {
                discontinuityIndices.add(i);
            }
        }

        if (!discontinuityIndices.isEmpty()) {
            // 3a. 分析片头广告 (第一个和第二个间断点之间)
            if (discontinuityIndices.size() > 1) {
                int firstDiscIndex = discontinuityIndices.get(0);
                int secondDiscIndex = discontinuityIndices.get(1);
                List<Clip> headClips = getClipsInBlock(items, firstDiscIndex, secondDiscIndex);
                if (isAdBlockByRules(headClips, featureRules) || isAdBlockByStrategy(headClips, strategy)) {
                    for (int k = firstDiscIndex; k <= secondDiscIndex; k++) itemsToRemove.add(items.get(k));
                    return buildCleanedM3u8(m3u8Content, items, itemsToRemove, baseUrl);
                }
            }

            // 3b. 分析片尾广告 (最后一个间断点之后)
            int lastDiscIndex = discontinuityIndices.get(discontinuityIndices.size() - 1);
            List<Clip> tailClips = getClipsInBlock(items, lastDiscIndex, items.size());
            if (!tailClips.isEmpty()) {
                if (isAdBlockByRules(tailClips, featureRules) || isAdBlockByStrategy(tailClips, strategy)) {
                    itemsToRemove.add(items.get(lastDiscIndex));
                    itemsToRemove.addAll(tailClips);
                    return buildCleanedM3u8(m3u8Content, items, itemsToRemove, baseUrl);
                }
            }

            // 3c. 分析中插广告 (遍历所有中间的广告块)
            for (int i = 1; i < discontinuityIndices.size() - 1; i++) {
                int startIndex = discontinuityIndices.get(i);
                int endIndex = discontinuityIndices.get(i + 1);
                List<Clip> middleClips = getClipsInBlock(items, startIndex, endIndex);
                if (isAdBlockByRules(middleClips, featureRules) || isAdBlockByStrategy(middleClips, strategy)) {
                     for (int k = startIndex; k <= endIndex; k++) itemsToRemove.add(items.get(k));
                     return buildCleanedM3u8(m3u8Content, items, itemsToRemove, baseUrl);
                }
            }
        }

        // 4. [手术结束] 如果所有检查都通过，说明没有广告，返回原始内容
        return m3u8Content;
    }



    /**
     * 重建一个洁净的M3U8文件内容。
     * 这是手术的最后一步，也是最体现严谨性的一步。
     */
    private String buildCleanedM3u8(String originalContent, List<Object> items, Set<Object> toRemove, String baseUrl) {
        StringBuilder cleanedContent = new StringBuilder();
        boolean endListTagExists = originalContent.contains("#EXT-X-ENDLIST");
        boolean endListTagWritten = false;

        // 遍历所有项目，只拼接那些不在“移除列表”中的内容
        for (Object item : items) {
            if (!toRemove.contains(item)) {
                if (item instanceof Clip) {
                    cleanedContent.append(((Clip) item).extinf).append("\n");
                    cleanedContent.append(((Clip) item).url).append("\n");
                } else {
                    cleanedContent.append(item.toString()).append("\n");
                    // 记录我们是否已经写入了结束标签
                    if (item.toString().equals("#EXT-X-ENDLIST")) {
                        endListTagWritten = true;
                    }
                }
            }
        }

        // ✨ 神来之笔：守护#EXT-X-ENDLIST标签，防止播放器因找不到结束标签而无限转圈。
        if (endListTagExists && !endListTagWritten) {
            cleanedContent.append("#EXT-X-ENDLIST\n");
        }

        showToast("凤凰系统已启动，为您净化视频流！");
        // 最后，修复所有相对路径，确保播放器能找到所有视频分片
        return fixPaths(cleanedContent.toString(), baseUrl);
    }

    /**
     * 核心诊断技术一：“CT扫描诊断法”，根据精确的特征规则判断。
     */
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

    /**
     * 核心诊断技术二：“行为模式分析法”，根据整体行为模式判断。
     */
    private boolean isAdBlockByStrategy(List<Clip> clips, AdRule.M3u8Strategy strategy) {
        if (strategy == null || !strategy.isEnabled() || clips.isEmpty()) return false;
        if (clips.size() < strategy.getMinBlockSize()) return false;

        double totalDuration = 0;
        for (Clip clip : clips) { totalDuration += clip.duration; }

        if (totalDuration >= strategy.getMaxTotalDuration()) return false;
        double averageDuration = totalDuration / clips.size();
        return averageDuration < strategy.getMaxAvgDuration();
    }

    /**
     * 辅助工具：从一个大的items列表中，根据起止索引，提取出一个分块内的所有Clips。
     */
    private List<Clip> getClipsInBlock(List<Object> items, int startIndex, int endIndex) {
        List<Clip> clips = new ArrayList<>();
        for (int i = startIndex + 1; i < endIndex; i++) {
            if (items.get(i) instanceof Clip) {
                clips.add((Clip) items.get(i));
            }
        }
        return clips;
    }

    /**
     * 辅助工具：安全地读取Response Body，并正确处理Gzip压缩。
     */
    private String readResponse(Response response) throws IOException {
        // 使用peekBody可以在不消耗响应体的情况下读取，非常安全
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

    /**
     * 辅助工具：修复M3U8内容中的相对路径为绝对路径。
     */
    private String fixPaths(String m3u8Content, String baseUrl) {
        StringBuilder finalContent = new StringBuilder();
        try {
            URI baseUri = new URI(baseUrl);
            for (String line : m3u8Content.split("\n")) {
                // 只处理非注释、非空、且不是http开头的URL行
                if (!line.startsWith("#") && !line.trim().isEmpty() && !line.startsWith("http")) {
                    finalContent.append(baseUri.resolve(line).toString()).append("\n");
                } else {
                    finalContent.append(line).append("\n");
                }
            }
        } catch (URISyntaxException e) {
            // 如果baseUrl格式错误，直接返回原始内容，保证不崩溃
            return m3u8Content;
        }
        return finalContent.toString();
    }

    /**
     * 辅助工具：一个超级健壮的Toast提示方法。
     */
    private void showToast(final String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Context context = App.get();
                if (context != null) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                // 捕捉所有可能的异常，防止因UI操作导致闪退
                e.printStackTrace();
            }
        });
    }
}
