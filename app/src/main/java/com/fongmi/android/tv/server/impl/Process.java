package com.fongmi.android.tv.server.impl;

import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.response.Response;

import java.util.Map;

public interface Process {

    boolean isRequest(IHTTPSession session, String path);

    Response doResponse(IHTTPSession session, String path, Map<String, String> files);
}
