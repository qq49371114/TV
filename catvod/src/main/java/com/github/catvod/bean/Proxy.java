package com.github.catvod.bean;

import android.net.Uri;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Proxy {

    @SerializedName("hosts")
    private List<String> hosts;
    @SerializedName("urls")
    private List<String> urls;

    public static List<java.net.Proxy> NO_PROXY = List.of(java.net.Proxy.NO_PROXY);

    public static List<Proxy> arrayFrom(JsonElement element) {
        try {
            Type listType = new TypeToken<List<Proxy>>() {}.getType();
            List<Proxy> items = new Gson().fromJson(element, listType);
            return items == null ? Collections.emptyList() : items;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public List<String> getHosts() {
        return hosts == null ? Collections.emptyList() : hosts;
    }

    public List<String> getUrls() {
        return urls == null ? Collections.emptyList() : urls;
    }

    public List<java.net.Proxy> select() {
        List<java.net.Proxy> items = new ArrayList<>();
        for (String url : getUrls()) items.add(proxy(url));
        items.removeIf(Objects::isNull);
        return items.isEmpty() ? NO_PROXY : items;
    }

    private java.net.Proxy proxy(String url) {
        Uri uri = Uri.parse(url);
        if (uri.getScheme() == null || uri.getHost() == null || uri.getPort() <= 0) return null;
        if (uri.getScheme().startsWith("http")) return new java.net.Proxy(java.net.Proxy.Type.HTTP, InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
        if (uri.getScheme().startsWith("socks")) return new java.net.Proxy(java.net.Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
        return null;
    }
}