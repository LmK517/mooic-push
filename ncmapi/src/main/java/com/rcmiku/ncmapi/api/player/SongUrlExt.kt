package com.rcmiku.ncmapi.api.player

import com.rcmiku.ncmapi.model.SongUrl
import com.rcmiku.ncmapi.model.SongUrlResponse
import kotlinx.serialization.json.JsonNull

/**
 * A NetEase url entry is only considered usable when it is a full length track.
 * Trial clips (30s/60s), "musicrep-ts" proxy links and entries carrying
 * [SongUrl.freeTrialInfo] are rejected.
 */
fun SongUrl.isFullPlayable(): Boolean {
    val playableUrl = url?.takeIf { it.isNotBlank() } ?: return false
    if (isTrialLink(playableUrl)) return false
    if (freeTrialInfo != null && freeTrialInfo !is JsonNull) return false
    if (time in 1..60_000) return false
    return true
}

fun SongUrlResponse.firstPlayableUrl(): SongUrl? = data.firstOrNull { it.isFullPlayable() }

fun isTrialLink(url: String): Boolean = url.contains("musicrep-ts", ignoreCase = true)
