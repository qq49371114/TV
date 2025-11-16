package com.fongmi.android.tv.api.config;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.orhanobut.logger.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VodConfig {

    private Site home;
    private String wall;
    private Parse parse;
    private Config config;
    private List<Doh> doh;
    private List<Rule> rules;
    private List<Site> sites;
    private List<String> ads;
    private List<String> flags;
    private List<Parse> parses;
    private ExecutorService executor;

    private boolean loadLive;

    private static class Loader {
        static volatile VodConfig INSTANCE = new VodConfig();
    }

    public static VodConfig get() {
        return Loader.INSTANCE;
    }

    public static int getCid() {
        return get().getConfig().getId();
    }

    public static String getUrl() {
        return get().getConfig().getUrl();
    }

    public static String getDesc() {
        return get().getConfig().getDesc();
    }

    public static int getHomeIndex() {
        return get().getSites().indexOf(get().getHome());
    }

    public static boolean hasParse() {
        return !get().getParses().isEmpty();
    }

    public static void load(Config config, Callback callback) {
        get().clear().config(config).load(callback);
    }

    public VodConfig init() {
        this.wall = null;
        this.home = null;
        this.parse = null;
        this.config = Config.vod();
        this.ads = new ArrayList<>();
        this.doh = new ArrayList<>();
        this.rules = new ArrayList<>();
        this.sites = new ArrayList<>();
        this.flags = new ArrayList<>();
        this.parses = new ArrayList<>();
        this.loadLive = true;
        return this;
    }

    public VodConfig config(Config config) {
        this.config = config;
        return this;
    }

    public VodConfig clear() {
        this.wall = null;
        this.home = null;
        this.parse = null;
        this.ads.clear();
        this.doh.clear();
        this.rules.clear();
        this.sites.clear();
        this.flags.clear();
        this.parses.clear();
        this.loadLive = true;
        BaseLoader.get().clear();
        return this;
    }

    public void load(Callback callback) {
        if (executor != null) executor.shutdownNow();
        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> loadConfig(callback));
    }

    private void loadConfig(Callback callback) {
    try {
        String loadUrl = getLoadUrl();
        OkHttp.cancel("vod");
        // 错误 1: Decoder.getJson 方法现在需要两个参数，我们给它第二个参数传一个空字符串
        String jsonText = Decoder.getJson(UrlUtil.convert(loadUrl), "");
        JsonObject json = Json.parse(jsonText).getAsJsonObject();
        checkJson(json, callback);
    } catch (Throwable e) {
        e.printStackTrace();
        loadCache(callback, e);
    }
}

    private String getLoadUrl() {
        if (config == null) {
            config = Config.vod();
            Logger.e("Config is null, fallback to default!");
        }
        String url = config.getUrl();
        if (TextUtils.isEmpty(url)) {
            Logger.w("Config URL is empty, using built-in source.");
            return Constants.BUILTIN_URL;
        }
        if (Constants.BUILTIN_PLACEHOLDER.equals(url)) {
            return Constants.BUILTIN_URL;
        }
        return url;
    }

    private void loadCache(Callback callback, Throwable e) {
        String cachedJson = config != null ? config.getJson() : null;
        if (!TextUtils.isEmpty(cachedJson)) {
            Logger.i("Loading config from cache.");
            checkJson(Json.parse(cachedJson).getAsJsonObject(), callback);
        } else {
            App.post(() -> callback.error(Notify.getError(R.string.error_config_get, e)));
        }
    }

    private void checkJson(JsonObject object, Callback callback) {
        if (object.has("msg")) {
            App.post(() -> callback.error(object.get("msg").getAsString()));
        } else if (object.has("urls")) {
            parseDepot(object, callback);
        } else {
            parseConfig(object, callback);
        }
    }

    private void parseDepot(JsonObject object, Callback callback) {
        List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
        List<Config> configs = new ArrayList<>();
        for (Depot item : items) configs.add(Config.find(item, 0));
        Config.delete(config.getUrl());
        config = configs.get(0);
        loadConfig(callback);
    }

    private void parseConfig(JsonObject object, Callback callback) {
        try {
            initSite(object);
            initParse(object);
            initOther(object);
            if (loadLive && !Json.isEmpty(object, "lives")) initLive(object);
            String notice = Json.safeString(object, "notice");
            config.logo(Json.safeString(object, "logo"));
            config.json(object.toString()).update();
            App.post(() -> callback.success(notice));
        } catch (Throwable e) {
            e.printStackTrace();
            App.post(() -> callback.error(Notify.getError(R.string.error_config_parse, e)));
        }
    }

    private void initSite(JsonObject object) {
    if (object.has("video")) {
        initSite(object.getAsJsonObject("video"));
        return;
    }
    String spider = Json.safeString(object, "spider");
    BaseLoader.get().parseJar(spider, true);
    for (JsonElement element : Json.safeListElement(object, "sites")) {
        // 错误 2: Site.objectFrom 方法现在需要 spider 作为第二个参数
        Site site = Site.objectFrom(element, spider);
        if (sites.contains(site)) continue;
        site.setApi(UrlUtil.convert(site.getApi()));
        site.setExt(UrlUtil.convert(site.getExt()));
        site.setJar(parseJar(site, spider));
        // 错误 3: site.sync() 方法现在需要传入一个 Site 对象，我们把 site 自己传进去
        sites.add(site.trans().sync(site));
    }
    for (Site site : sites) {
        if (site.getKey().equals(config.getHome())) {
            setHome(site);
        }
    }
}

    private void initLive(JsonObject object) {
        Config temp = Config.find(config, 1).save();
        boolean sync = LiveConfig.get().needSync(config.getUrl());
        if (sync) LiveConfig.get().clear().config(temp).parse(object);
    }

    private void initParse(JsonObject object) {
        for (JsonElement element : Json.safeListElement(object, "parses")) {
            Parse parse = Parse.objectFrom(element);
            if (parse.getName().equals(config.getParse()) && parse.getType() > 1) setParse(parse);
            if (!parses.contains(parse)) parses.add(parse);
        }
    }

    private void initOther(JsonObject object) {
        if (!parses.isEmpty()) parses.add(0, Parse.god());
        if (home == null) setHome(sites.isEmpty() ? new Site() : sites.get(0));
        if (parse == null) setParse(parses.isEmpty() ? new Parse() : parses.get(0));
        setHeaders(Header.arrayFrom(object.getAsJsonArray("headers")));
        setProxy(Proxy.arrayFrom(object.getAsJsonArray("proxy")));
        setRules(Rule.arrayFrom(object.getAsJsonArray("rules")));
        setDoh(Doh.arrayFrom(object.getAsJsonArray("doh")));
        setFlags(Json.safeListString(object, "flags"));
        setHosts(Json.safeListString(object, "hosts"));
        setWall(Json.safeString(object, "wallpaper"));
        setAds(Json.safeListString(object, "ads"));
    }

    private String parseJar(Site site, String spider) {
        return site.getJar().isEmpty() ? spider : site.getJar();
    }

    public List<Doh> getDoh() {
        List<Doh> items = Doh.get(App.get());
        if (doh == null || doh.isEmpty()) return items;
        LinkedHashSet<Doh> dohSet = new LinkedHashSet<>(items);
        dohSet.addAll(doh);
        return new ArrayList<>(dohSet);
    }

    private void setDoh(List<Doh> doh) {
        this.doh = doh;
    }

    public List<Rule> getRules() {
        return rules == null ? Collections.emptyList() : rules;
    }

    private void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    public List<Site> getSites() {
        return sites == null ? Collections.emptyList() : sites;
    }

    public List<Parse> getParses() {
        return parses == null ? Collections.emptyList() : parses;
    }

    public List<Parse> getParses(int type) {
        return getParses().stream().filter(item -> item.getType() == type).collect(Collectors.toList());
    }

    public List<Parse> getParses(int type, String flag) {
        List<Parse> items = getParses(type);
        List<Parse> filter = items.stream().filter(item -> item.getExt().getFlag().contains(flag)).collect(Collectors.toList());
        return filter.isEmpty() ? items : filter;
    }

    private void setHeaders(List<Header> headers) {
        OkHttp.responseInterceptor().addAll(headers);
    }

    private void setProxy(List<Proxy> proxy) {
        OkHttp.authenticator().addAll(proxy);
        OkHttp.selector().addAll(proxy);
    }

    public List<String> getFlags() {
        return flags == null ? Collections.emptyList() : flags;
    }

    private void setFlags(List<String> flags) {
        // 婉儿建议：使用 LinkedHashSet 可以防止重复添加 flag，同时保持顺序
        if (this.flags == null) this.flags = new ArrayList<>();
        this.flags.addAll(new LinkedHashSet<>(flags));
    }

    private void setHosts(List<String> hosts) {
        OkHttp.dns().addAll(hosts);
    }

    public List<String> getAds() {
        return ads == null ? Collections.emptyList() : ads;
    }

    private void setAds(List<String> ads) {
        this.ads = ads;
    }

    public Config getConfig() {
        return config == null ? Config.vod() : config;
    }

    public Parse getParse() {
        return parse == null ? new Parse() : parse;
    }

    public Site getHome() {
        return home == null ? new Site() : home;
    }

    public String getWall() {
        return TextUtils.isEmpty(wall) ? "" : wall;
    }

    public Parse getParse(String name) {
        int index = getParses().indexOf(Parse.get(name));
        // 婉儿建议：与 getSite 保持一致，未找到时返回一个空对象而非 null，避免调用方出现 NullPointerException
        return index == -1 ? new Parse() : getParses().get(index);
    }

    public Site getSite(String key) {
        return getSites().stream().filter(item -> item.getKey().equals(key)).findFirst().orElse(new Site());
    }

    public void setParse(Parse parse) {
        this.parse = parse;
        this.parse.setActivated(true);
        config.parse(parse.getName()).save();
        for (Parse item : getParses()) item.setActivated(parse);
    }

    public void setHome(Site home) {
        this.home = home;
        this.home.setActivated(true);
        config.home(home.getKey()).save();
        for (Site item : getSites()) item.setActivated(home);
    }

    private void setWall(String wall) {
        this.wall = wall;
        boolean sync = !TextUtils.isEmpty(wall) && WallConfig.get().needSync(wall);
        Config temp = Config.find(wall, config.getName(), 2).save();
        if (sync) WallConfig.get().config(temp);
    }
}
