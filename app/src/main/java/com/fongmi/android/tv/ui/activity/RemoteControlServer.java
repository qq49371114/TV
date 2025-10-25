package com.fongmi.android.tv.ui.activity;

import android.widget.Toast;
import com.fongmi.android.tv.App;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import fi.iki.elonen.NanoHTTPD;

public class RemoteControlServer extends NanoHTTPD {

    private static final String ADMIN_PASSWORD = "admin";
    private boolean isAdminAuthenticated = false;

    public RemoteControlServer(int port) throws IOException {
        super(port);
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        System.out.println("\n>>> 婉儿守护控制台已启动，请访问 http://<电视IP>:" + port + "\n");
    }

    public static class TimeSlot {
        public String start;
        public String end;
        public TimeSlot(String start, String end) { this.start = start; this.end = end; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TimeSlot ts = (TimeSlot) o;
            return Objects.equals(start, ts.start) && Objects.equals(end, ts.end);
        }
        @Override
        public int hashCode() { return Objects.hash(start, end); }
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (!isAdminAuthenticated && !isLoginRequest(session)) {
            return newFixedLengthResponse(Response.Status.OK, "text/html", getLoginPageHtml(""));
        }
        if (Method.POST.equals(session.getMethod())) {
            try {
                session.parseBody(session.getHeaders());
                Map<String, String> params = session.getParms();
                if (params.containsKey("admin_password")) {
                    if (ADMIN_PASSWORD.equals(params.get("admin_password"))) isAdminAuthenticated = true;
                    else return newFixedLengthResponse(Response.Status.OK, "text/html", getLoginPageHtml("管理员密码错误!"));
                }
                switch (params.getOrDefault("action", "")) {
                    case "add_time": addTimeSlot(params.get("startTime"), params.get("endTime")); break;
                    case "delete_time": deleteTimeSlot(Integer.parseInt(params.get("index"))); break;
                    case "set_password": setAppPassword(params.get("password")); break;
                }
            } catch (Exception e) { e.printStackTrace(); }
            Response response = newFixedLengthResponse(Response.Status.REDIRECT, "text/html", "");
            response.addHeader("Location", "/");
            return response;
        }
        return newFixedLengthResponse(Response.Status.OK, "text/html", getAdminPanelHtml());
    }
    
    private void addTimeSlot(String start, String end) {
        if (start == null || end == null || start.isEmpty() || end.isEmpty()) return;
        List<TimeSlot> slots = SecurePrefs.getTimeSlots();
        TimeSlot newSlot = new TimeSlot(start, end);
        if (!slots.contains(newSlot)) {
            slots.add(newSlot);
            SecurePrefs.put("allowed_time_slots", App.gson().toJson(slots));
            showToast("时间段已添加");
        } else {
            showToast("该时间段已存在，无需重复添加");
        }
    }

    private void deleteTimeSlot(int index) {
        List<TimeSlot> slots = SecurePrefs.getTimeSlots();
        if (index >= 0 && index < slots.size()) {
            slots.remove(index);
            SecurePrefs.put("allowed_time_slots", App.gson().toJson(slots));
            showToast("时间段已删除");
        }
    }

    private void setAppPassword(String password) {
        if (password != null) {
            if (password.isEmpty()) SecurePrefs.remove("app_password");
            else SecurePrefs.put("app_password", password);
            showToast("App 启动密码已更新");
        }
    }

