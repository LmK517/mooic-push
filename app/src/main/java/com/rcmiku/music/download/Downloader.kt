package com.rcmiku.music.download

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import com.rcmiku.music.source.SourceResolver
import com.rcmiku.ncmapi.api.player.SongLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Streams one [DownloadTask] to the device and returns the media store uri.
 */
object Downloader {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000

    data class Outcome(
        val fileUri: String,
        val fileName: String,
        val size: Long,
        val sourceName: String,
    )

    suspend fun download(
        context: Context,
        task: DownloadTask,
        onProgress: (received: Long, total: Long) -> Unit,
    ): Result<Outcome> = withContext(Dispatchers.IO) {
        val level = runCatching { SongLevel.valueOf(task.level) }.getOrDefault(SongLevel.STANDARD)
        val resolved = SourceResolver.resolve(task.songId.toString(), level)
            ?: return@withContext Result.failure(IOException("所有音源线路都拿不到下载地址"))
        val extension = guessExtension(resolved.url)
        val fileName = buildFileName(task, extension)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStore(context, resolved.url, fileName, extension, resolved.sourceName, onProgress)
        } else {
            saveToAppDir(context, resolved.url, fileName, resolved.sourceName, onProgress)
        }
    }

    private suspend fun saveToMediaStore(
        context: Context,
        url: String,
        fileName: String,
        extension: String,
        sourceName: String,
        onProgress: (Long, Long) -> Unit,
    ): Result<Outcome> {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeTypeOf(extension))
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Mooic")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: return Result.failure(IOException("无法在媒体库创建文件"))

        return try {
            val connection = openConnection(url)
            val total = connection.contentLengthLong.takeIf { it > 0L } ?: 0L
            val copied = connection.inputStream.use { input ->
                resolver.openOutputStream(uri)?.use { output ->
                    copyStream(input, output, total, onProgress)
                } ?: throw IOException("无法写入媒体库文件")
            }
            runCatching {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
            Result.success(Outcome(uri.toString(), fileName, copied, sourceName))
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            Result.failure(t)
        }
    }

    private suspend fun saveToAppDir(
        context: Context,
        url: String,
        fileName: String,
        sourceName: String,
        onProgress: (Long, Long) -> Unit,
    ): Result<Outcome> {
        val directory = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir,
            "Mooic",
        )
        if (!directory.exists() && !directory.mkdirs()) {
            return Result.failure(IOException("无法创建下载目录"))
        }
        val file = File(directory, fileName)
        return try {
            val connection = openConnection(url)
            val total = connection.contentLengthLong.takeIf { it > 0L } ?: 0L
            val copied = connection.inputStream.use { input ->
                file.outputStream().use { output -> copyStream(input, output, total, onProgress) }
            }
            Result.success(Outcome(file.toUri().toString(), fileName, copied, sourceName))
        } catch (t: Throwable) {
            runCatching { file.delete() }
            Result.failure(t)
        }
    }

    private suspend fun copyStream(
        input: InputStream,
        output: OutputStream,
        total: Long,
        onProgress: (Long, Long) -> Unit,
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        var lastReport = 0L
        var lastTime = System.currentTimeMillis()
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            copied += read
            val now = System.currentTimeMillis()
            if (copied - lastReport > 256 * 1024 || now - lastTime > 500) {
                lastReport = copied
                lastTime = now
                onProgress(copied, total)
            }
        }
        output.flush()
        onProgress(copied, if (total > 0L) total else copied)
        return copied
    }

    private fun openConnection(url: String): HttpURLConnection {
        var current = url
        var redirects = 0
        while (true) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Referer", "https://music.163.com/")
            }
            connection.connect()
            val code = connection.responseCode
            if (code in 300..399 && redirects < 5) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) throw IOException("HTTP $code 缺少跳转地址")
                current = if (location.startsWith("http")) {
                    location
                } else {
                    URI(current).resolve(location).toString()
                }
                redirects++
                continue
            }
            if (code !in 200..299) {
                connection.disconnect()
                throw IOException("HTTP $code")
            }
            return connection
        }
    }

    private fun guessExtension(url: String): String {
        val path = runCatching { URI(url).path }.getOrNull().orEmpty().lowercase()
        return when {
            path.endsWith(".flac") -> "flac"
            path.endsWith(".m4a") -> "m4a"
            path.endsWith(".ogg") -> "ogg"
            path.endsWith(".wav") -> "wav"
            path.endsWith(".aac") -> "aac"
            else -> "mp3"
        }
    }

    private fun mimeTypeOf(extension: String): String = when (extension) {
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "aac" -> "audio/aac"
        else -> "audio/mpeg"
    }

    private fun buildFileName(task: DownloadTask, extension: String): String {
        val raw = when {
            task.artist.isBlank() -> task.title
            task.title.isBlank() -> task.artist
            else -> "${task.artist} - ${task.title}"
        }
        val safe = raw
            .replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_")
            .trim()
            .ifBlank { "song_${task.songId}" }
            .take(120)
        return "$safe.$extension"
    }
}
