package com.fongmi.android.tv.api.config;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.text.TextUtils;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.utils.Path;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InterruptedIOException;
import java.util.concurrent.Future;

public class WallConfig {

    private Config config;
    private Future<?> future;
    private boolean sync;

    private static class Loader {
        static volatile WallConfig INSTANCE = new WallConfig();
    }

    public static WallConfig get() {
        return Loader.INSTANCE;
    }

    public static String getUrl() {
        return get().getConfig().getUrl();
    }

    public static String getDesc() {
        return get().getConfig().getDesc();
    }

    public static void load(Config config, Callback callback) {
        get().config(config).load(callback);
    }

    public WallConfig init() {
        return config(Config.wall());
    }

    public WallConfig config(Config config) {
        this.config = config;
        if (config.isEmpty()) return this;
        this.sync = config.getUrl().equals(VodConfig.get().getWall());
        return this;
    }

    private boolean isCanceled(Throwable e) {
        return e.getCause() instanceof InterruptedException || e.getCause() instanceof InterruptedIOException;
    }

    public void load() {
        load(new Callback());
    }

    public void load(Callback callback) {
        if (future != null && !future.isDone()) future.cancel(true);
        future = App.submit(() -> loadConfig(callback));
        callback.start();
    }

    private void loadConfig(Callback callback) {
        try {
            download();
            config.update();
            RefreshEvent.wall();
            App.post(callback::success);
        } catch (Throwable e) {
            if (isCanceled(e)) return;
            if (TextUtils.isEmpty(config.getUrl())) App.post(() -> callback.error(""));
            else App.post(() -> callback.error(Notify.getError(R.string.error_config_get, e)));
            Setting.putWall(1);
            RefreshEvent.wall();
            e.printStackTrace();
        }
    }

    private void download() throws Throwable {
        File file = FileUtil.getWall(0);
        Path.clear(FileUtil.getWallCache());
        if (getUrl().startsWith("file")) Path.copy(Path.local(getUrl()), file);
        else Download.create(UrlUtil.convert(getUrl()), file).start();
        if (!Path.exists(file)) throw new FileNotFoundException();
        createSnapshot(file);
        Setting.putWallType(0);
        if (isGif(file)) Setting.putWallType(1);
        else if (isVideo(file)) Setting.putWallType(2);
    }

    private void createSnapshot(File file) throws Throwable {
        Bitmap bitmap = Glide.with(App.get()).asBitmap().frame(0).load(file).override(ResUtil.getScreenWidth(), ResUtil.getScreenHeight()).skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE).submit().get();
        try (FileOutputStream fos = new FileOutputStream(FileUtil.getWallCache())) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        }
    }

    private boolean isVideo(File file) {
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(file.getAbsolutePath());
            return "yes".equalsIgnoreCase(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isGif(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            return "image/gif".equals(options.outMimeType);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean needSync(String url) {
        return sync || TextUtils.isEmpty(config.getUrl()) || url.equals(config.getUrl());
    }

    public Config getConfig() {
        return config == null ? Config.wall() : config;
    }
}
