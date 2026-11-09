package com.rcmiku.music.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rcmiku.music.R
import com.rcmiku.music.source.MusicSource
import com.rcmiku.music.source.RouteStatus
import com.rcmiku.music.source.SourceHealth
import com.rcmiku.music.source.SourceResolver
import com.rcmiku.music.source.SourceRegistry
import com.rcmiku.music.ui.components.SourceEditDialog
import com.rcmiku.music.ui.components.SourceImportDialog
import com.rcmiku.music.ui.icons.AudioLines
import com.rcmiku.music.ui.icons.Cloud
import com.rcmiku.music.ui.icons.GraphicEq
import com.rcmiku.music.ui.icons.LibraryMusic
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SourceScreen(navController: NavHostController) {
    val sources by SourceRegistry.sources.collectAsState()
    val health by SourceHealth.status.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var showImportDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<MusicSource?>(null) }

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val fromId = from.key as? String
        val toId = to.key as? String
        if (fromId != null && toId != null) {
            SourceRegistry.move(fromId, toId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.music_source)) },
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
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 12.dp,
                end = 12.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.installed_sources),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.source_count, sources.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(onClick = { showImportDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.import_from_url))
                    }
                }
            }

            itemsIndexed(sources, key = { _, source -> source.id }) { index, source ->
                ReorderableItem(reorderableState, key = source.id) { _ ->
                    SourceCard(
                        source = source,
                        isPrimary = index == 0 && source.enabled,
                        enabledRoutes = source.routes.count {
                            SourceHealth.get(source.id, it) != RouteStatus.FAILED
                        },
                        totalRoutes = source.routes.size,
                        health = health,
                        onEnabledChange = { SourceRegistry.setEnabled(source.id, it) },
                        onRouteClick = { route -> scope.launch { SourceResolver.testRoute(source, route) } },
                        onTestAll = { scope.launch { SourceResolver.testAll(source) } },
                        onEditUrl = { editingSource = source },
                        onDelete = { SourceRegistry.remove(source.id) },
                        dragHandle = {
                            Icon(
                                imageVector = com.rcmiku.music.ui.icons.DragHandle,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(24.dp)
                                    .draggableHandle(),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            item(key = "help_header") {
                Column(modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)) {
                    Text(
                        text = stringResource(R.string.feature_help),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.feature_help_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(helpItems) { help ->
                HelpCard(help)
            }
        }
    }

    editingSource?.let { source ->
        SourceEditDialog(
            title = source.name,
            currentUrl = source.baseUrl,
            onDismiss = { editingSource = null },
            onConfirm = { url -> SourceRegistry.setBaseUrl(source.id, url) },
        )
    }

    if (showImportDialog) {
        SourceImportDialog(
            onDismiss = { showImportDialog = false },
            onImported = { source ->
                SourceRegistry.add(source)
                showImportDialog = false
            },
        )
    }
}

private data class HelpEntry(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

private val helpItems = listOf(
    HelpEntry(
        icon = GraphicEq,
        title = "播放路由",
        description = "按已安装顺序依次尝试音源，先拿到完整可播放地址的音源胜出；官方音源拿不到时自动回落到匹配音源。",
    ),
    HelpEntry(
        icon = Cloud,
        title = "音源导入",
        description = "支持导入 UnblockNeteaseMusic 兼容服务地址，或指向一份 JSON 描述的远程音源配置。",
    ),
    HelpEntry(
        icon = com.rcmiku.music.ui.icons.DragHandle,
        title = "排序与启停",
        description = "长按卡片右侧拖拽把手可调整优先顺序，卡片开关用于控制音源启用状态。",
    ),
    HelpEntry(
        icon = AudioLines,
        title = "线路检测",
        description = "点击卡片中的线路标签可单独检测；绿色表示正常，红色表示异常，灰色表示尚未检测。",
    ),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceCard(
    source: MusicSource,
    isPrimary: Boolean,
    enabledRoutes: Int,
    totalRoutes: Int,
    health: Map<String, RouteStatus>,
    onEnabledChange: (Boolean) -> Unit,
    onRouteClick: (String) -> Unit,
    onTestAll: () -> Unit,
    onEditUrl: () -> Unit,
    onDelete: () -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (source.enabled) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(16.dp),
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (source.type == com.rcmiku.music.source.SourceType.NETEASE) {
                            LibraryMusic
                        } else {
                            Cloud
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = source.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        if (isPrimary) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = stringResource(R.string.priority_source),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(
                                    color = if (source.enabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = CircleShape
                                )
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = stringResource(
                                if (source.enabled) R.string.source_running else R.string.source_stopped
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = " · ${source.version} · " + stringResource(
                                R.string.route_count,
                                enabledRoutes,
                                totalRoutes
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(checked = source.enabled, onCheckedChange = onEnabledChange)
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.test_all_routes)) },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onTestAll()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit_address)) },
                            enabled = source.type == com.rcmiku.music.source.SourceType.UNBLOCK,
                            onClick = {
                                menuExpanded = false
                                onEditUrl()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_source)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            enabled = !source.builtIn,
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
                dragHandle()
            }

            Spacer(Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                source.routes.forEach { route ->
                    val status = health[SourceHealth.key(source.id, route)] ?: RouteStatus.UNTESTED
                    RouteChip(
                        name = route,
                        status = status,
                        enabled = source.enabled,
                        onClick = { onRouteClick(route) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteChip(
    name: String,
    status: RouteStatus,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val dotColor = when {
        !enabled -> MaterialTheme.colorScheme.outlineVariant
        status == RouteStatus.OK -> MaterialTheme.colorScheme.primary
        status == RouteStatus.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color = dotColor, shape = CircleShape)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HelpCard(entry: HelpEntry) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
