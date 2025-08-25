package com.github.catvod.net;

import com.github.catvod.bean.Proxy;
import com.github.catvod.utils.Util;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class OkProxySelector extends ProxySelector {

    private final List<Proxy> proxy;

    public OkProxySelector() {
        proxy = new ArrayList<>();
    }

    public synchronized void addAll(List<Proxy> items) {
        proxy.addAll(items);
    }

    public void clear() {
        proxy.clear();
    }

    @Override
    public List<java.net.Proxy> select(URI uri) {
        if (proxy.isEmpty() || uri.getHost() == null || "127.0.0.1".equals(uri.getHost())) return Proxy.NO_PROXY;
        for (Proxy item : proxy) for (String host : item.getHosts()) if (Util.containOrMatch(uri.getHost(), host)) return item.select();
        return Proxy.NO_PROXY;
    }

    @Override
    public void connectFailed(URI uri, SocketAddress socketAddress, IOException e) {
    }
}
