package com.asimzf.asimpdf.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

/** Hands files to another app on the device through the system share sheet. */
object Sharing {

    fun shareFile(context: Context, file: File, mimeType: String = "application/pdf", title: String = "Share") {
        val uri = Workspace.shareUri(context, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launchChooser(context, intent, title)
    }

    fun shareFiles(
        context: Context,
        files: List<File>,
        mimeType: String = "application/pdf",
        title: String = "Share"
    ) {
        if (files.isEmpty()) return
        if (files.size == 1) {
            shareFile(context, files.first(), mimeType, title)
            return
        }
        val uris = ArrayList<Uri>(files.map { Workspace.shareUri(context, it) })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launchChooser(context, intent, title)
    }

    private fun launchChooser(context: Context, intent: Intent, title: String) {
        val chooser = Intent.createChooser(intent, title).apply {
            // The caller is often the application context, which needs its own task.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
