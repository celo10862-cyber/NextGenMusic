package com.nextgenmusic.player.data

data class Song(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val year: Int,
    val trackNumber: Int,
    val dateModified: Long,
    val mimeType: String,
    val folder: String,
    val artworkUri: String?
)

data class Playlist(val id: Long, val name: String, val songCount: Int, val createdAt: Long)
data class ScanLocation(val id: Long, val uri: String, val label: String, val accessible: Boolean)
data class BrowserBookmark(val id: Long, val title: String, val url: String, val createdAt: Long)
data class BrowserHistoryEntry(val id: Long, val title: String, val url: String, val visitedAt: Long)

enum class RepeatMode { OFF, ALL, ONE }
enum class PerformanceMode { AUTO, ULTRA_LITE, BALANCED, MAXIMUM }
enum class ThemeMode { DARK, LIGHT, SYSTEM, AMOLED }