package com.nextgenmusic.player.storage

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.nextgenmusic.player.data.MusicRepository
import com.nextgenmusic.player.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class MediaScanner(private val context: Context, private val repository: MusicRepository) {
    suspend fun scan(onProgress: (Int) -> Unit): Int = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection = arrayOf(
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ARTIST, MediaStore.Audio.Media.GENRE,
                MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.DATE_MODIFIED, MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.ALBUM_ID
            )
            context.contentResolver.query(collection, projection, null, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")
                ?.use { cursor ->
                    val total = cursor.count.coerceAtLeast(1)
                    var index = 0
                    while (cursor.moveToNext()) {
                        coroutineContext.ensureActive()
                        val id = cursor.getLong(0)
                        val uri = ContentUris.withAppendedId(collection, id)
                        songs += Song(
                            id = id, uri = uri.toString(), title = safe(cursor, 1, "Unknown track"),
                            artist = safe(cursor, 2, "Unknown artist"), album = safe(cursor, 3, "Unknown album"),
                            albumArtist = safe(cursor, 4, safe(cursor, 2, "Unknown artist")),
                            genre = safe(cursor, 5, "Unknown genre"), durationMs = cursor.getLong(6),
                            sizeBytes = cursor.getLong(7), year = cursor.getInt(8), trackNumber = cursor.getInt(9),
                            dateModified = cursor.getLong(10), mimeType = safe(cursor, 11, "audio/*"),
                            folder = safe(cursor, 12, ""), artworkUri = cursor.getLong(13).takeIf { it > 0 }?.let {
                                ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), it).toString()
                            }
                        )
                        index++
                        if (index % 25 == 0) onProgress((index * 100) / total)
                    }
                }
        } else {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.DATA
            )
            context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, null, null, null)?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    songs += Song(id, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString(),
                        safe(it, 1, "Unknown track"), safe(it, 2, "Unknown artist"), safe(it, 3, "Unknown album"),
                        safe(it, 2, "Unknown artist"), "Unknown genre", it.getLong(4), it.getLong(5), 0, 0, 0,
                        safe(it, 6, "audio/*"), safe(it, 7, ""), null)
                }
            }
        }
        repository.upsertSongs(songs)
        onProgress(100)
        songs.size
    }

    suspend fun scanTree(uri: Uri, onProgress: (Int) -> Unit): Int = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, uri) ?: return@withContext 0
        val files = tree.listFiles().toList()
        val total = files.size.coerceAtLeast(1)
        var count = 0
        files.forEachIndexed { index, file ->
            coroutineContext.ensureActive()
            if (file.isDirectory) count += scanTree(file.uri, { _ -> })
            else if (isAudio(file.name, file.type)) {
                val name = file.name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Unknown track"
                repository.upsertSongs(listOf(Song(
                    id = file.uri.toString().hashCode().toLong(), uri = file.uri.toString(), title = name,
                    artist = "Unknown artist", album = "Folder audio", albumArtist = "Unknown artist",
                    genre = "Unknown genre", durationMs = 0, sizeBytes = file.length(), year = 0, trackNumber = 0,
                    dateModified = file.lastModified(), mimeType = file.type ?: "audio/*", folder = tree.name ?: "", artworkUri = null
                )))
                count++
            }
            onProgress(((index + 1) * 100) / total)
        }
        count
    }

    private fun isAudio(name: String?, type: String?): Boolean {
        val lower = name?.lowercase() ?: ""
        return type?.startsWith("audio/") == true || listOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "aiff", "alac").any { lower.endsWith(".$it") }
    }

    private fun safe(cursor: android.database.Cursor, index: Int, fallback: String): String =
        if (cursor.isNull(index)) fallback else cursor.getString(index).takeIf { it.isNotBlank() } ?: fallback
}