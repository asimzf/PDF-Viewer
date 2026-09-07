package com.asimzf.asimpdf.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.asimzf.asimpdf.data.AppSettings
import com.asimzf.asimpdf.data.RecentFiles
import com.asimzf.asimpdf.model.PdfAnnotation
import com.asimzf.asimpdf.pdf.PageBitmapCache
import com.asimzf.asimpdf.pdf.PageRenderer
import com.asimzf.asimpdf.pdf.PageSize
import com.asimzf.asimpdf.pdf.PdfDocumentTools
import com.asimzf.asimpdf.pdf.PdfIo
import com.asimzf.asimpdf.pdf.PdfOperationException
import com.asimzf.asimpdf.pdf.PdfPasswordRequiredException
import com.asimzf.asimpdf.pdf.PdfStamp
import com.asimzf.asimpdf.pdf.PdfText
import com.asimzf.asimpdf.util.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Holds the open document and drives every edit.
 *
 * The document the user picked is copied into a private working file; edits are
 * applied there and only written back when the user saves, which is what makes
 * "undo" and "discard" safe.
 */
class PdfViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application get() = getApplication()

    /** The application context, for the tool screens that need one. */
    val appContext: Application get() = app
    private val recents = RecentFiles(app)
    private val settings = AppSettings(app)

    private val _state = MutableStateFlow(
        AppState(
            nightMode = settings.nightMode,
            continuousScroll = settings.continuousScroll,
            keepScreenOn = settings.keepScreenOn
        )
    )
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var renderer: PageRenderer? = null
    private var cache = PageBitmapCache()
    private val undoStack = ArrayDeque<File>()
    private val redoStack = ArrayDeque<File>()
    private var searchJob: Job? = null

    init {
        PdfIo.attach(app)
        refreshRecents()
    }

    // ---------------------------------------------------------------- opening

    fun openDocument(uri: Uri, password: String? = null) {
        viewModelScope.launch {
            setBusy(JobProgress("Opening document"))
            try {
                val name = io { Workspace.displayName(app, uri) }
                val work = io { Workspace.importToWork(app, uri) }
                val encrypted = io { decryptIfNeeded(work, password) }
                if (encrypted == DecryptResult.NEEDS_PASSWORD) {
                    work.delete()
                    _state.value = _state.value.copy(
                        passwordRequest = uri,
                        passwordError = if (password.isNullOrEmpty()) null
                        else "That password did not work.",
                        busy = null
                    )
                    return@launch
                }
                closeRenderer()
                val pageRenderer = io { PageRenderer(work) }
                renderer = pageRenderer
                cache = PageBitmapCache()
                clearHistory()
                recents.persistPermission(uri)
                recents.remember(uri, name, pageRenderer.pageCount, Workspace.sizeOf(app, uri))
                val document = OpenDocument(
                    workFile = work,
                    sourceUri = uri,
                    name = name,
                    pageCount = pageRenderer.pageCount,
                    wasEncrypted = encrypted == DecryptResult.DECRYPTED
                )
                _state.value = _state.value.copy(
                    document = document,
                    screen = Screen.Viewer,
                    currentPage = settings.lastPage(uri.toString())
                        .coerceIn(0, (pageRenderer.pageCount - 1).coerceAtLeast(0)),
                    passwordRequest = null,
                    passwordError = null,
                    busy = null,
                    search = SearchState(),
                    annotation = AnnotationState(),
                    selectedPages = emptySet(),
                    outline = emptyList()
                )
                refreshRecents()
                loadOutline()
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** Takes over a file that was produced by a tool (merge, images to PDF …). */
    fun adoptGeneratedFile(file: File, name: String) {
        viewModelScope.launch {
            try {
                closeRenderer()
                val pageRenderer = io { PageRenderer(file) }
                renderer = pageRenderer
                cache = PageBitmapCache()
                clearHistory()
                _state.value = _state.value.copy(
                    document = OpenDocument(
                        workFile = file,
                        sourceUri = null,
                        name = name,
                        pageCount = pageRenderer.pageCount,
                        unsavedChanges = true
                    ),
                    screen = Screen.Viewer,
                    currentPage = 0,
                    busy = null,
                    search = SearchState(),
                    annotation = AnnotationState(),
                    selectedPages = emptySet(),
                    outline = emptyList()
                )
                loadOutline()
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    private enum class DecryptResult { PLAIN, DECRYPTED, NEEDS_PASSWORD }

    /** The platform renderer cannot open encrypted files, so unlock ours first. */
    private fun decryptIfNeeded(work: File, password: String?): DecryptResult = try {
        val encrypted = PdfIo.read(work, password) { it.isEncrypted }
        if (encrypted) {
            PdfIo.edit(app, work, password) { }
            DecryptResult.DECRYPTED
        } else {
            DecryptResult.PLAIN
        }
    } catch (e: PdfPasswordRequiredException) {
        DecryptResult.NEEDS_PASSWORD
    }

    fun dismissPasswordRequest() {
        _state.value = _state.value.copy(passwordRequest = null, passwordError = null)
    }

    fun closeDocument() {
        val document = _state.value.document
        document?.let { settings.rememberPage(it.key(), _state.value.currentPage) }
        closeRenderer()
        clearHistory()
        _state.value = _state.value.copy(
            document = null,
            screen = Screen.Home,
            currentPage = 0,
            search = SearchState(),
            annotation = AnnotationState(),
            selectedPages = emptySet(),
            outline = emptyList()
        )
        refreshRecents()
    }

    // ---------------------------------------------------------------- saving

    /** True when the document already knows where it belongs on disk. */
    fun canSaveInPlace(): Boolean = _state.value.document?.sourceUri != null

    fun save(onNeedsLocation: () -> Unit) {
        val document = _state.value.document ?: return
        val target = document.sourceUri
        if (target == null) {
            onNeedsLocation()
            return
        }
        viewModelScope.launch {
            setBusy(JobProgress("Saving"))
            try {
                io { Workspace.exportToUri(app, document.workFile, target) }
                _state.value = _state.value.copy(
                    document = document.copy(unsavedChanges = false),
                    busy = null,
                    message = "Saved to ${document.name}"
                )
                recents.remember(target, document.name, document.pageCount, document.workFile.length())
                refreshRecents()
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun saveAs(uri: Uri) {
        val document = _state.value.document ?: return
        viewModelScope.launch {
            setBusy(JobProgress("Saving"))
            try {
                io { Workspace.exportToUri(app, document.workFile, uri) }
                val name = Workspace.displayName(app, uri)
                recents.persistPermission(uri)
                recents.remember(uri, name, document.pageCount, document.workFile.length())
                _state.value = _state.value.copy(
                    document = document.copy(sourceUri = uri, name = name, unsavedChanges = false),
                    busy = null,
                    message = "Saved as $name"
                )
                refreshRecents()
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** Copies a generated file (an export, a split part) to a place the user picked. */
    fun exportFileTo(source: File, uri: Uri, successMessage: String) {
        viewModelScope.launch {
            try {
                io { Workspace.exportToUri(app, source, uri) }
                _state.value = _state.value.copy(message = successMessage)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    // ---------------------------------------------------------------- editing

    /**
     * Runs an edit against the working copy with an undo snapshot taken first.
     * [block] is expected to leave the file valid or throw.
     */
    fun edit(label: String, block: suspend (File) -> Unit) {
        val document = _state.value.document ?: return
        viewModelScope.launch {
            setBusy(JobProgress(label))
            val snapshotFile = io { snapshot(document.workFile) }
            try {
                block(document.workFile)
                pushUndo(snapshotFile)
                redoStack.forEach { it.delete() }
                redoStack.clear()
                reloadRenderer(markDirty = true)
            } catch (e: Exception) {
                snapshotFile?.delete()
                fail(e)
            }
        }
    }

    /** Reopens the renderer after the file on disk changed underneath it. */
    private suspend fun reloadRenderer(markDirty: Boolean) {
        val document = _state.value.document ?: return
        closeRenderer()
        try {
            val pageRenderer = io { PageRenderer(document.workFile) }
            renderer = pageRenderer
            cache = PageBitmapCache()
            _state.value = _state.value.copy(
                document = document.copy(
                    pageCount = pageRenderer.pageCount,
                    unsavedChanges = document.unsavedChanges || markDirty,
                    version = document.version + 1
                ),
                currentPage = _state.value.currentPage
                    .coerceIn(0, (pageRenderer.pageCount - 1).coerceAtLeast(0)),
                selectedPages = _state.value.selectedPages.filter {
                    it < pageRenderer.pageCount
                }.toSet(),
                busy = null,
                undoDepth = undoStack.size,
                redoDepth = redoStack.size
            )
            loadOutline()
        } catch (e: Exception) {
            fail(e)
        }
    }

    fun undo() = stepHistory(fromUndo = true)

    fun redo() = stepHistory(fromUndo = false)

    private fun stepHistory(fromUndo: Boolean) {
        val document = _state.value.document ?: return
        val source = if (fromUndo) undoStack else redoStack
        val destination = if (fromUndo) redoStack else undoStack
        val restore = source.removeLastOrNull() ?: return
        viewModelScope.launch {
            setBusy(JobProgress(if (fromUndo) "Undoing" else "Redoing"))
            try {
                val current = io { snapshot(document.workFile) }
                io {
                    restore.copyTo(document.workFile, overwrite = true)
                    restore.delete()
                }
                if (current != null) destination.addLast(current)
                reloadRenderer(markDirty = true)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    private fun snapshot(file: File): File? = runCatching {
        val target = Workspace.newWorkFile(app, ".snapshot")
        file.copyTo(target, overwrite = true)
    }.getOrNull()

    private fun pushUndo(snapshot: File?) {
        if (snapshot == null) return
        undoStack.addLast(snapshot)
        while (undoStack.size > MAX_HISTORY) {
            undoStack.removeFirstOrNull()?.delete()
        }
    }

    private fun clearHistory() {
        undoStack.forEach { it.delete() }
        redoStack.forEach { it.delete() }
        undoStack.clear()
        redoStack.clear()
        _state.value = _state.value.copy(undoDepth = 0, redoDepth = 0)
    }

    // ------------------------------------------------------------- rendering

    fun pageSize(index: Int): PageSize = renderer?.sizeOf(index) ?: PageSize(612f, 792f)

    suspend fun renderPage(index: Int, widthPx: Int): Bitmap? {
        val bucket = bucketed(widthPx)
        cache.get(index, bucket)?.let { return it }
        val bitmap = renderer?.render(index, bucket) ?: return null
        cache.put(index, bucket, bitmap)
        return bitmap
    }

    private fun bucketed(widthPx: Int): Int {
        val step = 160
        return ((widthPx + step - 1) / step * step).coerceIn(step, 4096)
    }

    private fun closeRenderer() {
        renderer?.close()
        renderer = null
        cache.clear()
    }

    // -------------------------------------------------------------- viewing

    fun goToPage(index: Int) {
        val count = _state.value.document?.pageCount ?: return
        _state.value = _state.value.copy(currentPage = index.coerceIn(0, (count - 1).coerceAtLeast(0)))
    }

    fun setScreen(screen: Screen) {
        _state.value = _state.value.copy(screen = screen)
    }

    fun toggleNightMode() {
        val value = !_state.value.nightMode
        settings.nightMode = value
        _state.value = _state.value.copy(nightMode = value)
    }

    fun toggleContinuousScroll() {
        val value = !_state.value.continuousScroll
        settings.continuousScroll = value
        _state.value = _state.value.copy(continuousScroll = value)
    }

    fun toggleKeepScreenOn() {
        val value = !_state.value.keepScreenOn
        settings.keepScreenOn = value
        _state.value = _state.value.copy(keepScreenOn = value)
    }

    private fun loadOutline() {
        val document = _state.value.document ?: return
        viewModelScope.launch {
            val entries = runCatching { PdfDocumentTools.outline(document.workFile) }
                .getOrDefault(emptyList())
            _state.value = _state.value.copy(outline = entries)
        }
    }

    // -------------------------------------------------------------- searching

    fun search(query: String) {
        val document = _state.value.document ?: return
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.value = _state.value.copy(search = SearchState())
            return
        }
        _state.value = _state.value.copy(
            search = SearchState(query = query, running = true)
        )
        searchJob = viewModelScope.launch {
            val collected = mutableListOf<com.asimzf.asimpdf.pdf.SearchHit>()
            runCatching {
                PdfText.search(document.workFile, query).collect { hit ->
                    collected.add(hit)
                    _state.value = _state.value.copy(
                        search = _state.value.search.copy(
                            results = collected.toList(),
                            activeIndex = if (_state.value.search.activeIndex < 0) 0
                            else _state.value.search.activeIndex
                        )
                    )
                    if (collected.size == 1) goToPage(hit.page)
                }
            }
            _state.value = _state.value.copy(
                search = _state.value.search.copy(running = false)
            )
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _state.value = _state.value.copy(search = SearchState())
    }

    fun stepSearch(forward: Boolean) {
        val search = _state.value.search
        if (search.results.isEmpty()) return
        val next = if (forward) {
            (search.activeIndex + 1) % search.results.size
        } else {
            (search.activeIndex - 1 + search.results.size) % search.results.size
        }
        _state.value = _state.value.copy(search = search.copy(activeIndex = next))
        goToPage(search.results[next].page)
    }

    // ------------------------------------------------------------ annotating

    fun setAnnotationState(transform: (AnnotationState) -> AnnotationState) {
        _state.value = _state.value.copy(annotation = transform(_state.value.annotation))
    }

    fun addAnnotation(annotation: PdfAnnotation) = setAnnotationState { current ->
        current.copy(pending = current.pending + annotation, redoStack = emptyList())
    }

    fun removeAnnotation(id: Long) = setAnnotationState { current ->
        current.copy(pending = current.pending.filterNot { it.id == id })
    }

    fun undoAnnotation() = setAnnotationState { current ->
        val last = current.pending.lastOrNull() ?: return@setAnnotationState current
        current.copy(
            pending = current.pending.dropLast(1),
            redoStack = current.redoStack + last
        )
    }

    fun redoAnnotation() = setAnnotationState { current ->
        val last = current.redoStack.lastOrNull() ?: return@setAnnotationState current
        current.copy(
            pending = current.pending + last,
            redoStack = current.redoStack.dropLast(1)
        )
    }

    fun discardAnnotations() = setAnnotationState { AnnotationState(tool = it.tool) }

    fun applyAnnotations(onDone: () -> Unit = {}) {
        val pending = _state.value.annotation.pending
        if (pending.isEmpty()) {
            onDone()
            return
        }
        edit("Applying markup") { file ->
            PdfStamp.applyAnnotations(app, file, pending)
            setAnnotationState { AnnotationState(tool = it.tool) }
            onDone()
        }
    }

    // ------------------------------------------------------------- selection

    fun togglePageSelection(index: Int) {
        val current = _state.value.selectedPages
        _state.value = _state.value.copy(
            selectedPages = if (index in current) current - index else current + index
        )
    }

    fun setSelection(pages: Set<Int>) {
        _state.value = _state.value.copy(selectedPages = pages)
    }

    fun selectAllPages() {
        val count = _state.value.document?.pageCount ?: 0
        _state.value = _state.value.copy(selectedPages = (0 until count).toSet())
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedPages = emptySet())
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    /**
     * Runs a one-off job (a tool, an export) with the busy overlay showing and
     * errors reported the same way everywhere.
     */
    fun run(label: String, onSuccess: (() -> Unit)? = null, block: suspend (Application) -> Unit) {
        viewModelScope.launch {
            setBusy(JobProgress(label))
            try {
                block(app)
                setBusy(null)
                onSuccess?.invoke()
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** Runs a job that produces a new document and opens the result. */
    fun runProducingDocument(label: String, name: String, block: suspend (Application) -> File) {
        viewModelScope.launch {
            setBusy(JobProgress(label))
            try {
                val produced = block(app)
                adoptGeneratedFile(produced, name)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** Imports a document the user picked into the working directory. */
    suspend fun importToWork(uri: Uri): File = io { Workspace.importToWork(app, uri) }

    // --------------------------------------------------------------- plumbing

    fun setBusy(progress: JobProgress?) {
        _state.value = _state.value.copy(busy = progress)
    }

    fun updateProgress(current: Int, total: Int) {
        val busy = _state.value.busy ?: return
        _state.value = _state.value.copy(busy = busy.copy(current = current, total = total))
    }

    fun showMessage(message: String) {
        _state.value = _state.value.copy(message = message)
    }

    fun showError(message: String) {
        _state.value = _state.value.copy(error = message, busy = null)
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun consumeError() {
        _state.value = _state.value.copy(error = null)
    }

    fun forgetRecent(uri: String) {
        recents.forget(uri)
        refreshRecents()
    }

    fun clearRecents() {
        recents.clear()
        refreshRecents()
    }

    private fun refreshRecents() {
        _state.value = _state.value.copy(recents = recents.all())
    }

    private fun fail(error: Throwable) {
        val message = when (error) {
            is PdfPasswordRequiredException -> error.message ?: "This PDF is protected."
            is PdfOperationException -> error.message ?: "That did not work."
            is OutOfMemoryError -> "This document is too large for the available memory."
            else -> error.message ?: "Something went wrong."
        }
        _state.value = _state.value.copy(busy = null, error = message)
    }

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
        closeRenderer()
        clearHistory()
    }

    private companion object {
        const val MAX_HISTORY = 8
    }
}

/** Stable key for remembering the last page of a document. */
fun OpenDocument.key(): String = sourceUri?.toString() ?: workFile.name
