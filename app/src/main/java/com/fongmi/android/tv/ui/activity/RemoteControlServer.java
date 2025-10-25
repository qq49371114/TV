    package com.fongmi.android.tv.ui.activity;

    import android.widget.Toast;
    import com.fongmi.android.tv.App;
    import com.github.catvod.utils.Prefers;
    import java.io.IOException;
    import java.util.Map;
    import fi.iki.elonen.NanoHTTPD;

    public class RemoteControlServer extends NanoHTTPD {

        public RemoteControlServer(int port) throws IOException {
            super(port);
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            System.out.println("\n>>> Web-based password manager is running on http://<YOUR_TV_IP>:" + port + "\n");
        }

        @Override
        public Response serve(IHTTPSession session) {
            if (Method.POST.equals(session.getMethod())) {
                try {
                    session.parseBody(session.getHeaders());
                    Map<String, String> params = session.getParms();
                    String newPassword = params.get("password");
                    if (newPassword != null) {
                        if (newPassword.isEmpty()) {
                            Prefers.remove("app_password");
                            showToast("启动密码已清除");
                        } else {
                            // ▼▼▼ 婉儿已经帮您把 putString 改成 put 啦！▼▼▼
                            Prefers.put("app_password", newPassword);
                            showToast("启动密码已更新为：" + newPassword);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return newFixedLengthResponse("Error processing request.");
                }
            }
            String html = getPasswordPageHtml();
            return newFixedLengthResponse(Response.Status.OK, "text/html", html);
        }

        private String getPasswordPageHtml() {
            String currentPassword = Prefers.getString("app_password", "");
            String statusText = currentPassword.isEmpty() ? "当前未设置密码" : "当前密码为: " + currentPassword;
            return "<!DOCTYPE html><html><head><title>App 密码远程管理</title><meta name='viewport' content='width=device-width, initial-scale=1'><style>body{background:#212121; color:white; font-family:sans-serif; text-align:center; padding-top:50px;}h2{color:#4CAF50;}p{color:#aaa;}input{padding:10px; width:80%; max-width:300px; margin-top:20px; border-radius:5px; border:1px solid #ccc;}button{background:#4CAF50; color:white; padding:10px 20px; border:none; border-radius:5px; cursor:pointer; margin-top:20px;}</style></head><body><h2>电视 App 远程密码管理器</h2><p id='status'>" + statusText + "</p><form method='POST'><input type='text' name='password' placeholder='输入新密码 (留空则清除密码)' /><br/><button type='submit'>设置 / 更新密码</button></form></body></html>";
        }
        
        private void showToast(String text) {
            App.get().getMainExecutor().execute(() -> {
                Toast.makeText(App.get(), text, Toast.LENGTH_LONG).show();
            });
        }
    }
