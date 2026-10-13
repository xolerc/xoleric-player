package com.xoleric.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.xoleric.app.R;

import java.util.List;

/** Treklar ro'yxati adapteri — premium qatorlar. */
public class SongAdapter extends BaseAdapter {

    private final Context ctx;
    private final List<Song> songs;
    private final LayoutInflater inf;
    private int selectedIndex = -1;
    private boolean playing = false;

    public SongAdapter(Context ctx, List<Song> songs) {
        this.ctx = ctx;
        this.songs = songs;
        this.inf = LayoutInflater.from(ctx);
    }

    public void setSelected(int idx, boolean playing) {
        this.selectedIndex = idx;
        this.playing = playing;
        notifyDataSetChanged();
    }

    public void setPlaying(boolean p) {
        this.playing = p;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return songs.size(); }
    @Override public Song getItem(int i) { return songs.get(i); }
    @Override public long getItemId(int i) { return songs.get(i).id; }

    @Override public boolean hasStableIds() { return true; }

    @Override
    public View getView(int i, View v, ViewGroup parent) {
        Holder h;
        if (v == null) {
            v = inf.inflate(R.layout.item_song, parent, false);
            h = new Holder();
            h.art = v.findViewById(R.id.itemArt);
            h.title = v.findViewById(R.id.itemTitle);
            h.artist = v.findViewById(R.id.itemArtist);
            h.idx = v.findViewById(R.id.itemIndex);
            v.setTag(h);
        } else {
            h = (Holder) v.getTag();
        }

        Song s = songs.get(i);
        h.title.setText(s.title);
        h.artist.setText(s.artist);

        boolean sel = (i == selectedIndex);
        h.title.setTextColor(sel ? 0xFFFFFFFF : 0xFFE8ECF4);
        v.setSelected(sel);
        v.setActivated(sel);

        // indeks yoki play belgisi
        if (sel && playing) {
            h.idx.setText("▶");
            h.idx.setTextColor(0xFF8EA0B8);
        } else {
            h.idx.setText(String.valueOf(i + 1));
            h.idx.setTextColor(0xFF6E7C8E);
        }

        // album art (async emas — kichik, lekin xavfsiz)
        Bitmap art = AudioScanner.albumArt(ctx, s.albumId);
        if (art != null) {
            h.art.setImageBitmap(art);
        } else {
            h.art.setImageResource(R.drawable.ic_art_placeholder);
        }
        return v;
    }

    private static class Holder {
        ImageView art;
        TextView title, artist, idx;
    }
}
