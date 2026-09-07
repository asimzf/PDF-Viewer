package com.asimzf.asimpdf.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.asimzf.asimpdf.model.PageRange
import com.asimzf.asimpdf.pdf.ImageFit
import com.asimzf.asimpdf.pdf.PaperSize
import com.asimzf.asimpdf.pdf.PdfConvert
import com.asimzf.asimpdf.pdf.PdfPageOps
import com.asimzf.asimpdf.pdf.PdfStamp
import com.asimzf.asimpdf.pdf.StampPosition
import com.asimzf.asimpdf.pdf.WatermarkSpec
import com.asimzf.asimpdf.pdf.PageNumberSpec
import com.asimzf.asimpdf.ui.AppState
import com.asimzf.asimpdf.ui.PdfViewModel
import com.asimzf.asimpdf.ui.common.ChoiceChips
import com.asimzf.asimpdf.ui.common.ColorPicker
import com.asimzf.asimpdf.ui.common.LabeledSlider
import com.asimzf.asimpdf.ui.common.PageRangeField
import com.asimzf.asimpdf.ui.common.SectionCard
import com.asimzf.asimpdf.ui.common.rememberDocumentExporter
import com.asimzf.asimpdf.ui.common.rememberImagePicker
import com.asimzf.asimpdf.ui.common.rememberMultiplePdfPicker
import com.asimzf.asimpdf.ui.common.rememberPdfPicker
import com.asimzf.asimpdf.util.Workspace

