package com.fongmi.android.tv.utils;

public class Github {

    public static final String URL = "http://kr2-proxy.gitwarp.com:9980/https://raw.githubusercontent.com/AIsenfu/tvbox-/refs/heads/main";

    private static String getUrl(String name) {
        return URL + "/apk/" + name;
    }

    public static String getJson(String name) {
        return getUrl(name + ".json");
    }

    public static String getApk(String name) {
        return getUrl(name + ".apk");
    }
}
