package com.asimzf.asimpdf.ui.tools

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.asimzf.asimpdf.model.PageRange
import com.asimzf.asimpdf.pdf.CompressionLevel
import com.asimzf.asimpdf.pdf.EncryptionStrength
import com.asimzf.asimpdf.pdf.ExportImageFormat
import com.asimzf.asimpdf.pdf.FormField
import com.asimzf.asimpdf.pdf.FormFieldType
import com.asimzf.asimpdf.pdf.PdfCompress
import com.asimzf.asimpdf.pdf.PdfConvert
import com.asimzf.asimpdf.pdf.PdfDocumentInfo
import com.asimzf.asimpdf.pdf.PdfDocumentTools
import com.asimzf.asimpdf.pdf.PdfForms
import com.asimzf.asimpdf.pdf.PdfMetadata
import com.asimzf.asimpdf.pdf.PdfPermissions
import com.asimzf.asimpdf.pdf.PdfSecurity
import com.asimzf.asimpdf.ui.AppState
import com.asimzf.asimpdf.ui.PdfViewModel
import com.asimzf.asimpdf.ui.common.ChoiceChips
import com.asimzf.asimpdf.ui.common.LabeledSlider
import com.asimzf.asimpdf.ui.common.PageRangeField
import com.asimzf.asimpdf.ui.common.SectionCard
import com.asimzf.asimpdf.ui.common.rememberDocumentExporter
import com.asimzf.asimpdf.util.Sharing
import com.asimzf.asimpdf.util.Workspace

@Composable
internal fun CompressTool(viewModel: PdfViewModel, state: AppState) {
    val document = state.document ?: return
    val context = LocalContext.current
    var level by remember { mutableStateOf(CompressionLevel.BALANCED) }

    SectionCard(
        title = "How much",
        subtitle = "Images are re-encoded; text and vector drawings are untouched"
    ) {
        ChoiceChips(
            options = CompressionLevel.entries.toList(),
            selected = level,
            onSelect = { level = it },
            label = { it.label },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            level.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Current size: ${Formatter.formatShortFileSize(context, document.workFile.length())}",
            style = MaterialTheme.typography.bodyMedium
        )
        ApplyButton("Compress") {
            viewModel.edit("Compressing") { file ->
                val result = PdfCompress.compress(viewModel.appContext, file, level) { current, total ->
                    viewModel.updateProgress(current, total)
                }
                viewModel.showMessage(
                    if (result.savedBytes > 0) {
                        "Saved ${Formatter.formatShortFileSize(
                            viewModel.appContext,
                            result.savedBytes
                        )} (${result.savedPercent}%)"
                    } else {
                        "This document was already as small as it gets."
                    }
                )
            }
        }
    }
}

