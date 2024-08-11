package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;

public class Jx {

    public static String getUrl(String jxToken, String realPlayUrl) {
        try {
            String jxUrl = Prefers.getString("jxUrl");
            if (jxUrl.isEmpty()) return realPlayUrl;
            System.out.println("公瑾TV - jxUrl: "+String.format(jxUrl, jxToken, realPlayUrl));
            String response = OkHttp.string(String.format(jxUrl, jxToken, realPlayUrl));
            if (response.isEmpty()) {
                System.out.println("公瑾TV - 解析服务返回空, 不处理!");
                return realPlayUrl;
            }
            com.alibaba.fastjson.JSONObject object = com.alibaba.fastjson.JSONObject.parseObject(response);

            // Handle potential missing "code" field
            if (object.containsKey("code") && object.getInteger("code") == 200) {
                System.out.println(object.getString("msg"));
                realPlayUrl = object.getJSONObject("data").getString("jx_url");
                App.post(() -> Notify.show("公瑾TV: 广告解析服务解析成功"));
            } else {
                // Extract message if available, otherwise use generic error message
                String message = object.containsKey("msg")? object.getString("msg"): object.getString("detail");
                System.out.println(object);
                App.post(() -> Notify.show("公瑾TV: "+message));
            }
            System.out.println("公瑾TV - outPutUrl: "+realPlayUrl);
            return realPlayUrl;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return realPlayUrl;
        }
    }
}
