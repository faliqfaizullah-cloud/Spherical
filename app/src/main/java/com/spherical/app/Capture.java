package com.spherical.app;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.Visualizer;

/** Streams a 512-bin magnitude spectrum (bytes 0..255) from system output or the microphone. */
public class Capture {
    public interface CB { void data(byte[] bins); }

    private Visualizer viz;
    private AudioRecord rec;
    private Thread th;
    private volatile boolean run;

    public boolean active() { return viz != null || run; }

    /** Taps the global audio output mix, so whatever a music app plays drives Spherical. */
    public boolean startSys(final CB cb) {
        stop();
        try {
            viz = new Visualizer(0);
            viz.setEnabled(false);
            int[] r = Visualizer.getCaptureSizeRange();
            viz.setCaptureSize(Math.min(1024, r[1]));
            viz.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                public void onWaveFormDataCapture(Visualizer v, byte[] w, int sr) {}
                public void onFftDataCapture(Visualizer v, byte[] fft, int sr) { cb.data(fromVisualizer(fft)); }
            }, Visualizer.getMaxCaptureRate() / 2, false, true);
            viz.setEnabled(true);
            return true;
        } catch (Throwable e) { stop(); return false; }
    }

    /** Listens through the microphone (works with any speaker playback). */
    public boolean startMic(final CB cb) {
        stop();
        try {
            final int N = 1024;
            int min = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            rec = new AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, N * 4));
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) { stop(); return false; }
            rec.startRecording();
            run = true;
            final AudioRecord r = rec;
            th = new Thread(() -> {
                short[] buf = new short[N];
                double[] re = new double[N], im = new double[N];
                while (run) {
                    int got = r.read(buf, 0, N);
                    if (got < N) continue;
                    for (int i = 0; i < N; i++) {
                        double w = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (N - 1));
                        re[i] = buf[i] / 32768.0 * w; im[i] = 0;
                    }
                    fft(re, im);
                    byte[] o = new byte[512];
                    for (int i = 1; i < 512; i++) {
                        double m = Math.sqrt(re[i] * re[i] + im[i] * im[i]) / (N / 4.0);
                        o[i] = (byte) Math.min(255, (int) (Math.sqrt(Math.min(1, m * 6)) * 255));
                    }
                    cb.data(o);
                }
            }, "spherical-mic");
            th.start();
            return true;
        } catch (Throwable e) { stop(); return false; }
    }

    public void stop() {
        run = false;
        if (viz != null) { try { viz.setEnabled(false); viz.release(); } catch (Throwable e) {} viz = null; }
        if (rec != null) { try { rec.stop(); rec.release(); } catch (Throwable e) {} rec = null; }
        th = null;
    }

    private static byte[] fromVisualizer(byte[] fft) {
        int n = fft.length / 2;
        byte[] o = new byte[512];
        for (int i = 1; i < 512; i++) {
            int j = Math.min(n - 1, i * n / 512);
            if (j < 1) continue;
            double re = fft[2 * j], im = fft[2 * j + 1];
            double m = Math.sqrt(re * re + im * im);
            o[i] = (byte) Math.min(255, (int) (Math.sqrt(m / 181.0) * 255 * 1.2));
        }
        return o;
    }

    private static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) { double t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len, wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = i + k + len / 2;
                    double xr = re[b] * cr - im[b] * ci, xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi; re[a] += xr; im[a] += xi;
                    double nr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = nr;
                }
            }
        }
    }
}