@Composable
internal fun SecurityTool(viewModel: PdfViewModel, state: AppState) {
    val document = state.document ?: return
    var userPassword by remember { mutableStateOf("") }
    var ownerPassword by remember { mutableStateOf("") }
    var strength by remember { mutableStateOf(EncryptionStrength.AES_256) }
    var permissions by remember { mutableStateOf(PdfPermissions()) }

    SectionCard(
        title = "Passwords",
        subtitle = "The open password is asked for when the document is opened; the owner " +
            "password protects the permissions below"
    ) {
        OutlinedTextField(
            value = userPassword,
            onValueChange = { userPassword = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Open password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        OutlinedTextField(
            value = ownerPassword,
            onValueChange = { ownerPassword = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Owner password (optional)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        ChoiceChips(
            options = EncryptionStrength.entries.toList(),
            selected = strength,
            onSelect = { strength = it },
            label = { it.label },
            modifier = Modifier.fillMaxWidth()
        )
    }

    SectionCard(title = "What readers may do") {
        PermissionRow("Print", permissions.canPrint) {
            permissions = permissions.copy(canPrint = it)
        }
        PermissionRow("Print at full quality", permissions.canPrintHighQuality) {
            permissions = permissions.copy(canPrintHighQuality = it)
        }
        PermissionRow("Change the document", permissions.canModify) {
            permissions = permissions.copy(canModify = it)
        }
        PermissionRow("Copy text and images", permissions.canCopyContent) {
            permissions = permissions.copy(canCopyContent = it)
        }
        PermissionRow("Add comments", permissions.canAnnotate) {
            permissions = permissions.copy(canAnnotate = it)
        }
        PermissionRow("Fill in forms", permissions.canFillForms) {
            permissions = permissions.copy(canFillForms = it)
        }
        PermissionRow("Reorganise pages", permissions.canAssemble) {
            permissions = permissions.copy(canAssemble = it)
        }
    }

    ApplyButton("Protect document", enabled = userPassword.isNotEmpty() || ownerPassword.isNotEmpty()) {
        viewModel.edit("Encrypting") { file ->
            PdfSecurity.protect(
                viewModel.appContext,
                file,
                userPassword,
                ownerPassword,
                permissions,
                strength
            )
            viewModel.showMessage("Encryption applied. Save the document to keep it.")
        }
    }
    SecondaryButton("Remove protection") {
        viewModel.edit("Removing protection") { file ->
            PdfSecurity.removeProtection(viewModel.appContext, file)
            viewModel.showMessage("The saved copy will have no password.")
        }
    }
    Text(
        if (document.wasEncrypted) {
            "This document arrived encrypted. asimPDF unlocked a private working copy so it " +
                "could be edited; re-protect it before sharing."
        } else {
            "Encryption is applied to the working copy and written out when you save."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PermissionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun MetadataTool(viewModel: PdfViewModel, state: AppState) {
    val document = state.document ?: return
    var metadata by remember(document.version) { mutableStateOf<PdfMetadata?>(null) }

    LaunchedEffect(document.workFile, document.version) {
        metadata = runCatching { PdfDocumentTools.info(document.workFile).metadata }.getOrNull()
            ?: PdfMetadata()
    }

    val current = metadata
    if (current == null) {
        Text("Reading document properties…")
        return
    }

    SectionCard(title = "Document properties") {
        MetadataField("Title", current.title) { metadata = current.copy(title = it) }
        MetadataField("Author", current.author) { metadata = current.copy(author = it) }
        MetadataField("Subject", current.subject) { metadata = current.copy(subject = it) }
        MetadataField("Keywords", current.keywords) { metadata = current.copy(keywords = it) }
        MetadataField("Creator", current.creator) { metadata = current.copy(creator = it) }
        ApplyButton("Save properties") {
            viewModel.edit("Updating properties") { file ->
                PdfDocumentTools.updateMetadata(viewModel.appContext, file, current)
            }
        }
        SecondaryButton("Strip all metadata") {
            viewModel.edit("Clearing metadata") { file ->
                PdfDocumentTools.clearMetadata(viewModel.appContext, file)
                viewModel.showMessage("Author, dates and other properties removed.")
            }
        }
    }
}

@Composable
private fun MetadataField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) }
    )
}

@Composable
internal fun FormsTool(viewModel: PdfViewModel, state: AppState) {
    val document = state.document ?: return
    var fields by remember(document.version) { mutableStateOf<List<FormField>?>(null) }
    val edits: SnapshotStateMap<String, String> = remember(document.version) { mutableStateMapOf() }

    LaunchedEffect(document.workFile, document.version) {
        fields = runCatching { PdfForms.fields(document.workFile) }.getOrDefault(emptyList())
    }

    val loaded = fields
    when {
        loaded == null -> Text("Looking for form fields…")
        loaded.isEmpty() -> Text(
            "This PDF has no interactive form fields. You can still type on the page with " +
                "the text tool in markup mode.",
            style = MaterialTheme.typography.bodyMedium
        )

        else -> {
            SectionCard(title = "${loaded.size} form fields") {
                loaded.forEach { field ->
                    val value = edits[field.name] ?: field.value
                    when (field.type) {
                        FormFieldType.CHECKBOX -> Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = value.toBoolean(),
                                enabled = !field.readOnly,
                                onCheckedChange = { checked ->
                                    edits[field.name] = checked.toString()
                                }
                            )
                            Text(field.label, modifier = Modifier.weight(1f))
                        }

                        FormFieldType.SIGNATURE -> Text(
                            "${field.label}: signature field",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        else -> {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { edits[field.name] = it },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !field.readOnly,
                                singleLine = !field.multiline,
                                label = { Text(field.label) },
                                supportingText = if (field.options.isNotEmpty()) {
                                    { Text("Options: ${field.options.joinToString(", ")}") }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
                ApplyButton("Save answers", enabled = edits.isNotEmpty()) {
                    val values = edits.toMap()
                    viewModel.edit("Filling in the form") { file ->
                        PdfForms.setValues(viewModel.appContext, file, values)
                        viewModel.showMessage("Form updated.")
                    }
                }
                SecondaryButton("Flatten form") {
                    viewModel.edit("Flattening form") { file ->
                        PdfForms.flatten(viewModel.appContext, file)
                        viewModel.showMessage("The answers are now part of the page.")
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExportImagesTool(viewModel: PdfViewModel, state: AppState) {
    val document = state.document ?: return
    var range by remember { mutableStateOf("all") }
    var dpi by remember { mutableStateOf(200f) }
    var format by remember { mutableStateOf(ExportImageFormat.PNG) }

    SectionCard(title = "Export pages as pictures") {
        PageRangeField(
            value = range,
            onValueChange = { range = it },
            pageCount = document.pageCount
        )
        ChoiceChips(
            options = ExportImageFormat.entries.toList(),
            selected = format,
            onSelect = { format = it },
            label = { it.label },
            modifier = Modifier.fillMaxWidth()
        )
        LabeledSlider(
            "Resolution", dpi, 72f..400f, { dpi = it },
            valueLabel = "${dpi.toInt()} dpi"
        )
        ApplyButton(
            label = "Export",
            enabled = PageRange.isValid(range, document.pageCount)
        ) {
            val pages = PageRange.parse(range, document.pageCount)
            viewModel.run("Rendering pages") { app ->
                val files = PdfConvert.pagesToImages(
                    context = app,
                    file = document.workFile,
                    pages = pages,
                    dpi = dpi.toInt(),
                    format = format,
                    baseName = document.name
                ) { current, total -> viewModel.updateProgress(current, total) }
                if (files.isEmpty()) {
                    viewModel.showError("Nothing was exported.")
                } else {
                    Sharing.shareFiles(
                        app,
                        files,
                        mimeType = if (format == ExportImageFormat.PNG) "image/png" else "image/jpeg",
                        title = "Save the exported pages"
                    )
                    viewModel.showMessage("${files.size} image(s) ready to save or share.")
                }
            }
        }
    }
}

@Composable
internal fun ExportTextTool(viewModel: PdfViewModel, state: AppState) {
    val document = state.document ?: return
    val exportText = rememberDocumentExporter(viewModel, mimeType = "text/plain", message = "Text saved")

    SectionCard(
        title = "Extract the text",
        subtitle = "Works on documents that contain real text. A scan is a picture, so nothing " +
            "would come out."
    ) {
        ApplyButton("Extract to a .txt file") {
            viewModel.run("Extracting text") { app ->
                val target = PdfConvert.toTextFile(app, document.workFile, document.name)
                exportText(target, "${Workspace.baseName(document.name)}.txt")
            }
        }
    }
}

@Composable
internal fun InfoTool(viewModel: PdfViewModel, state: AppState) {
    val document = state.document ?: return
    val context = LocalContext.current
    var info by remember(document.version) { mutableStateOf<PdfDocumentInfo?>(null) }

    LaunchedEffect(document.workFile, document.version) {
        info = runCatching { PdfDocumentTools.info(document.workFile) }.getOrNull()
    }

    val current = info
    if (current == null) {
        Text("Reading document…")
        return
    }

    SectionCard(title = document.name) {
        InfoRow("Pages", current.pageCount.toString())
        InfoRow(
            "Page size",
            "${current.firstPageSize.width.toInt()} × ${current.firstPageSize.height.toInt()} pt"
        )
        InfoRow("PDF version", current.version)
        InfoRow(
            "Working copy",
            Formatter.formatShortFileSize(context, current.fileSizeBytes)
        )
        InfoRow("Created", current.created)
        InfoRow("Modified", current.modified)
        InfoRow("Encrypted", if (document.wasEncrypted) "Yes (unlocked here)" else "No")
        InfoRow("Interactive form", if (current.hasForm) "Yes" else "No")
        InfoRow("Title", current.metadata.title.ifBlank { "—" })
        InfoRow("Author", current.metadata.author.ifBlank { "—" })
        InfoRow("Subject", current.metadata.subject.ifBlank { "—" })
        InfoRow("Keywords", current.metadata.keywords.ifBlank { "—" })
        InfoRow("Producer", current.metadata.producer.ifBlank { "—" })
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            modifier = Modifier.weight(1.4f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
