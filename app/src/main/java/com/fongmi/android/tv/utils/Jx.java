package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;

public class Jx {

    public static String getUrl(String jxToken, String realPlayUrl) {
        try {
            String jxUrl = Prefers.getString("jxUrl");
            System.out.println("公瑾TV - jxUrl: "+jxUrl);
            if (jxUrl.isEmpty()) return realPlayUrl;
            System.out.println("公瑾TV - originalUrl: "+realPlayUrl);
            System.out.println("公瑾TV - jxToken: "+jxToken);
            String response = OkHttp.string(String.format(jxUrl, jxToken, realPlayUrl));
            if (response.isEmpty()) {
                System.out.println("公瑾TV - 解析服务返回空, 不处理!");
                return  realPlayUrl;
            }
            com.alibaba.fastjson.JSONObject object = com.alibaba.fastjson.JSONObject.parseObject(response);

            // Handle potential missing "code" field
            if (object.containsKey("code") && object.getInteger("code") == 200) {
                System.out.println(object.getString("msg"));
                realPlayUrl = object.getJSONObject("data").getString("jx_url");
                App.post(() -> Notify.show("公瑾TV: 广告解析服务解析成功"));
            } else {
                // Extract message if available, otherwise use generic error message
                String message = object.getString("msg");
                System.out.println(object);
                App.post(() -> Notify.show("公瑾TV: "+message));
            }
            System.out.println("公瑾TV - realPlayUrl: "+realPlayUrl);
            return realPlayUrl;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return realPlayUrl;
        }
    }


    public interface UrlCallback {
        void onUrlProcessed(String url);
    }

    public static void getUrl(String jxToken, String realPlayUrl, UrlCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String resultUrl = realPlayUrl; // 使用局部变量存储结果
                try {
                    String jxUrl = Prefers.getString("jxUrl");
                    System.out.println("公瑾TV - jxUrl: " + jxUrl);
                    if (jxUrl.isEmpty()) {
                        callback.onUrlProcessed(resultUrl);
                        return;
                    }
                    System.out.println("公瑾TV - originalUrl: " + realPlayUrl);
                    System.out.println("公瑾TV - jxToken: " + jxToken);
                    String response = OkHttp.string(String.format(jxUrl, jxToken, realPlayUrl));
                    if (response.isEmpty()) {
                        System.out.println("公瑾TV - 解析服务返回空, 不处理!");
                        callback.onUrlProcessed(resultUrl);
                        return;
                    }
                    com.alibaba.fastjson.JSONObject object = com.alibaba.fastjson.JSONObject.parseObject(response);

                    // 处理可能缺失的 "code" 字段
                    if (object.containsKey("code") && object.getInteger("code") == 200) {
                        System.out.println(object.getString("msg"));
                        resultUrl = object.getJSONObject("data").getString("jx_url");
                        App.post(() -> Notify.show("公瑾TV: 广告解析服务解析成功"));
                    } else {
                        // 如果存在消息，则提取，否则使用通用错误消息
                        String message = object.getString("msg");
                        System.out.println(object);
                        App.post(() -> Notify.show("公瑾TV: "+message));
                    }
                    System.out.println("公瑾TV - realPlayUrl: " + resultUrl);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                } finally {
                    callback.onUrlProcessed(resultUrl);
                }
            }
        }).start();
    }
}
