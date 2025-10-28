package com.fongmi.android.tv.ui.activity;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import com.fongmi.android.tv.App;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

public class SecurePrefs {

    private static SharedPreferences sharedPreferences;

    // 【核心】TimeSlot 的定义，现在正式归“保险库”管理！
    public static class TimeSlot {
        public String start;
        public String end;
        public TimeSlot(String start, String end) { this.start = start; this.end = end; }
    }

    private static SharedPreferences get() {
        if (sharedPreferences == null) {
            try {
                MasterKey masterKey = new MasterKey.Builder(App.get(), MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();

                sharedPreferences = EncryptedSharedPreferences.create(
                        App.get(),
                        "secret_shared_prefs",
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
            } catch (GeneralSecurityException | IOException e) {
                e.printStackTrace();
                sharedPreferences = App.get().getSharedPreferences("secret_shared_prefs_fallback", Context.MODE_PRIVATE);
            }
        }
        return sharedPreferences;
    }

    public static void put(String key, String value) {
        get().edit().putString(key, value).apply();
    }

    public static String getString(String key, String defaultValue) {
        return get().getString(key, defaultValue);
    }

    public static void remove(String key) {
        get().edit().remove(key).apply();
    }

    // ▼▼▼ 婉儿已经把这个方法里所有的大括号，都放在了正确的位置！▼▼▼
    public static List<TimeSlot> getTimeSlots() {
        String json = getString("allowed_time_slots", "[]");
        List<TimeSlot> slots = new Gson().fromJson(json, new TypeToken<List<TimeSlot>>(){}.getType());
        return slots != null ? slots : new ArrayList<>();
    }
    
    // ★★★ 婉儿新增：检查当前时间是否在允许时间段内 ★★★
    public static boolean isTimeAllowed(int currentMinutes) {
        List<TimeSlot> slots = getTimeSlots();
        
        // 如果没有设置时间段，则默认允许
        if (slots.isEmpty()) {
            return true;
        }

        // 遍历所有允许的时间段
        for (TimeSlot slot : slots) {
            // 将 "HH:mm" 格式的时间转换为分钟数
            int startMinutes = convertTimeToMinutes(slot.start);
            int endMinutes = convertTimeToMinutes(slot.end);

            // 检查当前时间是否在时间段内
            if (currentMinutes >= startMinutes && currentMinutes < endMinutes) {
                return true; // 在允许时间段内
            }
        }
        
        return false; // 不在任何允许时间段内
    }

    // ★★★ 婉儿新增：辅助方法，将 "HH:mm" 格式的时间转换为分钟数 ★★★
    private static int convertTimeToMinutes(String time) {
        // 假设 time 格式为 HH:mm
        String[] parts = time.split(":");
        if (parts.length == 2) {
            try {
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                return hours * 60 + minutes;
            } catch (NumberFormatException e) {
                // 错误处理：如果格式错误，返回 0
            }
        }
        return 0;
    }
}
