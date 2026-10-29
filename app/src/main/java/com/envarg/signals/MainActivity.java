package com.envarg.signals;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.net.Uri;
import android.widget.Toast;
import android.view.View;
import android.widget.FrameLayout;

public class MainActivity extends Activity {
  private WebView web;
  private FrameLayout root;
  private static final String URL = "https://envarg-v9-signal.hatchable.site";

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setStatusBarColor(0xff0a1328);
    root = new FrameLayout(this);
    web = new WebView(this);
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setDomStorageEnabled(true);
    web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    CookieManager.getInstance().setAcceptCookie(true);
    CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
    web.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        Uri uri=request.getUrl();
        String host=uri.getHost();
        if("https".equalsIgnoreCase(uri.getScheme()) && host!=null &&
          (host.equals("envarg-v9-signal.hatchable.site") || host.equals("hatchable.com") || host.endsWith(".hatchable.com") || host.equals("accounts.google.com") || host.endsWith(".google.com") || host.equals("google.com"))) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW,uri)); } catch(Exception ignored) {}
        return true;
      }
    });
    web.addJavascriptInterface(new Object() {
      @JavascriptInterface public void start() {
        runOnUiThread(() -> {
          if(Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},7);
          try {
            startForegroundService(new Intent(MainActivity.this,MonitorService.class));
            Toast.makeText(MainActivity.this,"Monitor requested · check notification for API status",Toast.LENGTH_LONG).show();
          } catch(Exception e) {
            Toast.makeText(MainActivity.this,"Monitor failed: "+e.getMessage(),Toast.LENGTH_LONG).show();
          }
        });
      }
      @JavascriptInterface public void stop() {
        runOnUiThread(() -> stopService(new Intent(MainActivity.this,MonitorService.class)));
      }
    },"EnvargNative");
    root.addView(web);
    setContentView(root);
    web.loadUrl(URL);
  }

  @Override protected void onPause() {
    CookieManager.getInstance().flush();
    super.onPause();
  }

  @Override public void onBackPressed() {
    if(web.canGoBack()) web.goBack();
    else super.onBackPressed();
  }
}
