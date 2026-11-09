package com.rcmiku.music.download

import kotlinx.serialization.Serializable

@Serializable
enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELED,
}

@Serializable
data class DownloadTask(
    val id: String,
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String = "",
    val coverUrl: String = "",
    val level: String = "STANDARD",
    val status: DownloadStatus = DownloadStatus.PENDING,
    val receivedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val fileName: String = "",
    val fileUri: String = "",
    val error: String? = null,
    val createdAt: Long = 0L,
) {
    val progress: Float
        get() = if (totalBytes > 0L) {
            (receivedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val isFinished: Boolean
        get() = status == DownloadStatus.COMPLETED ||
            status == DownloadStatus.FAILED ||
            status == DownloadStatus.CANCELED
}
