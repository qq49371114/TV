package com.fongmi.android.tv.utils;

import static com.github.catvod.net.OkHttp.isUrlReachable;

import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.Util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class Jx {

    public static String getUrl(String jxToken, String realPlayUrl, Map<String, String> header) {
        try {
            String jxUrl = Prefers.getString("jxUrl");
            if (jxUrl.isEmpty() || jxToken.isEmpty()) return realPlayUrl;
            if (!isUrlReachable(CustomUtil.CHECK_URL, 1000)) return realPlayUrl;
            jxUrl = String.format(jxUrl, jxToken, URLEncoder.encode(realPlayUrl, "UTF-8"));
            System.out.println("APP - jxUrl: "+jxUrl);
            System.out.println("APP - header: "+header.toString());
            String response = OkHttp.string(jxUrl, header);
            System.out.println("APP - 接口返回: " + response);
            if (response.isEmpty()) {
                System.out.println("APP - 解析服务返回空, 不处理!");
                return realPlayUrl;
            }
            com.alibaba.fastjson.JSONObject object = com.alibaba.fastjson.JSONObject.parseObject(response);
            // Handle potential missing "code" field
            if (object.containsKey("code") && object.getInteger("code") == 200) {
                realPlayUrl = object.getJSONObject("data").getString("jx_url");
                App.post(() -> Notify.show(object.getString("msg")));
            } else {
                // Extract message if available, otherwise use generic error message
                String message = object.containsKey("msg")? object.getString("msg"): "";
                App.post(() -> Notify.show(message));
            }
            return realPlayUrl;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return realPlayUrl;
        }
    }
}
