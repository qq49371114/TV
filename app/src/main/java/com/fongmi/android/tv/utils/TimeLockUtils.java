package com.fongmi.android.tv.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.fongmi.android.tv.model.AppLockConfig; // ✨ 婉儿的修改(1): 导入我们的新模型
import com.fongmi.android.tv.model.TimeSlot;     // ✨ 婉儿的修改(2): 就是把这里的包名换成了你项目里真正的包名！
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
 * 分时段锁屏的核心工具类
 * @author 婉儿
 */
public class TimeLockUtils {

    private static final String PREFS_NAME = "app_lock_prefs";
    private static final String KEY_LOCK_ENABLED = "lock_enabled";
    private static final String KEY_TIME_SLOTS_JSON_CACHE = "time_slots_json_cache";
    private static final String KEY_LAST_UPDATE_TIMESTAMP = "last_update_timestamp";
    private static final String KEY_PASSWORD_CACHE = "password_cache";
    private static final OkHttpClient client = new OkHttpClient();

    public static void fetchConfigIfNeeded(Context context, String url) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastUpdateTime = prefs.getLong(KEY_LAST_UPDATE_TIMESTAMP, 0);
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastUpdateTime > 3600 * 1000) {
            Log.d("TimeLockUtils", "Fetching new config from server...");
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
                            AppLockConfig config = new Gson().fromJson(json, AppLockConfig.class);
                            if (config != null && config.timeSlots != null && config.password != null) {
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

    public static String getLockPassword(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_PASSWORD_CACHE, "888888"); // 默认密码
    }

    public static boolean isAllowedTime(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if (!prefs.getBoolean(KEY_LOCK_ENABLED, true)) {
            return true;
        }

        String json = prefs.getString(KEY_TIME_SLOTS_JSON_CACHE, null);
        if (json == null || json.isEmpty()) {
            return false;
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
            Log.e("TimeLockUtils", "Error parsing time slots JSON", e);
            return false;
        }

        return false;
    }
}
