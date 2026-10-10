package com.marvel.library;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.net.HttpURLConnection;
import java.net.URL;

/** Shows the Marvel Library website full screen. One app for Android phones and Android TV. */
public class MainActivity extends Activity {

    private static final String SETUP = "file:///android_asset/setup.html";
    private static final String ERROR = "file:///android_asset/error.html";

    private WebView web;
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customCb;
    private SharedPreferences prefs;
    private boolean tv;
    private String baseUrl = "";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiModeManager ui = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        tv = ui != null && ui.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        prefs = getSharedPreferences("marvel", MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);   // videos may start without an extra tap
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setUserAgentString(s.getUserAgentString() + " MarvelLibraryApp/1.0" + (tv ? " MarvelLibraryTV" : ""));
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);   // keeps the password login

        web.addJavascriptInterface(new Bridge(), "AndroidApp");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("file".equals(u.getScheme())) return false;   // the app's own setup / error pages
                String host = baseUrl.isEmpty() ? null : Uri.parse(baseUrl).getHost();
                if (host != null && host.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;   // links to other sites open outside the app
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) v.loadUrl(ERROR);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView = view;
                customCb = cb;
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                web.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() { hideCustom(); }
        });
        web.setFocusable(true);
        web.requestFocus();

        if (tv) immersive(true);
        connect();
    }

    /** Picks the home address when it answers, otherwise the internet address, then opens it. */
    private void connect() {
        final String home = prefs.getString("home", getString(R.string.default_home_url));
        final String away = prefs.getString("away", getString(R.string.default_away_url));
        if (home.isEmpty() && away.isEmpty()) { web.loadUrl(SETUP); return; }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String url = away.isEmpty() ? home : home.isEmpty() ? away : (reachable(home) ? home : away);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() { baseUrl = url; web.loadUrl(url); }
                });
            }
        }).start();
    }

    private boolean reachable(String u) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(2000);
            c.setReadTimeout(2000);
            c.setInstanceFollowRedirects(false);
            c.getResponseCode();   // any answer means the server is there
            c.disconnect();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String normalize(String u) {
        u = u == null ? "" : u.trim();
        if (u.isEmpty() || u.matches("(?i)^https?://.*")) return u;
        return (u.matches("^[0-9.]+(:[0-9]+)?(/.*)?$") ? "http://" : "https://") + u;   // addresses like 192.168.1.20:3000 are plain http
    }

    @SuppressWarnings("deprecation")
    private void immersive(boolean on) {
        getWindow().getDecorView().setSystemUiVisibility(on
                ? View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                : View.SYSTEM_UI_FLAG_VISIBLE);
    }

    private void hideCustom() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        web.setVisibility(View.VISIBLE);
        if (customCb != null) customCb.onCustomViewHidden();
        customCb = null;
        web.requestFocus();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && tv) immersive(true);
    }

    /** The Back button first closes menus and the player inside the page, then leaves the app. */
    @Override
    public void onBackPressed() {
        if (customView != null) { hideCustom(); return; }
        web.evaluateJavascript("(window.__back && window.__back()) === true", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if (!"true".equals(value)) {
                    if (web.canGoBack()) web.goBack(); else finish();   // this callback already runs on the main thread
                }
            }
        });
    }

    /** Remote-control play, pause, fast-forward and rewind keys are passed to the player. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            String a = null;
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_HEADSETHOOK: a = "playpause"; break;
                case KeyEvent.KEYCODE_MEDIA_PLAY: a = "play"; break;
                case KeyEvent.KEYCODE_MEDIA_PAUSE: a = "pause"; break;
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: a = "ff"; break;
                case KeyEvent.KEYCODE_MEDIA_REWIND: a = "rw"; break;
                case KeyEvent.KEYCODE_MEDIA_NEXT: a = "next"; break;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS: a = "prev"; break;
                default: break;
            }
            if (a != null) {
                web.evaluateJavascript("window.__media && window.__media('" + a + "')", null);
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    protected void onPause() { super.onPause(); web.onPause(); }

    @Override
    protected void onResume() { super.onResume(); web.onResume(); }

    @Override
    protected void onDestroy() {
        root.removeAllViews();
        web.destroy();
        super.onDestroy();
    }

    /** Functions the website and the setup page can call. */
    private class Bridge {
        @JavascriptInterface
        public String get(String key) {
            return prefs.getString(key, getString("home".equals(key) ? R.string.default_home_url : R.string.default_away_url));
        }

        @JavascriptInterface
        public void save(String home, String away) {
            prefs.edit().putString("home", normalize(home)).putString("away", normalize(away)).apply();
            runOnUiThread(new Runnable() {
                @Override
                public void run() { connect(); }
            });
        }

        /** The website reports the PC's newest internet address, so a restart of the tunnel does not break the app. */
        @JavascriptInterface
        public void away(String url) {
            String n = normalize(url);
            if (!n.isEmpty() && !n.equals(prefs.getString("away", ""))) prefs.edit().putString("away", n).apply();
        }

        @JavascriptInterface
        public void retry() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() { connect(); }
            });
        }

        @JavascriptInterface
        public void settings() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() { web.loadUrl(SETUP); }
            });
        }

        /** Called when a video opens or closes: phones turn sideways and hide the bars while playing. */
        @JavascriptInterface
        public void player(final boolean playing) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (tv) return;
                    setRequestedOrientation(playing ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    immersive(playing);
                }
            });
        }
    }
}
