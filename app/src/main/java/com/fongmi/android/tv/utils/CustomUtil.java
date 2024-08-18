package com.fongmi.android.tv.utils;

import android.os.Handler;
import android.os.Looper;

import com.fongmi.android.tv.player.Players;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


public class CustomUtil {

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
        return Prefers.getString("prefix", "");
    }

    public static String getTitle() {
        return Prefers.getString("title", "");
    }

    public static String getAppMsg() {
        return Prefers.getString("app_message", "");
    }

    public static String getSource() {
        return Prefers.getString("source", "");
    }

    public static int getForceRefresh() {
        return Prefers.getInt("force_refresh", -1);
    }

    public interface Callback {
        void onResult(String result);
    }

    public static void initCache(CustomUtil.Callback callback) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                String url = "https://gitee.com/bestpvp/config/raw/master/config/unify.json";
                System.out.println("initCache: 请求接口: " + url);
                String data = OkHttp.string(url);

                // 使用 Handler 将结果传回主线程
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onResult(data);
                        }
                    }
                });
            }
        });
        thread.start();
    }

}