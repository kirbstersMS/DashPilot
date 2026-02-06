package com.example.dashpilot.util

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.dashpilot.data.GigPrefs
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object GigLogger {
    private const val LOG_FILE_NAME = "gig_pilot_debug.log"
    private const val MAX_FILE_SIZE = 5 * 1024 * 1024 // 5 MB Limit
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    private var logFile: File? = null
    private var isEnabled = false

    // Single-thread executor ensures logs are written sequentially, preventing race conditions
    private val ioExecutor = Executors.newSingleThreadExecutor()

    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE_NAME)
        isEnabled = GigPrefs(context).isDebugLoggingEnabled

        // Initial cleanup check
        ioExecutor.execute { checkRotation() }
    }

    fun setLoggingEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    // --- PUBLIC API ---

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        if (isEnabled) postLog("DEBUG", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        if (isEnabled) postLog("INFO", tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        if (isEnabled) postLog("WARN", tag, message)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        Log.e(tag, message, error)
        val stackTrace = error?.stackTraceToString() ?: ""
        if (isEnabled) postLog("ERROR", tag, "$message\n$stackTrace")
    }

    // --- FORENSIC LOGGING ---

    fun logTree(root: AccessibilityNodeInfo?) {
        if (!isEnabled || root == null) return

        // Offload the heavy tree traversal to the background immediately
        ioExecutor.execute {
            try {
                val sb = StringBuilder()
                sb.append("\n=== ACCESSIBILITY TREE SNAPSHOT ===\n")
                // Note: We traverse here on the background thread.
                // Ensure root is not recycled by the system if passed from an event!
                // (Usually safe in instant dump scenarios, but keep in mind).
                buildTreeRecursive(root, "", sb)
                sb.append("===================================\n")
                writeRaw(sb.toString())
            } catch (e: Exception) {
                Log.e("GigLogger", "Failed to dump tree", e)
            }
        }
    }

    private fun buildTreeRecursive(node: AccessibilityNodeInfo, indent: String, sb: StringBuilder) {
        sb.append(indent)
        sb.append(node.className?.toString()?.substringAfterLast('.') ?: "View")

        if (!node.text.isNullOrEmpty()) {
            sb.append(" [TEXT: \"${node.text}\"]")
        }
        if (!node.contentDescription.isNullOrEmpty()) {
            sb.append(" [DESC: \"${node.contentDescription}\"]")
        }
        if (!node.viewIdResourceName.isNullOrEmpty()) {
            sb.append(" [ID: ${node.viewIdResourceName}]")
        }
        sb.append("\n")

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                buildTreeRecursive(child, "$indent  | ", sb)
                // accessibility nodes should ideally be recycled if obtained via getChild,
                // but for a quick dump loop, standard GC usually catches up.
            }
        }
    }

    // --- INTERNAL I/O ---

    private fun postLog(level: String, tag: String, message: String) {
        ioExecutor.execute {
            val timestamp = dateFormat.format(Date())
            val entry = "$timestamp [$level/$tag]: $message\n"
            writeRaw(entry)
        }
    }

    // This method is always called from the ioExecutor, so it's thread-safe
    private fun writeRaw(text: String) {
        val file = logFile ?: return
        try {
            checkRotation() // Rotate if too big
            FileWriter(file, true).use { it.write(text) }
        } catch (e: IOException) {
            Log.e("GigLogger", "Failed to write log", e)
        }
    }

    private fun checkRotation() {
        val file = logFile ?: return
        if (file.exists() && file.length() > MAX_FILE_SIZE) {
            val backup = File(file.parent, "$LOG_FILE_NAME.old")
            if (backup.exists()) backup.delete()
            file.renameTo(backup)
            // Create new empty file
            file.createNewFile()
            Log.i("GigLogger", "Log file rotated (Size limit reached)")
        }
    }

    fun clear() {
        ioExecutor.execute {
            val file = logFile ?: return@execute
            try {
                FileWriter(file, false).use { it.write("") }
                Log.i("GigLogger", "Logs cleared by user.")
            } catch (e: IOException) {
                Log.e("GigLogger", "Failed to clear logs", e)
            }
        }
    }
}