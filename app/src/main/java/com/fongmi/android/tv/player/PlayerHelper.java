package com.fongmi.android.tv.player;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;

public class PlayerHelper {

    public static void share(Activity activity, String url, Map<String, String> headers, CharSequence title) {
        try {
            if (url == null || url.isEmpty()) return;
            Bundle bundle = ExoUtil.toBundle(headers);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Intent.EXTRA_TEXT, url);
            intent.putExtra("extra_headers", bundle);
            intent.putExtra("title", title).putExtra("name", title);
            intent.setType("text/plain");
            activity.startActivity(Util.getChooser(intent));
        } catch (Exception ignored) {
        }
    }

    public static void choose(Activity activity, String url, Map<String, String> headers, boolean isVod, long position, CharSequence title) {
        try {
            if (url == null || url.isEmpty()) return;
            List<String> list = new ArrayList<>();
            headers.forEach((key, value) -> {
                list.add(key);
                list.add(value);
            });
            Uri data = url.startsWith("file://") || url.startsWith("/") ? FileUtil.getShareUri(url) : Uri.parse(url);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(data, "video/*");
            intent.putExtra("title", title).putExtra("return_result", isVod);
            intent.putExtra("headers", list.toArray(String[]::new));
            if (isVod) intent.putExtra("position", (int) position);
            activity.startActivityForResult(Util.getChooser(intent), 1001);
        } catch (Exception ignored) {
        }
    }

    public static void onExternalResult(Intent data, Runnable onNext, LongConsumer seekTo) {
        try {
            if (data == null || data.getExtras() == null) return;
            long position = data.getExtras().getInt("position", 0);
            String endBy = data.getExtras().getString("end_by", "");
            if ("playback_completion".equals(endBy)) App.post(onNext);
            if ("user".equals(endBy)) seekTo.accept(position);
        } catch (Exception ignored) {
        }
    }
}
