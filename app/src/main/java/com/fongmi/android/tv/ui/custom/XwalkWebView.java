package com.fongmi.android.tv.ui.custom;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.net.http.SslError;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.webkit.ValueCallback;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Sniffer;
import com.github.catvod.crawler.Spider;
import com.google.common.net.HttpHeaders;
import com.orhanobut.logger.Logger;

import org.xwalk.core.XWalkCookieManager;
import org.xwalk.core.XWalkJavascriptResult;
import org.xwalk.core.XWalkResourceClient;
import org.xwalk.core.XWalkUIClient;
import org.xwalk.core.XWalkView;
import org.xwalk.core.XWalkWebResourceRequest;
import org.xwalk.core.XWalkWebResourceResponse;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class XwalkWebView extends XWalkView {

    private static final String TAG = XwalkWebView.class.getSimpleName();
    private static final String BLANK = "about:blank";

    private Map<String, String> headers;
    private XWalkWebResourceResponse empty;
    private XWalkCookieManager xWalkCookieManager;
    private XWalkResourceClient xWalkResourceClient;
    private ParseCallback callback;
    private AlertDialog dialog;
    private Runnable timer;
    private boolean detect;
    private String click;
    private String from;
    private String key;

    public static XwalkWebView create(@NonNull Context context) {
        return new XwalkWebView(context);
    }

    public XwalkWebView(@NonNull Context context) {
        super(context);
        initSettings();
        showTips();
    }

    private void showTips() {
        Notify.show(R.string.x5webview_parsing);
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void initSettings() {
        this.timer = () -> stop(true);
        xWalkCookieManager = new XWalkCookieManager();
        xWalkResourceClient = xWalkResourceClient();
        this.empty = xWalkResourceClient.createXWalkWebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
        getSettings().setSupportZoom(true);
        getSettings().setUseWideViewPort(true);
        getSettings().setDatabaseEnabled(true);
        getSettings().setDomStorageEnabled(true);
        getSettings().setJavaScriptEnabled(true);
        getSettings().setBuiltInZoomControls(true);
        getSettings().setLoadWithOverviewMode(true);
        getSettings().setUserAgentString(Setting.getUa());
        getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        getSettings().setMediaPlaybackRequiresUserGesture(false);
        xWalkCookieManager.setAcceptCookie(true);
        setResourceClient(xWalkResourceClient);
        setUIClient(xWalkUIClient());
    }

    public XwalkWebView start(String key, String from, Map<String, String> headers, String url, String click, ParseCallback callback, boolean detect) {
        App.post(timer, Constant.TIMEOUT_PARSE_WEB);
        this.callback = callback;
        this.headers = headers;
        this.detect = detect;
        this.click = click;
        this.from = from;
        this.key = key;
        start(url, headers);
        return this;
    }

    private void start(String url, Map<String, String> headers) {
        checkHeader(url, headers);
        loadUrl(url, headers);
    }

    private void checkHeader(String url, Map<String, String> headers) {
        for (String key : headers.keySet()) {
            if (HttpHeaders.COOKIE.equalsIgnoreCase(key)) xWalkCookieManager.setCookie(url, headers.get(key));
            if (HttpHeaders.USER_AGENT.equalsIgnoreCase(key)) getSettings().setUserAgentString(headers.get(key));
        }
    }

    private XWalkResourceClient xWalkResourceClient() {
        return new XWalkResourceClient(this) {

            @Override
            public void onDocumentLoadedInFrame(XWalkView view, long frameId) {
                super.onDocumentLoadedInFrame(view, frameId);
            }

            @Override
            public void onLoadStarted(XWalkView view, String url) {
                super.onLoadStarted(view, url);
                if (dialog != null) hideDialog();
            }

            @Override
            public void onLoadFinished(XWalkView view, String url) {
                super.onLoadFinished(view, url);
                if (url.equals(BLANK)) return;
                evaluate(getScript(url));
            }

            @Override
            public void onProgressChanged(XWalkView view, int progressInPercent) {
                super.onProgressChanged(view, progressInPercent);
            }

            @Override
            public XWalkWebResourceResponse shouldInterceptLoadRequest(XWalkView view, XWalkWebResourceRequest request) {
                String url = request.getUrl().toString();
                String host = request.getUrl().getHost();
                Map<String, String> headers = request.getRequestHeaders();
                if (TextUtils.isEmpty(host) || VodConfig.get().getAds().contains(host)) return empty;
                if (url.contains("challenges.cloudflare.com/cdn-cgi")) App.post(() -> showDialog());
                if (detect && url.contains("player/?url=")) onParseAdd(headers, url);
                else if (isVideoFormat(url)) interrupt(headers, url);
                return super.shouldInterceptLoadRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(XWalkView view, String s) {
                return false;
            }

            @Override
            public void onReceivedSslError(XWalkView view, ValueCallback<Boolean> callback, SslError error) {
                callback.onReceiveValue(true);
            }
        };
    }

    private XWalkUIClient xWalkUIClient() {
        return new XWalkUIClient(this) {
            @Override
            public boolean onConsoleMessage(XWalkView view, String message, int lineNumber, String sourceId, ConsoleMessageType messageType) {
                return false;
            }

            @Override
            public boolean onJsAlert(XWalkView view, String url, String message, XWalkJavascriptResult result) {
                return true;
            }

            @Override
            public boolean onJsConfirm(XWalkView view, String url, String message, XWalkJavascriptResult result) {
                return true;
            }

            @Override
            public boolean onJsPrompt(XWalkView view, String url, String message, String defaultValue, XWalkJavascriptResult result) {
                return true;
            }
        };
    }

    private void showDialog() {
        if (dialog != null || App.activity() == null) return;
        if (getParent() != null) ((ViewGroup) getParent()).removeView(this);
        dialog = new AlertDialog.Builder(App.activity()).setView(this).show();
    }

    private void hideDialog() {
        if (dialog != null) dialog.dismiss();
        dialog = null;
    }

    private List<String> getScript(String url) {
        List<String> script = new ArrayList<>(Sniffer.getScript(Uri.parse(url)));
        if (TextUtils.isEmpty(click) || script.contains(click)) return script;
        script.add(0, click);
        return script;
    }

    private void evaluate(List<String> script) {
        if (script.isEmpty()) return;
        if (TextUtils.isEmpty(script.get(0))) {
            evaluate(script.subList(1, script.size()));
        } else {
            loadUrl("javascript:" + script.get(0));
        }
    }

    private boolean isVideoFormat(String url) {
        try {
            Logger.t(TAG).d(url);
            Site site = VodConfig.get().getSite(key);
            Spider spider = VodConfig.get().getSpider(site);
            if (spider.manualVideoCheck()) return spider.isVideoFormat(url);
            return Sniffer.isVideoFormat(url);
        } catch (Exception ignored) {
            return Sniffer.isVideoFormat(url);
        }
    }

    private void interrupt(Map<String, String> headers, String url) {
        String cookie = xWalkCookieManager.getCookie(url);
        if (cookie != null) headers.put(HttpHeaders.COOKIE, cookie);
        onParseSuccess(headers, url);
    }

    private void onParseAdd(Map<String, String> headers, String url) {
        App.post(() -> XwalkWebView.create(App.get()).start(key, from, headers, url, click, callback, false));
    }

    private void onParseSuccess(Map<String, String> headers, String url) {
        if (callback != null) callback.onParseSuccess(headers, url, from);
        App.post(() -> stop(false));
        callback = null;
    }

    private void onParseError() {
        if (callback != null) callback.onParseError();
        callback = null;
    }

    public void stop(boolean error) {
        hideDialog();
        stopLoading();
        loadUrl(BLANK);
        App.removeCallbacks(timer);
        if (error) onParseError();
        else callback = null;
    }
}
