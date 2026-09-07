package com.asimzf.asimpdf.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Everything asimPDF edits lives in a private working directory first; the user's
 * original file is only touched when they explicitly save.
 */
object Workspace {

    private const val WORK_DIR = "work"
    private const val SHARE_DIR = "shared"
    private const val EXPORT_DIR = "exports"

    fun workDir(context: Context): File = File(context.cacheDir, WORK_DIR).apply { mkdirs() }

    fun shareDir(context: Context): File = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }

    fun exportDir(context: Context): File = File(context.filesDir, EXPORT_DIR).apply { mkdirs() }

    fun newWorkFile(context: Context, suffix: String = ".pdf"): File =
        File(workDir(context), "doc_${System.currentTimeMillis()}_${counter()}$suffix")

    private var seq = 0
    @Synchronized
    private fun counter(): Int = ++seq

    /** Copies [uri] into the working directory so edits never mutate the source. */
    fun importToWork(context: Context, uri: Uri, suffix: String = ".pdf"): File {
        val target = newWorkFile(context, suffix)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to read the selected file." }
            FileOutputStream(target).use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
        }
        return target
    }

    fun copyStream(input: InputStream, target: File): File {
        FileOutputStream(target).use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
        return target
    }

    /** Writes [file] back to [uri], truncating whatever was there. */
    fun exportToUri(context: Context, file: File, uri: Uri) {
        val resolver: ContentResolver = context.contentResolver
        // "wt" truncates; some providers reject the mode, so fall back to plain write.
        val stream = runCatching { resolver.openOutputStream(uri, "wt") }
            .getOrNull() ?: resolver.openOutputStream(uri)
        requireNotNull(stream) { "Unable to write to the selected location." }
        stream.use { output -> file.inputStream().use { it.copyTo(output, DEFAULT_BUFFER_SIZE) } }
    }

    /** Returns a content:// URI other apps can read, for share and print intents. */
    fun shareUri(context: Context, file: File): Uri {
        val shared = File(shareDir(context), file.name)
        if (file.absolutePath != shared.absolutePath) {
            file.copyTo(shared, overwrite = true)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shared)
    }

    fun displayName(context: Context, uri: Uri): String {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.lastPathSegment ?: "document.pdf"
        }
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getString(0)
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
    }

    fun sizeOf(context: Context, uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0) }
        return 0L
    }

    fun baseName(name: String): String = name.substringBeforeLast('.', name)

    fun pdfName(base: String, suffix: String): String = "${baseName(base)}$suffix.pdf"

    /** Drops working files from previous sessions on a cold start. */
    fun clearStaleTemp(context: Context) {
        runCatching {
            val cutoff = System.currentTimeMillis() - STALE_AFTER_MS
            listOf(workDir(context), shareDir(context)).forEach { dir ->
                dir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoff) file.deleteRecursively()
                }
            }
        }
    }

    private const val STALE_AFTER_MS = 24L * 60 * 60 * 1000
}
