package com.fongmi.android.tv.utils;

public class Github {

    public static final String URL = "http://47.109.61.116:86";

    private static String getUrl(String path, String name) {
        return URL + "/" + path + "/" + name;
    }

    public static String getJson(boolean dev, String name) {
        return getUrl("apk/" + (dev ? "dev" : "release"), name + ".json");
    }

    public static String getApk(boolean dev, String name) {
        return getUrl("apk/" + (dev ? "dev" : "release"), name + ".apk");
    }
}
