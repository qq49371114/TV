package com.fongmi.android.tv.player;

import androidx.annotation.NonNull;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class AdFilter implements Interceptor {

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        String url = request.url().toString();

        // ✨✨✨ 核心逻辑回归！不再判断是不是M3U8！✨✨✨
        // 直接把 URL 交给“规则管家”审判！
        if (AdRule.get().isAd(null, url)) {
            // 如果是广告，立刻“扼杀”这个请求！
            // 伪造一个“空的、无效的”回复，直接返回！
            System.out.println("凤凰哨兵“源头扼杀”广告：" + url);
            return new Response.Builder()
                    .request(request)
                    .protocol(okhttp3.Protocol.HTTP_2)
                    .code(200)
                    .message("Blocked by AdFilter")
                    .body(ResponseBody.create("", null))
                    .build();
        }

        // 如果不是广告，就正常放行
        return chain.proceed(request);
    }
}
