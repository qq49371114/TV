package com.fongmi.quickjs.crawler;

import com.whl.quickjs.android.QuickJSLoader;

import dalvik.system.DexClassLoader;

public class Loader {

    public Loader() {
        QuickJSLoader.init();
    }

    public Spider spider(String key, String api, DexClassLoader dex) {
        return new Spider(key, api, dex);
    }
}
