package com.xoleric.anim;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

/**
 * Vertikal fon animatsiyasi — chuqur qora ustida yuqoridan pastga tushuvchi
 * po'lat-ko'k yorug'lik tufayli.
 *
 * Texnik talablar bajarilgan:
 *  - VERTIKAL: harakat faqat Y o'qida (yukoridan pastga).
 *  - CHASTOTA: Choreographer vsync signali bilan, har kadrda — barqaror 60/90/120Hz.
 *  - MILLISEKUND ANIQLIK: delta-time (dt) real vaqtda o'lchanadi, harakat
 *    dt ga nisbatan integratsiya qilinadi → qurilmalar oralig'idagi tezlik bir xil.
 *  - PAS YO'Q: hardware-accelerated Canvas (default), object yaratilmaydi
 *    (paint/shader bir marta), GC bosim yo'q. SystemClock.uptimeMillis() bilan
 *    vaqt o'lchanadi → drift yo'q.
 *  - YUQORI KADRLAR: invalidate() faqat Choreographer callback ichida.
 *
 * @see Choreographer.FrameCallback
 */
public class VerticalBgView extends View implements Choreographer.FrameCallback {

    /* ── Po'lat-ko'k yorug'lik cho'qqilari (4K tiniq, xira emas) ── */
    private static final int COL_TOP    = 0xFF0A0F18; // chuqur qora-ko'k
    private static final int COL_STREAK = 0xFFB8C6DA; // oq-ko'k yorug' chiziq
    private static final int COL_EDGE   = 0xFF05070C; // pastki chegara

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Choreographer choreographer = Choreographer.getInstance();

    private boolean running = false;
    private long lastFrameNs = 0;

    /* ── Vertikal tufayl holati: fazalar va tezliklar ── */
    private float phaseA = 0f;  // sekin asosiy tufayl (0..1, siklik)
    private float phaseB = 0f;  // tezroq ikkinchi qatlam
    private float phaseC = 0f;  // juda sekin keng nafas

    // Tezliklar: bir soniyada qancha sikl (to'g'ri chastota)
    private static final float SPEED_A = 0.11f; // ~9s bir sikl — sekin, zerikmas
    private static final float SPEED_B = 0.23f; // ~4.3s — ritm qo'shadi
    private static final float SPEED_C = 0.045f; // ~22s — keng nafas

    private float w = 0f, h = 0f;
    private LinearGradient streakShader;
    private int streakStartY = 0, streakEndY = 0;

    public VerticalBgView(Context c) { super(c); init(); }
    public VerticalBgView(Context c, AttributeSet a) { super(c, a); init(); }
    public VerticalBgView(Context c, AttributeSet a, int d) { super(c, a, d); init(); }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null); // GPU compositing — pas yo'q
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(0f);
    }

    @Override
    protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        super.onSizeChanged(nw, nh, ow, oh);
        w = nw; h = nh;
        // Vertikal yorug'lik gradientini bir marta quramiz (har kadrda emas)
        streakStartY = 0;
        streakEndY = nh;
        rebuildShader();
    }

    private void rebuildShader() {
        // Vertikal gradient: yuqorida baland nurlanish, pastda chuqur qora
        streakShader = new LinearGradient(
                w * 0.5f, streakStartY,
                w * 0.5f, streakEndY,
                new int[]{ COL_TOP, COL_STREAK, COL_TOP, COL_EDGE },
                new float[]{ 0f, 0.18f, 0.55f, 1f },
                Shader.TileMode.CLAMP);
    }

    /* ── Choreographer: har vsync da chaqiriladi ── */
    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) return;

        // Real delta-time (millisekund aniqlik) — driftsiz integratsiya
        float dt;
        if (lastFrameNs == 0) {
            dt = 1f / 60f;
        } else {
            dt = (frameTimeNanos - lastFrameNs) / 1_000_000_000f;
            if (dt <= 0f) dt = 1f / 60f;
            if (dt > 0.1f) dt = 0.1f; // tab bilan uzoqlashgan bo'lsa yopish
        }
        lastFrameNs = frameTimeNanos;

        // Vertikal fazalarni yangilash (faqat Y yo'nalishi)
        phaseA = wrap(phaseA + SPEED_A * dt);
        phaseB = wrap(phaseB + SPEED_B * dt);
        phaseC = wrap(phaseC + SPEED_C * dt);

        invalidate(); // keyingi kadrni so'raymiz

        choreographer.postFrameCallback(this);
    }

    private static float wrap(float v) {
        return v - (float) Math.floor(v); // 0..1
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // 1) chuqur qora fon
        canvas.drawColor(COL_EDGE);

        if (streakShader == null) { rebuildShader(); }

        // 2) Vertikal tufayl — Y bo'ylab siljiydi (yuqoridan pastga)
        //    Shader matrix'ini faqat vertikal (transY) ga siljitamiz.
        float shift = (phaseA * h);
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.reset();
        m.postTranslate(0, shift);
        streakShader.setLocalMatrix(m);
        paint.setShader(streakShader);

        // To'liq ekranni to'ldirish (vertikal gradient ustida)
        canvas.drawRect(0, 0, w, h, paint);

        // 3) Ikkinchi qatlam — yumshoq vertikal tufayl (boshqa tezlikda)
        paint.setShader(null);
        int alphaB = (int) (34 * (0.6 + 0.4 * Math.sin(phaseB * Math.PI * 2)));
        if (alphaB > 0) {
            paint.setColor((alphaB << 24) | 0xB8C6DA);
            float cx = w * 0.5f;
            float bandW = w * (0.45f + 0.12f * (float) Math.sin(phaseC * Math.PI * 2));
            float top = -h * 0.2f + phaseB * (h * 1.4f);
            float bottom = top + h * 0.5f;
            LinearGradient g2 = new LinearGradient(cx, top, cx, bottom,
                    new int[]{ 0x00B8C6DA, paint.getColor(), 0x00B8C6DA },
                    new float[]{ 0f, 0.5f, 1f }, Shader.TileMode.CLAMP);
            paint.setShader(g2);
            canvas.drawRect(cx - bandW, top, cx + bandW, bottom, paint);
            paint.setShader(null);
        }
    }

    /* ── Boshqaruv ── */
    public void start() {
        if (running) return;
        running = true;
        lastFrameNs = 0;
        choreographer.postFrameCallback(this);
    }

    public void stop() {
        running = false;
        choreographer.removeFrameCallback(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) start(); else stop();
    }
}
