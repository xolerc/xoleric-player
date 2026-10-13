package com.xoleric.player;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

/** Bitta audio yozuv. */
public class Song {
    public long id;
    public String title;
    public String artist;
    public Uri uri;
    public long durationMs;
    public long albumId;

    public Song(long id, String title, String artist, Uri uri, long durationMs, long albumId) {
        this.id = id; this.title = title; this.artist = artist;
        this.uri = uri; this.durationMs = durationMs; this.albumId = albumId;
    }
}
