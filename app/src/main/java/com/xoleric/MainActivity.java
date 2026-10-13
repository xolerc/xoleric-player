package com.xoleric;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.xoleric.anim.LoaderView;
import com.xoleric.anim.VerticalBgView;
import com.xoleric.app.R;
import com.xoleric.player.AudioScanner;
import com.xoleric.player.PlayerController;
import com.xoleric.player.Song;
import com.xoleric.player.SongAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * XOLERIC — premium musiqa pleyer.
 * mydrime loader (matrix shamol → logo) → 4K tiniq premium UI.
 */
public class MainActivity extends Activity implements PlayerController.Callback {

    private static final int REQ_AUDIO = 101;

    private VerticalBgView bgAnim;
    private LoaderView loader;
    private View content;

    private ImageView albumArt;
    private TextView trackTitle, trackArtist, timePos, timeDur;
    private SeekBar seekBar;
    private ImageButton btnPlay, btnPrev, btnNext;
    private ListView songList;

    private final List<Song> songs = new ArrayList<>();
    private PlayerController player;
    private SongAdapter adapter;
    private int currentIndex = -1;
    private boolean playing = false;
    private boolean userSeeking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();

        // mydrime loader — tugagach kontentni ochadi (icon emas, to'liq loader)
        loader.setListener(() -> runOnUiThread(this::revealContent));
        bgAnim.start();

        // Audio ruxsati
        if (hasAudioPermission()) {
            loadSongs();
        } else {
            requestAudioPermission();
        }
    }

    private void bindViews() {
        bgAnim = findViewById(R.id.bgAnim);
        loader = findViewById(R.id.loader);
        content = findViewById(R.id.content);
        albumArt = findViewById(R.id.albumArt);
        trackTitle = findViewById(R.id.trackTitle);
        trackArtist = findViewById(R.id.trackArtist);
        timePos = findViewById(R.id.timePos);
        timeDur = findViewById(R.id.timeDur);
        seekBar = findViewById(R.id.seekBar);
        btnPlay = findViewById(R.id.btnPlay);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        songList = findViewById(R.id.songList);

        btnPlay.setOnClickListener(v -> {
            if (player != null && player.hasTrack()) player.toggle();
            else if (!songs.isEmpty()) selectAndPlay(0);
        });
        btnNext.setOnClickListener(v -> { if (player != null) player.next(); });
        btnPrev.setOnClickListener(v -> { if (player != null) player.prev(); });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                if (u) timePos.setText(fmt(p));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                if (player != null) player.seekTo(sb.getProgress());
                userSeeking = false;
            }
        });
    }

    /* ── Ruxsat ── */
    private boolean hasAudioPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestAudioPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_AUDIO}, REQ_AUDIO);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_AUDIO);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == REQ_AUDIO) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                loadSongs();
            } else {
                Toast.makeText(this, "Musiqa ruxsati kerak", Toast.LENGTH_LONG).show();
            }
        }
    }

    /* ── Kontentni ochish (loader dan keyin) ── */
    private void revealContent() {
        content.setVisibility(View.VISIBLE);
        content.setAlpha(0f);
        content.animate().alpha(1f).setDuration(500)
                .setListener(null).start();
        loader.animate().alpha(0f).setDuration(400)
                .setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator a) {
                        loader.setVisibility(View.GONE);
                        loader.stop();
                    }
                }).start();
    }

    /* ── Treklarni yuklash ── */
    private void loadSongs() {
        new Thread(() -> {
            List<Song> found = AudioScanner.scan(this);
            runOnUiThread(() -> {
                songs.clear();
                songs.addAll(found);
                if (songs.isEmpty()) {
                    Toast.makeText(this, "Turli xil musiqa topilmadi",
                            Toast.LENGTH_LONG).show();
                }
                player = new PlayerController(songs);
                player.setCallback(this);
                adapter = new SongAdapter(this, songs);
                songList.setAdapter(adapter);
                songList.setOnItemClickListener((p, v, pos, id) -> selectAndPlay(pos));
            });
        }).start();
    }

    private void selectAndPlay(int pos) {
        if (player == null) return;
        currentIndex = pos;
        adapter.setSelected(pos, true);
        player.playAt(pos);
        songList.setSelection(pos);
    }

    /* ── PlayerController callbacklari (UI thread) ── */
    @Override public void onPrepared(Song song) {
        if (song == null) return;
        trackTitle.setText(song.title);
        trackArtist.setText(song.artist);
        seekBar.setMax((int) song.durationMs);
        timeDur.setText(fmt((int) song.durationMs));
        Bitmap art = AudioScanner.albumArt(this, song.albumId);
        if (art != null) albumArt.setImageBitmap(art);
        else albumArt.setImageResource(R.drawable.ic_art_placeholder);
    }

    @Override public void onProgress(long posMs, long durMs) {
        if (!userSeeking) {
            seekBar.setMax((int) durMs);
            seekBar.setProgress((int) posMs);
            timePos.setText(fmt((int) posMs));
        }
    }

    @Override public void onCompletion(Song song) {
        // avtomatik navbat — indeks yangilanadi onStateChanged orqali
    }

    @Override public void onError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override public void onStateChanged(boolean playing) {
        this.playing = playing;
        btnPlay.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        if (adapter != null) adapter.setPlaying(playing);
        // joriy indeksni yangilash
        if (player != null && player.current() != null) {
            for (int i = 0; i < songs.size(); i++) {
                if (songs.get(i).id == player.current().id) {
                    currentIndex = i;
                    break;
                }
            }
        }
    }

    private static String fmt(int ms) {
        int s = ms / 1000;
        int m = s / 60;
        s = s % 60;
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    @Override protected void onResume() {
        super.onResume();
        if (bgAnim != null) bgAnim.start();
    }

    @Override protected void onPause() {
        super.onPause();
        if (bgAnim != null) bgAnim.stop();
    }

    @Override protected void onDestroy() {
        if (player != null) player.release();
        super.onDestroy();
    }
}
