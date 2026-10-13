package com.xoleric.anim;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

import java.util.Random;

/**
 * mydrime uslubidagi LOADER — "matrix shamol".
 *
 * Tabiat: qora fonda oq raqam/harf zanjiri chapdan o'ngga shamol bo'lib
 * o'tadi, keyin XOLERIC logosi (chegarali quti) paydo bo'ladi.
 * Bu shunchaki ICON emas — to'liq ANIMATSIYALI loader.
 *
 * Texnik:
 *  - Choreographer vsync → barqaror chastota, millisekund dt integratsiyasi
 *  - Hardware Canvas, obyektlar oldindan tayyor → pas yo'q
 *  - onLoadingComplete() → asosiy ekranga o'tish (callback)
 *
 * mydrime index.html dagi #loader va js/matrix-canvas dan moslashtirilgan.
 */
public class LoaderView extends View implements Choreographer.FrameCallback {

    public interface Listener {
        void onLoadingComplete();
    }

    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    private final Paint matrixPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint logoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();
    private final Choreographer choreographer = Choreographer.getInstance();

    /* ── Matrix shamol ustunlari ── */
    private static final class Column {
        float x;        // ekran ustunidagi X
        float y;        // joriy bosh Y
        float speed;    // px/s — vertikal (chapdan o'ngga shamol = har bir ustun vertikal tushadi)
        float len;      // zanjir uzunligi (px)
        char[] glyphs;  // belgilar zanjiri
        float glyphH;   // bitta belgi balandligi
        float drift;    // gorizontal drift (shamol effekti, px/s)
    }

    private Column[] cols;
    private float w = 0, h = 0;
    private boolean running = false;
    private long lastNs = 0;
    private long startMs = 0;

    private boolean logoShown = false;
    private float logoAlpha = 0f;      // 0..1
    private float glitchPx = 0f;       // glitch siljish
    private boolean completed = false;

    /* Vaqt jadvali (millisekund) */
    private static final long T_MATRIX_MS = 2100;  // matrix shamol davri
    private static final long T_LOGO_MS   = 1100;  // logoning paydo bo'lishi
    private static final long T_TOTAL_MS  = 3400;  // umumiy loader vaqti

    private static final String GLYPHS = "01ABCDEFXY789#*+=<>/\\|";

