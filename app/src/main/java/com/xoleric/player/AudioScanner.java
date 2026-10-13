package com.xoleric.player;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

/**
 * MediaStore orqali qurilmadagi audiolarni skanerlaydi.
 * READ_MEDIA_AUDIO (API 33+) yoki READ_EXTERNAL_STORAGE (API <33) ruxsati talab qilinadi.
 */
public class AudioScanner {

    public static List<Song> scan(Context ctx) {
        List<Song> out = new ArrayList<>();
        Uri base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] proj = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.IS_MUSIC
        };
        String sel = MediaStore.Audio.Media.IS_MUSIC + "!=0 AND "
                + MediaStore.Audio.Media.DURATION + ">0";
        String order = MediaStore.Audio.Media.TITLE + " ASC";

        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(base, proj, sel, null, order);
            if (c == null) return out;
            int iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int iT = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int iA = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int iD = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int iAl = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
            while (c.moveToNext()) {
                long id = c.getLong(iId);
                Uri uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                String artist = c.getString(iA);
                if (artist == null || artist.equals("<unknown>")) artist = "Noma'lum";
                out.add(new Song(
                        id,
                        c.getString(iT),
                        artist,
                        uri,
                        c.getLong(iD),
                        c.getLong(iAl)
                ));
            }
        } catch (SecurityException e) {
            // ruxsat yo'q — bo'sh ro'yxat
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    /** Album artwork (keshlangan). */
    public static android.graphics.Bitmap albumArt(Context ctx, long albumId) {
        try {
            Uri uri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId);
            android.content.ContentResolver cr = ctx.getContentResolver();
            android.content.res.AssetFileDescriptor fd = cr.openAssetFileDescriptor(uri, "r");
            if (fd == null) return null;
            android.graphics.Bitmap b = android.graphics.BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor());
            fd.close();
            return b;
        } catch (Exception e) {
            return null;
        }
    }
}
