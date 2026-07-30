package com.flowseal.tgwsproxy.util

import android.content.Context
import android.util.Log
import com.flowseal.tgwsproxy.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppLog {
    private const val TAG = "TgWsProxy"
    /** In-memory UI ring buffer — bounded RAM. */
    private const val MAX_LINES = 300
    /** On-disk cap before truncate (bytes). */
    private const val MAX_FILE_BYTES = 2L * 1024L * 1024L // 2 MB
    private const val KEEP_TAIL_BYTES = 512L * 1024L // keep last 512 KB on rotate

    private val lines = CopyOnWriteArrayList<String>()
    private val _tail = MutableStateFlow("")
    val tail: StateFlow<String> = _tail.asStateFlow()

    @Volatile
    private var logFile: File? = null

    @Volatile
    var verbose: Boolean = false

    fun init(context: Context) {
        val dir = File(context.filesDir, "logs").also { it.mkdirs() }
        logFile = File(dir, "proxy.log")
        rotateIfNeeded()
    }

    fun i(msg: String) {
        append("I", msg)
        Log.i(TAG, sanitize(msg))
    }

    /** Debug-only lines — skipped unless verbose is on (saves spam + I/O). */
    fun d(msg: String) {
        if (!verbose) return
        append("D", msg)
        Log.d(TAG, sanitize(msg))
    }

    fun w(msg: String) {
        append("W", msg)
        Log.w(TAG, sanitize(msg))
    }

    fun e(msg: String, t: Throwable? = null) {
        append("E", msg + (t?.let { ": ${it.message}" } ?: ""))
        Log.e(TAG, sanitize(msg), t)
    }

    fun clear() {
        lines.clear()
        _tail.value = ""
        try {
            logFile?.writeText("")
        } catch (_: Exception) {
        }
    }

    /**
     * Copy on-disk log into cacheDir/export for sharing or SAF save.
     * Returns null if there is nothing to export.
     */
    fun snapshotForExport(context: Context): File? {
        val src = logFile ?: return null
        if (!src.exists() || src.length() == 0L) {
            // Fall back to in-memory ring if file empty but UI has lines
            if (lines.isEmpty()) return null
        }
        return try {
            val dir = File(context.cacheDir, "export").also { it.mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val dest = File(dir, "tgwsproxy-$stamp.log")
            if (src.exists() && src.length() > 0L) {
                src.copyTo(dest, overwrite = true)
            } else {
                dest.writeText(lines.joinToString("\n") + "\n")
            }
            dest
        } catch (_: Exception) {
            null
        }
    }

    private fun append(level: String, msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val line = "$ts  $level  $msg"
        lines.add(line)
        // Drop from front in batches (CopyOnWriteArrayList removeAt(0) is costly one-by-one)
        if (lines.size > MAX_LINES) {
            val drop = lines.size - MAX_LINES
            val kept = lines.drop(drop)
            lines.clear()
            lines.addAll(kept)
        }
        _tail.value = lines.joinToString("\n")
        try {
            val f = logFile ?: return
            f.appendText(line + "\n")
            rotateIfNeeded()
        } catch (_: Exception) {
        }
    }

    private fun rotateIfNeeded() {
        val f = logFile ?: return
        try {
            if (!f.exists() || f.length() <= MAX_FILE_BYTES) return
            val raw = f.readBytes()
            val keep = raw.copyOfRange(
                (raw.size - KEEP_TAIL_BYTES.toInt()).coerceAtLeast(0),
                raw.size,
            )
            // Start at next newline so we don't keep a torn line
            val start = keep.indexOf('\n'.code.toByte()).let { if (it in 0 until keep.lastIndex) it + 1 else 0 }
            f.writeBytes(keep.copyOfRange(start, keep.size))
            f.appendText("\n--- log truncated ---\n")
        } catch (_: Exception) {
        }
    }

    /** Never print full MTProto secret in release logcat. */
    fun sanitize(msg: String): String {
        if (BuildConfig.DEBUG) return msg
        return msg
            .replace(Regex("secret=dd[0-9a-fA-F]{32}"), "secret=dd****")
            .replace(Regex("Secret:\\s+[0-9a-fA-F]{32}"), "Secret: ****")
            .replace(Regex("tg://proxy\\?[^\"]*secret=dd[0-9a-fA-F]{32}"), "tg://proxy?…")
    }
}
