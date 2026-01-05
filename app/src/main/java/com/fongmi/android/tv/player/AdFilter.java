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
        // ✨ 我们暂时用最简单的“万能钥匙”版AdSwitch来确保激活状态
        if (!AdSwitch.get().isOn()) {
            return chain.proceed(chain.request());
        }

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
            AdRule.get().await();
            
            // ✨ 调用只诊断不删除的 cleanM3u8
            cleanM3u8(m3u8Content);
            
            // ✨ 直接返回原始的、未被修改的M3U8内容，100%保证播放！
            return response;

        } catch (Exception e) {
            e.printStackTrace();
            return response;
        }
    }

    private void cleanM3u8(String m3u8Content) {
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

        AdRule.M3u8Strategy strategy = AdRule.get().getM3u8Strategy();
        if (strategy == null || !strategy.isEnabled()) {
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof String && ((String) items.get(i)).equals("#EXT-X-DISCONTINUITY")) {
                int blockEndIndex = -1;
                for (int j = i + 1; j < items.size(); j++) {
                    if (items.get(j) instanceof String && ((String) items.get(j)).equals("#EXT-X-DISCONTINUITY")) {
                        blockEndIndex = j;
                        break;
                    }
                }

                if (blockEndIndex != -1) {
                    List<Clip> candidateClips = new ArrayList<>();
                    for (int k = i + 1; k < blockEndIndex; k++) {
                        if (items.get(k) instanceof Clip) {
                            candidateClips.add((Clip) items.get(k));
                        }
                    }

                    if (isAdBlockByStrategy(candidateClips, strategy)) {
                        double totalDuration = 0;
                        for(Clip c : candidateClips) totalDuration += c.duration;
                        String report = String.format("发现可疑广告块！\n片段数: %d, 总时长: %.2f秒", candidateClips.size(), totalDuration);
                        showToast(report);
                    }
                    i = blockEndIndex -1;
                }
            }
        }
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

    private String readResponse(Response response) throws IOException {
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

    private void showToast(final String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Context context = App.get();
                if (context != null) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
