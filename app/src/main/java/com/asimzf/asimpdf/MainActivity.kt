package com.asimzf.asimpdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.asimzf.asimpdf.print.Printing
import com.asimzf.asimpdf.ui.AppState
import com.asimzf.asimpdf.ui.PdfToolId
import com.asimzf.asimpdf.ui.PdfViewModel
import com.asimzf.asimpdf.ui.Screen
import com.asimzf.asimpdf.ui.common.BusyOverlay
import com.asimzf.asimpdf.ui.common.PasswordDialog
import com.asimzf.asimpdf.ui.common.rememberFileSaver
import com.asimzf.asimpdf.ui.common.rememberImagePicker
import com.asimzf.asimpdf.ui.common.rememberMultiplePdfPicker
import com.asimzf.asimpdf.ui.common.rememberPdfPicker
import com.asimzf.asimpdf.ui.home.HomeScreen
import com.asimzf.asimpdf.ui.organize.OrganizeScreen
import com.asimzf.asimpdf.ui.theme.AsimPdfTheme
import com.asimzf.asimpdf.ui.tools.ToolDetailScreen
import com.asimzf.asimpdf.ui.tools.ToolsScreen
import com.asimzf.asimpdf.ui.viewer.ViewerScreen
import com.asimzf.asimpdf.pdf.ImageFit
import com.asimzf.asimpdf.pdf.PaperSize
import com.asimzf.asimpdf.pdf.PdfConvert
import com.asimzf.asimpdf.pdf.PdfPageOps
import com.asimzf.asimpdf.util.Sharing
import com.asimzf.asimpdf.util.Workspace

class MainActivity : ComponentActivity() {

    private val viewModel: PdfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AsimPdfTheme {
                AsimPdfApp(viewModel)
            }
        }
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /** Opens a PDF another app sent us, so asimPDF works as the system PDF viewer. */
    private fun handleIncomingIntent(intent: Intent?) {
        val incoming = intent ?: return
        val uri: Uri? = when (incoming.action) {
            Intent.ACTION_VIEW -> incoming.data
            Intent.ACTION_SEND -> incoming.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            else -> null
        }
        if (uri != null) viewModel.openDocument(uri)
    }

    fun applyKeepScreenOn(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
private fun AsimPdfApp(viewModel: PdfViewModel) {
    val state: AppState by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    var confirmExit by remember { mutableStateOf(false) }

    val openPdf = rememberPdfPicker { uri -> viewModel.openDocument(uri) }
    val saveAs = rememberFileSaver("application/pdf") { uri -> viewModel.saveAs(uri) }
    val mergeFromHome = rememberMultiplePdfPicker { uris ->
        viewModel.runProducingDocument("Merging documents", "merged.pdf") { app ->
            PdfPageOps.merge(app, uris.map { uri -> Workspace.importToWork(app, uri) })
        }
    }
    val imagesFromHome = rememberImagePicker { uris ->
        viewModel.runProducingDocument("Building PDF", "images.pdf") { app ->
            PdfConvert.imagesToPdf(
                context = app,
                images = uris,
                paper = PaperSize.A4,
                landscape = false,
                fit = ImageFit.FIT,
                marginPoints = 24f
            ) { current, total -> viewModel.updateProgress(current, total) }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHost.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(state.keepScreenOn) {
        (context as? MainActivity)?.applyKeepScreenOn(state.keepScreenOn)
    }

    val goHome = {
        if (state.document?.unsavedChanges == true) {
            confirmExit = true
        } else {
            viewModel.closeDocument()
        }
    }

    BackHandler(enabled = state.screen != Screen.Home) {
        when (state.screen) {
            is Screen.Tool -> viewModel.setScreen(Screen.Tools)
            Screen.Tools, Screen.Organize -> viewModel.setScreen(Screen.Viewer)
            Screen.Viewer -> goHome()
            Screen.Home -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val screen = state.screen) {
                Screen.Home -> HomeScreen(
                    recents = state.recents,
                    onOpen = openPdf,
                    onOpenRecent = { uri -> viewModel.openDocument(uri) },
                    onForgetRecent = { uri -> viewModel.forgetRecent(uri) },
                    onClearRecents = { viewModel.clearRecents() },
                    onImagesToPdf = imagesFromHome,
                    onMerge = mergeFromHome
                )

                Screen.Viewer -> ViewerScreen(
                    viewModel = viewModel,
                    state = state,
                    onBack = goHome,
                    onSave = { viewModel.save { saveAs(state.document?.name ?: "document.pdf") } },
                    onSaveAs = { saveAs(state.document?.name ?: "document.pdf") },
                    onShare = {
                        state.document?.let { document ->
                            Sharing.shareFile(context, document.workFile, title = "Share PDF")
                        }
                    },
                    onPrint = {
                        state.document?.let { document ->
                            Printing.print(
                                context,
                                document.workFile,
                                document.name,
                                document.pageCount
                            )
                        }
                    },
                    onOrganize = { viewModel.setScreen(Screen.Organize) },
                    onTools = { viewModel.setScreen(Screen.Tools) }
                )

                Screen.Organize -> OrganizeScreen(
                    viewModel = viewModel,
                    state = state,
                    onBack = { viewModel.setScreen(Screen.Viewer) }
                )

                Screen.Tools -> ToolsScreen(
                    documentName = state.document?.name,
                    onBack = { viewModel.setScreen(Screen.Viewer) },
                    onSelect = { tool ->
                        if (tool == PdfToolId.ORGANIZE) {
                            viewModel.setScreen(Screen.Organize)
                        } else {
                            viewModel.setScreen(Screen.Tool(tool))
                        }
                    }
                )

                is Screen.Tool -> ToolDetailScreen(
                    tool = screen.tool,
                    viewModel = viewModel,
                    state = state,
                    onBack = { viewModel.setScreen(Screen.Tools) },
                    onOpenOrganize = { viewModel.setScreen(Screen.Organize) }
                )
            }

            BusyOverlay(progress = state.busy)
        }
    }

    state.passwordRequest?.let { uri ->
        PasswordDialog(
            error = state.passwordError,
            onSubmit = { password -> viewModel.openDocument(uri, password) },
            onDismiss = { viewModel.dismissPasswordRequest() }
        )
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.consumeError() },
            title = { Text("That did not work") },
            text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeError() }) { Text("OK") }
            }
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Unsaved changes") },
            text = { Text("Save the document before closing it?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmExit = false
                        viewModel.save { saveAs(state.document?.name ?: "document.pdf") }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmExit = false
                        viewModel.closeDocument()
                    }
                ) { Text("Discard") }
            }
        )
    }
}
