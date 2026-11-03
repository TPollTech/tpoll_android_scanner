package com.tpoll.tempoitacurubi;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.graphics.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    private WebView browser;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(7, 19, 39));
        getWindow().setNavigationBarColor(Color.rgb(9, 24, 49));
        browser = new WebView(this);
        browser.setBackgroundColor(Color.rgb(7, 19, 39));
        browser.setWebChromeClient(new WebChromeClient());
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("https://tempo-itacurubi.local/")) return false;
                openExternal(url);
                return true;
            }
        });
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        browser.addJavascriptInterface(new ExternalLinks(), "AndroidBridge");
        setContentView(browser);
        try (InputStream in = getAssets().open("index.html")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            browser.loadDataWithBaseURL("https://tempo-itacurubi.local/",
                    new String(out.toByteArray(), StandardCharsets.UTF_8),
                    "text/html", "UTF-8", null);
        } catch (Exception ex) {
            browser.loadData("Não foi possível abrir o aplicativo.", "text/plain", "UTF-8");
        }
    }
    private void openExternal(String url) {
        if (url == null || !url.startsWith("https://")) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception ignored) { }
    }
    private final class ExternalLinks {
        @JavascriptInterface public void openUrl(String url) {
            runOnUiThread(() -> openExternal(url));
        }
    }
    @Override public void onBackPressed() {
        if (browser != null && browser.canGoBack()) browser.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if (browser != null) { browser.destroy(); browser = null; }
        super.onDestroy();
    }
}