    private boolean isLoginRequest(IHTTPSession session) {
        try {
            if (Method.POST.equals(session.getMethod())) {
                session.parseBody(session.getHeaders());
                return session.getParms().containsKey("admin_password");
            }
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }

    // ▼▼▼【核心修改】婉儿为您全新设计的“星际驾驶舱” CSS 样式！▼▼▼
    private final String CSS_STYLE = "<style>" +
            "html{height:100%;}" +
            "body{background:radial-gradient(ellipse at bottom, #1b2735 0%, #090a0f 100%); color:white; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif,'Apple Color Emoji','Segoe UI Emoji','Segoe UI Symbol'; text-align:center; padding:20px; height:100%; margin:0; overflow:hidden;}" +
            ".card{background:rgba(255, 255, 255, 0.1); border-radius:15px; padding:25px; margin-bottom:20px; max-width:500px; display:inline-block; vertical-align:top; backdrop-filter:blur(10px); border:1px solid rgba(255,255,255,0.2); box-shadow: 0 8px 32px 0 rgba(31, 38, 135, 0.37);}" +
            "h1{font-size:2.5em; color:white; text-shadow:0 0 10px #00e676, 0 0 20px #00e676, 0 0 30px #00e676;}" +
            "h2{color:#00e676;}" +
            "ul{list-style:none; padding:0;} li{background:rgba(0,0,0,0.2); margin-bottom:10px; padding:10px; border-radius:8px; display:flex; justify-content:space-between; align-items:center; transition: all 0.3s ease;}" +
            "li:hover{background:rgba(0,0,0,0.4); transform: scale(1.02);}"+
            "input[type=time],input[type=text],input[type=password]{margin:0 10px; padding:10px; border-radius:8px; border:1px solid #555; background:rgba(0,0,0,0.3); color:white; font-size:16px;}" +
            "button{cursor:pointer; background:linear-gradient(45deg, #2980b9, #8e44ad); color:white; border:none; padding:12px 25px; border-radius:25px; font-weight:bold; text-transform:uppercase; transition: all 0.3s ease;}" +
            "button:hover{box-shadow: 0 0 15px #8e44ad; transform: translateY(-2px);}" +
            ".btn-delete{background:linear-gradient(45deg, #c0392b, #e74c3c); padding:3px 10px; font-size:10px; border-radius:15px;}" +
            ".btn-delete:hover{box-shadow: 0 0 10px #e74c3c;}" +
            "p,label{color:#ccc;}" +
            "</style>";

    private String getLoginPageHtml(String error) {
        String errorMsg = error.isEmpty() ? "" : "<p style='color:red;'>" + error + "</p>";
        return "<!DOCTYPE html><html><head><title>远程管理登录</title><meta name='viewport' content='width=device-width, initial-scale=1'>" + CSS_STYLE + "</head><body>" +
               "<div class='card'><h2>请输入管理员密码</h2>" + errorMsg + "<form method='POST'><input type='password' name='admin_password' autofocus/><br/><button type='submit'>登 录</button></form></div>" +
               "</body></html>";
    }

    private String getAdminPanelHtml() {
        StringBuilder timeSlotsHtml = new StringBuilder();
        List<TimeSlot> slots = SecurePrefs.getTimeSlots();
        if (slots.isEmpty()) {
            timeSlotsHtml.append("<p>当前未设置任何时间段 (全天可用)</p>");
        } else {
            for (int i = 0; i < slots.size(); i++) {
                TimeSlot slot = slots.get(i);
                timeSlotsHtml.append("<li><span>").append(slot.start).append(" - ").append(slot.end).append("</span> <form method='POST' style='display:inline;'><input type='hidden' name='action' value='delete_time'><input type='hidden' name='index' value='").append(i).append("'><button type='submit' class='btn-delete'>删除</button></form></li>");
            }
        }
        String password = SecurePrefs.getString("app_password", "");
        String passStatus = password.isEmpty() ? "当前未设置 App 启动密码" : "当前 App 密码为: " + password;
        return "<!DOCTYPE html><html><head><title>婉儿守护控制台</title><meta name='viewport' content='width=device-width, initial-scale=1'>" + CSS_STYLE + "</head><body><h1>婉儿守护控制台</h1>" +
               "<div class='card'><h2>定时锁管理</h2><ul>" + timeSlotsHtml.toString() + "</ul><hr style='border-color:#333;'>" +
               "<form method='POST' style='padding-top:15px;'><input type='hidden' name='action' value='add_time'><label>开始:</label><input type='time' name='startTime' value='08:00'><label>结束:</label><input type='time' name='endTime' value='22:00'><br/><button type='submit' style='margin-top:15px;'>添加时间段</button></form></div>" +
               "<div class='card'><h2>密码锁管理</h2><p style='color:#aaa;'>" + passStatus + "</p>" +
               "<form method='POST'><input type='hidden' name='action' value='set_password'><input type='text' name='password' placeholder='输入新密码 (留空则清除)' /><br/><button type='submit' style='margin-top:15px;'>设置密码</button></form></div>" +
               "</body></html>";
    }
    
    private void showToast(String text) {
        App.get().getMainExecutor().execute(() -> Toast.makeText(App.get(), text, Toast.LENGTH_LONG).show());
    }
}
