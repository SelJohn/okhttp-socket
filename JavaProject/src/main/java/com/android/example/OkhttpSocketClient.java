package com.android.example;

import java.net.Proxy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class OkhttpSocketClient extends WebSocketListener {

    private final AtomicBoolean isOpening = new AtomicBoolean(true);
    private Request mRequest;
    private OkHttpClient mClient;

    public interface MsgCallBack {
        void onMessage(String msg);
        void onOpen();
        void onClose(String error);
    }
    private final String mWsUrl;
    private final Proxy mProxy;
    private WebSocket mWebSocket;
    private final OkhttpSocketClient.MsgCallBack mCallback;
    public OkhttpSocketClient(String url, Proxy proxy, OkhttpSocketClient.MsgCallBack mc) {
        mWsUrl = url;
        mProxy = proxy;
        mCallback = mc;
        init();
    }


    private void init() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        if (mProxy != null) {
            builder.proxy(mProxy);
        }
        mClient = builder
                .connectTimeout(30, TimeUnit.SECONDS)
                // 10s没有心跳包会报异常，可以考虑重连 reconnect
                .pingInterval(10, TimeUnit.SECONDS)
                .build();
        mRequest = new Request.Builder()
                .url(mWsUrl)
                .build();
        mWebSocket = mClient.newWebSocket(mRequest, this);
    }

    public boolean isOpening() {
        return isOpening.get();
    }
    public void authFiled() {
        if (mWebSocket != null) {
            mWebSocket.close(1002, "auth failed");
        }
    }

    public void reconnect() {
        if (mClient != null && mRequest != null) {
            isOpening.set(true);
            mWebSocket = mClient.newWebSocket(mRequest, this);
        }
    }

    public void send(String string) {
        if (mWebSocket != null) {
            mWebSocket.send(string);
        }
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        // WebSocket连接已打开 开始鉴权
        if (mCallback != null) {
            mCallback.onOpen();
        }
        isOpening.set(true);
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        // 收到WebSocket消息
        if (mCallback != null) {
            mCallback.onMessage(text);
        }
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        isOpening.set(false);
        // WebSocket连接已关闭
        if (mCallback != null) {
            mCallback.onClose("onClose" + reason  + "code: " + code);
        }
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable ex, Response response) {
        isOpening.set(false);
        // 连接失败
        if (mCallback != null) {
            if (ex != null) {
                mCallback.onClose("onError: " + ex);
            } else {
                mCallback.onClose("onError: " + null);
            }
        }
    }
    public boolean isOpen() {
        return isOpening.get();
    }
}
