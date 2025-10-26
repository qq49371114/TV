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

    // 【核心修改】TimeSlot 的定义，现在正式归“保险库”管理！
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
                        "secret_shared_prefs", // 加密后的文件名
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
            } catch (GeneralSecurityException | IOException e) {
                e.printStackTrace();
                // 如果加密初始化失败，就退回到普通模式，保证App最起码还能用
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

    // 【核心修改】这个方法现在返回的是自己内部定义的 TimeSlot
    public static List<TimeSlot> getTimeSlots() {
        String json = getString("allowed_time_slots", "[]");
        List<TimeSlot> slots = new Gson().fromJson(json, new TypeToken<List<TimeSlot>>(){}.getType());
        return slots != null ? slots : new ArrayList<>();
    }
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

    // ▼▼▼ 核心修改在这里！▼▼▼
    // 这个方法现在会去解析 RemoteControlServer 里定义的那个 TimeSlot
    public static List<RemoteControlServer.TimeSlot> getTimeSlots() {
        String json = getString("allowed_time_slots", "[]");
        List<RemoteControlServer.TimeSlot> slots = new Gson().fromJson(json, new TypeToken<List<RemoteControlServer.TimeSlot>>(){}.getType());
        return slots != null ? slots : new ArrayList<>();
    }
    // ▲▲▲ 它现在能看懂新的“存取表格”啦！▲▲▲
}
