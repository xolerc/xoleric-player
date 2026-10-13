package com.xoleric.player;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import java.util.List;

/**
 * MediaPlayer asosidagi pleyer boshqaruvchisi.
 * Nol external dependency (media3 emas) — offline build xavfsiz.
 * Progress callback Handler bilan (UI thread) — barqaror.
 */
public class PlayerController implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    public interface Callback {
        void onPrepared(Song song);
        void onProgress(long posMs, long durMs);
        void onCompletion(Song song);
        void onError(String msg);
        void onStateChanged(boolean playing);
    }

    private final MediaPlayer mp = new MediaPlayer();
    private final List<Song> queue;
    private Callback cb;
    private int index = -1;
    private boolean prepared = false;
    private boolean playing = false;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (cb != null && prepared) {
                try {
                    cb.onProgress(mp.getCurrentPosition(), mp.getDuration());
                } catch (IllegalStateException ignored) {}
            }
            ui.postDelayed(this, 500); // 500ms progress yangilash (samarali)
        }
    };

    public PlayerController(List<Song> queue) {
        this.queue = queue;
        mp.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());
        mp.setOnPreparedListener(this);
        mp.setOnCompletionListener(this);
        mp.setOnErrorListener(this);
    }

    public void setCallback(Callback cb) { this.cb = cb; }

    public boolean hasTrack() { return index >= 0 && index < queue.size(); }
    public boolean isPlaying() { return playing; }
    public Song current() { return hasTrack() ? queue.get(index) : null; }

    public void playAt(int i) {
        if (i < 0 || i >= queue.size()) return;
        index = i;
        Song s = queue.get(i);
        prepared = false;
        playing = false;
        try {
            mp.reset();
            mp.setDataSource(s.uri.toString());
            mp.prepareAsync();
        } catch (Exception e) {
            if (cb != null) cb.onError("Ovoz ochilmadi: " + e.getMessage());
        }
    }

    public void toggle() {
        if (!prepared) return;
        if (playing) { mp.pause(); playing = false; }
        else { mp.start(); playing = true; }
        if (cb != null) cb.onStateChanged(playing);
    }

    public void next() {
        if (queue.isEmpty()) return;
        playAt((index + 1) % queue.size());
    }

    public void prev() {
        if (queue.isEmpty()) return;
        int p = index - 1;
        if (p < 0) p = queue.size() - 1;
        playAt(p);
    }

    public void seekTo(int ms) {
        if (prepared) { try { mp.seekTo(ms); } catch (IllegalStateException ignored) {} }
    }

    public void release() {
        ui.removeCallbacks(tick);
        try { mp.release(); } catch (Exception ignored) {}
        prepared = false; playing = false;
    }

    @Override public void onPrepared(MediaPlayer m) {
        prepared = true;
        playing = true;
        m.start();
        ui.removeCallbacks(tick);
        ui.post(tick);
        if (cb != null) {
            cb.onPrepared(current());
            cb.onStateChanged(true);
        }
    }

    @Override public void onCompletion(MediaPlayer m) {
        if (cb != null && current() != null) cb.onCompletion(current());
        next(); // avtomatik navbat
    }

    @Override public boolean onError(MediaPlayer m, int what, int extra) {
        prepared = false; playing = false;
        if (cb != null) cb.onError("Player xatosi: " + what + "/" + extra);
        return true;
    }
}
