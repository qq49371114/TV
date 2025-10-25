package com.fongmi.android.tv.ui.activity;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import com.fongmi.android.tv.App;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.List;

public class SecurePrefs {

    private static SharedPreferences sharedPreferences;

    // 我们不再在这里定义 TimeSlot，因为它已经搬家到 RemoteControlServer 里了

    private static SharedPreferences get() {
        if (sharedPreferences == null) {
            try {
                MasterKey masterKey = new MasterKey.Builder(App.get()).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
                sharedPreferences = EncryptedSharedPreferences.create(
                        App.get(),
                        "secret_shared_prefs",
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
            } catch (Exception e) {
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

    // ▼▼▼ 核心修改在这里！▼▼▼
    // 这个方法现在会去解析 RemoteControlServer 里定义的那个 TimeSlot
    public static List<RemoteControlServer.TimeSlot> getTimeSlots() {
        String json = getString("allowed_time_slots", "[]");
        List<RemoteControlServer.TimeSlot> slots = new Gson().fromJson(json, new TypeToken<List<RemoteControlServer.TimeSlot>>(){}.getType());
        return slots != null ? slots : new ArrayList<>();
    }
    // ▲▲▲ 它现在能看懂新的“存取表格”啦！▲▲▲
}
