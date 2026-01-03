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
 * 分时段锁屏的核心工具类 (最终“默认+定制”版)
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

    // ✨↓ 婉儿帮你把你的云端地址，直接内置在这里啦！↓✨
    private static final String DEFAULT_CONFIG_URL = "http://47.109.61.116:86/apk/app_lock_config.json";

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
        String customUrl = getConfigUrl(context);
        String finalUrl = customUrl.isEmpty() ? DEFAULT_CONFIG_URL : customUrl;
        showToast(context, "开始强制同步远程配置...");
        fetchFromServer(context, finalUrl);
    }

    public static void fetchConfigIfNeeded(Context context) {
        // ✨↓ 我们先在这里加上内置的默认地址！↓✨
        final String DEFAULT_CONFIG_URL = "http://47.109.61.116:86/apk/app_lock_config.json";

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // 1. 先去“小本本”里找用户自己设置的地址
        String customUrl = getConfigUrl(context);
        // 2. 如果用户没设置，我们就用内置的默认地址！
        String finalUrl = customUrl.isEmpty() ? DEFAULT_CONFIG_URL : customUrl;

        long lastUpdateTime = prefs.getLong(KEY_LAST_UPDATE_TIMESTAMP, 0);
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastUpdateTime > 3600 * 1000) { // 仍然是超过1小时才自动同步
            fetchFromServer(context, finalUrl); // 使用我们最终决定好的地址！
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

    public static boolean isConfigReady(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String jsonCache = prefs.getString(KEY_TIME_SLOTS_JSON_CACHE, "");
        return !jsonCache.isEmpty();
    }

    public static String getTodayAllowedSlotsText(Context context) {
    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    String json = prefs.getString(KEY_TIME_SLOTS_JSON_CACHE, null);
    if (json == null) return "允许时段：未设置";

    try {
        Type type = new TypeToken<ArrayList<TimeSlot>>() {}.getType();
        List<TimeSlot> allSlots = new Gson().fromJson(json, type);

        if (allSlots == null || allSlots.isEmpty()) {
            return "允许时段：未设置";
        }

        Calendar current = Calendar.getInstance();
        int dayOfWeek = current.get(Calendar.DAY_OF_WEEK);
        int ourDayOfWeek = (dayOfWeek == Calendar.SUNDAY) ? 0 : dayOfWeek - 1;

        StringBuilder sb = new StringBuilder();
        for (TimeSlot slot : allSlots) {
            if (slot.getDays() != null && slot.getDays().contains(ourDayOfWeek)) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                // 格式化时间，保证是两位数，比如 08:05
                String startTime = String.format("%02d:%02d", slot.getStartHour(), slot.getStartMinute());
                String endTime = String.format("%02d:%02d", slot.getEndHour(), slot.getEndMinute());
                sb.append(startTime).append("-").append(endTime);
            }
        }

        if (sb.length() == 0) {
            return "今天没有允许的时段";
        } else {
            return "允许时段：" + sb.toString();
        }
    } catch (Exception e) {
        return "允许时段：规则解析错误";
    }
}

    public static boolean isAllowedTime(Context context) {
    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

    if (!prefs.getBoolean(KEY_LOCK_ENABLED, true)) {
        return true;
    }

    if (!isConfigReady(context)) {
        return false;
    }

    String json = prefs.getString(KEY_TIME_SLOTS_JSON_CACHE, null);
    try {
        // --- 核心修改1：我们的数据模型现在是 TimeSlot ---
        Type type = new TypeToken<ArrayList<TimeSlot>>() {}.getType();
        List<TimeSlot> allowedSlots = new Gson().fromJson(json, type);

        if (allowedSlots == null || allowedSlots.isEmpty()) {
            return false;
        }

        Calendar current = Calendar.getInstance();
        
        // --- 核心修改2：获取今天的星期 ---
        // Java的Calendar里，周日是1，周一是2...周六是7。我们需要转换一下。
        int dayOfWeek = current.get(Calendar.DAY_OF_WEEK);
        // 转换为我们约定的：周一=1, 周二=2, ..., 周六=6, 周日=0
        int ourDayOfWeek = (dayOfWeek == Calendar.SUNDAY) ? 0 : dayOfWeek - 1;

        int currentTimeInMinutes = current.get(Calendar.HOUR_OF_DAY) * 60 + current.get(Calendar.MINUTE);

        for (TimeSlot slot : allowedSlots) {
            // --- 核心修改3：进行“双重匹配”！---
            
            // 1. 先判断星期匹不匹配
            if (slot.getDays() == null || !slot.getDays().contains(ourDayOfWeek)) {
                continue; // 如果这条规则不包含今天，就直接跳过，看下一条规则
            }

            // 2. 如果星期匹配上了，再判断时间
            int startTimeInMinutes = slot.getStartHour() * 60 + slot.getStartMinute();
            int endTimeInMinutes = slot.getEndHour() * 60 + slot.getEndMinute();

            boolean isWithinSlot;
            if (startTimeInMinutes > endTimeInMinutes) { // 跨天时间段
                isWithinSlot = currentTimeInMinutes >= startTimeInMinutes || currentTimeInMinutes < endTimeInMinutes;
            } else {
                isWithinSlot = currentTimeInMinutes >= startTimeInMinutes && currentTimeInMinutes < endTimeInMinutes;
            }

            if (isWithinSlot) {
                return true; // 只要找到任何一条完全匹配的规则，就立刻返回true！
            }
        }
    } catch (Exception e) {
        showToast(context, "错误：解析时间段JSON失败！");
        return false;
    }

    return false; // 如果遍历完所有规则，都没找到匹配的，就返回false
  }
}
