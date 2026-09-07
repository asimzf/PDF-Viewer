package com.asimzf.asimpdf.pdf

import android.content.Context
import com.asimzf.asimpdf.util.Workspace
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Named page sizes offered when creating or inserting pages. */
enum class PaperSize(val label: String, val rect: PDRectangle) {
    A4("A4", PDRectangle.A4),
    A5("A5", PDRectangle.A5),
    A3("A3", PDRectangle.A3),
    LETTER("Letter", PDRectangle.LETTER),
    LEGAL("Legal", PDRectangle.LEGAL);

    fun rectangle(landscape: Boolean): PDRectangle =
        if (landscape) PDRectangle(rect.height, rect.width) else PDRectangle(rect.width, rect.height)
}

/**
 * Structural page editing: rotate, delete, reorder, duplicate, extract, insert,
 * merge, split and crop. Everything happens on the local working copy.
 */
object PdfPageOps {

    suspend fun rotate(context: Context, file: File, pages: Collection<Int>, degrees: Int) =
        withContext(Dispatchers.IO) {
            PdfIo.edit(context, file) { document ->
                pages.forEach { index ->
                    document.pageOrNull(index)?.let { page ->
                        page.rotation = normalizeRotation(page.rotation + degrees)
                    }
                }
            }
        }

    suspend fun setRotation(context: Context, file: File, pages: Collection<Int>, degrees: Int) =
        withContext(Dispatchers.IO) {
            PdfIo.edit(context, file) { document ->
                pages.forEach { index ->
                    document.pageOrNull(index)?.rotation = normalizeRotation(degrees)
                }
            }
        }

    suspend fun delete(context: Context, file: File, pages: Collection<Int>) =
        withContext(Dispatchers.IO) {
            PdfIo.edit(context, file) { document ->
                val doomed = pages.toSet()
                if (doomed.size >= document.numberOfPages) {
                    throw PdfOperationException("A PDF needs at least one page.")
                }
                doomed.sortedDescending().forEach { index ->
                    if (index in 0 until document.numberOfPages) document.removePage(index)
                }
            }
        }

    /** Moves the pages in [pages] so they sit immediately before [targetIndex]. */
    suspend fun move(context: Context, file: File, pages: List<Int>, targetIndex: Int) =
        withContext(Dispatchers.IO) {
            PdfIo.edit(context, file) { document ->
                val order = (0 until document.numberOfPages).toMutableList()
                val moving = pages.filter { it in order }.sorted()
                if (moving.isEmpty()) return@edit
                val movingSet = moving.toSet()
                // Where the block lands once the moved pages are lifted out.
                val insertAt = (0 until targetIndex.coerceIn(0, order.size))
                    .count { it !in movingSet }
                order.removeAll(movingSet)
                order.addAll(insertAt.coerceIn(0, order.size), moving)
                reorder(document, order)
            }
        }

    /** Rewrites the page tree to the exact order given (0-based source indices). */
    suspend fun reorder(context: Context, file: File, order: List<Int>) =
        withContext(Dispatchers.IO) {
            PdfIo.edit(context, file) { document -> reorder(document, order) }
        }

    suspend fun reverse(context: Context, file: File) = withContext(Dispatchers.IO) {
        PdfIo.edit(context, file) { document ->
            reorder(document, (document.numberOfPages - 1 downTo 0).toList())
        }
    }

    suspend fun duplicate(context: Context, file: File, pages: Collection<Int>) =
        withContext(Dispatchers.IO) {
            PdfIo.edit(context, file) { document ->
                pages.sorted().forEach { index ->
                    document.pageOrNull(index)?.let { page ->
                        document.importPage(page)
                    }
                }
            }
        }

    suspend fun insertBlank(
        context: Context,
        file: File,
        afterIndex: Int,
        size: PaperSize,
        landscape: Boolean
    ) = withContext(Dispatchers.IO) {
        PdfIo.edit(context, file) { document ->
            val blank = PDPage(size.rectangle(landscape))
            document.addPage(blank)
            val order = (0 until document.numberOfPages - 1).toMutableList()
            val newIndex = document.numberOfPages - 1
            val insertAt = (afterIndex + 1).coerceIn(0, order.size)
            order.add(insertAt, newIndex)
            reorder(document, order)
        }
    }

