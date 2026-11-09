package com.rcmiku.music.source

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Result of the last "线路检测" for a single route, kept in memory only.
 */
enum class RouteStatus {
    UNTESTED,
    OK,
    FAILED,
}

object SourceHealth {

    private val _status = MutableStateFlow<Map<String, RouteStatus>>(emptyMap())
    val status: StateFlow<Map<String, RouteStatus>> = _status.asStateFlow()

    fun key(sourceId: String, route: String): String = "$sourceId|$route"

    fun get(sourceId: String, route: String): RouteStatus =
        _status.value[key(sourceId, route)] ?: RouteStatus.UNTESTED

    fun mark(sourceId: String, route: String, ok: Boolean) {
        _status.value = _status.value + (key(sourceId, route) to if (ok) RouteStatus.OK else RouteStatus.FAILED)
    }

    fun markAll(sourceId: String, routes: List<String>, ok: Boolean) {
        _status.value = _status.value + routes.associate { route ->
            key(sourceId, route) to if (ok) RouteStatus.OK else RouteStatus.FAILED
        }
    }

    fun reset(sourceId: String, routes: List<String>) {
        _status.value = _status.value - routes.map { key(sourceId, it) }.toSet()
    }
}
