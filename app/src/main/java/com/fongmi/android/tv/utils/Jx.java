package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;

public class Jx {

//    private static final String jxUrl = "https://www.bestpvp.site/api/m3u8/parse?token=%s&url=%s";
//    private static final String jxUrl = "https://www.lintech.work/api/m3u8/parse?token=%s&url=%s";

    public static String getUrl(String jxToken, String realPlayUrl) {
        try {
            String jxUrl = Prefers.getString("jxUrl");
            System.out.println("公瑾TV - jxUrl: "+jxUrl);
            if (jxUrl.isEmpty()) return realPlayUrl;
            App.post(() -> Notify.show("公瑾TV: 广告解析服务启动"));
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
//                App.post(() -> Notify.show("公瑾TV 广告解析成功: "+object.getString("msg")));
                realPlayUrl = object.getJSONObject("data").getString("jx_url");
            } else {
                // Extract message if available, otherwise use generic error message
                String message = object.containsKey("msg") ? object.getString("msg") : "解析报错 - "+jxToken;
//                App.post(() -> Notify.show("公瑾TV 广告解析失败: "+message));
                System.out.println(object);
            }
            System.out.println("公瑾TV - realPlayUrl: "+realPlayUrl);
            return realPlayUrl;
        } catch (Exception e) {
//            App.post(() -> Notify.show("公瑾TV 广告解析异常: "+e.getMessage()));
            System.out.println(e.getMessage());
            return realPlayUrl;
        }
    }

}
