package com.dongffl.dfweb.pay;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 非沙箱支付容器：加载微信 checkmweb（经商户 Referer 中转），拦截 weixin:// / alipays:// 拉起对应 App。
 * <p>
 * 由外壳 WebView 拦截到 checkmweb 后启动；不依赖 JSBridge，不嵌入官方支付 SDK。
 */
public class DFPayWebActivity extends AppCompatActivity {

    private static final String TAG = "DFPayWebActivity";

    private WebView webView;
    private String mwebUrl;
    private String refererBase;
    private boolean jumpTried;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mwebUrl = getIntent().getStringExtra(DFPayEscapeHelper.EXTRA_MWEB_URL);
        refererBase = getIntent().getStringExtra(DFPayEscapeHelper.EXTRA_REFERER_BASE);
        if (TextUtils.isEmpty(refererBase)) {
            refererBase = DFPayEscapeHelper.resolveRefererBase(mwebUrl);
        }
        if (TextUtils.isEmpty(mwebUrl) || !DFPayEscapeHelper.isHttpOrHttps(mwebUrl)) {
            Toast.makeText(this, "缺少有效支付中间页 URL", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        initWebView();
        startLoadWithReferer();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setLoadsImagesAutomatically(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) {
                    return false;
                }
                return handleUrl(request.getUrl().toString());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.contains("wx.tenpay.com")) {
                    tryHarvestWeixinScheme();
                }
            }
        });
    }

    private boolean handleUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        if (DFPayEscapeHelper.isPayScheme(url) || DFPayEscapeHelper.shouldEscapeNonHttp(url)) {
            jumpTried = true;
            DFPayEscapeHelper.openByScheme(DFPayWebActivity.this, url);
            return true;
        }
        return false;
    }

    private void startLoadWithReferer() {
        jumpTried = false;
        try {
            String html = DFPayEscapeHelper.buildRefererBridgeHtml(mwebUrl);
            // baseUrl / historyUrl 使用商户收银台域，保证中间页拿到正确 Referer
            webView.loadDataWithBaseURL(refererBase, html, "text/html", "UTF-8", refererBase);
            Log.i(TAG, "load checkmweb via referer bridge, referer=" + refererBase);
        } catch (Exception e) {
            Log.e(TAG, "referer bridge failed, fallback loadUrl: " + e.getMessage());
            webView.loadUrl(mwebUrl);
        }
    }

    /** 中间页偶发不自动跳 weixin://，从页面中解析后拉起。 */
    private void tryHarvestWeixinScheme() {
        if (jumpTried || webView == null) {
            return;
        }
        String js = "(function(){"
                + "try{"
                + "var a=document.querySelector('a[href^=\"weixin://\"]');"
                + "if(a&&a.href){return a.href;}"
                + "var html=document.documentElement?document.documentElement.innerHTML:'';"
                + "var m=html.match(/weixin:\\/\\/wap\\/pay[^\"'\\s]*/);"
                + "if(m){return m[0];}"
                + "return '';"
                + "}catch(e){return '';}"
                + "})();";
        webView.evaluateJavascript(js, value -> {
            if (jumpTried) {
                return;
            }
            if (TextUtils.isEmpty(value) || "null".equals(value) || "\"\"".equals(value)) {
                return;
            }
            String scheme = value;
            if (scheme.startsWith("\"") && scheme.endsWith("\"") && scheme.length() >= 2) {
                scheme = scheme.substring(1, scheme.length() - 1);
            }
            scheme = scheme.replace("\\u003d", "=").replace("\\/", "/");
            if (scheme.startsWith("weixin://")) {
                jumpTried = true;
                Log.i(TAG, "harvest weixin scheme ok");
                DFPayEscapeHelper.openByScheme(DFPayWebActivity.this, scheme);
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
