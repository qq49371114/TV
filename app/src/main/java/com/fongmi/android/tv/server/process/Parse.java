package com.fongmi.android.tv.server.process;

import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;
import com.github.catvod.utils.Asset;

import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;

import java.util.Map;

public class Parse implements Process {

    @Override
    public boolean isRequest(IHTTPSession session, String path) {
        return "/parse".equals(path);
    }

    @Override
    public Response doResponse(IHTTPSession session, String path, Map<String, String> files) {
        try {
            Map<String, String> params = session.getParms();
            String jxs = params.get("jxs");
            String url = params.get("url");
            String html = String.format(Asset.read("parse.html"), jxs, url);
            return Response.newFixedLengthResponse(Status.OK, NanoHTTPD.MIME_HTML, html);
        } catch (Exception e) {
            return Nano.error(e.getMessage());
        }
    }
}
