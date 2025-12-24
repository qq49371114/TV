package com.fongmi.android.tv.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.fongmi.android.tv.model.TimeSlot;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
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
 * 分时段锁屏的核心工具类 (最终安全版)
 * @author 婉儿
 */
public class TimeLockUtils {

    private static final String PREFS_NAME = "app_lock_prefs";
    private static final String KEY_LOCK_ENABLED = "lock_enabled";
    private static final String KEY_TIME_SLOTS_JSON_CACHE = "time_slots_json_cache";
    private static final String KEY_LAST_UPDATE_TIMESTAMP = "last_update_timestamp";
    private static final String KEY_PASSWORD_CACHE = "password_cache";
    private static final String KEY_CONFIG_URL = "config_url";
    private static final OkHttpClient client = new OkHttpClient();

    private static void showToast(Context context, String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_LONG).show();
        });
    }

    private static void fetchFromServer(Context context, String url) {
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                showToast(context, "同步失败：" + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    try {
                        JsonObject jsonObject = new Gson().fromJson(json, JsonObject.class);
                        if (jsonObject != null && jsonObject.has("password") && jsonObject.has("timeSlots")) {
                            String password = jsonObject.get("password").getAsString();
                            String timeSlotsJson = jsonObject.get("timeSlots").toString();
                            SharedPreferences innerPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                            innerPrefs.edit()
                                 .putString(KEY_TIME_SLOTS_JSON_CACHE, timeSlotsJson)
                                 .putString(KEY_PASSWORD_CACHE, password)
                                 .putLong(KEY_LAST_UPDATE_TIMESTAMP, System.currentTimeMillis())
                                 .apply();
                            showToast(context, "同步成功！新配置已缓存！");
                        } else {
                            showToast(context, "错误：JSON内容不完整！");
                        }
                    } catch (JsonSyntaxException e) {
                        showToast(context, "致命错误：JSON解析时崩溃！" + e.getMessage());
                    }
                } else {
                    showToast(context, "同步失败：服务器响应码 " + response.code());
                }
            }
        });
    }

    public static void forceFetchConfig(Context context) {
        String url = getConfigUrl(context);
        if (url.isEmpty()) {
            showToast(context, "错误：未在设置中输入远程URL！");
            return;
        }
        showToast(context, "开始强制同步远程配置...");
        fetchFromServer(context, url);
    }

    public static void fetchConfigIfNeeded(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastUpdateTime = prefs.getLong(KEY_LAST_UPDATE_TIMESTAMP, 0);
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime > 3600 * 1000) {
            String url = getConfigUrl(context);
            if (!url.isEmpty()) {
                fetchFromServer(context, url);
            }
        }
    }

    public static void saveConfigUrl(Context context, String url) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_CONFIG_URL, url).apply();
    }

    public static String getConfigUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_CONFIG_URL, "");
    }

    public static String getLockPassword(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_PASSWORD_CACHE, "888888");
    }

    /**
     * 判断当前时间是否在任何一个允许的时间段内 (最终安全版)
     */
    public static boolean isAllowedTime(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if (!prefs.getBoolean(KEY_LOCK_ENABLED, true)) {
            return true;
        }

        // ✨↓ 婉儿的最终修改就在这里！↓✨
        // 1. 检查URL是否已配置
        String url = getConfigUrl(context);
        if (url.isEmpty()) {
            return false; // 如果没有设置远程URL，就直接锁定！
        }

        // 2. 检查时间段缓存是否存在
        String json = prefs.getString(KEY_TIME_SLOTS_JSON_CACHE, null);
        if (json == null || json.isEmpty()) {
            return false; // 如果URL已设置，但还没有成功同步过数据，也直接锁定！
        }
        // ✨↑ 修改结束！↑✨

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
                if (startTimeInMinutes > endTimeInMinutes) {
                    isWithinSlot = currentTimeInMinutes >= startTimeInMinutes || currentTimeInMinutes < endTimeInMinutes;
                } else {
                    isWithinSlot = currentTimeInMinutes >= startTimeInMinutes && currentTimeInMinutes < endTimeInMinutes;
                }

                if (isWithinSlot) {
                    return true;
                }
            }
        } catch (Exception e) {
            showToast(context, "错误：解析时间段JSON失败！");
            return false;
        }

        return false;
    }
}
