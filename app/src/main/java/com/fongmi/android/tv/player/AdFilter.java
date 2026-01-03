package com.fongmi.android.tv.player;

import androidx.annotation.NonNull;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class AdFilter implements Interceptor {
    @NonNull @Override public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        String url = request.url().toString();
        if (AdRule.get().isAd(null, url)) {
            System.out.println("凤凰哨兵“源头扼杀”广告：" + url);
            return new Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_2).code(200).message("Blocked").body(ResponseBody.create("", null)).build();
        }
        return chain.proceed(request);
    }
}
