package com.nextgenmusic.player.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    "next_gen_music.db",
    null,
    1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE songs (
            id INTEGER PRIMARY KEY AUTOINCREMENT, uri TEXT NOT NULL UNIQUE, title TEXT NOT NULL,
            artist TEXT NOT NULL, album TEXT NOT NULL, album_artist TEXT NOT NULL, genre TEXT NOT NULL,
            duration_ms INTEGER NOT NULL DEFAULT 0, size_bytes INTEGER NOT NULL DEFAULT 0,
            year INTEGER NOT NULL DEFAULT 0, track_number INTEGER NOT NULL DEFAULT 0,
            date_modified INTEGER NOT NULL DEFAULT 0, mime_type TEXT NOT NULL, folder TEXT NOT NULL,
            artwork_uri TEXT)""")
        db.execSQL("CREATE INDEX songs_artist_index ON songs(artist)")
        db.execSQL("CREATE INDEX songs_album_index ON songs(album)")
        db.execSQL("CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE playlist_songs (playlist_id INTEGER NOT NULL, song_id INTEGER NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(playlist_id, song_id))")
        db.execSQL("CREATE TABLE favorites (song_id INTEGER PRIMARY KEY, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE history (song_id INTEGER PRIMARY KEY, played_at INTEGER NOT NULL, position_ms INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE scan_locations (id INTEGER PRIMARY KEY AUTOINCREMENT, uri TEXT NOT NULL UNIQUE, label TEXT NOT NULL)")
        db.execSQL("CREATE TABLE browser_bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, url TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE browser_history (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, url TEXT NOT NULL, visited_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE downloads (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL, filename TEXT NOT NULL, status TEXT NOT NULL, progress INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun putSetting(key: String, value: String) {
        writableDatabase.insertWithOnConflict(
            "settings",
            null,
            ContentValues().apply { put("key", key); put("value", value) },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getSetting(key: String, defaultValue: String): String =
        readableDatabase.query("settings", arrayOf("value"), "key=?", arrayOf(key), null, null, null)
            .use { if (it.moveToFirst()) it.getString(0) else defaultValue }
}