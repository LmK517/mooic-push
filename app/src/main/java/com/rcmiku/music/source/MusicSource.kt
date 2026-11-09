package com.rcmiku.music.source

import kotlinx.serialization.Serializable

/**
 * Where a [MusicSource] gets its playable url from.
 */
@Serializable
enum class SourceType {
    /** The official NetEase endpoint, quality is limited by the logged in account. */
    NETEASE,

    /** A third party matching service compatible with UnblockNeteaseMusic-utils. */
    UNBLOCK,
}

/**
 * One entry of the "已安装音源" list.
 *
 * [baseUrl] is only used by [SourceType.UNBLOCK]; an empty value means
 * "use the global default matching server".
 */
@Serializable
data class MusicSource(
    val id: String,
    val name: String,
    val type: SourceType = SourceType.UNBLOCK,
    val baseUrl: String = "",
    val providers: List<String> = DEFAULT_PROVIDERS,
    val enabled: Boolean = true,
    val version: String = "1.0",
    val builtIn: Boolean = false,
) {

    /** Routes are shown as chips on the source card. */
    val routes: List<String>
        get() = when (type) {
            SourceType.NETEASE -> listOf(OFFICIAL_ROUTE)
            SourceType.UNBLOCK -> providers
        }

    companion object {
        const val OFFICIAL_SOURCE_ID = "netease_official"
        const val MATCH_SOURCE_ID = "netease_match"
        const val OFFICIAL_ROUTE = "官方接口"

        val DEFAULT_PROVIDERS = listOf(
            "byfuns",
            "unm",
            "ddyr",
            "gdmusic",
            "msls",
            "oi",
            "qijieya",
        )

        fun defaults(): List<MusicSource> = listOf(
            MusicSource(
                id = OFFICIAL_SOURCE_ID,
                name = "网易云音乐",
                type = SourceType.NETEASE,
                version = "1.0",
                builtIn = true,
            ),
            MusicSource(
                id = MATCH_SOURCE_ID,
                name = "匹配音源",
                type = SourceType.UNBLOCK,
                version = "1.0",
                builtIn = true,
            ),
        )
    }
}
