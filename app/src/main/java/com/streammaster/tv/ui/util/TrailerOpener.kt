package com.streammaster.tv.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

/**
 * Trailers (2026-11-22): opens the provider's YouTube trailer when the
 * title has one (Xtream `youtube_trailer`), otherwise a YouTube search
 * for "<title> trailer". Always an external open — YouTube playback
 * stays in the YouTube app / browser.
 */
object TrailerOpener {

    fun open(context: Context, title: String, trailerUrl: String?) {
        val url = trailerUrl?.takeIf { it.isNotBlank() }
            ?: "https://www.youtube.com/results?search_query=" +
                URLEncoder.encode("$title trailer", "UTF-8")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
