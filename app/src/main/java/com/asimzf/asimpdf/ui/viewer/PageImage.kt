package com.asimzf.asimpdf.ui.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.asimzf.asimpdf.ui.PdfViewModel

/** Inverts the page for night reading without touching the file itself. */
private val NightFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -0.85f, 0f, 0f, 0f, 235f,
            0f, -0.85f, 0f, 0f, 235f,
            0f, 0f, -0.85f, 0f, 235f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

/**
 * One rasterised page. The bitmap is produced off the main thread and re-made
 * whenever the page, the requested width or the document version changes.
 */
@Composable
fun PdfPageImage(
    viewModel: PdfViewModel,
    pageIndex: Int,
    documentVersion: Int,
    widthPx: Int,
    nightMode: Boolean,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    var bitmap by remember(pageIndex, documentVersion) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex, documentVersion, widthPx) {
        if (widthPx > 0) {
            bitmap = viewModel.renderPage(pageIndex, widthPx)
        }
    }

    Box(
        modifier = modifier.background(
            if (nightMode) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.White
        ),
        contentAlignment = Alignment.Center
    ) {
        val current = bitmap
        if (current != null && !current.isRecycled) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                colorFilter = if (nightMode) NightFilter else null
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }
        overlay()
    }
}
