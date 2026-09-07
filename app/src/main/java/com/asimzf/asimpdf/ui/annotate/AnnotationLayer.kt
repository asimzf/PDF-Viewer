package com.asimzf.asimpdf.ui.annotate

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.asimzf.asimpdf.model.AnnotationTool
import com.asimzf.asimpdf.model.MarkupKind
import com.asimzf.asimpdf.model.NPoint
import com.asimzf.asimpdf.model.NRect
import com.asimzf.asimpdf.model.PdfAnnotation
import com.asimzf.asimpdf.model.ShapeKind
import com.asimzf.asimpdf.ui.AnnotationState
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The transparent drawing surface that sits on top of a page.
 *
 * Everything is stored in normalised page coordinates, so a stroke drawn on a
 * phone lands in exactly the same spot when the PDF is opened anywhere else.
 */
@Composable
fun AnnotationLayer(
    pageIndex: Int,
    annotations: List<PdfAnnotation>,
    state: AnnotationState,
    pageWidthPoints: Float,
    onAdd: (PdfAnnotation) -> Unit,
    onErase: (Long) -> Unit,
    onRequestText: (NPoint) -> Unit,
    onRequestNote: (NPoint) -> Unit,
    onRequestPlacement: (NRect) -> Unit,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var stroke by remember(pageIndex) { mutableStateOf<List<Offset>>(emptyList()) }
    var dragStart by remember(pageIndex) { mutableStateOf<Offset?>(null) }
    var dragEnd by remember(pageIndex) { mutableStateOf<Offset?>(null) }

    val pageAnnotations = annotations.filter { it.page == pageIndex }
    val stampCache = remember { mutableMapOf<String, ImageBitmap?>() }

    fun normalise(offset: Offset): NPoint = NPoint(
        (offset.x / canvasSize.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
        (offset.y / canvasSize.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { measured ->
                canvasSize = Size(measured.width.toFloat(), measured.height.toFloat())
            }
            .pointerInput(state.tool, pageIndex, pageAnnotations) {
                detectTapGestures { position ->
                    if (canvasSize == Size.Zero) return@detectTapGestures
                    val point = normalise(position)
                    when (state.tool) {
                        AnnotationTool.TEXT -> onRequestText(point)
                        AnnotationTool.NOTE -> onRequestNote(point)
                        AnnotationTool.SIGNATURE, AnnotationTool.IMAGE -> {
                            val width = 0.35f
                            val height = width * 0.4f
                            onRequestPlacement(
                                NRect(
                                    (point.x - width / 2).coerceIn(0f, 1f - width),
                                    (point.y - height / 2).coerceIn(0f, 1f - height),
                                    (point.x + width / 2).coerceIn(width, 1f),
                                    (point.y + height / 2).coerceIn(height, 1f)
                                ).ordered()
                            )
                        }

                        AnnotationTool.ERASER -> {
                            pageAnnotations.lastOrNull { annotation ->
                                hitTest(annotation, point)
                            }?.let { onErase(it.id) }
                        }

                        else -> Unit
                    }
                }
            }
            .pointerInput(state.tool, state.color, state.strokeWidth, state.shape, state.filled, pageIndex) {
                if (!state.tool.usesDrag()) return@pointerInput
                detectDragGestures(
                    onDragStart = { position ->
                        dragStart = position
                        dragEnd = position
                        stroke = listOf(position)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragEnd = change.position
                        if (state.tool == AnnotationTool.INK) {
                            stroke = stroke + change.position
                        }
                    },
                    onDragEnd = {
                        val start = dragStart
                        val end = dragEnd
                        if (start != null && end != null && canvasSize != Size.Zero) {
                            buildAnnotation(
                                tool = state.tool,
                                state = state,
                                pageIndex = pageIndex,
                                stroke = stroke.map { normalise(it) },
                                start = normalise(start),
                                end = normalise(end)
                            )?.let(onAdd)
                        }
                        dragStart = null
                        dragEnd = null
                        stroke = emptyList()
                    },
                    onDragCancel = {
                        dragStart = null
                        dragEnd = null
                        stroke = emptyList()
                    }
                )
            }
    ) {
        val scale = if (pageWidthPoints > 0f) size.width / pageWidthPoints else 1f

        pageAnnotations.forEach { annotation ->
            drawAnnotation(annotation, scale, stampCache)
        }

        // The shape being drawn right now, so the gesture has feedback.
        val start = dragStart
        val end = dragEnd
        if (state.tool == AnnotationTool.INK && stroke.size > 1) {
            drawPath(
                path = pathOf(stroke),
                color = Color(state.color).copy(alpha = state.opacity),
                style = Stroke(width = state.strokeWidth * scale, join = StrokeJoin.Round)
            )
        } else if (start != null && end != null) {
            drawPreview(state, start, end, scale)
        }
    }
}

private fun AnnotationTool.usesDrag(): Boolean = when (this) {
    AnnotationTool.INK,
    AnnotationTool.HIGHLIGHT,
    AnnotationTool.UNDERLINE,
    AnnotationTool.STRIKEOUT,
    AnnotationTool.SHAPE,
    AnnotationTool.REDACT -> true

    else -> false
}

private fun buildAnnotation(
    tool: AnnotationTool,
    state: AnnotationState,
    pageIndex: Int,
    stroke: List<NPoint>,
    start: NPoint,
    end: NPoint
): PdfAnnotation? {
    val id = System.nanoTime()
    val rect = NRect(start.x, start.y, end.x, end.y)
    val tiny = abs(rect.width) < 0.004f && abs(rect.height) < 0.004f
    return when (tool) {
        AnnotationTool.INK -> if (stroke.size < 2) null else PdfAnnotation.Ink(
            id = id,
            page = pageIndex,
            points = stroke,
            color = state.color,
            strokeWidth = state.strokeWidth,
            opacity = state.opacity
        )

        AnnotationTool.HIGHLIGHT -> if (tiny) null else PdfAnnotation.Markup(
            id, pageIndex, rect.ordered(), MarkupKind.HIGHLIGHT, state.color, 0.4f
        )

        AnnotationTool.UNDERLINE -> if (tiny) null else PdfAnnotation.Markup(
            id, pageIndex, rect.ordered(), MarkupKind.UNDERLINE, state.color, 1f
        )

        AnnotationTool.STRIKEOUT -> if (tiny) null else PdfAnnotation.Markup(
            id, pageIndex, rect.ordered(), MarkupKind.STRIKEOUT, state.color, 1f
        )

        AnnotationTool.SHAPE -> if (tiny) null else PdfAnnotation.Shape(
            id = id,
            page = pageIndex,
            rect = rect,
            kind = state.shape,
            color = state.color,
            strokeWidth = state.strokeWidth,
            filled = state.filled,
            opacity = state.opacity
        )

        AnnotationTool.REDACT -> if (tiny) null else PdfAnnotation.Redaction(
            id, pageIndex, rect.ordered()
        )

        else -> null
    }
}

private fun hitTest(annotation: PdfAnnotation, point: NPoint): Boolean = when (annotation) {
    is PdfAnnotation.Ink -> annotation.points.any {
        abs(it.x - point.x) < 0.03f && abs(it.y - point.y) < 0.03f
    }

    is PdfAnnotation.Markup -> annotation.rect.contains(point)
    is PdfAnnotation.Shape -> annotation.rect.ordered().contains(point)
    is PdfAnnotation.Redaction -> annotation.rect.contains(point)
    is PdfAnnotation.Stamp -> annotation.rect.contains(point)
    is PdfAnnotation.TextBox -> abs(annotation.position.x - point.x) < 0.2f &&
        abs(annotation.position.y - point.y) < 0.06f

    is PdfAnnotation.Note -> abs(annotation.position.x - point.x) < 0.05f &&
        abs(annotation.position.y - point.y) < 0.05f
}

private fun pathOf(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points.first().x, points.first().y)
    points.drop(1).forEach { lineTo(it.x, it.y) }
}

private fun DrawScope.drawPreview(
    state: AnnotationState,
    start: Offset,
    end: Offset,
    scale: Float
) {
    val color = Color(state.color)
    val topLeft = Offset(minOf(start.x, end.x), minOf(start.y, end.y))
    val size = Size(abs(end.x - start.x), abs(end.y - start.y))
    when (state.tool) {
        AnnotationTool.HIGHLIGHT -> drawRect(
            color = color.copy(alpha = 0.4f),
            topLeft = topLeft,
            size = size,
            blendMode = BlendMode.Multiply
        )

        AnnotationTool.UNDERLINE -> drawLine(
            color = color,
            start = Offset(topLeft.x, topLeft.y + size.height),
            end = Offset(topLeft.x + size.width, topLeft.y + size.height),
            strokeWidth = 2f * scale
        )

        AnnotationTool.STRIKEOUT -> drawLine(
            color = color,
            start = Offset(topLeft.x, topLeft.y + size.height / 2f),
            end = Offset(topLeft.x + size.width, topLeft.y + size.height / 2f),
            strokeWidth = 2f * scale
        )

        AnnotationTool.REDACT -> drawRect(Color.Black, topLeft, size)

        AnnotationTool.SHAPE -> when (state.shape) {
            ShapeKind.RECTANGLE ->
                if (state.filled) {
                    drawRect(color, topLeft, size)
                } else {
                    drawRect(color, topLeft, size, style = Stroke(state.strokeWidth * scale))
                }

            ShapeKind.OVAL ->
                if (state.filled) {
                    drawOval(color, topLeft, size)
                } else {
                    drawOval(color, topLeft, size, style = Stroke(state.strokeWidth * scale))
                }

            ShapeKind.LINE -> drawLine(color, start, end, strokeWidth = state.strokeWidth * scale)
            ShapeKind.ARROW -> drawArrow(color, start, end, state.strokeWidth * scale)
        }

        else -> Unit
    }
}

private fun DrawScope.drawAnnotation(
    annotation: PdfAnnotation,
    scale: Float,
    stampCache: MutableMap<String, ImageBitmap?>
) {
    val width = size.width
    val height = size.height
    when (annotation) {
        is PdfAnnotation.Ink -> {
            if (annotation.points.size < 2) return
            val points = annotation.points.map { Offset(it.x * width, it.y * height) }
            drawPath(
                path = pathOf(points),
                color = Color(annotation.color).copy(alpha = annotation.opacity),
                style = Stroke(width = annotation.strokeWidth * scale, join = StrokeJoin.Round)
            )
        }

        is PdfAnnotation.Markup -> {
            val rect = annotation.rect.ordered()
            val topLeft = Offset(rect.left * width, rect.top * height)
            val boxSize = Size(rect.width * width, rect.height * height)
            when (annotation.kind) {
                MarkupKind.HIGHLIGHT -> drawRect(
                    color = Color(annotation.color).copy(alpha = annotation.opacity),
                    topLeft = topLeft,
                    size = boxSize,
                    blendMode = BlendMode.Multiply
                )

                MarkupKind.UNDERLINE -> drawLine(
                    color = Color(annotation.color),
                    start = Offset(topLeft.x, topLeft.y + boxSize.height),
                    end = Offset(topLeft.x + boxSize.width, topLeft.y + boxSize.height),
                    strokeWidth = 2f * scale
                )

                MarkupKind.STRIKEOUT -> drawLine(
                    color = Color(annotation.color),
                    start = Offset(topLeft.x, topLeft.y + boxSize.height / 2f),
                    end = Offset(topLeft.x + boxSize.width, topLeft.y + boxSize.height / 2f),
                    strokeWidth = 2f * scale
                )
            }
        }

        is PdfAnnotation.Shape -> {
            val color = Color(annotation.color).copy(alpha = annotation.opacity)
            val rect = annotation.rect
            val start = Offset(rect.left * width, rect.top * height)
            val end = Offset(rect.right * width, rect.bottom * height)
            val ordered = rect.ordered()
            val topLeft = Offset(ordered.left * width, ordered.top * height)
            val boxSize = Size(ordered.width * width, ordered.height * height)
            when (annotation.kind) {
                ShapeKind.RECTANGLE ->
                    if (annotation.filled) {
                        drawRect(color, topLeft, boxSize)
                    } else {
                        drawRect(
                            color,
                            topLeft,
                            boxSize,
                            style = Stroke(annotation.strokeWidth * scale)
                        )
                    }

                ShapeKind.OVAL ->
                    if (annotation.filled) {
                        drawOval(color, topLeft, boxSize)
                    } else {
                        drawOval(
                            color,
                            topLeft,
                            boxSize,
                            style = Stroke(annotation.strokeWidth * scale)
                        )
                    }

                ShapeKind.LINE ->
                    drawLine(color, start, end, strokeWidth = annotation.strokeWidth * scale)

                ShapeKind.ARROW ->
                    drawArrow(color, start, end, annotation.strokeWidth * scale)
            }
        }

        is PdfAnnotation.Redaction -> {
            val rect = annotation.rect.ordered()
            drawRect(
                Color.Black,
                Offset(rect.left * width, rect.top * height),
                Size(rect.width * width, rect.height * height)
            )
        }

        is PdfAnnotation.TextBox -> {
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = annotation.color
                    textSize = annotation.fontSize * scale
                }
                var y = annotation.position.y * height + paint.textSize
                annotation.text.split('\n').forEach { line ->
                    canvas.nativeCanvas.drawText(line, annotation.position.x * width, y, paint)
                    y += paint.textSize * 1.25f
                }
            }
        }

        is PdfAnnotation.Note -> {
            val boxSize = 18f * scale
            val topLeft = Offset(annotation.position.x * width, annotation.position.y * height)
            drawRect(Color(annotation.color), topLeft, Size(boxSize, boxSize))
            drawRect(
                Color.Black,
                topLeft,
                Size(boxSize, boxSize),
                style = Stroke(1f * scale)
            )
        }

        is PdfAnnotation.Stamp -> {
            val image = stampCache.getOrPut(annotation.imagePath) {
                runCatching {
                    BitmapFactory.decodeFile(annotation.imagePath)?.asImageBitmap()
                }.getOrNull()
            } ?: return
            val rect = annotation.rect.ordered()
            drawImage(
                image = image,
                dstOffset = IntOffset(
                    (rect.left * width).toInt(),
                    (rect.top * height).toInt()
                ),
                dstSize = IntSize(
                    (rect.width * width).toInt().coerceAtLeast(1),
                    (rect.height * height).toInt().coerceAtLeast(1)
                ),
                alpha = annotation.opacity
            )
        }
    }
}

private fun DrawScope.drawArrow(color: Color, start: Offset, end: Offset, strokeWidth: Float) {
    drawLine(color, start, end, strokeWidth = strokeWidth)
    val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
    val head = (strokeWidth * 5f).coerceIn(16f, 80f)
    val spread = Math.toRadians(24.0)
    drawLine(
        color,
        end,
        Offset(
            end.x - (head * cos(angle - spread)).toFloat(),
            end.y - (head * sin(angle - spread)).toFloat()
        ),
        strokeWidth = strokeWidth
    )
    drawLine(
        color,
        end,
        Offset(
            end.x - (head * cos(angle + spread)).toFloat(),
            end.y - (head * sin(angle + spread)).toFloat()
        ),
        strokeWidth = strokeWidth
    )
}
