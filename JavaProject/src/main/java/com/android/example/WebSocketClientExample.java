package com.android.example;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.UUID;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.json.JSONObject;

public class WebSocketClientExample {
    // 测试用ak，权限可能会不定时关闭，需要自己申请ak
    private static final String AK = "QiYydeMN9o6eK6BThcSlnDrEaEkxEjpE";
    private static final String URI = "wss://api.map.baidu.com/websocket";
    // 设备唯一识别码,可以使用Android id，车辆的唯一识别码
    private static final String ENTITY_ID = UUID.randomUUID().toString();
    private static final int ROUTE_TYPE = 2; // 1 巡航，2 导航
    private static long MSG_ID = 0;
    public static boolean init_flag = false;
    public static OkhttpSocketClient mChatClient;

    // TODO: 2024/7/2  修改模拟gps数据路径 ExcelCreate/JavaProject/src/jks/shanghai_gaosu_case.txt
    public static final String GPS_PATH = "/你的工程所在目录/ExcelCreate/JavaProject/src/jks/shanghai_gaosu_case.txt";
    public static final String ROUTE_PATH = "/你的工程所在目录/ExcelCreate/JavaProject/src/jks/shanghai_gaosu_route.txt";

    /*
     * Keystore with certificate created like so (in JKS format):
     *
     *keytool -genkey -keyalg RSA -validity 3650 -keystore "keystore.jks" -storepass "storepassword" -keypass "keypassword" -alias "default" -dname "CN=127.0.0.1, OU=MyOrgUnit, O=MyOrg, L=MyCity, S=MyRegion, C=MyCountry"
     */
    public static void main(String[] args) throws Exception {

        mChatClient = new OkhttpSocketClient(URI, null, new OkhttpSocketClient.MsgCallBack() {
            @Override
            public void onMessage(String message) {
                System.out.println("got: " + message);
                processMessage(message);
            }

            @Override
            public void onOpen() {
                System.out.println("Connected");
                sendAuth();
            }

            @Override
            public void onClose(String error) {
                init_flag = false;
                System.out.println("考虑是否需要重连 Connection closed " + error);
            }
        });

        // 设置SSL上下文以跳过证书验证
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }
                }
        };

        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // 创建主机名验证器，它接受任何主机名
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };

            // 安装所有主机的验证器
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

            // 设置连续设置GPS线程
            GPSWorker gpsWorker = new GPSWorker();
            Thread thread = new Thread(gpsWorker);
            thread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendAuth() {
        // 设置鉴权参数
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", MSG_ID++);
        jsonObject.put("qt", "auth");
        jsonObject.put("ver", "1.0");
        jsonObject.put("prt", 1);
        jsonObject.put("enc", 1);
        jsonObject.put("ts", System.currentTimeMillis());

        JSONObject data = new JSONObject();
        data.put("ak", AK);
        data.put("entity_id", ENTITY_ID);
        jsonObject.put("data", data);
        String message = jsonObject.toString();

        // 判断是否处于连接状态
        if (mChatClient.isOpen()) {
            System.out.println("WebSocket----处于开启状态");
            mChatClient.send(message);
        } else {
            System.out.println("WebSocket----处于关闭状态");
        }
    }

    public static void sendMessage(String message) {
        // 检查连接是否鉴权通过
        if (!init_flag) {
            System.out.println("WebSocket----处于关闭状态");
            return;
        }

        // 判断是否处于连接状态
        if (mChatClient.isOpen()) {
            mChatClient.send(message);
        } else {
            System.out.println("WebSocket----处于关闭状态");
        }
    }

    public static void processMessage(String message) {
        JSONObject jsonObject = new JSONObject(message);
        JSONObject data = (JSONObject) jsonObject.get("data");
        // 返回的是鉴权结果，判断鉴权结果是否成功
        if (jsonObject.get("qt").equals("auth") && data != null && (int) data.get("status") == 0) {
            System.out.println("鉴权成功");
            init_flag = true;
        }
    }

    public static void setRoute(long msgId) {
        try {
            // 如果有路线，先设置路线，要注意模拟测试时轨迹点一定要在路线上，否则会导致无数据下发
            Path routePath = Paths.get(ROUTE_PATH);
            if (ROUTE_TYPE == 2) {
                byte[] filesContext = Files.readAllBytes(routePath);
                if (filesContext == null) {
                    System.out.println("route file is empty");
                }
                String jsonString = new String(filesContext, StandardCharsets.UTF_8);
                JSONObject data = new JSONObject(jsonString);

                JSONObject jsonObject = new JSONObject();
                jsonObject.put("id", msgId++);
                jsonObject.put("qt", "horizon");
                jsonObject.put("ver", "1.0");
                jsonObject.put("prt", 1);
                jsonObject.put("enc", 1);
                jsonObject.put("ts", System.currentTimeMillis());

                JSONObject route = (JSONObject) data.get("route");
                if (route != null) {
                    data.put("type", 2);

//                    JSONObject field = new JSONObject();
//                    JSONArray slope = field.optJSONArray("slope");
//                    data.put("route_rsp_field", field);
                }

                jsonObject.put("data", data);
                String message = jsonObject.toString();
                // 向服务端发送位置
                sendMessage(message);
            } else if (ROUTE_TYPE == 1) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("id", msgId++);
                jsonObject.put("qt", "horizon");
                jsonObject.put("ver", "1.0");
                jsonObject.put("prt", 1);
                jsonObject.put("enc", 1);
                jsonObject.put("ts", System.currentTimeMillis());

                JSONObject routeInfo = new JSONObject();
                routeInfo.put("type", 1);

                JSONObject data = new JSONObject();
                data.put("route", routeInfo);

                jsonObject.put("data", data);

                String message = jsonObject.toString();

                // 向服务端发送位置
                sendMessage(message);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class GPSWorker implements Runnable {

        @Override
        public void run() {
            try {
                while (!mChatClient.isOpen()) {
                    System.out.println("connection not establish, waiting 1s...");
                    Thread.sleep(1000);
                }

                long msgId = 0;

                setRoute(msgId);

                Path GPSPath = Paths.get(GPS_PATH);
                // 假设gps坐标已经录制好，并存放在case.csv文件中
                // 从case.csv文件读取坐标并进行回放
                List<String> lines = Files.readAllLines(GPSPath);
                for (String line : lines) {
                    System.out.println(line);
                    // 处理每一行的内容
                    String[] locInfo = line.split(",");

                    if (locInfo.length < 4) {
                        continue;
                    }

                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("id", msgId++);
                    jsonObject.put("qt", "horizon");
                    jsonObject.put("ver", "1.0");
                    jsonObject.put("prt", 1);
                    jsonObject.put("enc", 1);
                    jsonObject.put("ts", System.currentTimeMillis());

                    JSONObject loc = new JSONObject();
                    loc.put("x", Double.parseDouble(locInfo[0]));
                    loc.put("y", Double.parseDouble(locInfo[1]));
                    loc.put("speed", Double.parseDouble(locInfo[2]));
                    loc.put("dir", Double.parseDouble(locInfo[3]));
                    loc.put("coordtype", 1);
                    loc.put("ts", System.currentTimeMillis());

                    JSONObject data = new JSONObject();
                    data.put("loc", loc);

                    jsonObject.put("data", data);

                    String message = jsonObject.toString();

                    // 向服务端发送位置
                    sendMessage(message);

                    Thread.sleep(950);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
