package com.fongmi.android.tv.utils;

import android.content.Context;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.server.Server;
import com.github.catvod.utils.Path;

import org.xwalk.core.XWalkInitializer;
import java.io.File;
import java.lang.reflect.Method;

public class Xwalk {
    private static XWalkInitializer xWalkInitializer;
    public static boolean inited;

    public static String url() {
        return Server.get().getAddress("XWalkRuntimeLib.apk");
    }

    public static File file() {
        return Path.cache("XWalkRuntimeLib.apk");
    }

    private static String apk() {
        return file().getAbsolutePath();
    }

    private static String lib() {
        return App.get().getDir("extracted_xwalkcore", Context.MODE_PRIVATE).getAbsolutePath();
    }

    private static String[] libFiles() {
        String[] libFiles = new String[]{
                "classes.dex",
                "icudtl.dat",
                "libxwalkcore.so",
                "xwalk.pak",
                "xwalk_100_percent.pak"
        };
        return libFiles;
    }

    public static void remove() {
        File apk = file();
        if (apk.exists()) apk.delete();
        String dir = lib();
        for (String lib : libFiles()) {
            File file = new File(dir + "/" + lib);
            if (file.exists()) file.delete();
        }
    }

    public static boolean extract() {
        try {
            Class cls = Class.forName("org.xwalk.core.XWalkDecompressor");
            Method method = cls.getMethod("extractResource", String.class, String.class);
            return (boolean) method.invoke(null, apk(), lib());
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean exist() {
        String dir = lib();
        for (String lib : libFiles()) {
            if (!new File(dir + "/" + lib).exists()) {
                return false;
            }
        }
        return true;
    }

    public static void init() {
        if (!exist()) return;
        if (xWalkInitializer == null) {
            xWalkInitializer = new XWalkInitializer(new XWalkInitializer.XWalkInitListener() {
                @Override
                public void onXWalkInitStarted() {

                }

                @Override
                public void onXWalkInitCancelled() {

                }

                @Override
                public void onXWalkInitFailed() {
                    inited = false;
                }

                @Override
                public void onXWalkInitCompleted() {
                    Notify.show(ResUtil.getString(R.string.x5webview_enabled));
                    inited = true;
                }
            }, App.get());
        }
        if (!xWalkInitializer.isXWalkReady()) xWalkInitializer.initAsync();
    }

}
