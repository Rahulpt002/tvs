package com.ntorqnav.bridge.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

enum class LogTag { BLE, PROTOCOL, NAVIGATION, GOOGLE_MAPS, BRIDGE, REPLAY, SAFETY, ERROR }

data class LogEntry(
    val timestamp: Long,
    val tag: LogTag,
    val message: String,
)

object AppLogger {
    private const val CAPACITY = 500

    @Volatile
    var verboseNavigationText: Boolean = false

    var clock: () -> Long = { System.currentTimeMillis() }

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun log(tag: LogTag, message: String) {
        val entry = LogEntry(clock(), tag, message)
        _entries.update { (it + entry).takeLast(CAPACITY) }
        runCatching {
            if (tag == LogTag.ERROR) Timber.e("[%s] %s", tag.name, message)
            else Timber.i("[%s] %s", tag.name, message)
        }
    }

    fun ble(msg: String) = log(LogTag.BLE, msg)
    fun protocol(msg: String) = log(LogTag.PROTOCOL, msg)
    fun navigation(msg: String) = log(LogTag.NAVIGATION, msg)
    fun maps(msg: String) = log(LogTag.GOOGLE_MAPS, msg)
    fun bridge(msg: String) = log(LogTag.BRIDGE, msg)
    fun replay(msg: String) = log(LogTag.REPLAY, msg)
    fun safety(msg: String) = log(LogTag.SAFETY, msg)
    fun error(msg: String, t: Throwable? = null) =
        log(LogTag.ERROR, if (t != null) "$msg: ${t.javaClass.simpleName}: ${t.message}" else msg)

    fun navText(text: String?): String =
        if (text == null) "-" else if (verboseNavigationText) text else "<${text.length} chars>"

    fun clear() = _entries.update { emptyList() }

    fun export(): String = entries.value.joinToString("\n") { "${it.timestamp} [${it.tag}] ${it.message}" }
}
