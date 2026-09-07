package com.asimzf.asimpdf.ui.organize

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asimzf.asimpdf.pdf.PdfPageOps
import com.asimzf.asimpdf.ui.AppState
import com.asimzf.asimpdf.ui.PdfViewModel
import com.asimzf.asimpdf.ui.common.AppIcons
import com.asimzf.asimpdf.ui.common.ConfirmDialog
import com.asimzf.asimpdf.ui.common.rememberDocumentExporter
import com.asimzf.asimpdf.ui.viewer.PdfPageImage
import com.asimzf.asimpdf.util.Workspace

/** Page manager: pick pages, then rotate, move, copy, delete or pull them out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeScreen(
    viewModel: PdfViewModel,
    state: AppState,
    onBack: () -> Unit
) {
    val document = state.document ?: return
    val selection = state.selectedPages
    var confirmDelete by remember { mutableStateOf(false) }
    val exportDocument = rememberDocumentExporter(viewModel, message = "Pages saved")

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text("Organise pages", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (selection.isEmpty()) {
                                "${document.pageCount} pages · tap to select"
                            } else {
                                "${selection.size} selected"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (selection.size == document.pageCount) {
                                viewModel.clearSelection()
                            } else {
                                viewModel.selectAllPages()
                            }
                        }
                    ) {
                        Text(if (selection.size == document.pageCount) "None" else "All")
                    }
                }
            )
        },
        bottomBar = {
            ActionBar(
                enabled = selection.isNotEmpty(),
                onRotateLeft = {
                    viewModel.edit("Rotating") { file ->
                        PdfPageOps.rotate(viewModel.appContext, file, selection, -90)
                    }
                },
                onRotateRight = {
                    viewModel.edit("Rotating") { file ->
                        PdfPageOps.rotate(viewModel.appContext, file, selection, 90)
                    }
                },
                onMoveUp = {
                    val target = (selection.minOrNull() ?: 0) - 1
                    if (target >= 0) {
                        viewModel.edit("Moving pages") { file ->
                            PdfPageOps.move(
                                viewModel.appContext,
                                file,
                                selection.sorted(),
                                target
                            )
                        }
                    }
                },
                onMoveDown = {
                    val target = (selection.maxOrNull() ?: 0) + 2
                    if (target <= document.pageCount) {
                        viewModel.edit("Moving pages") { file ->
                            PdfPageOps.move(
                                viewModel.appContext,
                                file,
                                selection.sorted(),
                                target
                            )
                        }
                    }
                },
                onDuplicate = {
                    viewModel.edit("Duplicating") { file ->
                        PdfPageOps.duplicate(viewModel.appContext, file, selection)
                    }
                },
                onExtract = {
                    val pages = selection.sorted()
                    viewModel.run("Extracting pages") { app ->
                        val extracted = PdfPageOps.extract(app, document.workFile, pages)
                        exportDocument(
                            extracted,
                            Workspace.pdfName(document.name, "_pages")
                        )
                    }
                },
                onDelete = { confirmDelete = true }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items((0 until document.pageCount).toList(), key = { it }) { index ->
                Thumbnail(
                    viewModel = viewModel,
                    state = state,
                    index = index,
                    selected = index in selection,
                    onClick = { viewModel.togglePageSelection(index) }
                )
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete pages?",
            message = "${selection.size} page${if (selection.size == 1) "" else "s"} will be " +
                "removed from the working copy. You can still undo this.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                viewModel.edit("Deleting pages") { file ->
                    PdfPageOps.delete(viewModel.appContext, file, selection)
                }
                viewModel.clearSelection()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun Thumbnail(
    viewModel: PdfViewModel,
    state: AppState,
    index: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val document = state.document ?: return
    val size = viewModel.pageSize(index)
    val density = LocalDensity.current
    val widthPx = with(density) { 120.dp.toPx() }.toInt()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (size.aspect > 0f) size.aspect else 0.72f)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = RoundedCornerShape(6.dp)
                )
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(6.dp),
            tonalElevation = 1.dp
        ) {
            Box {
                PdfPageImage(
                    viewModel = viewModel,
                    pageIndex = index,
                    documentVersion = document.version,
                    widthPx = widthPx,
                    nightMode = false,
                    modifier = Modifier.fillMaxSize()
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Icon(
                            AppIcons.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
        }
        Text(
            "${index + 1}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ActionBar(
    enabled: Boolean,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onExtract: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = onRotateLeft,
                enabled = enabled,
                label = { Text("Rotate left") },
                leadingIcon = { Icon(AppIcons.RotateLeft, contentDescription = null) }
            )
            AssistChip(
                onClick = onRotateRight,
                enabled = enabled,
                label = { Text("Rotate right") },
                leadingIcon = { Icon(AppIcons.RotateRight, contentDescription = null) }
            )
            AssistChip(
                onClick = onMoveUp,
                enabled = enabled,
                label = { Text("Move up") },
                leadingIcon = { Icon(AppIcons.MoveUp, contentDescription = null) }
            )
            AssistChip(
                onClick = onMoveDown,
                enabled = enabled,
                label = { Text("Move down") },
                leadingIcon = { Icon(AppIcons.MoveDown, contentDescription = null) }
            )
            AssistChip(
                onClick = onDuplicate,
                enabled = enabled,
                label = { Text("Duplicate") },
                leadingIcon = { Icon(AppIcons.Copy, contentDescription = null) }
            )
            AssistChip(
                onClick = onExtract,
                enabled = enabled,
                label = { Text("Extract") },
                leadingIcon = { Icon(AppIcons.Cut, contentDescription = null) }
            )
            AssistChip(
                onClick = onDelete,
                enabled = enabled,
                label = { Text("Delete") },
                leadingIcon = { Icon(AppIcons.Delete, contentDescription = null) }
            )
        }
    }
}
