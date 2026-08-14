package com.nextgenmusic.player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Compose entry point for API 21+ feature surfaces. The production navigation path uses
 * XML views so the same APK remains runnable on the API 19 minimum supported device.
 */
@Composable
fun NextGenMusicComposeHeader(title: String) {
    MaterialTheme { Text(text = title) }
}