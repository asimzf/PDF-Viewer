package com.asimzf.asimpdf.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.asimzf.asimpdf.model.AnnotationTool
import com.asimzf.asimpdf.model.NPoint
import com.asimzf.asimpdf.model.NRect
import com.asimzf.asimpdf.model.PdfAnnotation
import com.asimzf.asimpdf.pdf.SearchHit
import com.asimzf.asimpdf.ui.AppState
import com.asimzf.asimpdf.ui.PdfViewModel
import com.asimzf.asimpdf.ui.annotate.AnnotationLayer
import com.asimzf.asimpdf.ui.annotate.AnnotationToolbar
import com.asimzf.asimpdf.ui.annotate.SignatureDialog
import com.asimzf.asimpdf.ui.annotate.TextEntryDialog
import com.asimzf.asimpdf.ui.common.AppIcons
import com.asimzf.asimpdf.ui.common.rememberSingleImagePicker
import kotlin.math.abs

/** The reading and markup surface: the screen people spend their time on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    viewModel: PdfViewModel,
    state: AppState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onOrganize: () -> Unit,
    onTools: () -> Unit
) {
    val document = state.document ?: return
    var menuOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var outlineOpen by remember { mutableStateOf(false) }
    var signatureOpen by remember { mutableStateOf(false) }
    var textPrompt by remember { mutableStateOf<Pair<Int, NPoint>?>(null) }
    var notePrompt by remember { mutableStateOf<Pair<Int, NPoint>?>(null) }
    var placement by remember { mutableStateOf<Pair<Int, NRect>?>(null) }
    var pendingImagePath by remember { mutableStateOf<String?>(null) }

    val pickImage = rememberSingleImagePicker { uri ->
        viewModel.run("Preparing image") {
            pendingImagePath = viewModel.importToWork(uri).absolutePath
            viewModel.showMessage("Now tap the page where the image should go.")
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(AppIcons.Back, contentDescription = "Back")
                        }
                    },
                    title = {
                        Column {
                            Text(
                                document.name,
                                maxLines = 1,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                buildString {
                                    append("Page ${state.currentPage + 1} of ${document.pageCount}")
                                    if (document.unsavedChanges) append(" · unsaved changes")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchOpen = !searchOpen }) {
                            Icon(AppIcons.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { viewModel.toggleNightMode() }) {
                            Icon(
                                if (state.nightMode) AppIcons.LightMode else AppIcons.DarkMode,
                                contentDescription = "Night mode"
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.setAnnotationState { current ->
                                    current.copy(
                                        tool = if (current.active) {
                                            AnnotationTool.NONE
                                        } else {
                                            AnnotationTool.INK
                                        }
                                    )
                                }
                            }
                        ) {
                            Icon(
                                if (state.annotation.active) AppIcons.Close else AppIcons.Edit,
                                contentDescription = "Markup"
                            )
                        }
                        IconButton(onClick = onSave) {
                            Icon(AppIcons.Save, contentDescription = "Save")
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(AppIcons.More, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Save as…") },
                                onClick = { menuOpen = false; onSaveAs() }
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                onClick = { menuOpen = false; onShare() }
                            )
                            DropdownMenuItem(
                                text = { Text("Print") },
                                onClick = { menuOpen = false; onPrint() }
                            )
                            DropdownMenuItem(
                                text = { Text("Organise pages") },
                                onClick = { menuOpen = false; onOrganize() }
                            )
                            DropdownMenuItem(
                                text = { Text("All tools") },
                                onClick = { menuOpen = false; onTools() }
                            )
                            DropdownMenuItem(
                                text = { Text("Contents") },
                                enabled = state.outline.isNotEmpty(),
                                onClick = { menuOpen = false; outlineOpen = true }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (state.continuousScroll) {
                                            "Single page view"
                                        } else {
                                            "Continuous scrolling"
                                        }
                                    )
                                },
                                onClick = { menuOpen = false; viewModel.toggleContinuousScroll() }
                            )
                            DropdownMenuItem(
                                text = { Text("Undo last edit") },
                                enabled = state.undoDepth > 0,
                                onClick = { menuOpen = false; viewModel.undo() }
                            )
                            DropdownMenuItem(
                                text = { Text("Redo") },
                                enabled = state.redoDepth > 0,
                                onClick = { menuOpen = false; viewModel.redo() }
                            )
                        }
                    }
                )
                if (searchOpen) {
                    SearchBar(
                        state = state,
                        onSearch = { viewModel.search(it) },
                        onStep = { viewModel.stepSearch(it) },
                        onClose = {
                            searchOpen = false
                            viewModel.clearSearch()
                        }
                    )
                }
            }
        },
        bottomBar = {
            if (state.annotation.active) {
                AnnotationToolbar(
                    state = state.annotation,
                    onToolSelected = { tool ->
                        when (tool) {
                            AnnotationTool.SIGNATURE -> signatureOpen = true
                            AnnotationTool.IMAGE -> pickImage()
                            else -> Unit
                        }
                        viewModel.setAnnotationState { it.copy(tool = tool) }
                    },
                    onStateChange = { transform -> viewModel.setAnnotationState(transform) },
                    onUndo = { viewModel.undoAnnotation() },
                    onRedo = { viewModel.redoAnnotation() },
                    onApply = { viewModel.applyAnnotations() },
                    onDiscard = { viewModel.discardAnnotations() },
                    canUndo = state.annotation.pending.isNotEmpty(),
                    canRedo = state.annotation.redoStack.isNotEmpty()
                )
            } else if (document.pageCount > 1) {
                PageSlider(
                    current = state.currentPage,
                    total = document.pageCount,
                    onChange = { viewModel.goToPage(it) }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            DocumentPages(
                viewModel = viewModel,
                state = state,
                onRequestText = { page, point -> textPrompt = page to point },
                onRequestNote = { page, point -> notePrompt = page to point },
                onRequestPlacement = { page, rect -> placement = page to rect }
            )
        }
    }

    if (outlineOpen) {
        OutlineSheet(
            entries = state.outline,
            onSelect = { page ->
                outlineOpen = false
                viewModel.goToPage(page)
            },
            onDismiss = { outlineOpen = false }
        )
    }

    textPrompt?.let { (page, point) ->
        TextEntryDialog(
            title = "Add text",
            label = "Text",
            onConfirm = { text ->
                viewModel.addAnnotation(
                    PdfAnnotation.TextBox(
                        id = System.nanoTime(),
                        page = page,
                        position = point,
                        text = text,
                        fontSize = state.annotation.fontSize,
                        color = state.annotation.color
                    )
                )
                textPrompt = null
            },
            onDismiss = { textPrompt = null }
        )
    }

    notePrompt?.let { (page, point) ->
        TextEntryDialog(
            title = "Add a note",
            label = "Note",
            onConfirm = { text ->
                viewModel.addAnnotation(
                    PdfAnnotation.Note(
                        id = System.nanoTime(),
                        page = page,
                        position = point,
                        text = text,
                        author = "asimPDF",
                        color = state.annotation.color
                    )
                )
                notePrompt = null
            },
            onDismiss = { notePrompt = null }
        )
    }

    if (signatureOpen) {
        SignatureDialog(
            onDismiss = { signatureOpen = false },
            onSigned = { path ->
                pendingImagePath = path
                signatureOpen = false
                viewModel.showMessage("Now tap the page where the signature should go.")
            }
        )
    }

    // A stamp needs both a picture and a spot on the page; place it once we have both.
    val stampPath = pendingImagePath
    val target = placement
    if (stampPath != null && target != null) {
        LaunchedEffect(stampPath, target) {
            viewModel.addAnnotation(
                PdfAnnotation.Stamp(
                    id = System.nanoTime(),
                    page = target.first,
                    rect = target.second,
                    imagePath = stampPath
                )
            )
            placement = null
        }
    } else if (target != null && stampPath == null) {
        LaunchedEffect(target) {
            viewModel.showMessage("Choose a signature or image first.")
            placement = null
        }
    }
}

@Composable
private fun DocumentPages(
    viewModel: PdfViewModel,
    state: AppState,
    onRequestText: (Int, NPoint) -> Unit,
    onRequestNote: (Int, NPoint) -> Unit,
    onRequestPlacement: (Int, NRect) -> Unit
) {
    val document = state.document ?: return
    var scale by remember(document.version) { mutableFloatStateOf(1f) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val zoomModifier = Modifier.pointerInput(Unit) {
            // Only pinches change the zoom; single finger drags stay with the list.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.size >= 2) {
                        val zoomChange = event.calculateZoom()
                        if (zoomChange != 1f) {
                            scale = (scale * zoomChange).coerceIn(1f, MAX_ZOOM)
                            event.changes.forEach { it.consume() }
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        }.pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = { scale = if (scale > 1.05f) 1f else 2.5f }
            )
        }

        val pageWidthDp = maxWidth * scale
        val pageWidthPx = (viewportWidthPx * scale).toInt()
        // Horizontal panning only matters once the page is wider than the screen.
        val horizontalPan = if (scale > 1f) {
            Modifier.horizontalScroll(rememberScrollState())
        } else {
            Modifier
        }

        if (state.continuousScroll) {
            val listState = rememberLazyListState()
            LaunchedEffect(state.currentPage) {
                if (abs(listState.firstVisibleItemIndex - state.currentPage) > 0) {
                    listState.scrollToItem(state.currentPage)
                }
            }
            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemIndex }
                    .collect { index -> viewModel.goToPage(index) }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(zoomModifier)
                    .then(horizontalPan),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(document.pageCount) { index ->
                    PageCard(
                        viewModel = viewModel,
                        state = state,
                        index = index,
                        widthDp = pageWidthDp,
                        widthPx = pageWidthPx,
                        onRequestText = onRequestText,
                        onRequestNote = onRequestNote,
                        onRequestPlacement = onRequestPlacement
                    )
                }
            }
        } else {
            val pagerState = rememberPagerState(
                initialPage = state.currentPage,
                pageCount = { document.pageCount }
            )
            LaunchedEffect(state.currentPage) {
                if (pagerState.currentPage != state.currentPage) {
                    pagerState.scrollToPage(state.currentPage)
                }
            }
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }
                    .collect { page -> viewModel.goToPage(page) }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(zoomModifier),
                pageSpacing = 12.dp
            ) { index ->
                val pageScroll = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (scale > 1f) Modifier.horizontalScroll(pageScroll) else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    PageCard(
                        viewModel = viewModel,
                        state = state,
                        index = index,
                        widthDp = pageWidthDp,
                        widthPx = pageWidthPx,
                        onRequestText = onRequestText,
                        onRequestNote = onRequestNote,
                        onRequestPlacement = onRequestPlacement
                    )
                }
            }
        }
    }
}

@Composable
private fun PageCard(
    viewModel: PdfViewModel,
    state: AppState,
    index: Int,
    widthDp: Dp,
    widthPx: Int,
    onRequestText: (Int, NPoint) -> Unit,
    onRequestNote: (Int, NPoint) -> Unit,
    onRequestPlacement: (Int, NRect) -> Unit
) {
    val document = state.document ?: return
    val size = viewModel.pageSize(index)
    val heightDp = widthDp * (if (size.aspect > 0f) 1f / size.aspect else 1.3f)
    val hits = state.search.results.filter { it.page == index }

    Surface(
        modifier = Modifier
            .width(widthDp)
            .height(heightDp),
        shape = RoundedCornerShape(2.dp),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        PdfPageImage(
            viewModel = viewModel,
            pageIndex = index,
            documentVersion = document.version,
            widthPx = widthPx,
            nightMode = state.nightMode,
            modifier = Modifier.fillMaxSize()
        ) {
            if (hits.isNotEmpty()) {
                SearchHighlights(hits = hits, active = state.search.active)
            }
            AnnotationLayer(
                pageIndex = index,
                annotations = state.annotation.pending,
                state = state.annotation,
                pageWidthPoints = size.width,
                onAdd = { viewModel.addAnnotation(it) },
                onErase = { viewModel.removeAnnotation(it) },
                onRequestText = { point -> onRequestText(index, point) },
                onRequestNote = { point -> onRequestNote(index, point) },
                onRequestPlacement = { rect -> onRequestPlacement(index, rect) }
            )
        }
    }
}

@Composable
private fun SearchHighlights(hits: List<SearchHit>, active: SearchHit?) {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        hits.forEach { hit ->
            val isActive = hit === active
            hit.rects.forEach { rect ->
                drawRect(
                    color = if (isActive) {
                        Color(0xFFFF9800).copy(alpha = 0.55f)
                    } else {
                        Color(0xFFFFEB3B).copy(alpha = 0.4f)
                    },
                    topLeft = Offset(rect.left * size.width, rect.top * size.height),
                    size = Size(rect.width * size.width, rect.height * size.height)
                )
            }
        }
    }
}

@Composable
private fun PageSlider(current: Int, total: Int, onChange: (Int) -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "${current + 1}/$total",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = current.toFloat(),
                onValueChange = { onChange(it.toInt()) },
                valueRange = 0f..(total - 1).toFloat(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SearchBar(
    state: AppState,
    onSearch: (String) -> Unit,
    onStep: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf(state.search.query) }
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onSearch(it)
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Find in document") },
                supportingText = {
                    Text(
                        when {
                            state.search.running -> "Searching…"
                            state.search.query.isBlank() -> "Type to search"
                            state.search.results.isEmpty() -> "No matches"
                            else -> "${state.search.activeIndex + 1} of " +
                                "${state.search.results.size} matches"
                        }
                    )
                }
            )
            IconButton(onClick = { onStep(false) }, enabled = state.search.results.isNotEmpty()) {
                Icon(AppIcons.Up, contentDescription = "Previous match")
            }
            IconButton(onClick = { onStep(true) }, enabled = state.search.results.isNotEmpty()) {
                Icon(AppIcons.Down, contentDescription = "Next match")
            }
            IconButton(onClick = onClose) {
                Icon(AppIcons.Close, contentDescription = "Close search")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutlineSheet(
    entries: List<com.asimzf.asimpdf.pdf.OutlineEntry>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Text(
                    "Contents",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            items(entries.size) { index ->
                val entry = entries[index]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (entry.depth * 16).dp)
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        entry.title,
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (entry.pageIndex >= 0) {
                                    Modifier.clickable { onSelect(entry.pageIndex) }
                                } else {
                                    Modifier
                                }
                            ),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (entry.pageIndex >= 0) {
                        Text(
                            "${entry.pageIndex + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_ZOOM = 6f
