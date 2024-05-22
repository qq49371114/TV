package com.github.catvod.net;

import androidx.annotation.NonNull;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Dns;

public class OkDns implements Dns {

    @NonNull
    @Override
    public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
        List<InetAddress> items = new ArrayList<>();
        for (InetAddress address : InetAddress.getAllByName(hostname)) {
            if (address instanceof Inet4Address) items.add(0, address);
            else items.add(address);
        }
        return items;
    }
}
