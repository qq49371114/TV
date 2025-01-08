package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class XInfo {

    @SerializedName("user_info")
    private UserInfo userInfo;
    @SerializedName("server_info")
    private ServerInfo serverInfo;

    public static XInfo objectFrom(String str) {
        XInfo item = App.gson().fromJson(str, XInfo.class);
        return item == null ? new XInfo() : item;
    }

    public UserInfo getUserInfo() {
        return userInfo == null ? new UserInfo() : userInfo;
    }

    public ServerInfo getServerInfo() {
        return serverInfo == null ? new ServerInfo() : serverInfo;
    }

    public static class UserInfo {

        @SerializedName("allowed_output_formats")
        private List<String> allowedOutputFormats;

        public List<String> getAllowedOutputFormats() {
            if (allowedOutputFormats == null) allowedOutputFormats = new ArrayList<>();
            if (allowedOutputFormats.isEmpty()) allowedOutputFormats.add("ts");
            allowedOutputFormats.remove("rtmp");
            return allowedOutputFormats;
        }
    }

    public static class ServerInfo {

        @SerializedName("timezone")
        private String timezone;

        public String getTimezone() {
            return TextUtils.isEmpty(timezone) ? "" : timezone;
        }
    }
}
