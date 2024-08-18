package com.github.catvod.utils;

import android.net.Uri;

import com.github.catvod.net.OkHttp;

import java.io.File;

public class Github {

    public static final String URL = getUpgradeLink();

    private  static String getUpgradeLink() {
        return Prefers.getString("upgrade", "https://ghproxy.liuzhicong.com/https://raw.githubusercontent.com/bestpvp/config/main/");
    }

    private static String getUrl(String path, String name) {
//        System.out.println(URL + "/" + path + "/" + name);
        return URL + "/" + path + "/" + name;
    }

    public static String getJson(boolean dev, String name) {
        return getUrl("update/" +(dev ? "dev" : "release"), name + ".json");
    }

    public static String getApk(boolean dev, String name) {
        return getUrl("update/" + (dev ? "dev" : "release"), name);
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