    /** Copies [pages] into a brand new document and returns the file. */
    suspend fun extract(
        context: Context,
        file: File,
        pages: Collection<Int>,
        password: String? = null
    ): File = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) throw PdfOperationException("Select at least one page.")
        PdfIo.load(file, password).use { source ->
            PdfIo.blank().use { target ->
                pages.sorted().forEach { index ->
                    // importPage already appends the copy to the target document.
                    source.pageOrNull(index)?.let { page -> target.importPage(page) }
                }
                if (target.numberOfPages == 0) throw PdfOperationException("Nothing to extract.")
                PdfIo.saveNew(context, target)
            }
        }
    }

    /** Inserts every page of [other] into [file] after [afterIndex]. */
    suspend fun insertDocument(context: Context, file: File, other: File, afterIndex: Int) =
        withContext(Dispatchers.IO) {
            val originalCount = PdfIo.read(file) { it.numberOfPages }
            // The source must stay open until the merged result is written out.
            PdfIo.load(other).use { source ->
                PdfIo.edit(context, file) { document ->
                    val merger = PDFMergerUtility()
                    merger.appendDocument(document, source)
                    val addedCount = document.numberOfPages - originalCount
                    if (addedCount > 0) {
                        val order = (0 until originalCount).toMutableList()
                        val inserted = (originalCount until document.numberOfPages).toList()
                        val insertAt = (afterIndex + 1).coerceIn(0, order.size)
                        order.addAll(insertAt, inserted)
                        reorder(document, order)
                    }
                }
            }
        }

    /** Concatenates [files] in order into a new document. */
    suspend fun merge(context: Context, files: List<File>): File = withContext(Dispatchers.IO) {
        if (files.size < 2) throw PdfOperationException("Pick at least two PDFs to merge.")
        val target = Workspace.newWorkFile(context)
        val merger = PDFMergerUtility()
        merger.destinationFileName = target.absolutePath
        files.forEach { source -> merger.addSource(source) }
        try {
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
        } catch (e: Exception) {
            target.delete()
            throw PdfOperationException("These files could not be merged: ${e.message}", e)
        }
        target
    }

    /** Splits into chunks of [pagesPerFile] pages; returns the parts in order. */
    suspend fun splitEvery(context: Context, file: File, pagesPerFile: Int): List<File> =
        withContext(Dispatchers.IO) {
            val size = pagesPerFile.coerceAtLeast(1)
            val total = PdfIo.read(file) { it.numberOfPages }
            val chunks = (0 until total).chunked(size)
            chunks.map { chunk -> extract(context, file, chunk) }
        }

    /** Splits so that [afterPage] (0-based) ends the first document. */
    suspend fun splitAt(context: Context, file: File, afterPage: Int): List<File> =
        withContext(Dispatchers.IO) {
            val total = PdfIo.read(file) { it.numberOfPages }
            val cut = (afterPage + 1).coerceIn(1, total - 1)
            listOf(
                extract(context, file, (0 until cut).toList()),
                extract(context, file, (cut until total).toList())
            )
        }

    /**
     * Crops [pages] to the given fractions of the visible page
     * (left/top/right/bottom, 0..1 from the top-left corner).
     */
    suspend fun crop(
        context: Context,
        file: File,
        pages: Collection<Int>,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) = withContext(Dispatchers.IO) {
        PdfIo.edit(context, file) { document ->
            pages.forEach { index ->
                val page = document.pageOrNull(index) ?: return@forEach
                val displayWidth = PdfCoords.displayWidth(page)
                val displayHeight = PdfCoords.displayHeight(page)
                val a = PdfCoords.displayToUser(page, left * displayWidth, top * displayHeight)
                val b = PdfCoords.displayToUser(page, right * displayWidth, bottom * displayHeight)
                val box = PDRectangle(
                    minOf(a[0], b[0]),
                    minOf(a[1], b[1]),
                    Math.abs(b[0] - a[0]),
                    Math.abs(b[1] - a[1])
                )
                if (box.width > 1f && box.height > 1f) page.cropBox = box
            }
        }
    }

    /** Restores the crop box to the media box. */
    suspend fun resetCrop(context: Context, file: File, pages: Collection<Int>) =
        withContext(Dispatchers.IO) {
            PdfIo.edit(context, file) { document ->
                pages.forEach { index ->
                    document.pageOrNull(index)?.let { page -> page.cropBox = page.mediaBox }
                }
            }
        }

    private fun reorder(document: PDDocument, order: List<Int>) {
        val pages = order.mapNotNull { document.pageOrNull(it) }
        if (pages.isEmpty()) throw PdfOperationException("A PDF needs at least one page.")
        val tree = document.pages
        val existing = (0 until document.numberOfPages).map { tree.get(it) }
        existing.forEach { page -> tree.remove(page) }
        pages.forEach { page -> tree.add(page) }
    }

    private fun normalizeRotation(value: Int): Int {
        val rotation = value % 360
        return if (rotation < 0) rotation + 360 else rotation
    }
}

internal fun PDDocument.pageOrNull(index: Int): PDPage? =
    if (index in 0 until numberOfPages) getPage(index) else null
