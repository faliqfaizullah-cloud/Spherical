package com.spherical.app;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.provider.Settings;
import android.app.PendingIntent;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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
    private static final String ACTION_INSTALL = "com.spherical.app.INSTALL_DONE";
    private volatile boolean updBusy;
    private String updUrl;
    private long updSize;

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
        IntentFilter f = new IntentFilter(ACTION_INSTALL);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(instRcv, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(instRcv, f);
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
        @JavascriptInterface public String version() {
            try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
            catch (Exception e) { return ""; }
        }
        @JavascriptInterface public void checkUpdate() { new Thread(MainActivity.this::doCheck).start(); }
        @JavascriptInterface public void installUpdate() { runOnUiThread(MainActivity.this::startInstall); }
        @JavascriptInterface public void theme(int night) { prefs.edit().putInt("night", night).apply(); }
        @JavascriptInterface public void widget(String json) {
            getSharedPreferences(SphericalWidget.PREFS, MODE_PRIVATE).edit().putString(SphericalWidget.KEY, json).apply();
            SphericalWidget.refreshAll(MainActivity.this);
        }
    }

    // ------------------------------------------------------------------ in-app updates
    private String readRepo() {
        try (InputStream in = getAssets().open("repo.txt")) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[256]; int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            String r = bo.toString("UTF-8").trim();
            return r.contains("/") ? r : null;
        } catch (Exception e) { return null; }
    }

    private static int codeFromTag(String tag) {
        try {
            String t = tag.startsWith("v") ? tag.substring(1) : tag;
            String[] p = t.split("\\.");
            int a = Integer.parseInt(p[0]), b = p.length > 1 ? Integer.parseInt(p[1]) : 0, c = p.length > 2 ? Integer.parseInt(p[2]) : 0;
            return a * 10000 + b * 100 + c;
        } catch (Exception e) { return -1; }
    }

    private void upd(String state, String cur, String latest, String notes) {
        try {
            JSONObject r = new JSONObject();
            r.put("state", state);
            if (cur != null) r.put("cur", cur);
            if (latest != null) r.put("latest", latest);
            if (notes != null) r.put("notes", notes);
            js("window.updInfo&&updInfo(" + JSONObject.quote(r.toString()) + ")");
        } catch (Exception ignored) {}
    }

    private void doCheck() {
        try {
            String repo = readRepo();
            if (repo == null) { upd("nosrc", null, null, null); return; }
            HttpURLConnection c = (HttpURLConnection) new URL("https://api.github.com/repos/" + repo + "/releases/latest").openConnection();
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("User-Agent", "Spherical");
            c.setConnectTimeout(10000); c.setReadTimeout(15000);
            if (c.getResponseCode() != 200) { upd("err", null, null, null); return; }
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            }
            JSONObject j = new JSONObject(bo.toString("UTF-8"));
            String tag = j.optString("tag_name");
            String url = null; long size = 0;
            JSONArray as = j.optJSONArray("assets");
            if (as != null) for (int i = 0; i < as.length(); i++) {
                JSONObject a = as.getJSONObject(i);
                if (a.optString("name").endsWith(".apk")) { url = a.optString("browser_download_url"); size = a.optLong("size"); break; }
            }
            int latest = codeFromTag(tag);
            @SuppressWarnings("deprecation") int cur = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            String curName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            if (url == null || latest < 0) { upd("err", null, null, null); return; }
            if (latest > cur) {
                updUrl = url; updSize = size;
                String notes = j.optString("body", "");
                if (notes.length() > 240) notes = notes.substring(0, 240) + "...";
                upd("avail", curName, tag.startsWith("v") ? tag.substring(1) : tag, notes);
            } else upd("none", curName, null, null);
        } catch (Exception e) { upd("err", null, null, null); }
    }

    private void updFail(String msg) { js("window.updFail&&updFail(" + JSONObject.quote(msg) + ")"); }

    private void startInstall() {
        if (updBusy) return;
        if (updUrl == null) { updFail("no update found, check again"); return; }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {}
            js("window.updNeed&&updNeed()");
            return;
        }
        updBusy = true;
        final String url = updUrl;
        final long hint = updSize;
        new Thread(() -> {
            PackageInstaller.Session s = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "Spherical");
                c.setConnectTimeout(15000); c.setReadTimeout(30000);
                if (c.getResponseCode() != 200) throw new Exception("download failed (" + c.getResponseCode() + ")");
                long total = c.getContentLengthLong();
                if (total <= 0) total = hint;
                PackageInstaller pi = getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                sp.setAppPackageName(getPackageName());
                if (total > 0) sp.setSize(total);
                if (Build.VERSION.SDK_INT >= 31) sp.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                int id = pi.createSession(sp);
                s = pi.openSession(id);
                try (InputStream in = new BufferedInputStream(c.getInputStream(), 65536);
                     OutputStream out = s.openWrite("Spherical.apk", 0, total > 0 ? total : -1)) {
                    byte[] buf = new byte[65536]; int n, last = -1; long done = 0;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n); done += n;
                        if (total > 0) {
                            int p = (int) Math.min(100, done * 100 / total);
                            if (p != last) { last = p; js("window.updProg&&updProg(" + p + ")"); }
                        }
                    }
                    s.fsync(out);
                }
                Intent it = new Intent(ACTION_INSTALL).setPackage(getPackageName());
                int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                PendingIntent pend = PendingIntent.getBroadcast(MainActivity.this, id, it, fl);
                js("window.updProg&&updProg(101)");
                s.commit(pend.getIntentSender());
            } catch (Exception e) {
                if (s != null) { try { s.abandon(); } catch (Exception ignored) {} }
                updFail(e.getMessage() == null ? "update failed" : e.getMessage());
            } finally {
                if (s != null) { try { s.close(); } catch (Exception ignored) {} }
                updBusy = false;
            }
        }).start();
    }

    private final BroadcastReceiver instRcv = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent i) {
            int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
            if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent conf = Build.VERSION.SDK_INT >= 33
                        ? i.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class)
                        : (Intent) i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (conf != null) { conf.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(conf); }
            } else if (st == PackageInstaller.STATUS_SUCCESS) {
                js("window.updProg&&updProg(102)");
            } else if (st == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE) {
                updFail("signature differs from the installed copy: uninstall once, then install this version");
            } else {
                String m = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                updFail(m == null ? "install failed" : m);
            }
        }
    };

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

    @Override protected void onDestroy() {
        cap.stop();
        try { unregisterReceiver(instRcv); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        web.evaluateJavascript("window.onBack?window.onBack():false", v -> {
            if (!"true".equals(v)) finish();
        });
    }
}
