package com.ntorqnav.bridge.diagnostic

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ntorqnav.bridge.logging.AppLogger
import java.io.File

/**
 * Writes diagnostic artifacts to the app's external files directory so they can be pulled off
 * the phone in the field (section 5 export, section 9 report). Read/write of our own files only.
 */
class DiagnosticExporter(private val context: Context) {

    private val outputDir: File
        get() = File(context.getExternalFilesDir(null), "diagnostic-sessions").apply { mkdirs() }

    /** Writes the raw session JSON. Returns the file written. */
    fun writeSessionJson(session: DiagnosticSession, json: String): File {
        val file = File(outputDir, "${session.sessionId}.json")
        file.writeText(json)
        AppLogger.replay("Session JSON written: ${file.absolutePath}")
        return file
    }

    /** Writes the validation report markdown (section 9). Returns the file written. */
    fun writeValidationReport(session: DiagnosticSession, markdown: String): File {
        val file = File(outputDir, "real-device-validation-${session.sessionId}.md")
        file.writeText(markdown)
        AppLogger.replay("Validation report written: ${file.absolutePath}")
        return file
    }

    fun listSessions(): List<File> =
        outputDir.listFiles { f -> f.extension == "json" }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun readSession(file: File): String = file.readText()

    /** Opens the Android share sheet for an exported file, if a FileProvider is configured. */
    fun share(file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Export diagnostic").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            AppLogger.error("Failed to share diagnostic file", e)
        }
    }
}
