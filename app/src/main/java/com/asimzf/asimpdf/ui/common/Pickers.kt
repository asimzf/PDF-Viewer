package com.asimzf.asimpdf.ui.common

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.asimzf.asimpdf.ui.PdfViewModel
import java.io.File

/** Opens the system file picker filtered to PDFs. */
@Composable
fun rememberPdfPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onPicked) }
    return remember(launcher) { { launcher.launch(arrayOf("application/pdf")) } }
}

/** Same picker, but for choosing several PDFs at once. */
@Composable
fun rememberMultiplePdfPicker(onPicked: (List<Uri>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) onPicked(uris) }
    return remember(launcher) { { launcher.launch(arrayOf("application/pdf")) } }
}

@Composable
fun rememberImagePicker(onPicked: (List<Uri>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) onPicked(uris) }
    return remember(launcher) { { launcher.launch(arrayOf("image/*")) } }
}

@Composable
fun rememberSingleImagePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onPicked) }
    return remember(launcher) { { launcher.launch(arrayOf("image/*")) } }
}

/** Asks the user where to write a new file, then hands back the location. */
@Composable
fun rememberFileSaver(mimeType: String, onChosen: (Uri) -> Unit): (String) -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(mimeType)
    ) { uri -> uri?.let(onChosen) }
    return remember(launcher) { { name: String -> launcher.launch(name) } }
}

/**
 * Saves a file the app just produced to a location the user picks, then reports
 * back through the view model.
 */
@Composable
fun rememberDocumentExporter(
    viewModel: PdfViewModel,
    mimeType: String = "application/pdf",
    message: String = "Saved"
): (File, String) -> Unit {
    var pending by remember { mutableStateOf<File?>(null) }
    val launch = rememberFileSaver(mimeType) { uri ->
        pending?.let { file -> viewModel.exportFileTo(file, uri, message) }
        pending = null
    }
    return { file, name ->
        pending = file
        launch(name)
    }
}
