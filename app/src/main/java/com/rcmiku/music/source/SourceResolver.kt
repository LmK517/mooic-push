package com.rcmiku.music.source

import com.rcmiku.ncmapi.api.UNBLOCK_BASE_URL
import com.rcmiku.ncmapi.api.player.PlayerApi
import com.rcmiku.ncmapi.api.player.SongLevel
import com.rcmiku.ncmapi.api.player.firstPlayableUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves a playable / downloadable url by walking the installed sources
 * in priority order.
 */
object SourceResolver {

    /** A song id used by the "线路检测" action. */
    const val TEST_SONG_ID = "1901371647"

    data class ResolvedSong(
        val url: String,
        val bitrate: Int,
        val sourceName: String,
        val route: String,
    )

    suspend fun resolve(rawSongId: String, level: SongLevel): ResolvedSong? =
        withContext(Dispatchers.IO) {
            SourceRegistry.enabledSources().forEach { source ->
                val resolved = resolveWith(source, rawSongId, level)
                if (resolved != null) return@withContext resolved
            }
            null
        }

    private suspend fun resolveWith(
        source: MusicSource,
        rawSongId: String,
        level: SongLevel,
    ): ResolvedSong? {
        when (source.type) {
            SourceType.NETEASE -> {
                val songUrl = PlayerApi.officialSongUrl(rawSongId, level)
                    .getOrNull()
                    ?.firstPlayableUrl()
                val url = songUrl?.url
                if (songUrl == null || url.isNullOrBlank()) return null
                SourceHealth.mark(source.id, MusicSource.OFFICIAL_ROUTE, true)
                return ResolvedSong(url, songUrl.br, source.name, MusicSource.OFFICIAL_ROUTE)
            }

            SourceType.UNBLOCK -> {
                val baseUrl = source.baseUrl.ifBlank { UNBLOCK_BASE_URL }
                for (route in source.routes) {
                    val songUrl = PlayerApi.matchedSongUrl(baseUrl, rawSongId, route)
                        .getOrNull()
                    val url = songUrl?.url
                    if (songUrl == null || url.isNullOrBlank()) {
                        SourceHealth.mark(source.id, route, false)
                        continue
                    }
                    SourceHealth.mark(source.id, route, true)
                    return ResolvedSong(url, songUrl.br, source.name, route)
                }
            }
        }
        return null
    }

    /** Probe one route of a source with [TEST_SONG_ID]. */
    suspend fun testRoute(source: MusicSource, route: String): Boolean =
        withContext(Dispatchers.IO) {
            val ok = when (source.type) {
                SourceType.NETEASE -> PlayerApi.officialSongUrl(TEST_SONG_ID, SongLevel.STANDARD)
                    .getOrNull()
                    ?.firstPlayableUrl() != null

                SourceType.UNBLOCK -> {
                    val baseUrl = source.baseUrl.ifBlank { UNBLOCK_BASE_URL }
                    val url = PlayerApi.matchedSongUrl(baseUrl, TEST_SONG_ID, route)
                        .getOrNull()
                        ?.url
                    !url.isNullOrBlank()
                }
            }
            SourceHealth.mark(source.id, route, ok)
            ok
        }

    suspend fun testAll(source: MusicSource): Boolean =
        withContext(Dispatchers.IO) {
            var anyOk = false
            for (route in source.routes) {
                if (testRoute(source, route)) anyOk = true
            }
            anyOk
        }
}
