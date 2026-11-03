package com.dongffl.dfweb.client;

import android.content.Context;
import android.net.http.SslError;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.dongffl.dfweb.pay.DFPayEscapeCallback;
import com.dongffl.dfweb.pay.DFPayEscapeHelper;

/**
 * 业务 WebViewClient：内置 H5 支付逃逸（checkmweb 新开支付页 + 支付 scheme 外抛）。
 * <p>
 * 适用于三方 App 嵌我方收银台 H5、商户为百福得的 MWEB / H5 支付场景。
 */
public class DFWebviewClient extends WebViewClient {

    private DFPayEscapeCallback payEscapeCallback;
    private boolean interceptCheckmweb = true;

    public DFWebviewClient() {
    }

    /**
     * 自定义 checkmweb 打开逻辑；返回 true 表示宿主已处理。
     */
    public void setPayEscapeCallback(DFPayEscapeCallback callback) {
        this.payEscapeCallback = callback;
    }

    /**
     * 是否拦截微信 checkmweb 并新开 {@link com.dongffl.dfweb.pay.DFPayWebActivity}。
     * 默认 true。若业务页本身已在顶层非沙箱 WebView 且可直跳 weixin://，可设为 false，仅保留 scheme 逃逸。
     */
    public void setInterceptCheckmweb(boolean interceptCheckmweb) {
        this.interceptCheckmweb = interceptCheckmweb;
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        super.onReceivedError(view, request, error);
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        // 收银台域名偶发证书链问题由宿主按需收紧；默认继续以保障联调
        if (handler != null) {
            handler.proceed();
        }
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        if (request == null || request.getUrl() == null) {
            return false;
        }
        return handleUrl(view, request.getUrl().toString());
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return handleUrl(view, url);
    }

    /**
     * @return true 表示已拦截，WebView 不应继续导航
     */
    protected boolean handleUrl(WebView view, String url) {
        if (url == null || view == null) {
            return false;
        }
        Context context = view.getContext();

        // 1) 支付 / 自定义 scheme → 系统拉起
        if (DFPayEscapeHelper.isPayScheme(url) || DFPayEscapeHelper.shouldEscapeNonHttp(url)) {
            DFPayEscapeHelper.openByScheme(context, url);
            return true;
        }

        // 2) 微信 checkmweb → 非沙箱支付页（避免 iframe 沙箱拦 top.location）
        if (interceptCheckmweb && DFPayEscapeHelper.isWechatCheckmweb(url)) {
            if (payEscapeCallback != null && payEscapeCallback.onOpenPayMiddlePage(context, url)) {
                return true;
            }
            DFPayEscapeHelper.openPayWebActivity(context, url);
            return true;
        }

        return false;
    }
}
