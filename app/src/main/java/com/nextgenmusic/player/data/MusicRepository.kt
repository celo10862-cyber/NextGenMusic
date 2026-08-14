package com.nextgenmusic.player.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(context: Context) {
    private val db = AppDatabase(context)

    suspend fun songs(search: String = ""): List<Song> = withContext(Dispatchers.IO) {
        val query = if (search.isBlank()) {
            db.readableDatabase.query("songs", null, null, null, null, null, "title COLLATE NOCASE ASC")
        } else {
            db.readableDatabase.query(
                "songs", null, "title LIKE ? OR artist LIKE ? OR album LIKE ?",
                arrayOf("%$search%", "%$search%", "%$search%"), null, null, "title COLLATE NOCASE ASC"
            )
        }
        query.use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.song()) } }
    }

    suspend fun recent(limit: Int = 12): List<Song> = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery(
            "SELECT s.* FROM songs s JOIN history h ON h.song_id=s.id ORDER BY h.played_at DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.song()) } }
    }

    suspend fun favorites(): List<Song> = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery(
            "SELECT s.* FROM songs s JOIN favorites f ON f.song_id=s.id ORDER BY s.title COLLATE NOCASE",
            null
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.song()) } }
    }

    suspend fun isFavorite(songId: Long): Boolean = withContext(Dispatchers.IO) {
        db.readableDatabase.query("favorites", arrayOf("song_id"), "song_id=?", arrayOf(songId.toString()), null, null, null)
            .use { it.moveToFirst() }
    }

    suspend fun toggleFavorite(songId: Long): Boolean = withContext(Dispatchers.IO) {
        if (isFavorite(songId)) {
            db.writableDatabase.delete("favorites", "song_id=?", arrayOf(songId.toString()))
            false
        } else {
            db.writableDatabase.insert("favorites", null, ContentValues().apply {
                put("song_id", songId); put("created_at", System.currentTimeMillis())
            })
            true
        }
    }

    suspend fun markPlayed(songId: Long, positionMs: Long = 0) = withContext(Dispatchers.IO) {
        db.writableDatabase.insertWithOnConflict(
            "history", null, ContentValues().apply {
                put("song_id", songId); put("played_at", System.currentTimeMillis()); put("position_ms", positionMs)
            }, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    suspend fun upsertSongs(items: List<Song>) = withContext(Dispatchers.IO) {
        db.writableDatabase.beginTransaction()
        try {
            items.forEach { song ->
                db.writableDatabase.insertWithOnConflict("songs", null, song.values(), android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.writableDatabase.setTransactionSuccessful()
        } finally {
            db.writableDatabase.endTransaction()
        }
    }

    suspend fun deleteMissingUris(existingUris: Set<String>) = withContext(Dispatchers.IO) {
        if (existingUris.isEmpty()) return@withContext
        val selectionMarks = existingUris.joinToString(",") { "?" }
        db.writableDatabase.delete("songs", "uri NOT IN ($selectionMarks)", existingUris.toTypedArray())
    }

    suspend fun playlists(): List<Playlist> = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery(
            "SELECT p.id,p.name,COUNT(ps.song_id),p.created_at FROM playlists p LEFT JOIN playlist_songs ps ON p.id=ps.playlist_id GROUP BY p.id ORDER BY p.name",
            null
        ).use { c ->
            buildList { while (c.moveToNext()) add(Playlist(c.getLong(0), c.getString(1), c.getInt(2), c.getLong(3))) }
        }
    }

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        db.writableDatabase.insert("playlists", null, ContentValues().apply {
            put("name", name.trim()); put("created_at", System.currentTimeMillis())
        })
    }

    suspend fun addToPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.insertWithOnConflict("playlist_songs", null, ContentValues().apply {
            put("playlist_id", playlistId); put("song_id", songId); put("position", nextPlaylistPosition(playlistId))
        }, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
    }

    suspend fun scanLocations(): List<ScanLocation> = withContext(Dispatchers.IO) {
        db.readableDatabase.query("scan_locations", null, null, null, null, null, "label").use { c ->
            buildList {
                while (c.moveToNext()) {
                    val uri = c.getString(c.getColumnIndexOrThrow("uri"))
                    add(ScanLocation(c.getLong(0), uri, c.getString(2), true))
                }
            }
        }
    }

    suspend fun addScanLocation(uri: String, label: String) = withContext(Dispatchers.IO) {
        db.writableDatabase.insertWithOnConflict("scan_locations", null, ContentValues().apply {
            put("uri", uri); put("label", label)
        }, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
    }

    suspend fun removeScanLocation(id: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("scan_locations", "id=?", arrayOf(id.toString()))
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) { db.writableDatabase.delete("history", null, null) }
    fun getSetting(key: String, defaultValue: String) = db.getSetting(key, defaultValue)
    fun putSetting(key: String, value: String) = db.putSetting(key, value)

    private fun nextPlaylistPosition(playlistId: Long): Int =
        db.readableDatabase.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM playlist_songs WHERE playlist_id=?", arrayOf(playlistId.toString()))
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun Cursor.song(): Song = Song(
        getLong(getColumnIndexOrThrow("id")),
        getString(getColumnIndexOrThrow("uri")),
        getString(getColumnIndexOrThrow("title")),
        getString(getColumnIndexOrThrow("artist")),
        getString(getColumnIndexOrThrow("album")),
        getString(getColumnIndexOrThrow("album_artist")),
        getString(getColumnIndexOrThrow("genre")),
        getLong(getColumnIndexOrThrow("duration_ms")),
        getLong(getColumnIndexOrThrow("size_bytes")),
        getInt(getColumnIndexOrThrow("year")),
        getInt(getColumnIndexOrThrow("track_number")),
        getLong(getColumnIndexOrThrow("date_modified")),
        getString(getColumnIndexOrThrow("mime_type")),
        getString(getColumnIndexOrThrow("folder")),
        getString(getColumnIndexOrThrow("artwork_uri"))
    )

    private fun Song.values() = ContentValues().apply {
        put("id", id); put("uri", uri); put("title", title); put("artist", artist); put("album", album)
        put("album_artist", albumArtist); put("genre", genre); put("duration_ms", durationMs)
        put("size_bytes", sizeBytes); put("year", year); put("track_number", trackNumber)
        put("date_modified", dateModified); put("mime_type", mimeType); put("folder", folder)
        artworkUri?.let { put("artwork_uri", it) }
    }
}