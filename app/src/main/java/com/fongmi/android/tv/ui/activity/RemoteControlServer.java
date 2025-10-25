        package com.fongmi.android.tv.ui.activity;

    import android.widget.Toast;
    import com.fongmi.android.tv.App;
    import com.google.gson.Gson;
    import java.io.IOException;
    import java.util.List;
    import java.util.Map;
    import fi.iki.elonen.NanoHTTPD;

    public class RemoteControlServer extends NanoHTTPD {

        private static final String ADMIN_PASSWORD = "admin";
        private boolean isAdminAuthenticated = false;

        public RemoteControlServer(int port) throws IOException {
            super(port);
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        }

        @Override
        public Response serve(IHTTPSession session) {
            // 如果还没登录，并且不是正在尝试登录，就显示登录页面
            if (!isAdminAuthenticated && !isLoginRequest(session)) {
                return newFixedLengthResponse(Response.Status.OK, "text/html", getLoginPageHtml(""));
            }

            // 处理各种 POST 请求（登录、添加时间、删除时间、设置密码）
            if (Method.POST.equals(session.getMethod())) {
                try {
                    session.parseBody(session.getHeaders());
                    Map<String, String> params = session.getParms();
                    // 处理登录请求
                    if (params.containsKey("admin_password")) {
                        if (ADMIN_PASSWORD.equals(params.get("admin_password"))) {
                            isAdminAuthenticated = true;
                        } else {
                            return newFixedLengthResponse(Response.Status.OK, "text/html", getLoginPageHtml("管理员密码错误!"));
                        }
                    }
                    // 根据 action 参数来执行不同操作
                    switch (params.getOrDefault("action", "")) {
                        case "add_time":
                            addTimeSlot(params.get("startTime"), params.get("endTime"));
                            break;
                        case "delete_time":
                            deleteTimeSlot(Integer.parseInt(params.get("index")));
                            break;
                        case "set_password":
                            setAppPassword(params.get("password"));
                            break;
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
            
            // 登录成功后，显示功能强大的主控制台
            return newFixedLengthResponse(Response.Status.OK, "text/html", getAdminPanelHtml());
        }
        
        // --- 核心业务逻辑 (现在都调用 SecurePrefs 来操作加密数据) ---
        private void addTimeSlot(String start, String end) {
            if (start == null || end == null || start.isEmpty() || end.isEmpty()) return;
            List<SecurePrefs.TimeSlot> slots = SecurePrefs.getTimeSlots();
            slots.add(new SecurePrefs.TimeSlot(start, end));
            SecurePrefs.put("allowed_time_slots", App.gson().toJson(slots));
            showToast("时间段已添加");
        }

        private void deleteTimeSlot(int index) {
            List<SecurePrefs.TimeSlot> slots = SecurePrefs.getTimeSlots();
            if (index >= 0 && index < slots.size()) {
                slots.remove(index);
                SecurePrefs.put("allowed_time_slots", App.gson().toJson(slots));
                showToast("时间段已删除");
            }
        }

        private void setAppPassword(String password) {
            if (password != null) {
                if (password.isEmpty()) {
                    SecurePrefs.remove("app_password");
                    showToast("App 启动密码已清除");
                } else {
                    SecurePrefs.put("app_password", password);
                    showToast("App 启动密码已更新");
                }
            }
        }

        // --- 动态生成 HTML 页面 ---
        private boolean isLoginRequest(IHTTPSession session) {
            try {
                if (Method.POST.equals(session.getMethod())) {
                    session.parseBody(session.getHeaders());
                    return session.getParms().containsKey("admin_password");
                }
            } catch (Exception e) { e.printStackTrace(); }
            return false;
        }

        private String getLoginPageHtml(String error) {
            String errorMsg = error.isEmpty() ? "" : "<p style='color:red;'>" + error + "</p>";
            return "<!DOCTYPE html><html><head><title>远程管理登录</title><meta name='viewport' content='width=device-width, initial-scale=1'><style>body{background:#121212; color:white; font-family:sans-serif; display:flex; justify-content:center; align-items:center; height:100vh; margin:0;} div{background:#212121; padding:30px; border-radius:10px; text-align:center;} input{margin-top:10px; padding:8px;} button{margin-top:15px; padding:8px 15px;}</style></head><body><div><h2>请输入管理员密码</h2>" + errorMsg + "<form method='POST'><input type='password' name='admin_password' autofocus/><br/><button type='submit'>登录</button></form></div></body></html>";
        }

        private String getAdminPanelHtml() {
            StringBuilder timeSlotsHtml = new StringBuilder();
            List<SecurePrefs.TimeSlot> slots = SecurePrefs.getTimeSlots();
            if (slots.isEmpty()) {
                timeSlotsHtml.append("<p>当前未设置任何时间段 (全天可用)</p>");
            } else {
                for (int i = 0; i < slots.size(); i++) {
                    SecurePrefs.TimeSlot slot = slots.get(i);
                    timeSlotsHtml.append("<li>").append(slot.start).append(" - ").append(slot.end).append(" <form method='POST' style='display:inline;'><input type='hidden' name='action' value='delete_time'><input type='hidden' name='index' value='").append(i).append("'><button type='submit' style='background:red; font-size:10px; padding:2px 5px;'>删除</button></form></li>");
                }
            }
            String password = SecurePrefs.getString("app_password", "");
            String passStatus = password.isEmpty() ? "当前未设置 App 启动密码" : "当前 App 密码为: " + password;
            return "<!DOCTYPE html><html><head><title>婉儿守护控制台</title><meta name='viewport' content='width=device-width, initial-scale=1'><style>body{background:#121212; color:white; font-family:sans-serif; text-align:center; padding:20px;} div{background:#212121; border-radius:10px; padding:20px; margin-bottom:20px; max-width:500px; display:inline-block; vertical-align:top;} h2{color:#4CAF50;} ul{list-style:none; padding:0;} li{margin-bottom:10px;} input[type=time]{margin:0 10px;} button{cursor:pointer;}</style></head><body><h1>婉儿守护控制台</h1>" +
                   "<div><h2>定时锁管理</h2><ul>" + timeSlotsHtml.toString() + "</ul><hr>" +
                   "<form method='POST'><input type='hidden' name='action' value='add_time'><label>开始:</label><input type='time' name='startTime' value='08:00'><label>结束:</label><input type='time' name='endTime' value='22:00'><br/><button type='submit' style='margin-top:15px;'>添加时间段</button></form></div>" +
                   "<div><h2>密码锁管理</h2><p>" + passStatus + "</p>" +
                   "<form method='POST'><input type='hidden' name='action' value='set_password'><input type='text' name='password' placeholder='输入新密码 (留空则清除)' /><br/><button type='submit' style='margin-top:15px;'>设置密码</button></form></div>" +
                   "</body></html>";
        }
        
        private void showToast(String text) {
            App.get().getMainExecutor().execute(() -> Toast.makeText(App.get(), text, Toast.LENGTH_LONG).show());
        }
    }
