package com.fongmi.android.tv.player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.AdRule; // 婉儿注：请确保这里的import路径是正确的

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
     * 核心算法：v4.1.6 婉儿最终谢罪版 (基于哥哥的原始v4.1代码)
     * 严格在原代码结构上，通过统一删除列表，彻底解决广告及分隔符的残留问题。
     * 作者：婉儿
     */
    private String cleanM3u8(String m3u8Content, String baseUrl) {
        // 1. 将M3U8文件解析成一个更易于操作的结构 (此部分不变)
        List<Object> items = new ArrayList<>();
        String[] lines = m3u8Content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF:")) {
                if (i + 1 < lines.length && !lines[i+1].trim().startsWith("#")) {
                    items.add(new Clip(line, lines[i + 1].trim()));
                    i++; // 跳过URL行
                }
            } else {
                items.add(line); // 将标签或其他行作为字符串添加
            }
        }

        // --- 婉儿修改：步骤2 - 获取规则并初始化一个统一的删除列表 ---
        List<AdRule.M3u8Rule> rules = AdRule.get().getM3u8Rules();
        Set<Object> itemsToRemove = new HashSet<>();

        // --- 婉儿修改：步骤3 - 在你原来的for循环上，升级其标记行为 ---
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item instanceof String && ((String) item).equals("#EXT-X-DISCONTINUITY")) {
                // 找到了一个锚点！
                
                // --- 3.1 向前侦测 ---
                List<Clip> beforeClips = new ArrayList<>();
                int separatorBeforeAdIndex = -1;
                for (int j = i - 1; j >= 0; j--) {
                    if (items.get(j) instanceof Clip) {
                        beforeClips.add(0, (Clip) items.get(j));
                    } else {
                        separatorBeforeAdIndex = j; // 记录广告块之前的那个分隔符的位置
                        break;
                    }
                }
                if (isAdBlock(beforeClips, rules)) {
                    itemsToRemove.addAll(beforeClips); // 标记广告片段
                    itemsToRemove.add(item); // 标记广告后面的分隔符 (当前锚点)
                    if (separatorBeforeAdIndex != -1) {
                        itemsToRemove.add(items.get(separatorBeforeAdIndex)); // 标记广告前面的分隔符
                    }
                }

                // --- 3.2 向后侦测 ---
                List<Clip> afterClips = new ArrayList<>();
                int separatorAfterAdIndex = -1;
                for (int j = i + 1; j < items.size(); j++) {
                    if (items.get(j) instanceof Clip) {
                        afterClips.add((Clip) items.get(j));
                    } else {
                        separatorAfterAdIndex = j; // 记录广告块之后的那个分隔符的位置
                        break;
                    }
                }
                if (isAdBlock(afterClips, rules)) {
                    itemsToRemove.addAll(afterClips); // 标记广告片段
                    itemsToRemove.add(item); // 标记广告前面的分隔符 (当前锚点)
                    if (separatorAfterAdIndex != -1) {
                        itemsToRemove.add(items.get(separatorAfterAdIndex)); // 标记广告后面的分隔符
                    }
                }
            }
        }

        // --- 婉儿修改：步骤4 - 使用统一的删除列表进行重建 ---
        StringBuilder cleanedContent = new StringBuilder();
        for (Object item : items) {
            // 只要这个item不在统一删除列表里，就保留它
            if (!itemsToRemove.contains(item)) {
                if (item instanceof Clip) {
                    cleanedContent.append(((Clip) item).extinf).append("\n");
                    cleanedContent.append(((Clip) item).url).append("\n");
                } else {
                    cleanedContent.append(item.toString()).append("\n");
                }
            }
        }

        return fixPaths(cleanedContent.toString(), baseUrl);
    }

    /**
     * 辅助方法：判断一个片段块是否为广告 (此部分不变)
     */
    private boolean isAdBlock(List<Clip> clips, List<AdRule.M3u8Rule> rules) {
        if (clips.isEmpty() || rules == null || rules.isEmpty()) {
            return false;
        }

        double totalDuration = 0;
        for (Clip clip : clips) {
            totalDuration += clip.duration;
        }

        // 核心升级：遍历所有规则，只要有一个匹配就成功
        for (AdRule.M3u8Rule rule : rules) {
            boolean countMatch = clips.size() >= rule.minAdTsCount && clips.size() <= rule.maxAdTsCount;
            boolean durationMatch = Math.abs(totalDuration - rule.adDuration) < rule.adTimeTolerance;

            if (countMatch && durationMatch) {
                // 规则命中！弹窗时可以带上规则名称
                showToast("凤凰系统为您定位一个 " + rule.name + "！");
                return true;
            }
        }
        return false; // 婉儿修改：补全原始代码缺失的return false
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
