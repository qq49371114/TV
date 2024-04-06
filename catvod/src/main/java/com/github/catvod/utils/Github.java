package com.github.catvod.utils;

import android.net.Uri;

import com.github.catvod.net.OkHttp;

import java.io.File;

public class Github {

    public static final String URL = "http://666.ewwe.gq/fongmi/release/main";

    private static String getUrl(String path, String name) {
        return URL + "/" + path + "/" + name;
    }

    public static String getJson(String name) {
        return getUrl("apk/kitkat", name + ".json");
    }

    public static String getApk(String name) {
        return getUrl("apk/kitkat", name + ".apk");
    }

    public static String getCrosswalk() {
        return getUrl("crosswalk", "XWalkRuntimeLib.apk");
    }

    public static String getSo(String url) {
        try {
            File file = new File(Path.so(), Uri.parse(url).getLastPathSegment());
            if (file.length() < 300) Path.write(file, OkHttp.newCall(url).execute().body().bytes());
            return file.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}
