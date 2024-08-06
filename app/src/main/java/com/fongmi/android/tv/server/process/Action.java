package com.fongmi.android.tv.server.process;

import android.os.Environment;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import fi.iki.elonen.NanoHTTPD;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class Action implements Process {

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String path) {
        return "/action".equals(path);
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String path, Map<String, String> files) {
        Map<String, String> params = session.getParms();
        switch (Objects.requireNonNullElse(params.get("do"), "")) {
            case "search":
                onSearch(params);
                return Nano.success();
            case "push":
                onPush(params);
                return Nano.success();
            case "setting":
                onSetting(params);
                return Nano.success();
            case "file":
                onFile(params);
                return Nano.success();
            case "refresh":
                onRefresh(params);
                return Nano.success();
            case "cast":
                onCast(params);
                return Nano.success();
            case "sync":
                onSync(params);
                return Nano.success();
            case "transmit":
                onTransmit(params, files);
                return Nano.success();
            default:
                return Nano.error(null);
        }
    }

    private void onSearch(Map<String, String> params) {
        String word = params.get("word");
        if (TextUtils.isEmpty(word)) return;
        ServerEvent.search(word);
    }

    private void onPush(Map<String, String> params) {
        String url = params.get("url");
        if (TextUtils.isEmpty(url)) return;
        ServerEvent.push(url);
    }

    private void onSetting(Map<String, String> params) {
        String text = params.get("text");
        String name = params.get("name");
        if (TextUtils.isEmpty(text)) return;
        ServerEvent.setting(text, name);
    }

    private void onFile(Map<String, String> params) {
        String path = params.get("path");
        if (TextUtils.isEmpty(path)) return;
        if (path.endsWith(".xml")) RefreshEvent.danmaku(path);
        else if (path.endsWith(".apk")) FileUtil.openFile(Path.local(path));
        else if (path.endsWith(".srt") || path.endsWith(".ssa") || path.endsWith(".ass")) RefreshEvent.subtitle(path);
        else ServerEvent.setting(path);
    }

    private void onRefresh(Map<String, String> params) {
        String type = params.get("type");
        String path = params.get("path");
        if (TextUtils.isEmpty(type)) return;
        switch (type) {
            case "detail":
                RefreshEvent.detail();
                break;
            case "player":
                RefreshEvent.player();
                break;
            case "danmaku":
                RefreshEvent.danmaku(path);
                break;
            case "subtitle":
                RefreshEvent.subtitle(path);
                break;
        }
    }

    private void onCast(Map<String, String> params) {
        Config config = Config.objectFrom(params.get("config"));
        Device device = Device.objectFrom(params.get("device"));
        History history = History.objectFrom(params.get("history"));
        CastEvent.post(Config.find(config), device, history);
    }

    private void onSync(Map<String, String> params) {
        boolean keep = Objects.equals(params.get("type"), "keep");
        boolean force = Objects.equals(params.get("force"), "true");
        boolean history = Objects.equals(params.get("type"), "history");
        String mode = Objects.requireNonNullElse(params.get("mode"), "0");
        if (params.get("device") != null && (mode.equals("0") || mode.equals("2"))) {
            Device device = Device.objectFrom(params.get("device"));
            if (history) sendHistory(device, params);
            else if (keep) sendKeep(device);
        }
        if (mode.equals("0") || mode.equals("1")) {
            if (history) syncHistory(params, force);
            else if (keep) syncKeep(params, force);
        }
    }

    private void onTransmit(Map<String, String> params, Map<String, String> files) {
        String type = params.get("type");
        switch (type) {
            case "apk":
                apk(params, files);
                break;
            case "vod_config":
                vodConfig(params);
                break;
            case "wall_config":
                wallConfig(params, files);
                break;
            case "push_restore":
                pushRestore(params, files);
                break;
            case "pull_restore":
                pullRestore(params, files);
                break;
            default:
                break;
        }
    }

    private void sendHistory(Device device, Map<String, String> params) {
        try {
            Config config = Config.find(Config.objectFrom(params.get("config")));
            FormBody.Builder body = new FormBody.Builder();
            body.add("config", config.toString());
            body.add("targets", App.gson().toJson(History.get(config.getId())));
            OkHttp.newCall(OkHttp.client(Constant.TIMEOUT_SYNC), device.getIp().concat("/action?do=sync&mode=0&type=history"), body.build()).execute();
        } catch (Exception e) {
            App.post(() -> Notify.show(e.getMessage()));
        }
    }

    private void sendKeep(Device device) {
        try {
            FormBody.Builder body = new FormBody.Builder();
            body.add("targets", App.gson().toJson(Keep.getVod()));
            body.add("configs", App.gson().toJson(Config.findUrls()));
            OkHttp.newCall(OkHttp.client(Constant.TIMEOUT_SYNC), device.getIp().concat("/action?do=sync&mode=0&type=keep"), body.build()).execute();
        } catch (Exception e) {
            App.post(() -> Notify.show(e.getMessage()));
        }
    }

    public void syncHistory(Map<String, String> params, boolean force) {
        Config config = Config.find(Config.objectFrom(params.get("config")));
        List<History> targets = History.arrayFrom(params.get("targets"));
        if (VodConfig.get().getConfig().equals(config)) {
            if (force) History.delete(config.getId());
            History.sync(targets);
        } else {
            VodConfig.load(config, getCallback(targets));
        }
    }

    private Callback getCallback(List<History> targets) {
        return new Callback() {
            @Override
            public void success() {
                RefreshEvent.config();
                RefreshEvent.video();
                History.sync(targets);
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    private void syncKeep(Map<String, String> params, boolean force) {
        List<Keep> targets = Keep.arrayFrom(params.get("targets"));
        List<Config> configs = Config.arrayFrom(params.get("configs"));
        if (TextUtils.isEmpty(VodConfig.getUrl()) && configs.size() > 0) {
            VodConfig.load(Config.find(configs.get(0)), getCallback(configs, targets));
        } else {
            if (force) Keep.deleteAll();
            Keep.sync(configs, targets);
        }
    }

    private Callback getCallback(List<Config> configs, List<Keep> targets) {
        return new Callback() {
            @Override
            public void success() {
                RefreshEvent.history();
                RefreshEvent.config();
                RefreshEvent.video();
                Keep.sync(configs, targets);
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    private void apk(Map<String, String> params, Map<String, String> files) {
        for (String k : files.keySet()) {
            String fn = params.get(k);
            File temp = new File(files.get(k));
            if (!temp.exists()) continue;
            if (fn.toLowerCase().endsWith(".apk")) {
                File apk = Path.cache(System.currentTimeMillis() + "-" + fn);
                Path.copy(temp, apk);
                FileUtil.openFile(apk);
            }
            temp.delete();
            break;
        }
    }

    private void vodConfig(Map<String, String> params) {
        String url = params.get("url");
        if (TextUtils.isEmpty(url)) return;
        App.post(() -> Notify.progress(App.activity()));
        VodConfig.load(Config.find(url, 0), getCallback());
    }

    private void wallConfig(Map<String, String> params, Map<String, String> files) {
        for (String k : files.keySet()) {
            String fn = params.get(k);
            File temp = new File(files.get(k));
            if (!temp.exists()) continue;
            File wall = new File(Path.download(), fn);
            Path.copy(temp, wall);
            App.post(() -> Notify.progress(App.activity()));
            WallConfig.load(Config.find("file://" + Environment.DIRECTORY_DOWNLOADS + "/" + fn, 2), new Callback() {
                @Override
                public void success() {
                    Notify.dismiss();
                }
                @Override
                public void error(String msg) {
                    Notify.dismiss();
                    Notify.show(msg);
                }
            });
            temp.delete();
            break;
        }
    }

    private void pushRestore(Map<String, String> params, Map<String, String> files) {
        for (String k : files.keySet()) {
            String fn = params.get(k);
            File temp = new File(files.get(k));
            if (!temp.exists()) continue;
            File restore = Path.cache(System.currentTimeMillis() + "-" + fn);
            Path.copy(temp, restore);
            AppDatabase.restore(restore, new Callback() {
                @Override
                public void success() {
                    App.post(() -> Notify.progress(App.activity()));
                    App.post(() -> {
                        AppDatabase.reset();
                        initConfig();
                    }, 3000);
                }
            });
            temp.delete();
            break;
        }
    }

    private void pullRestore(Map<String, String> params, Map<String, String> files) {
        String ip = params.get("ip");
        if (TextUtils.isEmpty(ip)) return;
        AppDatabase.backup(new Callback() {
            @Override
            public void success(String path) {
                String type = "push_restore";
                File file = new File(path);
                MediaType mediaType = MediaType.parse("multipart/form-data");
                MultipartBody.Builder body = new MultipartBody.Builder();
                body.setType(MultipartBody.FORM);
                body.addFormDataPart("name", file.getName());
                body.addFormDataPart("files-0", file.getName(), RequestBody.create(mediaType, file));
                OkHttp.newCall(OkHttp.client(Constant.TIMEOUT_TRANSMIT), ip.concat("/action?do=transmit&type=").concat(type), body.build()).enqueue(getCallback());
            }
        });
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success(String result) {
                Notify.show(result);
            }

            @Override
            public void success() {
                Notify.dismiss();
                RefreshEvent.history();
                RefreshEvent.config();
                RefreshEvent.video();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        };
    }

    private void initConfig() {
        WallConfig.get().init();
        LiveConfig.get().init().load();
        VodConfig.get().init().load(getCallback());
    }
}
