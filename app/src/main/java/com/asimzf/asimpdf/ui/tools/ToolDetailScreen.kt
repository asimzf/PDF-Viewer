package com.asimzf.asimpdf.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asimzf.asimpdf.ui.AppState
import com.asimzf.asimpdf.ui.PdfToolId
import com.asimzf.asimpdf.ui.PdfViewModel
import com.asimzf.asimpdf.ui.common.AppIcons

/** One screen per tool, all sharing the same header and layout. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolDetailScreen(
    tool: PdfToolId,
    viewModel: PdfViewModel,
    state: AppState,
    onBack: () -> Unit,
    onOpenOrganize: () -> Unit
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
                        Text(tool.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            tool.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (tool.needsDocument && state.document == null) {
                Text("Open a PDF first.", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            when (tool) {
                PdfToolId.ORGANIZE -> OrganizeShortcut(onOpenOrganize)
                PdfToolId.INSERT -> InsertTool(viewModel, state)
                PdfToolId.MERGE -> MergeTool(viewModel, state)
                PdfToolId.SPLIT -> SplitTool(viewModel, state)
                PdfToolId.ROTATE -> RotateTool(viewModel, state)
                PdfToolId.CROP -> CropTool(viewModel, state)
                PdfToolId.WATERMARK -> WatermarkTool(viewModel, state)
                PdfToolId.PAGE_NUMBERS -> PageNumbersTool(viewModel, state)
                PdfToolId.COMPRESS -> CompressTool(viewModel, state)
                PdfToolId.SECURITY -> SecurityTool(viewModel, state)
                PdfToolId.METADATA -> MetadataTool(viewModel, state)
                PdfToolId.FORMS -> FormsTool(viewModel, state)
                PdfToolId.EXPORT_IMAGES -> ExportImagesTool(viewModel, state)
                PdfToolId.EXPORT_TEXT -> ExportTextTool(viewModel, state)
                PdfToolId.FLATTEN -> FlattenTool(viewModel, state)
                PdfToolId.IMAGES_TO_PDF -> ImagesToPdfTool(viewModel)
                PdfToolId.INFO -> InfoTool(viewModel, state)
            }
        }
    }
}

@Composable
private fun OrganizeShortcut(onOpenOrganize: () -> Unit) {
    Text(
        "Page thumbnails let you select pages and then rotate, move, duplicate, " +
            "extract or delete them.",
        style = MaterialTheme.typography.bodyMedium
    )
    Button(onClick = onOpenOrganize, modifier = Modifier.fillMaxWidth()) {
        Text("Open page manager")
    }
}

/** Primary action button used at the bottom of every tool form. */
@Composable
internal fun ApplyButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label)
    }
}

@Composable
internal fun SecondaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label)
    }
}

@Composable
internal fun ButtonRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        content()
    }
}
