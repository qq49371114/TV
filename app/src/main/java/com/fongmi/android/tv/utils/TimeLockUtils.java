package com.fongmi.android.tv.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.fongmi.android.tv.model.AppLockConfig;
import com.fongmi.android.tv.model.TimeSlot;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 分时段锁屏的核心工具类 (云端同步版)
 * @author 婉儿
 */
public class TimeLockUtils {

    private static final String PREFS_NAME = "app_lock_prefs";
    private static final String KEY_LOCK_ENABLED = "lock_enabled";
    private static final String KEY_TIME_SLOTS_JSON_CACHE = "time_slots_json_cache";
    private static final String KEY_LAST_UPDATE_TIMESTAMP = "last_update_timestamp";
    private static final String KEY_PASSWORD_CACHE = "password_cache";
    private static final String KEY_CONFIG_URL = "config_url"; // ✨ 我们上次说好要加的URL存储key
    private static final OkHttpClient client = new OkHttpClient();

    /**
     * 智能地从服务器获取配置（如果需要的话）
     */
    public static void fetchConfigIfNeeded(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String url = getConfigUrl(context); // ✨ 从小本本里读取URL
        if (url.isEmpty()) {
            Log.d("TimeLockUtils", "Config URL is empty, skipping fetch.");
            return;
        }

        long lastUpdateTime = prefs.getLong(KEY_LAST_UPDATE_TIMESTAMP, 0);
        long currentTime = System.currentTimeMillis();

        // 距离上次成功更新超过1小时，才再次请求
        if (currentTime - lastUpdateTime > 3600 * 1000) {
            Log.d("TimeLockUtils", "Fetching new config from: " + url);
            Request request = new Request.Builder().url(url).build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e("TimeLockUtils", "Failed to fetch config: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        try {
                            // ✨ 用新的模型来解析JSON
                            AppLockConfig config = new Gson().fromJson(json, AppLockConfig.class);
                            if (config != null && config.timeSlots != null && config.password != null) {
                                // ✨ 把密码和时间段列表分别缓存起来
                                prefs.edit()
                                     .putString(KEY_TIME_SLOTS_JSON_CACHE, new Gson().toJson(config.timeSlots))
                                     .putString(KEY_PASSWORD_CACHE, config.password)
                                     .putLong(KEY_LAST_UPDATE_TIMESTAMP, System.currentTimeMillis())
                                     .apply();
                                Log.d("TimeLockUtils", "Config updated successfully!");
                            }
                        } catch (Exception e) {
                            Log.e("TimeLockUtils", "Error parsing remote config JSON", e);
                        }
                    }
                }
            });
        } else {
            Log.d("TimeLockUtils", "Config is fresh, using cache.");
        }
    }

    /**
     * 保存远程配置的URL地址
     */
    public static void saveConfigUrl(Context context, String url) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_CONFIG_URL, url).apply();
    }

    /**
     * 读取保存的URL地址
     */
    public static String getConfigUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_CONFIG_URL, "");
    }

    /**
     * 获取缓存的解锁密码
     */
    public static String getLockPassword(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_PASSWORD_CACHE, "888888"); // 提供一个默认密码
    }

    /**
     * 判断当前时间是否在任何一个允许的时间段内
     */
    public static boolean isAllowedTime(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if (!prefs.getBoolean(KEY_LOCK_ENABLED, true)) {
            return true; // 如果总开关是关闭的，永远允许
        }

        String json = prefs.getString(KEY_TIME_SLOTS_JSON_CACHE, null);
        if (json == null || json.isEmpty()) {
            return false; // 如果没有任何配置缓存，默认是锁定的
        }

        try {
            Type type = new TypeToken<ArrayList<TimeSlot>>() {}.getType();
            List<TimeSlot> allowedSlots = new Gson().fromJson(json, type);

            if (allowedSlots == null || allowedSlots.isEmpty()) {
                return false;
            }

            Calendar current = Calendar.getInstance();
            int currentTimeInMinutes = current.get(Calendar.HOUR_OF_DAY) * 60 + current.get(Calendar.MINUTE);

            for (TimeSlot slot : allowedSlots) {
                int startTimeInMinutes = slot.startHour * 60 + slot.startMinute;
                int endTimeInMinutes = slot.endHour * 60 + slot.endMinute;

                boolean isWithinSlot;
                if (startTimeInMinutes > endTimeInMinutes) { // 跨天
                    isWithinSlot = currentTimeInMinutes >= startTimeInMinutes || currentTimeInMinutes < endTimeInMinutes;
                } else { // 不跨天
                    isWithinSlot = currentTimeInMinutes >= startTimeInMinutes && currentTimeInMinutes < endTimeInMinutes;
                }

                if (isWithinSlot) {
                    return true; // 找到一个匹配的，立刻放行
                }
            }
        } catch (Exception e) {
            Log.e("TimeLockUtils", "Error parsing time slots JSON", e);
            return false; // JSON解析失败，安全起见，也锁定
        }

        return false; // 所有时间段都不匹配，禁止通行
    }
}
