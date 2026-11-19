package com.spherical.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

/** Home-screen widget: cream card showing pattern, key, BPM and the drawn density curves with their active notes. */
public class SphericalWidget extends AppWidgetProvider {
    static final String PREFS = "sph", KEY = "w";
    private static final String[] NAMES = {"KICK", "PERC", "HAT", "SUB", "SYN", "BELL", "CHORD", "CLAP", "PAD", "TIDE", "SPARK"};
    private static final int INK = 0xFF1F1F1B, GREY = 0xFF8A887B;

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { refresh(c, m, ids); }
    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) { refresh(c, m, new int[]{id}); }

    public static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, SphericalWidget.class));
        if (ids.length > 0) refresh(c, m, ids);
    }

    static void refresh(Context c, AppWidgetManager m, int[] ids) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONObject o = null;
        try { String s = sp.getString(KEY, null); if (s != null) o = new JSONObject(s); } catch (Exception ignored) {}
        float d = c.getResources().getDisplayMetrics().density;
        for (int id : ids) {
            Bundle opt = m.getAppWidgetOptions(id);
            int wd = opt.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250);
            int hd = opt.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110);
            float k = Math.min(1f, 900f / (wd * d));
            int w = Math.max(200, (int) (wd * d * k)), h = Math.max(100, (int) (hd * d * k));
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);
            rv.setImageViewBitmap(R.id.wimg, render(o, w, h));
            Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            rv.setOnClickPendingIntent(R.id.wroot, PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            m.updateAppWidget(id, rv);
        }
    }

    static Bitmap render(JSONObject o, int w, int h) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        float u = h / 300f, pad = h * .07f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.MONOSPACE);
        JSONArray tr = o == null ? null : o.optJSONArray("t");
        if (o == null || tr == null) {
            p.setColor(GREY); p.setTextSize(h * .09f); p.setTextAlign(Paint.Align.CENTER);
            cv.drawText("open spherical", w / 2f, h / 2f, p);
            return bmp;
        }
        String pat = o.optString("p", "SPHERICAL"), key = o.optString("k", "");
        int bpm = o.optInt("b", 120);
        boolean on = o.optInt("on", 0) == 1;

        // choose up to 7 audible tracks (fall back to the first 7)
        int[] pick = new int[7];
        int n = 0, total = 0;
        for (int i = 0; i < tr.length() && n < 7; i++) {
            JSONObject t = tr.optJSONObject(i);
            if (t != null && t.optInt("m", 0) == 0) pick[n++] = i;
        }
        if (n == 0) for (n = 0; n < Math.min(7, tr.length()); n++) pick[n] = n;
        for (int i = 0; i < tr.length(); i++) {
            JSONObject t = tr.optJSONObject(i);
            if (t != null && t.optInt("m", 0) == 0) total += t.optJSONArray("a") == null ? 0 : t.optJSONArray("a").length();
        }

        // header: state dot, pattern, notes, key + BPM
        float hy = pad + h * .15f, dot = h * .035f;
        p.setStyle(Paint.Style.FILL); p.setColor(INK); p.setFakeBoldText(true);
        p.setTextAlign(Paint.Align.LEFT); p.setTextSize(h * .15f);
        if (on) cv.drawCircle(pad + dot, hy - h * .05f, dot, p);
        else { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.4f * u); cv.drawCircle(pad + dot, hy - h * .05f, dot, p); p.setStyle(Paint.Style.FILL); }
        cv.drawText(pat, pad + dot * 3f, hy, p);
        float patW = p.measureText(pat) + dot * 3f;

        p.setTextAlign(Paint.Align.RIGHT);
        p.setTextSize(h * .06f); p.setFakeBoldText(false); p.setColor(GREY);
        cv.drawText("BPM", w - pad, hy, p);
        float bw = p.measureText("BPM") + h * .02f;
        p.setTextSize(h * .15f); p.setFakeBoldText(true); p.setColor(INK);
        String bs = String.valueOf(bpm);
        cv.drawText(bs, w - pad - bw, hy, p);
        float used = bw + p.measureText(bs) + h * .05f;
        p.setTextSize(h * .06f); p.setFakeBoldText(false); p.setColor(GREY);
        String ex = (key.isEmpty() ? "" : "key " + key + "   ") + total + " notes";
        if (w - pad - used - p.measureText(ex) > pad + patW) cv.drawText(ex, w - pad - used, hy, p);

        // tracks
        float top = hy + h * .07f, rowH = (h - pad - top) / 7f;
        float lab = Math.min(w * .15f, h * .35f), x0 = pad + lab, L = w - pad - x0;
        for (int r = 0; r < n; r++) {
            int ti = pick[r];
            JSONObject t = tr.optJSONObject(ti);
            if (t == null) continue;
            JSONArray c = t.optJSONArray("c"), a = t.optJSONArray("a");
            int len = Math.max(2, Math.min(16, t.optInt("l", 16)));
            boolean muted = t.optInt("m", 0) == 1;
            float base = top + rowH * (r + .84f), amp = rowH * .78f, xe = x0 + L * len / 16f;
            int al = muted ? 90 : 255;

            p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.LEFT); p.setFakeBoldText(false);
            p.setColor(GREY); p.setAlpha(al); p.setTextSize(Math.min(rowH * .42f, h * .045f));
            cv.drawText(ti < NAMES.length ? NAMES[ti] : "", pad, base, p);

            if (c != null && c.length() > 1) {
                int cnt = c.length();
                int last = Math.max(1, (int) Math.ceil(cnt * len / 16.0));
                Path line = new Path(), fill = new Path();
                fill.moveTo(x0, base);
                for (int i = 0; i < last; i++) {
                    float px = x0 + (i + .5f) / cnt * L;
                    if (px > xe) px = xe;
                    float py = base - (float) (c.optDouble(i, 0) / 99.0) * amp;
                    if (i == 0) line.moveTo(px, py); else line.lineTo(px, py);
                    fill.lineTo(px, py);
                }
                fill.lineTo(xe, base); fill.close();
                p.setStyle(Paint.Style.FILL); p.setColor(INK); p.setAlpha(muted ? 14 : 48); cv.drawPath(fill, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.2f * u); p.setColor(INK); p.setAlpha(al); cv.drawPath(line, p);
            }
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.1f * u); p.setColor(INK); p.setAlpha((int) (al * .55f));
            cv.drawLine(x0, base, xe, base, p);
            cv.drawLine(xe, base - 3 * u, xe, base + 3 * u, p);
            p.setStyle(Paint.Style.FILL); p.setColor(GREY); p.setAlpha((int) (al * .5f));
            for (float px = xe + 7 * u; px < x0 + L; px += 6 * u) cv.drawCircle(px, base, Math.max(.8f, .8f * u), p);
            p.setColor(INK); p.setAlpha(al);
            if (a != null) for (int j = 0; j < a.length(); j++)
                cv.drawCircle(x0 + (a.optInt(j) + .5f) / 16f * L, base, Math.max(2f, 2.4f * u), p);
        }
        return bmp;
    }
}
