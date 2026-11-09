package com.rcmiku.music.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import com.rcmiku.music.constants.downloadTasksKey
import com.rcmiku.music.utils.dataStore
import com.rcmiku.ncmapi.api.player.SongLevel
import com.rcmiku.ncmapi.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * In-memory download queue, persisted to DataStore on every status change.
 */
object DownloadRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var appContext: Context? = null
    private var scope: CoroutineScope? = null
    private var started = false

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    fun init(context: Context, coroutineScope: CoroutineScope) {
        if (started) return
        started = true
        val app = context.applicationContext
        appContext = app
        scope = coroutineScope
        coroutineScope.launch {
            val stored = app.dataStore.data.first()[downloadTasksKey]
            val restored = stored
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { json.decodeFromString<List<DownloadTask>>(it) }.getOrNull() }
                ?: emptyList()
            _tasks.value = restored.map { task ->
                // A task that was running when the process died can be resumed.
                if (task.status == DownloadStatus.DOWNLOADING) {
                    task.copy(status = DownloadStatus.PENDING, receivedBytes = 0L, totalBytes = 0L)
                } else {
                    task
                }
            }
        }
    }

    fun enqueue(context: Context, song: Song, level: SongLevel) {
        val task = DownloadTask(
            id = UUID.randomUUID().toString(),
            songId = song.id,
            title = song.name,
            artist = song.ar.joinToString("/") { it.name },
            album = song.al.name,
            coverUrl = song.al.picUrl,
            level = level.name,
            status = DownloadStatus.PENDING,
            createdAt = System.currentTimeMillis(),
        )
        _tasks.value = listOf(task) + _tasks.value
        persist()
        ContextCompat.startForegroundService(
            context.applicationContext,
            Intent(context.applicationContext, DownloadService::class.java),
        )
    }

    internal fun takeNext(): DownloadTask? {
        val next = _tasks.value.firstOrNull { it.status == DownloadStatus.PENDING } ?: return null
        markStatus(next.id, DownloadStatus.DOWNLOADING)
        return next
    }

    internal fun markDownloading(id: String) = markStatus(id, DownloadStatus.DOWNLOADING)

    /** Returns false when the task was canceled while downloading. */
    internal fun updateProgress(id: String, received: Long, total: Long): Boolean {
        var alive = true
        _tasks.value = _tasks.value.map { task ->
            when {
                task.id != id -> task
                task.status == DownloadStatus.CANCELED -> {
                    alive = false
                    task
                }

                else -> task.copy(receivedBytes = received, totalBytes = total)
            }
        }
        return alive
    }

    internal fun complete(id: String, outcome: Downloader.Outcome) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == id && task.status != DownloadStatus.CANCELED) {
                task.copy(
                    status = DownloadStatus.COMPLETED,
                    fileName = outcome.fileName,
                    fileUri = outcome.fileUri,
                    receivedBytes = outcome.size,
                    totalBytes = outcome.size,
                    error = null,
                )
            } else {
                task
            }
        }
        persist()
    }

    internal fun fail(id: String, message: String) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == id && task.status != DownloadStatus.CANCELED) {
                task.copy(status = DownloadStatus.FAILED, error = message)
            } else {
                task
            }
        }
        persist()
    }

    fun cancel(id: String) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == id && !task.isFinished) {
                task.copy(status = DownloadStatus.CANCELED)
            } else {
                task
            }
        }
        persist()
    }

    fun remove(context: Context, id: String) {
        val task = _tasks.value.firstOrNull { it.id == id }
        if (task != null && task.status == DownloadStatus.COMPLETED) {
            deleteFile(context, task.fileUri)
        }
        _tasks.value = _tasks.value.filterNot { it.id == id }
        persist()
    }

    fun retry(context: Context, id: String) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == id && (task.status == DownloadStatus.FAILED || task.status == DownloadStatus.CANCELED)) {
                task.copy(
                    status = DownloadStatus.PENDING,
                    receivedBytes = 0L,
                    totalBytes = 0L,
                    error = null,
                )
            } else {
                task
            }
        }
        persist()
        ContextCompat.startForegroundService(
            context.applicationContext,
            Intent(context.applicationContext, DownloadService::class.java),
        )
    }

    private fun markStatus(id: String, status: DownloadStatus) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == id) task.copy(status = status) else task
        }
        if (status != DownloadStatus.DOWNLOADING) persist()
    }

    private fun persist() {
        val app = appContext ?: return
        val targetScope = scope ?: return
        val snapshot = _tasks.value
        targetScope.launch {
            val encoded = runCatching { json.encodeToString(snapshot) }.getOrNull() ?: return@launch
            app.dataStore.edit { preferences ->
                preferences[downloadTasksKey] = encoded
            }
        }
    }

    private fun deleteFile(context: Context, fileUri: String) {
        if (fileUri.isBlank()) return
        runCatching {
            val uri = Uri.parse(fileUri)
            if (uri.scheme == "content") {
                context.contentResolver.delete(uri, null, null)
            } else {
                uri.path?.let { path -> java.io.File(path).delete() }
            }
        }
    }
}
