package com.asimzf.asimpdf.pdf

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The document properties shown in "Document info" and edited in "Metadata". */
data class PdfMetadata(
    val title: String = "",
    val author: String = "",
    val subject: String = "",
    val keywords: String = "",
    val creator: String = "",
    val producer: String = ""
)

data class PdfDocumentInfo(
    val pageCount: Int,
    val metadata: PdfMetadata,
    val created: String,
    val modified: String,
    val version: String,
    val encrypted: Boolean,
    val hasForm: Boolean,
    val firstPageSize: PageSize,
    val fileSizeBytes: Long
)

/** One entry of the document's table of contents. */
data class OutlineEntry(
    val title: String,
    val pageIndex: Int,
    val depth: Int
)

object PdfDocumentTools {

    suspend fun info(file: File): PdfDocumentInfo = withContext(Dispatchers.IO) {
        PdfIo.read(file) { document ->
            val information = document.documentInformation
            val page = document.pageOrNull(0)
            PdfDocumentInfo(
                pageCount = document.numberOfPages,
                metadata = PdfMetadata(
                    title = information.title.orEmpty(),
                    author = information.author.orEmpty(),
                    subject = information.subject.orEmpty(),
                    keywords = information.keywords.orEmpty(),
                    creator = information.creator.orEmpty(),
                    producer = information.producer.orEmpty()
                ),
                created = formatDate(information.creationDate),
                modified = formatDate(information.modificationDate),
                version = String.format(Locale.US, "%.1f", document.version),
                encrypted = document.isEncrypted,
                hasForm = document.documentCatalog?.acroForm?.fields?.isNotEmpty() == true,
                firstPageSize = page?.let {
                    PageSize(PdfCoords.displayWidth(it), PdfCoords.displayHeight(it))
                } ?: PageSize(612f, 792f),
                fileSizeBytes = file.length()
            )
        }
    }

    suspend fun updateMetadata(context: Context, file: File, metadata: PdfMetadata) =
        withContext(Dispatchers.IO) {
            PdfIo.edit(context, file) { document ->
                val information = document.documentInformation
                information.title = metadata.title.takeIf { it.isNotBlank() }
                information.author = metadata.author.takeIf { it.isNotBlank() }
                information.subject = metadata.subject.takeIf { it.isNotBlank() }
                information.keywords = metadata.keywords.takeIf { it.isNotBlank() }
                information.creator = metadata.creator.takeIf { it.isNotBlank() }
                information.producer = metadata.producer.takeIf { it.isNotBlank() }
                information.modificationDate = Calendar.getInstance()
            }
        }

    /** Strips author, dates and other identifying entries. */
    suspend fun clearMetadata(context: Context, file: File) = withContext(Dispatchers.IO) {
        PdfIo.edit(context, file) { document ->
            document.documentInformation.cosObject.clear()
            document.documentCatalog.metadata = null
        }
    }

    suspend fun outline(file: File): List<OutlineEntry> = withContext(Dispatchers.IO) {
        PdfIo.read(file) { document ->
            val entries = mutableListOf<OutlineEntry>()
            val root = document.documentCatalog?.documentOutline
            if (root != null) collect(document, root, 0, entries)
            entries.toList()
        }
    }

    private fun collect(
        document: PDDocument,
        node: PDOutlineNode,
        depth: Int,
        into: MutableList<OutlineEntry>
    ) {
        if (depth > MAX_OUTLINE_DEPTH) return
        var child: PDOutlineItem? = node.firstChild
        var guard = 0
        while (guard < MAX_OUTLINE_ENTRIES) {
            val current: PDOutlineItem = child ?: break
            guard++
            val pageIndex = runCatching {
                current.findDestinationPage(document)?.let { page -> document.pages.indexOf(page) }
            }.getOrNull() ?: -1
            into.add(
                OutlineEntry(
                    title = current.title.orEmpty().ifBlank { "Untitled" },
                    pageIndex = pageIndex,
                    depth = depth
                )
            )
            collect(document, current, depth + 1, into)
            child = current.nextSibling
        }
    }

    private fun formatDate(calendar: Calendar?): String {
        val date: Date = calendar?.time ?: return "—"
        return SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(date)
    }

    private const val MAX_OUTLINE_DEPTH = 6
    private const val MAX_OUTLINE_ENTRIES = 2000
}
