package com.dongffl.dfweb.pay;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.net.URLDecoder;
import java.util.Locale;

/**
 * 三方 App 嵌我方 H5 收银台时的支付逃逸工具。
 * <p>
 * 商户身份始终为百福得（服务端下单）；宿主只负责：
 * 1) 拦截微信支付中间页 checkmweb，在非沙箱容器加载；
 * 2) 拦截 weixin:// / alipays:// 等 scheme，用系统 Intent 拉起对应 App。
 * <p>
 * 不引入微信/支付宝官方 OpenSDK，不在三方包内使用百福得 appId 做原生 App 支付。
 */
public final class DFPayEscapeHelper {

    private static final String TAG = "DFPayEscape";

    public static final String EXTRA_MWEB_URL = "df_pay_mweb_url";
    public static final String EXTRA_REFERER_BASE = "df_pay_referer_base";

    /** 默认收银台 Referer（正式） */
    public static final String DEFAULT_REFERER_PROD = "https://mcashier.dongfangfuli.com/";
    /** 默认收银台 Referer（预发） */
    public static final String DEFAULT_REFERER_STAGE = "https://mcashier-stage.dongfangfuli.com/";

    private DFPayEscapeHelper() {
    }

    public static boolean isHttpOrHttps(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /**
     * 微信 H5 支付中间页：https://wx.tenpay.com/.../checkmweb?...
     */
    public static boolean isWechatCheckmweb(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        return lower.contains("wx.tenpay.com") && lower.contains("checkmweb");
    }

    /**
     * 需要交给系统 / 第三方 App 的支付相关 scheme（不可让 WebView 自行加载）。
     */
    public static boolean isPayScheme(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        return lower.startsWith("weixin://")
                || lower.startsWith("wechat://")
                || lower.startsWith("alipays://")
                || lower.startsWith("alipay://")
                || lower.startsWith("alipayqr://")
                || lower.startsWith("intent://");
    }

    /**
     * 非 http(s) 且非空白：一般应外抛给系统处理。
     */
    public static boolean shouldEscapeNonHttp(String url) {
        return !TextUtils.isEmpty(url) && !isHttpOrHttps(url);
    }

    /**
     * 从 checkmweb URL 的 redirect_url 解析商户 Referer 根地址。
     */
    public static String resolveRefererBase(String mwebUrl) {
        if (TextUtils.isEmpty(mwebUrl)) {
            return DEFAULT_REFERER_STAGE;
        }
        try {
            Uri uri = Uri.parse(mwebUrl);
            String redirect = uri.getQueryParameter("redirect_url");
            if (!TextUtils.isEmpty(redirect)) {
                String decoded = redirect;
                try {
                    decoded = URLDecoder.decode(redirect, "UTF-8");
                } catch (Exception ignore) {
                    // keep raw
                }
                Uri r = Uri.parse(decoded);
                if (r != null && !TextUtils.isEmpty(r.getScheme()) && !TextUtils.isEmpty(r.getHost())) {
                    return r.getScheme() + "://" + r.getHost() + "/";
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "resolveRefererBase failed: " + e.getMessage());
        }
        String lower = mwebUrl.toLowerCase(Locale.US);
        if (lower.contains("dongfangfuli.com") && !lower.contains("stage") && !lower.contains("-test")) {
            return DEFAULT_REFERER_PROD;
        }
        return DEFAULT_REFERER_STAGE;
    }

    /**
     * Referer 中转页 HTML：以商户域为 base，再 location.replace 到 checkmweb。
     */
    public static String buildRefererBridgeHtml(String checkmwebUrl) {
        String safe = checkmwebUrl == null ? "" : checkmwebUrl
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "")
                .replace("\r", "");
        return "<!DOCTYPE html><html><head>"
                + "<meta charset=\"utf-8\"/>"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no\"/>"
                + "<title>pay bridge</title></head>"
                + "<body style=\"font-family:sans-serif;padding:16px;color:#333\">"
                + "<p>正在跳转支付中间页…</p>"
                + "<script>location.replace('" + safe + "');</script>"
                + "</body></html>";
    }

    /**
     * 用 Intent.ACTION_VIEW 拉起外部 App（微信 / 支付宝等）。
     *
     * @return true 已尝试启动；false 启动失败
     */
    public static boolean openByScheme(Context context, String schemeUrl) {
        if (context == null || TextUtils.isEmpty(schemeUrl)) {
            return false;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(schemeUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PackageManager pm = context.getPackageManager();
            if (intent.resolveActivity(pm) == null) {
                Log.w(TAG, "no activity for scheme: " + schemeUrl);
                Toast.makeText(context.getApplicationContext(),
                        tipForMissingApp(schemeUrl), Toast.LENGTH_SHORT).show();
                return false;
            }
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "openByScheme failed: " + e.getMessage());
            Toast.makeText(context.getApplicationContext(),
                    tipForMissingApp(schemeUrl), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private static String tipForMissingApp(String schemeUrl) {
        String lower = schemeUrl == null ? "" : schemeUrl.toLowerCase(Locale.US);
        if (lower.startsWith("weixin://") || lower.startsWith("wechat://")) {
            return "未安装微信或无法打开微信支付";
        }
        if (lower.startsWith("alipay")) {
            return "未安装支付宝或无法打开支付宝支付";
        }
        return "无法打开支付应用";
    }

    /**
     * 打开 SDK 内置非沙箱支付页（Referer 桥 + scheme 拦截）。
     */
    public static void openPayWebActivity(Context context, String mwebUrl) {
        if (context == null || TextUtils.isEmpty(mwebUrl)) {
            return;
        }
        Intent intent = new Intent(context, DFPayWebActivity.class);
        intent.putExtra(EXTRA_MWEB_URL, mwebUrl);
        intent.putExtra(EXTRA_REFERER_BASE, resolveRefererBase(mwebUrl));
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }
}
