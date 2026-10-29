package com.envarg.signals;

import android.app.Activity;
import android.app.BiometricPrompt;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
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
import android.graphics.Color;
import android.widget.TextView;

public class MainActivity extends Activity {
  private WebView web;
  private FrameLayout root;
  private TextView lockScreen;
  private boolean unlocked = false;
  private boolean authInProgress = false;
  private boolean webLoaded = false;
  private static final int DEVICE_UNLOCK = 81;
  private static final String URL = "https://envarg-v9-signal.hatchable.site";

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setStatusBarColor(0xff0a1328);
    root = new FrameLayout(this);
    web = new WebView(this);
    web.setVisibility(View.GONE);
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
    lockScreen = new TextView(this);
    lockScreen.setText("ENVARG SIGNALS\n\nUnlock with fingerprint or phone screen lock");
    lockScreen.setTextColor(Color.WHITE);
    lockScreen.setTextSize(20);
    lockScreen.setGravity(android.view.Gravity.CENTER);
    lockScreen.setBackgroundColor(0xff0a1328);
    lockScreen.setOnClickListener(v -> requestUnlock());
    root.addView(web);
    root.addView(lockScreen);
    setContentView(root);
  }

  @Override protected void onResume() {
    super.onResume();
    if(!unlocked && !authInProgress) requestUnlock();
  }

  private void requestUnlock() {
    if(unlocked || authInProgress) return;
    authInProgress = true;
    if(Build.VERSION.SDK_INT >= 28) {
      try {
        BiometricPrompt prompt = new BiometricPrompt.Builder(this)
          .setTitle("Unlock ENVARG Signals")
          .setSubtitle("Fingerprint / biometric verification")
          .setNegativeButton("Use phone PIN",getMainExecutor(),(d,which) -> {
            authInProgress = false;
            deviceUnlock();
          }).build();
        prompt.authenticate(new CancellationSignal(),getMainExecutor(),new BiometricPrompt.AuthenticationCallback() {
          @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
            authInProgress = false;
            unlock();
          }
          @Override public void onAuthenticationError(int code,CharSequence message) {
            authInProgress = false;
            if(code == BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS ||
               code == BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE ||
               code == BiometricPrompt.BIOMETRIC_ERROR_NO_HARDWARE)
              deviceUnlock();
          }
        });
      } catch(Exception e) { authInProgress = false; deviceUnlock(); }
    } else { authInProgress = false; deviceUnlock(); }
  }

  private void deviceUnlock() {
    KeyguardManager km = (KeyguardManager)getSystemService(Context.KEYGUARD_SERVICE);
    if(km != null && km.isDeviceSecure()) {
      Intent i = km.createConfirmDeviceCredentialIntent("Unlock ENVARG Signals","Use your phone PIN, pattern or password");
      if(i != null) { authInProgress = true; startActivityForResult(i,DEVICE_UNLOCK); return; }
    }
    Toast.makeText(this,"Set a phone screen lock to protect ENVARG",Toast.LENGTH_LONG).show();
  }

  @Override protected void onActivityResult(int request,int result,Intent data) {
    super.onActivityResult(request,result,data);
    if(request == DEVICE_UNLOCK) {
      authInProgress = false;
      if(result == RESULT_OK) unlock();
    }
  }

  private void unlock() {
    unlocked = true;
    lockScreen.setVisibility(View.GONE);
    web.setVisibility(View.VISIBLE);
    if(!webLoaded) { webLoaded = true; web.loadUrl(URL); }
  }

  @Override protected void onPause() {
    CookieManager.getInstance().flush();
    unlocked = false;
    web.setVisibility(View.GONE);
    lockScreen.setVisibility(View.VISIBLE);
    super.onPause();
  }

  @Override public void onBackPressed() {
    if(unlocked && web.canGoBack()) web.goBack();
    else super.onBackPressed();
  }
}
