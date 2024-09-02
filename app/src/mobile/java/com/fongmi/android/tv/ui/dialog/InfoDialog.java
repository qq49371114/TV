package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.alibaba.fastjson.JSON;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogInfoBinding;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class InfoDialog {

    private final DialogInfoBinding binding;
    private final Listener callback;
    private AlertDialog dialog;
    private CharSequence title;
    private String header;
    private String url;

    public static InfoDialog create(Activity activity) {
        return new InfoDialog(activity);
    }

    public InfoDialog(Activity activity) {
        this.binding = DialogInfoBinding.inflate(LayoutInflater.from(activity));
        this.callback = (Listener) activity;
    }

    public InfoDialog title(CharSequence title) {
        this.title = title;
        return this;
    }

    public InfoDialog headers(Map<String, String> headers) {
        StringBuilder sb = new StringBuilder();
        for (String key : headers.keySet()) sb.append(key).append(" : ").append(headers.get(key)).append("\n");
        this.header = Util.substring(sb.toString());
        return this;
    }

    public InfoDialog url(String url) {
        this.url = url;
        return this;
    }

    public void show() {
        initDialog();
        initView();
        initEvent();
        initReportButton();  // 新增
    }

    private void initDialog() {
        dialog = new MaterialAlertDialogBuilder(binding.getRoot().getContext()).setView(binding.getRoot()).create();
        dialog.getWindow().setDimAmount(0);
        dialog.show();
    }

    private void initView() {
        binding.title.setText(title);
        binding.url.setText(fixUrl());
//        binding.header.setText(header);
        binding.url.setVisibility(TextUtils.isEmpty(url) ? View.GONE : View.VISIBLE);
//        binding.header.setVisibility(TextUtils.isEmpty(header) ? View.GONE : View.VISIBLE);
    }

    private void initEvent() {
        binding.url.setOnClickListener(this::onShare);
        binding.url.setOnLongClickListener(v -> onCopy(url));
//        binding.header.setOnLongClickListener(v -> onCopy(header));
    }

    private String fixUrl() {
        return TextUtils.isEmpty(url) ? "" : url.startsWith("data") ? url.substring(0, Math.min(url.length(), 128)).concat("...") : url;
    }

    private void onShare(View view) {
        callback.onShare(title);
        dialog.dismiss();
    }

    private boolean onCopy(String text) {
        Notify.show(R.string.copied);
        Util.copy(text);
        return true;
    }

    private void initReportButton() {
        binding.reportButton.setOnClickListener(v -> reportIssue());
    }

    private void reportIssue() {

        if (!url.isEmpty() && !url.contains("127.0.0.1") && url.toLowerCase().contains("m3u8")){
            // 假设你使用的是 OkHttp
            OkHttpClient client = new OkHttpClient();

            // 使用显式的泛型声明创建 HashMap
            Map<String, Object> jsonMap = new HashMap<String, Object>();
            jsonMap.put("title", title);
            jsonMap.put("url", url);
            jsonMap.put("header", header);

            // 使用 Fastjson 将 Map 转换为 JSON 字符串
            String json = JSON.toJSONString(jsonMap);

            // 创建 JSON 请求体
            RequestBody requestBody = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));

            // 构建请求
            Request request = new Request.Builder()
                    .url("https://www.lintech.work/api/tvbox/reportBug")
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    // 处理请求失败
                    binding.getRoot().post(() -> {
                        Notify.show("上报失败!");
                    });
                    // 使用 Handler 延迟 1 秒关闭弹窗
                    new Handler(Looper.getMainLooper()).postDelayed(() -> dialog.dismiss(), 1000);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    com.alibaba.fastjson.JSONObject object = com.alibaba.fastjson.JSONObject.parseObject(response.body().string());
                    System.out.println(object);
                    // Handle potential missing "code" field
                    if (object.containsKey("code") && object.getInteger("code") == 200) {
                        String message = object.getString("msg");
                        binding.getRoot().post(() -> {
                            Notify.show(message);
                        });
                    } else {
                        // Extract message if available, otherwise use generic error message
                        String message = object.containsKey("msg")? object.getString("msg"): object.getString("detail");
                        binding.getRoot().post(() -> {
                            Notify.show(message);
                        });
                    }
                    // 使用 Handler 延迟 1 秒关闭弹窗
                    new Handler(Looper.getMainLooper()).postDelayed(() -> dialog.dismiss(), 1000);
                }
            });
        } else {
            binding.getRoot().post(() -> {
                Notify.show("该链接不支持广告解析, 无需上报!");
            });
            // 使用 Handler 延迟 1 秒关闭弹窗
            new Handler(Looper.getMainLooper()).postDelayed(() -> dialog.dismiss(), 1000);
        }
    }

    public interface Listener {

        void onShare(CharSequence title);
    }
}
