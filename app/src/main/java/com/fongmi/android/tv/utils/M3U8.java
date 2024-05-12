package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import androidx.media3.common.util.UriUtil;

import com.github.catvod.net.OkHttp;
import com.google.common.net.HttpHeaders;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Headers;
import okhttp3.Response;

public class M3U8 {
    private static final String TAG_KEY = "#EXT-X-KEY";
    private static final Pattern REGEX_URI = Pattern.compile("URI=\"(.+?)\"");

    public static String get(String url, Map<String, String> headers) {
        try {
            if (TextUtils.isEmpty(url)) return "";
            Response response = OkHttp.newCall(url, getHeader(headers)).execute();
            if (response.header(HttpHeaders.ACCEPT_RANGES) != null && !url.contains(".m3u8")) return "";
            String result = response.body().string();
            Matcher matcher = Pattern.compile("#EXT-X-STREAM-INF(.*)\\n?(.*)").matcher(result.replaceAll("\r\n", "\n"));
            if (matcher.find() && matcher.groupCount() > 1) return get(UriUtil.resolve(url, matcher.group(2)), headers);
            StringBuilder sb = new StringBuilder();
            for (String line : result.split("\n")) sb.append(shouldResolve(line) ? resolve(url, line) : line).append("\n");
            return clean(sb.toString());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String clean(String line) {
        return line;
    }

    private static Headers getHeader(Map<String, String> headers) {
        Headers.Builder builder = new Headers.Builder();
        for (Map.Entry<String, String> header : headers.entrySet()) if (HttpHeaders.USER_AGENT.equalsIgnoreCase(header.getKey()) || HttpHeaders.REFERER.equalsIgnoreCase(header.getKey()) || HttpHeaders.COOKIE.equalsIgnoreCase(header.getKey())) builder.add(header.getKey(), header.getValue());
        builder.add(HttpHeaders.RANGE, "bytes=0-");
        return builder.build();
    }

    private static boolean shouldResolve(String line) {
        return (!line.startsWith("#") && !line.startsWith("http")) || line.startsWith(TAG_KEY);
    }

    private static String resolve(String base, String line) {
        if (line.startsWith(TAG_KEY)) {
            Matcher matcher = REGEX_URI.matcher(line);
            String value = matcher.find() ? matcher.group(1) : null;
            return value == null ? line : line.replace(value, UriUtil.resolve(base, value));
        } else {
            return UriUtil.resolve(base, line);
        }
    }
}
