package com.rcmiku.music.source

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID

/**
 * Imports a source either from a plain matching-server url or from a small
 * JSON descriptor:
 *
 * ```json
 * { "name": "青听音源", "type": "unblock", "url": "https://example.com", "providers": ["unm"] }
 * ```
 */
object SourceImporter {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun import(context: Context, rawInput: String): Result<MusicSource> =
        withContext(Dispatchers.IO) {
            val input = rawInput.trim()
            if (input.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("请输入音源地址"))
            }
            val url = normalize(input)
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return@withContext Result.failure(IllegalArgumentException("只支持 http/https 地址"))
            }
            if (url.substringBefore('?').endsWith(".json", ignoreCase = true)) {
                runCatching { parseDescriptor(fetch(url)) }
                    .fold(
                        onSuccess = { Result.success(it) },
                        onFailure = { Result.failure(it) },
                    )
            } else {
                Result.success(
                    MusicSource(
                        id = "source_" + UUID.randomUUID().toString().take(8),
                        name = hostOf(url),
                        type = SourceType.UNBLOCK,
                        baseUrl = url.trimEnd('/'),
                        providers = MusicSource.DEFAULT_PROVIDERS,
                    )
                )
            }
        }

    private fun parseDescriptor(body: String): MusicSource {
        val element = json.parseToJsonElement(body)
        val obj: JsonObject = when (element) {
            is JsonObject -> element
            is JsonArray -> element.firstOrNull() as? JsonObject ?: error("JSON 中没有音源配置")
            else -> error("无法识别的音源配置")
        }
        val baseUrl = obj["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        require(baseUrl.startsWith("http")) { "音源配置缺少有效的 url 字段" }
        val type = when (obj["type"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
            "netease", "official" -> SourceType.NETEASE
            else -> SourceType.UNBLOCK
        }
        val providers = (obj["providers"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: MusicSource.DEFAULT_PROVIDERS
        return MusicSource(
            id = "source_" + UUID.randomUUID().toString().take(8),
            name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: hostOf(baseUrl),
            type = type,
            baseUrl = baseUrl.trimEnd('/'),
            providers = providers,
        )
    }

    private fun fetch(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
        }
        connection.connect()
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            error("HTTP $code")
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        return body
    }

    private fun normalize(input: String): String {
        if (input.startsWith("http://") || input.startsWith("https://")) return input
        if (input.contains("://")) return input
        return "https://$input"
    }

    private fun hostOf(url: String): String =
        runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "自定义音源"
}
