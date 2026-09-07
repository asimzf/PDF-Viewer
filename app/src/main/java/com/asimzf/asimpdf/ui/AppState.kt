package com.asimzf.asimpdf.ui

import android.net.Uri
import com.asimzf.asimpdf.model.AnnotationTool
import com.asimzf.asimpdf.model.PdfAnnotation
import com.asimzf.asimpdf.model.ShapeKind
import com.asimzf.asimpdf.pdf.OutlineEntry
import com.asimzf.asimpdf.pdf.SearchHit
import java.io.File

/** Which top level screen is showing. */
sealed interface Screen {
    data object Home : Screen
    data object Viewer : Screen
    data object Organize : Screen
    data object Tools : Screen
    data class Tool(val tool: PdfToolId) : Screen
}

/** Every tool in the tool box, with the copy the UI shows. */
enum class PdfToolId(val title: String, val summary: String, val needsDocument: Boolean = true) {
    ORGANIZE("Organise pages", "Reorder, rotate, delete, duplicate or extract pages"),
    INSERT("Insert pages", "Add a blank page or another PDF into this one"),
    MERGE("Merge PDFs", "Join this document with others, in the order you choose"),
    SPLIT("Split PDF", "Cut the document into several files"),
    ROTATE("Rotate pages", "Turn a range of pages 90, 180 or 270 degrees"),
    CROP("Crop pages", "Trim the margins of a page range"),
    WATERMARK("Watermark", "Stamp text across the pages, tiled or centred"),
    PAGE_NUMBERS("Page numbers", "Add numbering or a header and footer"),
    COMPRESS("Compress", "Shrink the file by re-encoding its images"),
    SECURITY("Password & permissions", "Encrypt the document or take the password off"),
    METADATA("Document properties", "Edit the title, author, subject and keywords"),
    FORMS("Fill forms", "Fill in and flatten interactive form fields"),
    EXPORT_IMAGES("Export as images", "Save pages as PNG or JPEG files"),
    EXPORT_TEXT("Extract text", "Pull the text out into a .txt file"),
    FLATTEN("Flatten pages", "Turn pages into pictures so nothing can be edited or copied"),
    IMAGES_TO_PDF("Images to PDF", "Build a new PDF out of photos or scans", needsDocument = false),
    INFO("Document info", "Page size, version, security and dates")
}

/** A long running job, so the UI can show what is happening. */
data class JobProgress(val label: String, val current: Int = 0, val total: Int = 0) {
    val fraction: Float?
        get() = if (total > 0) (current.toFloat() / total).coerceIn(0f, 1f) else null
}

/** The document currently open, always backed by a private working copy. */
data class OpenDocument(
    val workFile: File,
    val sourceUri: Uri?,
    val name: String,
    val pageCount: Int,
    val unsavedChanges: Boolean = false,
    val wasEncrypted: Boolean = false,
    val version: Int = 0
)

/** Drawing tool settings for the annotation layer. */
data class AnnotationState(
    val tool: AnnotationTool = AnnotationTool.NONE,
    val color: Int = 0xFFE53935.toInt(),
    val strokeWidth: Float = 3f,
    val fontSize: Float = 14f,
    val opacity: Float = 1f,
    val shape: ShapeKind = ShapeKind.RECTANGLE,
    val filled: Boolean = false,
    val pending: List<PdfAnnotation> = emptyList(),
    val redoStack: List<PdfAnnotation> = emptyList()
) {
    val active: Boolean get() = tool != AnnotationTool.NONE
    val hasPending: Boolean get() = pending.isNotEmpty()
}

data class SearchState(
    val query: String = "",
    val running: Boolean = false,
    val results: List<SearchHit> = emptyList(),
    val activeIndex: Int = -1
) {
    val active: SearchHit? get() = results.getOrNull(activeIndex)
}

data class AppState(
    val screen: Screen = Screen.Home,
    val document: OpenDocument? = null,
    val recents: List<com.asimzf.asimpdf.data.RecentDocument> = emptyList(),
    val currentPage: Int = 0,
    val nightMode: Boolean = false,
    val continuousScroll: Boolean = true,
    val keepScreenOn: Boolean = false,
    val busy: JobProgress? = null,
    val message: String? = null,
    val error: String? = null,
    val passwordRequest: Uri? = null,
    val passwordError: String? = null,
    val annotation: AnnotationState = AnnotationState(),
    val search: SearchState = SearchState(),
    val outline: List<OutlineEntry> = emptyList(),
    val selectedPages: Set<Int> = emptySet(),
    val undoDepth: Int = 0,
    val redoDepth: Int = 0
)
