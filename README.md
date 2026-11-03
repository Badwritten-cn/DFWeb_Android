# DFWeb_Android

东福 WebView 集成 SDK（三方 App 嵌百福得 H5）。

## 能力概览

| 能力 | 方式 | 说明 |
|------|------|------|
| 定位 / 扫码 | JSBridge + 模块 Handler | `getGpsLoc` / `openAppScan` |
| **H5 支付逃逸** | WebViewClient URL 拦截 | 我方收银台 + 我方商户；宿主拦 checkmweb / `weixin://` / `alipays://` |

## 支付逃逸（推荐接入）

商户身份始终为百福得（服务端下单）。**不要**在三方包内嵌入微信/支付宝官方 OpenSDK 并用百福得 appId 做原生 App 支付。

```java
webView.setWebViewClient(new DFWebviewClient());
```

行为：

1. 拦到 `https://wx.tenpay.com/.../checkmweb` → 打开 `DFPayWebActivity`（Referer 中转后加载）
2. 拦到 `weixin://` / `alipays://` 等 → `Intent.ACTION_VIEW` 拉起对应 App

可选：自定义 checkmweb 打开方式

```java
DFWebviewClient client = new DFWebviewClient();
client.setPayEscapeCallback((context, mwebUrl) -> {
    // 打开你们自己的支付 Web 页，return true
    return false; // false = 使用 SDK 默认 DFPayWebActivity
});
webView.setWebViewClient(client);
```

详细说明见工作流仓库文档：`docs/客户App-Android嵌H5-支付逃逸对接说明.md`。
