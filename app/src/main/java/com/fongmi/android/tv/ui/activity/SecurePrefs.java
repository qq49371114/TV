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
}
