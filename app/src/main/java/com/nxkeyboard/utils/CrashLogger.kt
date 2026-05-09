package com.nxkeyboard.utils

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {

    private const val LOG_FILENAME = "nxkeyboard_error_log.txt"
    private const val MAX_LOG_SIZE_BYTES = 256 * 1024
    private const val HEADER = "THIS IS THE ERROR — please send this file to the developer to fix it"

    @Volatile
    private var initialized = false

    fun install(context: Context) {
        if (initialized) return
        initialized = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeEntry(appContext, "FATAL CRASH on thread '${thread.name}'", throwable)
            } catch (_: Throwable) {}
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun logNonFatal(context: Context, tag: String, throwable: Throwable) {
        try {
            writeEntry(context.applicationContext, "NON-FATAL [$tag]", throwable)
        } catch (_: Throwable) {}
    }

    fun logMessage(context: Context, tag: String, message: String) {
        try {
            writeEntry(context.applicationContext, "INFO [$tag]", null, message)
        } catch (_: Throwable) {}
    }

    fun getLogFile(context: Context): File {
        val dir = context.applicationContext.getExternalFilesDir(null)
            ?: context.applicationContext.filesDir
        return File(dir, LOG_FILENAME)
    }

    fun clear(context: Context) {
        try {
            getLogFile(context).delete()
        } catch (_: Throwable) {}
    }

    fun read(context: Context): String {
        return try {
            val file = getLogFile(context)
            if (!file.exists()) "" else file.readText(Charsets.UTF_8)
        } catch (_: Throwable) {
            ""
        }
    }

    fun hasLog(context: Context): Boolean {
        return try {
            val file = getLogFile(context)
            file.exists() && file.length() > 0
        } catch (_: Throwable) {
            false
        }
    }

    private fun writeEntry(context: Context, kind: String, throwable: Throwable?, extraMessage: String? = null) {
        val file = getLogFile(context)
        val parent = file.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        if (file.exists() && file.length() > MAX_LOG_SIZE_BYTES) {
            file.delete()
        }
        val isNew = !file.exists()
        val writer = PrintWriter(file.outputStream().bufferedWriter(Charsets.UTF_8).let {
            java.io.BufferedWriter(java.io.FileWriter(file, true))
        })
        try {
            if (isNew) {
                writer.println(HEADER)
                writer.println("=".repeat(72))
                writer.println("App: NX Keyboard")
                writer.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                writer.println("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                writer.println("ABI: ${Build.SUPPORTED_ABIS.joinToString(",")}")
                writer.println("=".repeat(72))
                writer.println()
            }
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            writer.println("--- $timestamp | $kind ---")
            if (extraMessage != null) {
                writer.println(extraMessage)
            }
            if (throwable != null) {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                writer.println(sw.toString())
            }
            writer.println()
            writer.flush()
        } finally {
            try { writer.close() } catch (_: Throwable) {}
        }
    }
}
