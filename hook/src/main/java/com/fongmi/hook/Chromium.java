package com.fongmi.hook;

import android.os.Looper;

import java.util.Arrays;
import java.util.Set;

public class Chromium {

    public static final String PKG = "com.android.chrome";

    private static final Set<String> CHROMIUM_CLASS_NAMES = Set.of(
            "org.chromium.base.buildinfo",
            "org.chromium.base.apkinfo"
    );

    private static final Set<String> CHROMIUM_METHOD_NAMES = Set.of(
            "getall",
            "getpackagename",
            "<init>"
    );

    public static boolean find() {
        try {
            return Arrays.stream(Looper.getMainLooper().getThread().getStackTrace()).anyMatch(trace -> CHROMIUM_CLASS_NAMES.contains(trace.getClassName().toLowerCase()) && CHROMIUM_METHOD_NAMES.contains(trace.getMethodName().toLowerCase()));
        } catch (Exception e) {
            return false;
        }
    }
}
