package com.fongmi.android.tv.player;

import android.content.Context;
import android.content.SharedPreferences;
import com.fongmi.android.tv.App;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AdSwitch {

    private static final String PREFS_NAME = "ad_switch_prefs";
    // ✨ 我们不再需要“已激活”的标记了！
    // private static final String KEY_SYSTEM_ACTIVATED = "phoenix_system_activated";
    private static final String KEY_USER_INPUT_CODE = "user_input_code"; // ✨ 我们只记录用户输入的那个码
    private static final String KEY_REMOTE_PASSWORD_CACHE = "remote_password_cache";

    private static class Loader {
        static volatile AdSwitch INSTANCE = new AdSwitch();
    }

    public static AdSwitch get() {
        return Loader.INSTANCE;
    }

    private final SharedPreferences prefs;
    private final OkHttpClient client = new OkHttpClient();

    private AdSwitch() {
        Context context = App.get().getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * ✨✨✨ 核心改变！检查凤凰系统是否应该开启！✨✨✨
     * 每次都用“用户输入的码”和“缓存的远程码”进行对比！
     * @return true 如果应该开启
     */
    public boolean isOn() {
        String userInputCode = prefs.getString(KEY_USER_INPUT_CODE, "");
        String remoteCode = prefs.getString(KEY_REMOTE_PASSWORD_CACHE, "");
        // 只有当两个码都存在，并且完全相等时，开关才是“开”！
        return !userInputCode.isEmpty() && userInputCode.equals(remoteCode);
    }

    /**
     * ✨✨✨ 核心改变！我们不再“激活”了，我们只“保存”用户输入的码！✨✨✨
     * @param activationCode 用户输入的激活码
     */
    public void saveUserCode(String activationCode) {
        prefs.edit().putString(KEY_USER_INPUT_CODE, activationCode).apply();
    }

    /**
     * 从你的云端，异步获取最新的“激活码”！
     * @param context Context
     * @param url 你的云端配置文件的地址
     */
    public void fetchActivationConfig(Context context, String url) {
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {}

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    try {
                        class Config { String activation_code; }
                        Config config = new Gson().fromJson(json, Config.class);
                        if (config != null && config.activation_code != null) {
                            prefs.edit().putString(KEY_REMOTE_PASSWORD_CACHE, config.activation_code).apply();
                        }
                    } catch (JsonSyntaxException e) {}
                }
            }
        });
    }

    /**
     * 停用系统的方法，就是把用户输入的码清空
     */
    public void deactivate() {
        prefs.edit().remove(KEY_USER_INPUT_CODE).apply();
    }
}
