package com.nextgenmusic.player.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Compose surface for API 21+ devices. The main app intentionally uses XML views
 * on API 19 and does not load this module on unsupported runtimes.
 */
@Composable
fun NextGenMusicComposeHeader(title: String) {
    MaterialTheme { Text(text = title) }
}