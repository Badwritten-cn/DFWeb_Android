package com.dongffl.dfweb.pay;

import android.content.Context;

/**
 * 宿主可接管微信 checkmweb 等支付中间页的打开方式。
 * 未设置时，{@link DFWebviewClient} 默认打开 {@link DFPayWebActivity}。
 */
public interface DFPayEscapeCallback {

    /**
     * @param context 当前 WebView Context
     * @param mwebUrl 例如 https://wx.tenpay.com/.../checkmweb?...
     * @return true 表示已处理（WebView 不再加载该 URL）；false 表示交回默认逻辑
     */
    boolean onOpenPayMiddlePage(Context context, String mwebUrl);
}
