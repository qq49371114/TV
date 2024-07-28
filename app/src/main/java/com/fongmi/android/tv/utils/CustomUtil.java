package com.fongmi.android.tv.utils;

import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;

public class CustomUtil {
    private static final HttpClient httpClient = new HttpClient();

    static class Cache {
        private final HashMap<String, String> cache;

        public Cache() {
            cache = new HashMap<>();
        }

        public String get(String url) {
            return cache.get(url);
        }

        public void put(String url, String data) {
            cache.put(url, data);
        }

        public boolean contains(String url) {
            return cache.containsKey(url);
        }
    }

    static class HttpClient {
        private static final Cache cache = new Cache();

        public String request(String url) {
            if (cache.contains(url)) {
                System.out.println("读取缓存: "+cache.get(url));
                return cache.get(url);
            } else {
                String data = OkHttp.string(url);
                System.out.println("请求接口: "+data);
                if (!data.isEmpty()){
                    cache.put(url, data);
                    System.out.println("保存缓存: "+cache.get(url));
                    return data;
                }else{
                    return "";
                }

            }
        }

    }

    static public JsonObject fetchCustomJson() {
        String response = httpClient.request("https://atomgit.com/jaychan/yylx/raw/master/custom.json");
        return Json.parse(response).getAsJsonObject();
    }

    public static String filterString(String input) {
        try {
            System.out.println("过滤数据: input - "+input);
            JsonObject object = fetchCustomJson();
            JsonArray filterListTest = object.getAsJsonArray("data");
            for (int i = 0; i < filterListTest.size(); i++) {
                String filter = filterListTest.get(i).getAsString();
                System.out.println("过滤数据: 循环 - "+filter);
                input = input.replace(filter, "").replaceAll("^\\s+|\\s+$", "");
            }
            System.out.println("过滤数据: output - "+input);
            return input;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("过滤数据: error - "+input);
            return input;
        }
    }
    public static String getPrefix() {
        try {
            JsonObject object = fetchCustomJson();
            System.out.println("获取前缀: "+object.get("prefix").getAsString());
            return object.get("prefix").getAsString();
        } catch (Exception e) {
            e.printStackTrace();
            return "\uD83D\uDCFA遥遥领先:";
        }
    }
}

