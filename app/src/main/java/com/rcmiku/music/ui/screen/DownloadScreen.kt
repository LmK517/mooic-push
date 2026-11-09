package com.rcmiku.music.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.rcmiku.music.R
import com.rcmiku.music.download.DownloadRepository
import com.rcmiku.music.download.DownloadStatus
import com.rcmiku.music.download.DownloadTask
import com.rcmiku.music.utils.CoverImageSize
import com.rcmiku.music.utils.toCoverImageUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(navController: NavHostController) {
    val context = LocalContext.current
    val tasks by DownloadRepository.tasks.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.download_management)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
            )
        }
    ) { padding ->
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.download_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tasks, key = { it.id }) { task ->
                DownloadRow(
                    task = task,
                    onCancel = { DownloadRepository.cancel(task.id) },
                    onRemove = { DownloadRepository.remove(context, task.id) },
                    onRetry = { DownloadRepository.retry(context, task.id) },
                    onOpen = { openDownloadedFile(context, task) },
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(
    task: DownloadTask,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = task.status == DownloadStatus.COMPLETED) { onOpen() }
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = task.coverUrl.toCoverImageUrl(CoverImageSize.LIST),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = task.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PENDING) {
                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
                Text(
                    text = statusText(task),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (task.status == DownloadStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when (task.status) {
                DownloadStatus.COMPLETED -> IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                }

                DownloadStatus.FAILED, DownloadStatus.CANCELED -> Row {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                }

                else -> IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }
        }
    }
}

private fun statusText(task: DownloadTask): String = when (task.status) {
    DownloadStatus.PENDING -> "等待下载"
    DownloadStatus.DOWNLOADING -> {
        val percent = (task.progress * 100).toInt()
        val mb = task.receivedBytes / 1024f / 1024f
        "下载中 $percent% · ${"%.1f".format(mb)} MB"
    }

    DownloadStatus.COMPLETED -> "已完成 · ${task.fileName}"
    DownloadStatus.FAILED -> "失败：${task.error ?: "未知错误"}"
    DownloadStatus.CANCELED -> "已取消"
}

private fun openDownloadedFile(context: android.content.Context, task: DownloadTask) {
    if (task.fileUri.isBlank()) return
    val uri = Uri.parse(task.fileUri)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "audio/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
