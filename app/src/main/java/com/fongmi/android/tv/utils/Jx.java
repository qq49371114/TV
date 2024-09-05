package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;

import java.net.URLEncoder;
import java.util.Map;

public class Jx {

    public static String getUrl(String jxToken, String realPlayUrl, Map<String, String> header) {
        try {
            String jxUrl = Prefers.getString("jxUrl");
            if (jxUrl.isEmpty()) return realPlayUrl;
            // 对 URL 进行编码
            String enCodeUrl = URLEncoder.encode(realPlayUrl, "UTF-8");
            System.out.println("jxUrl: "+String.format(jxUrl, jxToken, enCodeUrl));
            System.out.println("headers: "+header.toString());
            String response = OkHttp.string(String.format(jxUrl, jxToken, enCodeUrl), header);
            if (response.isEmpty()) {
                System.out.println("解析服务返回空, 不处理!");
                return realPlayUrl;
            }
            com.alibaba.fastjson.JSONObject object = com.alibaba.fastjson.JSONObject.parseObject(response);
            System.out.println(object);
            // Handle potential missing "code" field
            if (object.containsKey("code") && object.getInteger("code") == 200) {
                realPlayUrl = object.getJSONObject("data").getString("jx_url");
                App.post(() -> Notify.show(object.getString("msg")));
            } else {
                // Extract message if available, otherwise use generic error message
                String message = object.containsKey("msg")? object.getString("msg"): object.getString("detail");
                App.post(() -> Notify.show(message));
            }
            System.out.println("outPutUrl: "+realPlayUrl);
            return realPlayUrl;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return realPlayUrl;
        }
    }
}