@Composable
internal fun InsertTool(viewModel: PdfViewModel, state: AppState) {
    val document = state.document ?: return
    var position by remember { mutableStateOf((state.currentPage + 1).toString()) }
    var paper by remember { mutableStateOf(PaperSize.A4) }
    var landscape by remember { mutableStateOf(false) }

    val insertPdf = rememberPdfPicker { uri ->
        val afterIndex = (position.toIntOrNull() ?: document.pageCount) - 1
        viewModel.edit("Inserting pages") { file ->
            val other = viewModel.importToWork(uri)
            PdfPageOps.insertDocument(viewModel.appContext, file, other, afterIndex)
            other.delete()
        }
    }

    SectionCard(
        title = "Where",
        subtitle = "New pages go in after this page number"
    ) {
        OutlinedTextField(
            value = position,
            onValueChange = { position = it.filter { char -> char.isDigit() } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("After page") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }

    SectionCard(title = "Blank page", subtitle = "Add an empty page of the size you need") {
        ChoiceChips(
            options = PaperSize.entries.toList(),
            selected = paper,
            onSelect = { paper = it },
            label = { it.label },
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Landscape", modifier = Modifier.weight(1f))
            Switch(checked = landscape, onCheckedChange = { landscape = it })
        }
        ApplyButton("Insert blank page") {
            val afterIndex = (position.toIntOrNull() ?: document.pageCount) - 1
            viewModel.edit("Inserting page") { file ->
                PdfPageOps.insertBlank(viewModel.appContext, file, afterIndex, paper, landscape)
            }
        }
    }

    SectionCard(title = "Another PDF", subtitle = "Drop every page of another file in here") {
        SecondaryButton("Choose a PDF to insert", onClick = insertPdf)
    }
}

@Composable
internal fun MergeTool(viewModel: PdfViewModel, state: AppState) {
    val document = state.document ?: return
    var appendAtEnd by remember { mutableStateOf(true) }

    val pickFiles = rememberMultiplePdfPicker { uris ->
        viewModel.runProducingDocument(
            label = "Merging documents",
            name = Workspace.pdfName(document.name, "_merged")
        ) { app ->
            val others = uris.map { uri -> Workspace.importToWork(app, uri) }
            val ordered = if (appendAtEnd) {
                listOf(document.workFile) + others
            } else {
                others + document.workFile
            }
            PdfPageOps.merge(app, ordered)
        }
    }

    SectionCard(
        title = "Merge with this document",
        subtitle = "The current document is one of the parts; pick the others in the order " +
            "you want them"
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Put this document first", modifier = Modifier.weight(1f))
            Switch(checked = appendAtEnd, onCheckedChange = { appendAtEnd = it })
        }
        ApplyButton("Choose PDFs to merge", onClick = pickFiles)
        Text(
            "The merged result opens as a new unsaved document, so the originals are untouched.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun SplitTool(viewModel: PdfViewModel, state: AppState) {
    val document = state.document ?: return
    var mode by remember { mutableStateOf(SplitMode.EVERY) }
    var chunkSize by remember { mutableStateOf("1") }
    var cutAfter by remember { mutableStateOf((document.pageCount / 2).coerceAtLeast(1).toString()) }
    var range by remember { mutableStateOf("1-${document.pageCount}") }
    val exportDocument = rememberDocumentExporter(viewModel, message = "Part saved")

    SectionCard(title = "How to split") {
        ChoiceChips(
            options = SplitMode.entries.toList(),
            selected = mode,
            onSelect = { mode = it },
            label = { it.label },
            modifier = Modifier.fillMaxWidth()
        )
        when (mode) {
            SplitMode.EVERY -> OutlinedTextField(
                value = chunkSize,
                onValueChange = { chunkSize = it.filter { char -> char.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Pages per file") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            SplitMode.AFTER -> OutlinedTextField(
                value = cutAfter,
                onValueChange = { cutAfter = it.filter { char -> char.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Split after page") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            SplitMode.RANGE -> PageRangeField(
                value = range,
                onValueChange = { range = it },
                pageCount = document.pageCount,
                label = "Pages to pull out"
            )
        }
        ApplyButton("Split") {
            viewModel.run("Splitting") { app ->
                val parts = when (mode) {
                    SplitMode.EVERY -> PdfPageOps.splitEvery(
                        app,
                        document.workFile,
                        chunkSize.toIntOrNull() ?: 1
                    )

                    SplitMode.AFTER -> PdfPageOps.splitAt(
                        app,
                        document.workFile,
                        (cutAfter.toIntOrNull() ?: 1) - 1
                    )

                    SplitMode.RANGE -> listOf(
                        PdfPageOps.extract(
                            app,
                            document.workFile,
                            PageRange.parse(range, document.pageCount)
                        )
                    )
                }
                if (parts.size == 1) {
                    exportDocument(parts.first(), Workspace.pdfName(document.name, "_part"))
                } else {
                    com.asimzf.asimpdf.util.Sharing.shareFiles(
                        app,
                        parts,
                        title = "Save the split parts"
                    )
                    viewModel.showMessage("${parts.size} parts ready to save or share.")
                }
            }
        }
        Text(
            "One part is offered as a file to save; several parts are handed to the share " +
                "sheet so you can drop them all into Files.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal enum class SplitMode(val label: String) {
    EVERY("Every N pages"),
    AFTER("Split at a page"),
    RANGE("Pull out a range")
}

@Composable
internal fun RotateTool(viewModel: PdfViewModel, state: AppState) {
    val document = state.document ?: return
    var range by remember { mutableStateOf("all") }
    var angle by remember { mutableStateOf(90) }

    SectionCard(title = "Rotate") {
        PageRangeField(
            value = range,
            onValueChange = { range = it },
            pageCount = document.pageCount
        )
        ChoiceChips(
            options = listOf(90, 180, 270),
            selected = angle,
            onSelect = { angle = it },
            label = { "$it°" },
            modifier = Modifier.fillMaxWidth()
        )
        ApplyButton(
            label = "Rotate pages",
            enabled = PageRange.isValid(range, document.pageCount)
        ) {
            val pages = PageRange.parse(range, document.pageCount)
            viewModel.edit("Rotating") { file ->
                PdfPageOps.rotate(viewModel.appContext, file, pages, angle)
            }
        }
    }
}

@Composable
internal fun CropTool(viewModel: PdfViewModel, state: AppState) {
    val document = state.document ?: return
    var range by remember { mutableStateOf("all") }
    var left by remember { mutableStateOf(0f) }
    var top by remember { mutableStateOf(0f) }
    var right by remember { mutableStateOf(0f) }
    var bottom by remember { mutableStateOf(0f) }

    SectionCard(
        title = "Trim the edges",
        subtitle = "Each slider takes that percentage off one side of the page"
    ) {
        PageRangeField(
            value = range,
            onValueChange = { range = it },
            pageCount = document.pageCount
        )
        LabeledSlider("Left", left, 0f..45f, { left = it }, valueLabel = "${left.toInt()}%")
        LabeledSlider("Top", top, 0f..45f, { top = it }, valueLabel = "${top.toInt()}%")
        LabeledSlider("Right", right, 0f..45f, { right = it }, valueLabel = "${right.toInt()}%")
        LabeledSlider("Bottom", bottom, 0f..45f, { bottom = it }, valueLabel = "${bottom.toInt()}%")
        ApplyButton(
            label = "Crop pages",
            enabled = PageRange.isValid(range, document.pageCount)
        ) {
            val pages = PageRange.parse(range, document.pageCount)
            viewModel.edit("Cropping") { file ->
                PdfPageOps.crop(
                    viewModel.appContext,
                    file,
                    pages,
                    left / 100f,
                    top / 100f,
                    1f - right / 100f,
                    1f - bottom / 100f
                )
            }
        }
        SecondaryButton("Undo cropping on these pages") {
            val pages = PageRange.parse(range, document.pageCount)
            viewModel.edit("Restoring page size") { file ->
                PdfPageOps.resetCrop(viewModel.appContext, file, pages)
            }
        }
    }
}

@Composable
internal fun WatermarkTool(viewModel: PdfViewModel, state: AppState) {
    val document = state.document ?: return
    var text by remember { mutableStateOf("CONFIDENTIAL") }
    var range by remember { mutableStateOf("all") }
    var fontSize by remember { mutableStateOf(64f) }
    var opacity by remember { mutableStateOf(0.25f) }
    var angle by remember { mutableStateOf(45f) }
    var color by remember { mutableStateOf(0xFF9E9E9E.toInt()) }
    var tiled by remember { mutableStateOf(false) }
    var behind by remember { mutableStateOf(false) }

    SectionCard(title = "Watermark text") {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Text") }
        )
        PageRangeField(
            value = range,
            onValueChange = { range = it },
            pageCount = document.pageCount
        )
    }

    SectionCard(title = "Look") {
        ColorPicker(selected = color, onSelect = { color = it }, modifier = Modifier.fillMaxWidth())
        LabeledSlider(
            "Size", fontSize, 12f..160f, { fontSize = it },
            valueLabel = "${fontSize.toInt()} pt"
        )
        LabeledSlider(
            "Opacity", opacity, 0.05f..1f, { opacity = it },
            valueLabel = "${(opacity * 100).toInt()}%"
        )
        LabeledSlider(
            "Angle", angle, -90f..90f, { angle = it },
            valueLabel = "${angle.toInt()}°"
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Repeat across the page", modifier = Modifier.weight(1f))
            Switch(checked = tiled, onCheckedChange = { tiled = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Draw behind the content", modifier = Modifier.weight(1f))
            Switch(checked = behind, onCheckedChange = { behind = it })
        }
        ApplyButton(
            label = "Add watermark",
            enabled = text.isNotBlank() && PageRange.isValid(range, document.pageCount)
        ) {
            val spec = WatermarkSpec(
                text = text,
                pages = PageRange.parse(range, document.pageCount),
                fontSize = fontSize,
                color = color,
                opacity = opacity,
                angleDegrees = angle,
                behindContent = behind,
                tiled = tiled
            )
            viewModel.edit("Stamping watermark") { file ->
                PdfStamp.addWatermark(viewModel.appContext, file, spec)
            }
        }
    }
}

@Composable
internal fun PageNumbersTool(viewModel: PdfViewModel, state: AppState) {
    val document = state.document ?: return
    var format by remember { mutableStateOf("{page} / {total}") }
    var range by remember { mutableStateOf("all") }
    var position by remember { mutableStateOf(StampPosition.BOTTOM_CENTER) }
    var startAt by remember { mutableStateOf("1") }
    var fontSize by remember { mutableStateOf(11f) }
    var margin by remember { mutableStateOf(28f) }
    var color by remember { mutableStateOf(0xFF000000.toInt()) }

    SectionCard(
        title = "Text",
        subtitle = "Use {page} for the running number and {total} for the page count"
    ) {
        OutlinedTextField(
            value = format,
            onValueChange = { format = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Format") }
        )
        OutlinedTextField(
            value = startAt,
            onValueChange = { startAt = it.filter { char -> char.isDigit() } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Start numbering at") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        PageRangeField(
            value = range,
            onValueChange = { range = it },
            pageCount = document.pageCount
        )
    }

    SectionCard(title = "Placement") {
        ChoiceChips(
            options = StampPosition.entries.toList(),
            selected = position,
            onSelect = { position = it },
            label = { it.label },
            modifier = Modifier.fillMaxWidth()
        )
        ColorPicker(selected = color, onSelect = { color = it }, modifier = Modifier.fillMaxWidth())
        LabeledSlider(
            "Text size", fontSize, 7f..28f, { fontSize = it },
            valueLabel = "${fontSize.toInt()} pt"
        )
        LabeledSlider(
            "Margin", margin, 8f..80f, { margin = it },
            valueLabel = "${margin.toInt()} pt"
        )
        ApplyButton(
            label = "Add to pages",
            enabled = format.isNotBlank() && PageRange.isValid(range, document.pageCount)
        ) {
            val spec = PageNumberSpec(
                pages = PageRange.parse(range, document.pageCount),
                format = format,
                position = position,
                startNumber = startAt.toIntOrNull() ?: 1,
                fontSize = fontSize,
                color = color,
                marginPoints = margin
            )
            viewModel.edit("Numbering pages") { file ->
                PdfStamp.addPageNumbers(viewModel.appContext, file, spec)
            }
        }
    }
}

@Composable
internal fun FlattenTool(viewModel: PdfViewModel, state: AppState) {
    val document = state.document ?: return
    var range by remember { mutableStateOf("all") }
    var dpi by remember { mutableStateOf(200f) }

    SectionCard(
        title = "Turn pages into pictures",
        subtitle = "Text, form fields and anything hidden under a black box stop existing. " +
            "This is how a redaction is made final."
    ) {
        PageRangeField(
            value = range,
            onValueChange = { range = it },
            pageCount = document.pageCount
        )
        LabeledSlider(
            "Quality", dpi, 96f..400f, { dpi = it },
            valueLabel = "${dpi.toInt()} dpi"
        )
        ApplyButton(
            label = "Flatten pages",
            enabled = PageRange.isValid(range, document.pageCount)
        ) {
            val pages = PageRange.parse(range, document.pageCount)
            viewModel.edit("Flattening pages") { file ->
                PdfConvert.flattenToImages(
                    viewModel.appContext,
                    file,
                    pages,
                    dpi.toInt()
                ) { current, total -> viewModel.updateProgress(current, total) }
            }
        }
    }
}

@Composable
internal fun ImagesToPdfTool(viewModel: PdfViewModel) {
    var paper by remember { mutableStateOf(PaperSize.A4) }
    var landscape by remember { mutableStateOf(false) }
    var fit by remember { mutableStateOf(ImageFit.FIT) }
    var margin by remember { mutableStateOf(24f) }

    val pickImages = rememberImagePicker { uris ->
        viewModel.runProducingDocument("Building PDF", "images.pdf") { app ->
            PdfConvert.imagesToPdf(
                context = app,
                images = uris,
                paper = paper,
                landscape = landscape,
                fit = fit,
                marginPoints = margin
            ) { current, total -> viewModel.updateProgress(current, total) }
        }
    }

    SectionCard(
        title = "Page setup",
        subtitle = "Pick the photos or scans last; the PDF opens as soon as it is built"
    ) {
        ChoiceChips(
            options = PaperSize.entries.toList(),
            selected = paper,
            onSelect = { paper = it },
            label = { it.label },
            modifier = Modifier.fillMaxWidth()
        )
        ChoiceChips(
            options = ImageFit.entries.toList(),
            selected = fit,
            onSelect = { fit = it },
            label = { it.label },
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Landscape", modifier = Modifier.weight(1f))
            Switch(checked = landscape, onCheckedChange = { landscape = it })
        }
        LabeledSlider(
            "Margin", margin, 0f..72f, { margin = it },
            valueLabel = "${margin.toInt()} pt"
        )
        ApplyButton("Choose images", onClick = pickImages)
    }
}