    public LoaderView(Context c) { super(c); init(); }
    public LoaderView(Context c, AttributeSet a) { super(c, a); init(); }
    public LoaderView(Context c, AttributeSet a, int d) { super(c, a, d); init(); }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null);
        matrixPaint.setColor(WHITE);
        matrixPaint.setTypeface(Typeface.MONOSPACE);
        matrixPaint.setTextAlign(Paint.Align.CENTER);

        logoPaint.setColor(WHITE);
        logoPaint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        logoPaint.setTextAlign(Paint.Align.CENTER);
        logoPaint.setLetterSpacing(0.18f);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3f);
        borderPaint.setColor(WHITE);
    }

    public void setListener(Listener l) { this.listener = l; }
    private Listener listener;

    @Override
    protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        super.onSizeChanged(nw, nh, ow, oh);
        w = nw; h = nh;
        buildColumns();
    }

    private void buildColumns() {
        // ustun oralig'i — mydrime dagidek zich, lekin o'qiladigan
        float glyphW = Math.max(18f, w * 0.028f);
        int n = Math.max(8, (int) (w / glyphW));
        cols = new Column[n];
        float step = w / n;
        for (int i = 0; i < n; i++) {
            Column c = new Column();
            c.x = step * i + step * 0.5f;
            c.glyphH = glyphW * 1.15f;
            int len = 8 + rnd.nextInt(16);
            c.len = len * c.glyphH;
            c.glyphs = new char[len];
            for (int k = 0; k < len; k++) c.glyphs[k] = GLYPHS.charAt(rnd.nextInt(GLYPHS.length()));
            c.speed = 140f + rnd.nextInt(320);      // px/s
            c.drift = (rnd.nextFloat() - 0.5f) * 26f; // shamol gorizontal silkinish
            int spread = Math.max(1, (int) h);
            c.y = -rnd.nextInt(spread);              // boshlang'ich tarqatish
            cols[i] = c;
        }
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) return;
        float dt;
        if (lastNs == 0) dt = 1f / 60f;
        else {
            dt = (frameTimeNanos - lastNs) / 1_000_000_000f;
            if (dt <= 0) dt = 1f / 60f;
            if (dt > 0.1f) dt = 0.1f;
        }
        lastNs = frameTimeNanos;

        long elapsed = SystemClock.uptimeMillis() - startMs;

        // Matrix ustunlarini vertikal harakatga solish (faqat matrix davrida)
        if (elapsed < T_MATRIX_MS && cols != null) {
            for (Column c : cols) {
                c.y += c.speed * dt;
                float top = c.y - c.len;
                // gorizontal drift (shamol)
                // (chegarada ushlab turish onDraw da)
                // zanjir pastdan chiqib ketdi → tepadan qaytish
                if (top > h) {
                    c.y = -rnd.nextInt(Math.max(1, (int) (h / 2))) - c.len;
                    c.speed = 140f + rnd.nextInt(320);
                    // belgilarni yangilash
                    for (int k = 0; k < c.glyphs.length; k++)
                        if (rnd.nextInt(3) == 0)
                            c.glyphs[k] = GLYPHS.charAt(rnd.nextInt(GLYPHS.length()));
                }
            }
        }

        // Logo bosqichiga o'tish
        if (elapsed >= T_MATRIX_MS && !logoShown) logoShown = true;
        if (logoShown) {
            float lt = (elapsed - T_MATRIX_MS) / (float) T_LOGO_MS;
            if (lt > 1f) lt = 1f;
            // ease-out
            logoAlpha = 1f - (float) Math.pow(1f - lt, 3);
            // dastlabki glitch (tez silkinish → keyin tinch)
            if (lt < 0.7f) {
                glitchPx = (float) (Math.sin(elapsed * 0.05) * (1f - lt) * 10f);
            } else glitchPx = 0f;
        }

        invalidate();
        choreographer.postFrameCallback(this);

        // Yakunlash
        if (elapsed >= T_TOTAL_MS && !completed) {
            completed = true;
            if (listener != null) listener.onLoadingComplete();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(BLACK);
        long elapsed = SystemClock.uptimeMillis() - startMs;

        // ── Matrix shamol (vertikal) ──
        float matrixAlpha = 1f;
        if (logoShown) {
            // logoga o'tganda matrix sekin so'nadi
            float fade = 1f - (elapsed - T_MATRIX_MS) / (float) Math.max(1, T_LOGO_MS);
            matrixAlpha = Math.max(0f, fade);
        }
        if (matrixAlpha > 0f && cols != null) {
            matrixPaint.setAlpha((int) (255 * matrixAlpha));
            float baseSize = Math.max(14f, w * 0.026f);
            for (Column c : cols) {
                int len = c.glyphs.length;
                for (int k = 0; k < len; k++) {
                    float gy = c.y - k * c.glyphH;
                    if (gy < -c.glyphH || gy > h + c.glyphH) continue;
                    float x = c.x + c.drift * (gy / Math.max(1f, h));
                    // bosh belgi yorqin, dum so'nadi (oq → kulrang)
                    int a = (int) (255 * matrixAlpha * (1f - (float) k / len) + 22);
                    matrixPaint.setAlpha(Math.min(255, a));
                    matrixPaint.setTextSize(baseSize);
                    canvas.drawText(String.valueOf(c.glyphs[k]), x, gy, matrixPaint);
                }
            }
        }

        // ── XOLERIC logosi (chegarali quti) — mydrime .xoleric-logo ──
        if (logoShown && logoAlpha > 0f) {
            float cx = w * 0.5f, cy = h * 0.5f;
            float boxW = Math.min(w * 0.82f, w - 48f);
            float textSize = boxW * 0.145f;
            float boxH = textSize * 2.3f;
            float left = cx - boxW / 2 + glitchPx;
            float top = cy - boxH / 2;

            borderPaint.setAlpha((int) (255 * logoAlpha));
            borderPaint.setStrokeWidth(Math.max(2f, w * 0.004f));
            canvas.drawRect(left, top, left + boxW, top + boxH, borderPaint);

            // ichki yumshoq glow (oq, xira emas)
            logoPaint.setTextSize(textSize);
            logoPaint.setAlpha((int) (255 * logoAlpha));
            // glitch uchun ikki qatlamli rang siljishi
            if (glitchPx != 0f) {
                Paint ghost = new Paint(logoPaint);
                ghost.setColor(0xFF8EA0B8);
                ghost.setAlpha((int) (120 * logoAlpha));
                canvas.drawText("XOLERIC", cx - glitchPx * 0.6f, cy + textSize * 0.34f, ghost);
            }
            canvas.drawText("XOLERIC", cx + glitchPx, cy + textSize * 0.34f, logoPaint);
        }
    }

    /* ── Boshqaruv ── */
    public void start() {
        if (running) return;
        running = true;
        lastNs = 0;
        startMs = SystemClock.uptimeMillis();
        choreographer.postFrameCallback(this);
    }

    public void stop() {
        running = false;
        choreographer.removeFrameCallback(this);
    }

    @Override
    protected void onAttachedToWindow() { super.onAttachedToWindow(); start(); }
    @Override
    protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
}
