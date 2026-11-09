package com.rcmiku.music.source

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.rcmiku.music.constants.musicSourcesKey
import com.rcmiku.music.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Holds the installed 音源 list. The list order is the play priority,
 * the first enabled source wins.
 */
object SourceRegistry {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var appContext: Context? = null
    private var scope: CoroutineScope? = null

    private val _sources = MutableStateFlow(MusicSource.defaults())
    val sources: StateFlow<List<MusicSource>> = _sources.asStateFlow()

    fun init(context: Context, coroutineScope: CoroutineScope) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        scope = coroutineScope
        coroutineScope.launch {
            val stored = app.dataStore.data.first()[musicSourcesKey]
            val restored = decode(stored)
            if (restored.isNullOrEmpty()) {
                persist(MusicSource.defaults())
            } else {
                _sources.value = restored
            }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        update { list -> list.map { if (it.id == id) it.copy(enabled = enabled) else it } }
    }

    fun setBaseUrl(id: String, baseUrl: String) {
        update { list ->
            list.map {
                if (it.id == id) it.copy(baseUrl = baseUrl.trim().trimEnd('/')) else it
            }
        }
    }

    fun add(source: MusicSource) {
        update { list -> list.filterNot { it.id == source.id } + source }
    }

    fun remove(id: String) {
        update { list -> list.filterNot { it.id == id } }
    }

    /** Called from the drag handle, [fromId] is moved to the position of [toId]. */
    fun move(fromId: String, toId: String) {
        update { list ->
            val fromIndex = list.indexOfFirst { it.id == fromId }
            val toIndex = list.indexOfFirst { it.id == toId }
            if (fromIndex == -1 || toIndex == -1 || fromIndex == toIndex) {
                list
            } else {
                list.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            }
        }
    }

    fun enabledSources(): List<MusicSource> = _sources.value.filter { it.enabled }

    private fun update(block: (List<MusicSource>) -> List<MusicSource>) {
        val newList = block(_sources.value)
        _sources.value = newList
        persist(newList)
    }

    private fun persist(list: List<MusicSource>) {
        val app = appContext ?: return
        val targetScope = scope ?: return
        targetScope.launch {
            app.dataStore.edit { preferences ->
                preferences[musicSourcesKey] = json.encodeToString(list)
            }
        }
    }

    private fun decode(raw: String?): List<MusicSource>? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<List<MusicSource>>(raw) }.getOrNull()
    }
}
