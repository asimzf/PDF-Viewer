package com.asimzf.asimpdf.ui.annotate

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream

/**
 * A place to sign with a finger or stylus. The result is saved as a
 * transparent PNG in the app's own storage and stamped onto the page.
 */
@Composable
fun SignatureDialog(
    onDismiss: () -> Unit,
    onSigned: (String) -> Unit
) {
    val context = LocalContext.current
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draw your signature") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Sign in the box, then place it on the page.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .onSizeChanged { measured ->
                            canvasSize = Size(measured.width.toFloat(), measured.height.toFloat())
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { position -> current = listOf(position) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    current = current + change.position
                                },
                                onDragEnd = {
                                    if (current.size > 1) strokes = strokes + listOf(current)
                                    current = emptyList()
                                },
                                onDragCancel = { current = emptyList() }
                            )
                        }
                ) {
                    (strokes + listOf(current)).forEach { stroke ->
                        if (stroke.size < 2) return@forEach
                        val path = Path().apply {
                            moveTo(stroke.first().x, stroke.first().y)
                            stroke.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path = path,
                            color = Color.Black,
                            style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
                TextButton(onClick = { strokes = emptyList() }) { Text("Clear") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = strokes.isNotEmpty(),
                onClick = {
                    val path = writeSignature(context.filesDir, strokes, canvasSize)
                    if (path != null) onSigned(path)
                }
            ) { Text("Use signature") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Renders the strokes into a transparent PNG, trimmed to what was drawn. */
private fun writeSignature(
    filesDir: File,
    strokes: List<List<Offset>>,
    canvasSize: Size
): String? {
    if (strokes.isEmpty() || canvasSize.width <= 0f || canvasSize.height <= 0f) return null
    val scale = (SIGNATURE_WIDTH / canvasSize.width).coerceAtLeast(1f)
    val width = (canvasSize.width * scale).toInt().coerceAtLeast(1)
    val height = (canvasSize.height * scale).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 5f * scale
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { stroke ->
        if (stroke.size < 2) return@forEach
        val path = AndroidPath().apply {
            moveTo(stroke.first().x * scale, stroke.first().y * scale)
            stroke.drop(1).forEach { lineTo(it.x * scale, it.y * scale) }
        }
        canvas.drawPath(path, paint)
    }
    val trimmed = trim(bitmap, strokes, scale, paint.strokeWidth)
    val directory = File(filesDir, "signatures").apply { mkdirs() }
    val target = File(directory, "signature_${System.currentTimeMillis()}.png")
    return runCatching {
        FileOutputStream(target).use { output ->
            trimmed.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        if (trimmed !== bitmap) trimmed.recycle()
        bitmap.recycle()
        target.absolutePath
    }.getOrNull()
}

/** Crops the empty space around the signature so it stamps at a sensible size. */
private fun trim(
    bitmap: Bitmap,
    strokes: List<List<Offset>>,
    scale: Float,
    strokeWidth: Float
): Bitmap {
    val points = strokes.flatten()
    if (points.isEmpty()) return bitmap
    val padding = strokeWidth
    val left = ((points.minOf { it.x } * scale) - padding).toInt().coerceIn(0, bitmap.width - 1)
    val top = ((points.minOf { it.y } * scale) - padding).toInt().coerceIn(0, bitmap.height - 1)
    val right = ((points.maxOf { it.x } * scale) + padding).toInt().coerceIn(left + 1, bitmap.width)
    val bottom = ((points.maxOf { it.y } * scale) + padding).toInt().coerceIn(top + 1, bitmap.height)
    return runCatching {
        Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }.getOrDefault(bitmap)
}

private const val SIGNATURE_WIDTH = 1200f
