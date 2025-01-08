package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.XtreamParser;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class XStream {

    @SerializedName("name")
    private String name;
    @SerializedName("stream_id")
    private String streamId;
    @SerializedName("stream_type")
    private String streamType;
    @SerializedName("stream_icon")
    private String streamIcon;
    @SerializedName("epg_channel_id")
    private String epgChannelId;
    @SerializedName("category_id")
    private String categoryId;
    @SerializedName("container_extension")
    private String containerExtension;

    public static List<XStream> arrayFrom(String str) {
        Type listType = new TypeToken<List<XStream>>() {
        }.getType();
        List<XStream> items = App.gson().fromJson(str, listType);
        return items == null ? Collections.emptyList() : items;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    public String getStreamId() {
        return TextUtils.isEmpty(streamId) ? "" : streamId;
    }

    public String getStreamType() {
        return TextUtils.isEmpty(streamType) ? "" : streamType;
    }

    public String getStreamIcon() {
        return TextUtils.isEmpty(streamIcon) ? "" : streamIcon;
    }

    public String getEpgChannelId() {
        return TextUtils.isEmpty(epgChannelId) ? "" : epgChannelId;
    }

    public String getCategoryId() {
        return TextUtils.isEmpty(categoryId) ? "" : categoryId;
    }

    public String getContainerExtension() {
        return TextUtils.isEmpty(containerExtension) ? "" : containerExtension;
    }

    public List<String> getPlayUrl(Live live, List<String> formats) {
        List<String> urls = new ArrayList<>();
        if (!getContainerExtension().isEmpty()) urls.add(XtreamParser.getBuilder(live).addPathSegment(getStreamType()).addPathSegment(live.getUsername()).addPathSegment(live.getPassword()).addPathSegment(getStreamId() + "." + getContainerExtension()).build().toString());
        else for (String format : formats) urls.add(XtreamParser.getBuilder(live).addPathSegment(getStreamType()).addPathSegment(live.getUsername()).addPathSegment(live.getPassword()).addPathSegment(getStreamId() + "." + format + "$" + format.toUpperCase()).build().toString());
        return urls;
    }
}
