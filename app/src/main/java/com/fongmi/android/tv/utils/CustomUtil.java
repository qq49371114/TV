package com.fongmi.android.tv.utils;

import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


public class CustomUtil {

    public static void initCache() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = "https://git.acwing.com/qq49371114/yylx/-/raw/main/yylx.json";
                    if (!Prefers.getString("source").isEmpty()) {
                        System.out.println("initCache: 读取缓存成功");
                    } else {
                        System.out.println("initCache: 请求接口: " + url);
                        String data = OkHttp.string(url);
                        if (!data.isEmpty()) {
                            JsonObject object = JsonParser.parseString(data).getAsJsonObject();
                            Prefers.put("force_refresh", object.get("force_refresh").getAsInt());
                            Prefers.put("source", object.get("source").getAsString());
                            Prefers.put("app_show_dialog", object.get("app_show_dialog").getAsBoolean());
                            Prefers.put("jar_show_dialog", object.get("jar_show_dialog").getAsBoolean());
                            Prefers.put("app_require_password", object.get("app_require_password").getAsBoolean());
                            Prefers.put("jar_require_password", object.get("jar_require_password").getAsBoolean());
                            Prefers.put("app_password", object.get("app_password").getAsString());
                            Prefers.put("jar_password", object.get("jar_password").getAsString());
                            Prefers.put("universal_password", object.get("universal_password").getAsString());
                            Prefers.put("app_message", object.get("app_message").getAsString());
                            Prefers.put("jar_message", object.get("jar_message").getAsString());
                            Prefers.put("filter", object.getAsJsonArray("filter").toString());
                            Prefers.put("prefix", object.get("prefix").getAsString());
                            Prefers.put("title", object.get("title").getAsString());
                            Prefers.put("picture", object.get("picture").getAsString());
                            Prefers.put("link", object.get("link").getAsString());
                            Prefers.put("notice", object.get("notice").getAsString());
                            System.out.println("initCache: 保存缓存成功");
                        } else {
                            System.out.println("initCache: 保存缓存失败: " + data);
                        }
                    }
//                    printAllCache();
                } catch (Exception e) {
                    System.out.println("initCache: 保存缓存异常");
                }
            }
        }).start();
    }

    public static void clearCache(){
        JsonArray keysToDelete = new JsonArray();
        keysToDelete.add("force_refresh");
        keysToDelete.add("source");
        keysToDelete.add("app_show_dialog");
        keysToDelete.add("jar_show_dialog");
        keysToDelete.add("app_require_password");
        keysToDelete.add("jar_require_password");
        keysToDelete.add("app_password");
        keysToDelete.add("jar_password");
        keysToDelete.add("app_message");
        keysToDelete.add("jar_message");
        keysToDelete.add("filter");
        keysToDelete.add("prefix");
        keysToDelete.add("title");
        keysToDelete.add("picture");
        keysToDelete.add("link");
        Prefers.removeKeys(keysToDelete);
        System.out.println("clearCache: 清除缓存成功");
    }

    public static void printAllCache(){
        Prefers.printAllEntries();
    }


    public static String filterString(String input) {
        try {
//            System.out.println("过滤数据: input - "+input);
            String jsonString = Prefers.getString("filter");
            if (!jsonString.isEmpty()){
//                System.out.println("过滤数据: 开始过滤 - "+jsonString);
                JsonArray filterListTest = JsonParser.parseString(jsonString).getAsJsonArray();
                for (int i = 0; i < filterListTest.size(); i++) {
                    String filter = filterListTest.get(i).getAsString();
//                    System.out.println("过滤数据: 循环 - "+filter);
                    input = input.replace(filter, "").replaceAll("^\\s+|\\s+$", "");
                }
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
        return Prefers.getString("prefix", "🐯遥遥领先🐯");
    }

    public static String getTitle() {
        return Prefers.getString("title", "🐯遥遥领先🐯");
    }

    public static String getAppMsg() {
        return Prefers.getString("app_message", "APP为免费开源项目，仅供测试，请勿付费购买！ \n\n播放时若出现广告均为三方插入, 与APP无关，请勿上当!");
    }

    public static String getSource() {
        return Prefers.getString("source", "http://1.116.112.145:86/yylxzxc.bmp");
    }

    public static int getForceRefresh() {
        return Prefers.getInt("force_refresh", -1);
    }
}