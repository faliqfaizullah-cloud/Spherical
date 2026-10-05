package com.spherical.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private WebView web;
    private Vibrator vib;
    private final Capture cap = new Capture();
    private final AtomicBoolean pending = new AtomicBoolean(false);
    private ValueCallback<Uri[]> fileCb;
    private String pendingMode;
    private boolean capLost;
    private SharedPreferences prefs;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(SphericalWidget.PREFS, MODE_PRIVATE);
        applyOri(prefs.getInt("ori", 1));   // landscape by default; rotation can be enabled from the menu
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        web = new WebView(this);
        web.setBackgroundColor(prefs.getInt("night", 0) == 1 ? 0xFF151410 : 0xFFE0DDCF);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try { startActivityForResult(p.createIntent(), 5); }
                catch (Exception e) { fileCb = null; return false; }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Native");
        setContentView(web);
        web.loadUrl("file:///android_asset/www/index.html");
        immersive();
    }

    class Bridge {
        @JavascriptInterface public void haptic(int t) { buzz(t); }
        @JavascriptInterface public void capture(final String mode) { runOnUiThread(() -> startCapture(mode)); }
        @JavascriptInterface public void stopCapture() { cap.stop(); }
        @JavascriptInterface public void orientation(final int m) {
            prefs.edit().putInt("ori", m).apply();
            runOnUiThread(() -> applyOri(m));
        }
        @JavascriptInterface public void theme(int night) { prefs.edit().putInt("night", night).apply(); }
        @JavascriptInterface public void widget(String json) {
            getSharedPreferences(SphericalWidget.PREFS, MODE_PRIVATE).edit().putString(SphericalWidget.KEY, json).apply();
            SphericalWidget.refreshAll(MainActivity.this);
        }
    }

    private void applyOri(int m) {
        setRequestedOrientation(m == 0 ? ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                : m == 2 ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    private void buzz(int t) {
        if (vib == null || !vib.hasVibrator()) return;
        t = Math.max(0, Math.min(4, t));
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                int[] fx = {VibrationEffect.EFFECT_TICK, VibrationEffect.EFFECT_CLICK, VibrationEffect.EFFECT_CLICK,
                            VibrationEffect.EFFECT_HEAVY_CLICK, VibrationEffect.EFFECT_DOUBLE_CLICK};
                vib.vibrate(VibrationEffect.createPredefined(fx[t]));
            } else if (Build.VERSION.SDK_INT >= 26) {
                long[] ms = {6, 12, 20, 32, 40};
                int[] amp = {70, 120, 170, 255, 200};
                vib.vibrate(VibrationEffect.createOneShot(ms[t], amp[t]));
            } else {
                vib.vibrate(new long[]{10, 20, 30, 40, 50}[t]);
            }
        } catch (Exception ignored) {}
    }

    private void js(final String code) { web.post(() -> web.evaluateJavascript(code, null)); }

    private final Capture.CB cb = bins -> {
        if (!pending.compareAndSet(false, true)) return;   // drop frames if the page is busy
        final String b64 = Base64.encodeToString(bins, Base64.NO_WRAP);
        web.post(() -> { web.evaluateJavascript("window.fftIn&&fftIn('" + b64 + "')", null); pending.set(false); });
    };

    private void startCapture(String mode) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingMode = mode;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7);
            return;
        }
        boolean ok = mode.equals("sys") ? cap.startSys(cb) : cap.startMic(cb);
        js("window.capState&&capState('" + mode + "'," + ok + ")");
    }

    @Override public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        if (rc != 7) return;
        String m = pendingMode == null ? "mic" : pendingMode;
        pendingMode = null;
        if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) startCapture(m);
        else js("window.capState&&capState('" + m + "',false)");
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == 5 && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCb = null;
        } else super.onActivityResult(req, res, data);
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) immersive(); }

    @Override protected void onPause() {
        super.onPause();
        if (cap.active()) { cap.stop(); capLost = true; }
        web.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        web.onResume();
        if (capLost) { capLost = false; js("window.capLost&&capLost()"); }
    }

    @Override protected void onDestroy() { cap.stop(); super.onDestroy(); }

    @Override public void onBackPressed() {
        web.evaluateJavascript("window.onBack?window.onBack():false", v -> {
            if (!"true".equals(v)) finish();
        });
    }
}
