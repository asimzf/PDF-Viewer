package com.asimzf.asimpdf.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.asimzf.asimpdf.ui.PdfToolId
import com.asimzf.asimpdf.ui.common.AppIcons

/** The full tool box for the open document. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    documentName: String?,
    onBack: () -> Unit,
    onSelect: (PdfToolId) -> Unit
) {
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
                        Text("Tools", style = MaterialTheme.typography.titleMedium)
                        documentName?.let {
                            Text(
                                it,
                                maxLines = 1,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(PdfToolId.entries.toList()) { tool ->
                ToolRow(tool = tool, onClick = { onSelect(tool) })
            }
        }
    }
}

@Composable
private fun ToolRow(tool: PdfToolId, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                iconFor(tool),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(tool.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    tool.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(AppIcons.Right, contentDescription = null)
        }
    }
}

private fun iconFor(tool: PdfToolId): ImageVector = when (tool) {
    PdfToolId.ORGANIZE -> AppIcons.ListView
    PdfToolId.INSERT -> AppIcons.Add
    PdfToolId.MERGE -> AppIcons.Layers
    PdfToolId.SPLIT -> AppIcons.Cut
    PdfToolId.ROTATE -> AppIcons.RotateRight
    PdfToolId.CROP -> AppIcons.Crop
    PdfToolId.WATERMARK -> AppIcons.Brush
    PdfToolId.PAGE_NUMBERS -> AppIcons.Text
    PdfToolId.COMPRESS -> AppIcons.Refresh
    PdfToolId.SECURITY -> AppIcons.Lock
    PdfToolId.METADATA -> AppIcons.Settings
    PdfToolId.FORMS -> AppIcons.Edit
    PdfToolId.EXPORT_IMAGES -> AppIcons.Image
    PdfToolId.EXPORT_TEXT -> AppIcons.Document
    PdfToolId.FLATTEN -> AppIcons.Layers
    PdfToolId.IMAGES_TO_PDF -> AppIcons.Pdf
    PdfToolId.INFO -> AppIcons.Info
}
