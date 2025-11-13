package com.github.catvod.net;

import androidx.annotation.NonNull;

import com.github.catvod.bean.Doh;
import com.github.catvod.utils.Util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;

public class OkDns implements Dns {

    private final ConcurrentHashMap<String, String> map;
    private DnsOverHttps doh;

    public OkDns() {
        this.map = new ConcurrentHashMap<>();
    }

    public void setDoh(Doh item) {
        if (item.getUrl().isEmpty()) return;
        this.doh = new DnsOverHttps.Builder().client(new OkHttpClient()).url(HttpUrl.get(item.getUrl())).bootstrapDnsHosts(item.getHosts()).build();
    }

    public void clear() {
        map.clear();
    }

    public synchronized void addAll(List<String> hosts) {
        for (String host : hosts) {
            if (!host.contains("=")) continue;
            String[] splits = host.split("=", 2);
            String oldHost = splits[0];
            String newHost = splits[1];
            map.put(oldHost, newHost);
        }
    }

    @NonNull
    @Override
    public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
        for (Map.Entry<String, String> entry : map.entrySet()) if (Util.containOrMatch(hostname, entry.getKey())) hostname = entry.getValue();
        return (doh != null ? doh : Dns.SYSTEM).lookup(hostname);
    }
}
