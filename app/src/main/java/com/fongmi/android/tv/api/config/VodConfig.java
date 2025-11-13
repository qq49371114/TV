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
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private Future<?> future;
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
        get().clear().config(config).live(true).load(callback);
    }

    public VodConfig init() {
        this.wall = null;
        this.home = null;
        this.parse = null;
        this.loadLive = false;
        this.config = Config.vod();
        this.ads = new ArrayList<>();
        this.doh = new ArrayList<>();
        this.rules = new ArrayList<>();
        this.sites = new ArrayList<>();
        this.flags = new ArrayList<>();
        this.parses = new ArrayList<>();
        return this;
    }

    public VodConfig config(Config config) {
        this.config = config;
        return this;
    }

    public VodConfig live(boolean loadLive) {
        this.loadLive = loadLive;
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
        BaseLoader.get().clear();
        return this;
    }

    public void load(Callback callback) {
        if (future != null && !future.isDone()) future.cancel(true);
        future = App.submit(() -> loadConfig(callback));
        callback.start();
    }

    private void loadConfig(Callback callback) {
        try {
            Server.get().start();
            String json = Decoder.getJson(UrlUtil.convert(config.getUrl()));
            JsonObject object = Json.parse(json).getAsJsonObject();
            checkJson(object, callback);
            config.update();
        } catch (Throwable e) {
            if (TextUtils.isEmpty(config.getUrl())) App.post(() -> callback.error(""));
            else App.post(() -> callback.error(Notify.getError(R.string.error_config_get, e)));
            e.printStackTrace();
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
            clear();
            initSite(object);
            initParse(object);
            initOther(object);
            config.logo(Json.safeString(object, "logo"));
            String notice = Json.safeString(object, "notice");
            if (loadLive && !Json.isEmpty(object, "lives")) initLive(object);
            App.post(() -> callback.success(notice));
            App.post(callback::success);
        } catch (Throwable e) {
            e.printStackTrace();
            App.post(() -> callback.error(Notify.getError(R.string.error_config_parse, e)));
        }
    }

    private void initSite(JsonObject object) {
        String spider = Json.safeString(object, "spider");
        BaseLoader.get().parseJar(spider, true);
        setSites(Json.safeListElement(object, "sites").stream().map(element -> Site.objectFrom(element, spider)).distinct().collect(Collectors.toCollection(ArrayList::new)));
        Map<String, Site> items = Site.findAll().stream().collect(Collectors.toMap(Site::getKey, Function.identity()));
        for (Site site : getSites()) {
            Site item = items.get(site.getKey());
            if (item != null) site.sync(item);
            if (site.getKey().equals(config.getHome())) setHome(site, false);
        }
    }

    private void initParse(JsonObject object) {
        setParses(Json.safeListElement(object, "parses").stream().map(Parse::objectFrom).distinct().collect(Collectors.toCollection(ArrayList::new)));
        for (Parse parse : getParses()) {
            if (parse.getName().equals(config.getParse()) && parse.getType() > 1) {
                setParse(parse, false);
                break;
            }
        }
    }

    private void initOther(JsonObject object) {
        if (!parses.isEmpty()) parses.add(0, Parse.god());
        if (home == null) setHome(sites.isEmpty() ? new Site() : sites.get(0), false);
        if (parse == null) setParse(parses.isEmpty() ? new Parse() : parses.get(0), false);
        setHeaders(Header.arrayFrom(object.getAsJsonArray("headers")));
        setProxy(Proxy.arrayFrom(object.getAsJsonArray("proxy")));
        setRules(Rule.arrayFrom(object.getAsJsonArray("rules")));
        setDoh(Doh.arrayFrom(object.getAsJsonArray("doh")));
        setFlags(Json.safeListString(object, "flags"));
        setHosts(Json.safeListString(object, "hosts"));
        setWall(Json.safeString(object, "wallpaper"));
        setAds(Json.safeListString(object, "ads"));
    }

    private void initLive(JsonObject object) {
        Config temp = Config.find(config, 1).save();
        boolean sync = LiveConfig.get().needSync(config.getUrl());
        if (sync) LiveConfig.get().config(temp.update()).parse(object);
    }

    public List<Site> getSites() {
        return sites == null ? Collections.emptyList() : sites;
    }

    private void setSites(List<Site> sites) {
        this.sites = sites;
    }

    public List<Parse> getParses() {
        return parses == null ? Collections.emptyList() : parses;
    }

    private void setParses(List<Parse> parses) {
        this.parses = parses;
    }

    public List<Doh> getDoh() {
        List<Doh> items = Doh.get(App.get());
        if (doh == null) return items;
        items.removeAll(doh);
        items.addAll(doh);
        return items;
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

    public List<Parse> getParses(int type) {
        List<Parse> items = new ArrayList<>();
        for (Parse item : getParses()) if (item.getType() == type) items.add(item);
        return items;
    }

    public List<Parse> getParses(int type, String flag) {
        List<Parse> items = new ArrayList<>();
        for (Parse item : getParses(type)) if (item.getExt().getFlag().contains(flag)) items.add(item);
        if (items.isEmpty()) items.addAll(getParses(type));
        return items;
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
        this.flags = flags;
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
        return index == -1 ? null : getParses().get(index);
    }

    public Site getSite(String key) {
        int index = getSites().indexOf(Site.get(key));
        return index == -1 ? new Site() : getSites().get(index);
    }

    public void setParse(Parse parse) {
        setParse(parse, true);
    }

    public void setParse(Parse parse, boolean save) {
        this.parse = parse;
        this.parse.setActivated(true);
        config.parse(parse.getName());
        getParses().forEach(item -> item.setActivated(parse));
        if (save) config.save();
    }

    public void setHome(Site site) {
        setHome(site, true);
    }

    public void setHome(Site site, boolean save) {
        home = site;
        home.setActivated(true);
        config.home(home.getKey());
        if (save) config.save();
        getSites().forEach(item -> item.setActivated(home));
    }

    private void setWall(String wall) {
        this.wall = wall;
        boolean sync = !TextUtils.isEmpty(wall) && WallConfig.get().needSync(wall);
        Config temp = Config.find(wall, config.getName(), 2).save();
        if (sync) WallConfig.get().config(temp.update());
    }
}
