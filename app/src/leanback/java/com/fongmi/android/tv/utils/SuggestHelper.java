package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Word;
import com.github.catvod.net.OkHttp;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class SuggestHelper {

    // --- 爱奇艺引擎 (主引擎)：获取相关建议词 ---
    public static void getSuggestions(String keyword, Consumer<List<Word.Data>> callback) {
        try {
            // 我们直接在这里调用ZhuToPin，让这个工具类完全独立
            String encodedKeyword = URLEncoder.encode(ZhuToPin.get(keyword), "UTF-8");
            String url = "https://suggest.video.iqiyi.com/?if=mobile&key=" + encodedKeyword;
            
            OkHttp.newCall(url).enqueue(new Callback() {
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String result = response.body().string();
                    if (TextUtils.isEmpty(result)) {
                        App.post(() -> callback.accept(Collections.emptyList()));
                        return;
                    }
                    List<Word.Data> suggestions = Word.objectFrom(result).getData();
                    App.post(() -> callback.accept(suggestions));
                }

                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    App.post(() -> callback.accept(Collections.emptyList()));
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            App.post(() -> callback.accept(Collections.emptyList()));
        }
    }

    // --- 360引擎 (辅助引擎)：获取热门排行榜 ---
    public static void getHot(Consumer<List<Word.Data>> callback) {
        try {
            String url = "https://api.web.360kan.com/v1/rank?cat=1";
            Map<String, String> headers = Map.of(HttpHeaders.REFERER, "https://www.360kan.com/rank/general");
            
            OkHttp.newCall(url, headers).enqueue(new Callback() {
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String result = response.body().string();
                    if (TextUtils.isEmpty(result)) {
                        App.post(() -> callback.accept(Collections.emptyList()));
                        return;
                    }
                    List<Word.Data> hotWords = Word.objectFrom(result).getData();
                    App.post(() -> callback.accept(hotWords));
                }

                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    App.post(() -> callback.accept(Collections.emptyList()));
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            App.post(() -> callback.accept(Collections.emptyList()));
        }
    }
}
