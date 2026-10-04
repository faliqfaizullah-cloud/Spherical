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

/** Home-screen widget: cream card showing the current pattern, BPM and every track's nodes. */
public class SphericalWidget extends AppWidgetProvider {
    static final String PREFS = "sph", KEY = "w";
    private static final String[] NAMES = {"KICK", "CLAP", "PERC", "HAT", "SUB", "SYN", "CHORD"};
    private static final double[] SIG = {.016, .02, .012, .01, .05, .02, .04};
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
        if (o == null) {
            p.setColor(GREY); p.setTextSize(h * .09f); p.setTextAlign(Paint.Align.CENTER);
            cv.drawText("open spherical", w / 2f, h / 2f, p);
            return bmp;
        }
        String pat = o.optString("p", "SPHERICAL");
        int bpm = o.optInt("b", 120);
        boolean on = o.optInt("on", 0) == 1;
        JSONArray tr = o.optJSONArray("t");
        JSONArray mu = o.optJSONArray("m");
        int total = 0;
        if (tr != null) for (int i = 0; i < tr.length(); i++) total += tr.optJSONArray(i).length();

        // header: state dot, pattern name, node count, BPM
        float hy = pad + h * .15f;
        p.setStyle(Paint.Style.FILL); p.setColor(INK);
        p.setTextAlign(Paint.Align.LEFT); p.setTextSize(h * .15f); p.setFakeBoldText(true);
        float dot = h * .035f, dx = pad + dot;
        if (on) cv.drawCircle(dx, hy - h * .05f, dot, p);
        else { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.4f * u); cv.drawCircle(dx, hy - h * .05f, dot, p); p.setStyle(Paint.Style.FILL); }
        cv.drawText(pat, pad + dot * 3f, hy, p);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setTextSize(h * .15f);
        String bs = String.valueOf(bpm);
        p.setTextSize(h * .06f); p.setFakeBoldText(false); p.setColor(GREY);
        cv.drawText("BPM", w - pad, hy, p);
        float bw = p.measureText("BPM") + h * .02f;
        p.setTextSize(h * .15f); p.setFakeBoldText(true); p.setColor(INK);
        cv.drawText(bs, w - pad - bw, hy, p);
        float nw = p.measureText(bs) + bw + h * .05f;
        p.setTextSize(h * .06f); p.setFakeBoldText(false); p.setColor(GREY);
        cv.drawText(total + " nodes", w - pad - nw, hy, p);

        // tracks
        float top = hy + h * .07f, rowH = (h - pad - top) / 7f;
        float lab = Math.min(w * .15f, h * .35f), x0 = pad + lab, L = w - pad - x0;
        p.setTextAlign(Paint.Align.LEFT);
        for (int i = 0; i < 7; i++) {
            JSONArray ns = tr == null ? new JSONArray() : tr.optJSONArray(i);
            boolean muted = mu != null && mu.optInt(i, 0) == 1;
            float base = top + rowH * (i + .84f), amp = rowH * .78f;
            int al = muted ? 90 : 255;
            p.setStyle(Paint.Style.FILL); p.setColor(GREY); p.setAlpha(al);
            p.setTextSize(Math.min(rowH * .42f, h * .045f));
            cv.drawText(NAMES[i], pad, base, p);

            int steps = Math.max(40, (int) (L / 2));
            Path line = new Path(), fill = new Path();
            fill.moveTo(x0, base);
            for (int s = 0; s <= steps; s++) {
                double uu = s / (double) steps, y = 0;
                for (int j = 0; j < ns.length(); j++) {
                    JSONArray n = ns.optJSONArray(j);
                    double x = n.optDouble(0), v = n.optDouble(1), sg = SIG[i] * (.55 + .9 * v), dd = uu - x;
                    y += v * Math.exp(-dd * dd / (2 * sg * sg));
                }
                float px = x0 + (float) uu * L, py = base - (float) Math.min(y, 1.1) * amp;
                if (s == 0) line.moveTo(px, py); else line.lineTo(px, py);
                fill.lineTo(px, py);
            }
            fill.lineTo(x0 + L, base); fill.close();
            p.setStyle(Paint.Style.FILL); p.setColor(INK); p.setAlpha(muted ? 14 : 48); cv.drawPath(fill, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.2f * u); p.setColor(INK); p.setAlpha(al); cv.drawPath(line, p);
            p.setAlpha((int) (al * .5f)); cv.drawLine(x0, base, x0 + L, base, p);
            p.setStyle(Paint.Style.FILL); p.setAlpha(al);
            for (int j = 0; j < ns.length(); j++)
                cv.drawCircle(x0 + (float) ns.optJSONArray(j).optDouble(0) * L, base, Math.max(2f, 2.4f * u), p);
        }
        return bmp;
    }
}
