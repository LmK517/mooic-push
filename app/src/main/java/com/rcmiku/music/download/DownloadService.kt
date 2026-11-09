package com.rcmiku.music.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rcmiku.music.MainActivity
import com.rcmiku.music.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that drains the [DownloadRepository] queue one task at a time.
 */
private class DownloadCanceledException : Exception("已取消")

class DownloadService : Service() {

    companion object {
        private const val CHANNEL_ID = "mooic_download"
        private const val CHANNEL_NAME = "下载"
        private const val NOTIFICATION_ID = 2001
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (worker?.isActive != true) {
            worker = serviceScope.launch { runQueue() }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        worker?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun runQueue() {
        while (true) {
            val task = DownloadRepository.takeNext() ?: break
            DownloadRepository.markDownloading(task.id)
            notifyProgress(task, 0f)
            val result = Downloader.download(this, task) { received, total ->
                if (!DownloadRepository.updateProgress(task.id, received, total)) {
                    throw DownloadCanceledException()
                }
                notifyProgress(
                    task,
                    if (total > 0L) (received.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f,
                )
            }
            result
                .onSuccess { outcome -> DownloadRepository.complete(task.id, outcome) }
                .onFailure { error ->
                    DownloadRepository.fail(task.id, error.message ?: "下载失败")
                }
        }
        stopForegroundCompat()
        stopSelf()
    }

    private fun startForegroundCompat() {
        val notification = buildNotification(null, 0f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun notifyProgress(task: DownloadTask, progress: Float) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching {
            manager.notify(NOTIFICATION_ID, buildNotification(task, progress))
        }
    }

    private fun buildNotification(task: DownloadTask?, progress: Float) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(task?.title ?: "准备下载")
            .setContentText(task?.artist ?: "Mooic 正在下载歌曲")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, (progress * 100).toInt().coerceIn(0, 100), task == null)
            .setContentIntent(openAppIntent())
            .build()

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
