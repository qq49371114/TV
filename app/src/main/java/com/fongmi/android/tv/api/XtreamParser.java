package com.fongmi.android.tv.api;

import android.net.Uri;

import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.XCategory;
import com.fongmi.android.tv.bean.XInfo;
import com.fongmi.android.tv.bean.XStream;
import com.github.catvod.net.OkHttp;

import java.util.List;

import okhttp3.HttpUrl;

public class XtreamParser {

    public static HttpUrl.Builder getBuilder(Live live) {
        HttpUrl url = HttpUrl.parse(live.getUrl());
        return new HttpUrl.Builder().scheme(url.scheme()).host(url.host()).port(url.port());
    }

    public static boolean isVerify(Uri uri) {
        return uri.getPath() != null && uri.getQueryParameter("username") != null && uri.getQueryParameter("password") != null && (uri.getPath().contains("player_api.php") || uri.getPath().contains("get.php"));
    }

    public static boolean isApiUrl(String url) {
        return isApiUrl(Uri.parse(url));
    }

    public static boolean isApiUrl(Uri uri) {
        return uri.getPath() != null && uri.getQueryParameter("username") != null && uri.getQueryParameter("password") != null && uri.getPath().contains("player_api.php");
    }

    public static boolean isGetUrl(String url) {
        return isGetUrl(Uri.parse(url));
    }

    public static boolean isGetUrl(Uri uri) {
        return uri.getPath() != null && uri.getQueryParameter("username") != null && uri.getQueryParameter("password") != null && uri.getPath().contains("get.php");
    }

    public static String getEpgUrl(Live live) {
        return getBuilder(live).addPathSegment("xmltv.php").addQueryParameter("username", live.getUsername()).addQueryParameter("password", live.getPassword()).build().toString();
    }

    public static String getApiUrl(Live live) {
        return getBuilder(live).addPathSegment("player_api.php").addQueryParameter("username", live.getUsername()).addQueryParameter("password", live.getPassword()).build().toString();
    }

    public static String getApiUrl(Live live, String action) {
        return getBuilder(live).addPathSegment("player_api.php").addQueryParameter("username", live.getUsername()).addQueryParameter("password", live.getPassword()).addQueryParameter("action", action).build().toString();
    }

    public static XInfo getInfo(Live live) {
        return XInfo.objectFrom(OkHttp.string(getApiUrl(live)));
    }

    public static List<XCategory> getLiveCategoryList(Live live) {
        return XCategory.arrayFrom(OkHttp.string(getApiUrl(live, "get_live_categories")));
    }

    public static List<XStream> getLiveStreamList(Live live) {
        return XStream.arrayFrom(OkHttp.string(getApiUrl(live, "get_live_streams")));
    }

    public static List<XCategory> getVodCategoryList(Live live) {
        return XCategory.arrayFrom(OkHttp.string(getApiUrl(live, "get_vod_categories")));
    }

    public static List<XStream> getVodStreamList(Live live) {
        return XStream.arrayFrom(OkHttp.string(getApiUrl(live, "get_vod_streams")));
    }

    public static List<XCategory> getCategoryList(Live live) {
        List<XCategory> categoryList = XtreamParser.getLiveCategoryList(live);
        categoryList.addAll(XtreamParser.getVodCategoryList(live));
        return categoryList;
    }

    public static List<XStream> getStreamList(Live live) {
        List<XStream> streamList = XtreamParser.getLiveStreamList(live);
        streamList.addAll(XtreamParser.getVodStreamList(live));
        return streamList;
    }
}
