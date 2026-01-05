package com.fongmi.android.tv.player;

import android.content.Context;
import android.content.SharedPreferences;
import com.fongmi.android.tv.App;

public class AdSwitch {
    private static final String PREFS_NAME = "ad_switch_prefs";
    private static final String KEY_USER_INPUT_CODE = "user_input_code";
    private static final String MASTER_KEY = "phoenix-2024";

    private static class Loader { static volatile AdSwitch INSTANCE = new AdSwitch(); }
    public static AdSwitch get() { return Loader.INSTANCE; }

    private SharedPreferences prefs;
    private volatile boolean isInitialized = false;

    private AdSwitch() {}

    public void init(Context context) {
        if (isInitialized) return;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.isInitialized = true;
    }

    public boolean isOn() {
        if (!isInitialized) return false;
        String userInputCode = prefs.getString(KEY_USER_INPUT_CODE, "");
        return userInputCode.equals(MASTER_KEY);
    }

    public void saveUserCode(String activationCode) {
        if (!isInitialized) return;
        prefs.edit().putString(KEY_USER_INPUT_CODE, activationCode).apply();
    }
}
