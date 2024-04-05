package com.fongmi.android.tv.utils;

import android.os.Build;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.github.catvod.utils.Path;
import com.orhanobut.logger.Logger;
import com.tencent.smtt.sdk.QbSdk;
import com.tencent.smtt.sdk.TbsListener;
import com.tencent.smtt.export.external.TbsCoreSettings;

import java.io.File;
import java.util.HashMap;

public class Tbs {
    private static final String TAG = Tbs.class.getSimpleName();

    public static final String URL64 = "https://tmf-pkg-1314481471.cos.ap-shanghai.myqcloud.com/x5/64/46471/tbs_core_046471_20230809100104_nolog_fs_obfs_arm64-v8a_release.tbs";
    public static final String URL32 = "https://tmf-pkg-1314481471.cos.ap-shanghai.myqcloud.com/x5/32/46471/tbs_core_046471_20230809095840_nolog_fs_obfs_armeabi_release.tbs";

    private static boolean isCpu64Bit() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if (abi.contains("64")) return true;
        }
        return false;
    }

    public static String getUrl() {
        return isCpu64Bit() ? URL64 : URL32;
    }

    private static void tbsInit() {
        HashMap map = new HashMap();
        map.put(TbsCoreSettings.TBS_SETTINGS_USE_PRIVATE_CLASSLOADER, true);
        QbSdk.initTbsSettings(map);
        QbSdk.setDownloadWithoutWifi(false);
        QbSdk.setCoreMinVersion(QbSdk.CORE_VER_ENABLE_202207);
        TbsListener tbsListener = new TbsListener() {

            /**
             * @param stateCode 用户可处理错误码请参考{@link com.tencent.smtt.sdk.TbsCommonCode}
             */
            @Override
            public void onDownloadFinish(int stateCode) {
                Logger.t(TAG).d("onDownloadFinish:" + stateCode);
            }

            /**
             * @param stateCode 用户可处理错误码请参考{@link com.tencent.smtt.sdk.TbsCommonCode}
             */
            @Override
            public void onInstallFinish(int stateCode) {
                Logger.t(TAG).d("onInstallFinish:" + stateCode);
            }

            /**
             * 首次安装应用，会触发内核下载，此时会有内核下载的进度回调。
             * @param progress 0 - 100
             */
            @Override
            public void onDownloadProgress(int progress) {
                Logger.t(TAG).d("onDownloadProgress:" + progress);
            }
        };
        QbSdk.PreInitCallback callback = new QbSdk.PreInitCallback() {
            @Override
            public void onViewInitFinished(boolean finished) {
                if (finished) Notify.show(R.string.x5webview_enabled);
            }

            @Override
            public void onCoreInitFinished() {
            }
        };
        QbSdk.setTbsListener(tbsListener);
        QbSdk.initX5Environment(App.get(), callback);
    }

    public static void init() {
        if (Setting.getParseWebView() == 0) return;
        App.post(() -> tbsInit());
    }

    public static String url() {
        return getUrl();
    }

    private static File tbs() {
        File tbsDir = Path.files("tbs");
        return tbsDir;
    }

    public static File file() {
        File tbsDir = tbs();
        if (!tbsDir.exists()) tbsDir.mkdirs();
        File x5 = new File(tbsDir, "x5.apk");
        return x5;
    }

    public static void download() {
        QbSdk.installLocalQbApk(App.get(), "46471", file().getAbsolutePath(), null);
    }

}
