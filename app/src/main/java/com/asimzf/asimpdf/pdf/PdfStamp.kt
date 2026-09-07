package com.asimzf.asimpdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.asimzf.asimpdf.model.MarkupKind
import com.asimzf.asimpdf.model.NRect
import com.asimzf.asimpdf.model.PdfAnnotation
import com.asimzf.asimpdf.model.ShapeKind
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Where a stamp sits on the page. */
enum class StampPosition(val label: String) {
    TOP_LEFT("Top left"),
    TOP_CENTER("Top centre"),
    TOP_RIGHT("Top right"),
    BOTTOM_LEFT("Bottom left"),
    BOTTOM_CENTER("Bottom centre"),
    BOTTOM_RIGHT("Bottom right"),
    CENTER("Centre")
}

data class WatermarkSpec(
    val text: String,
    val pages: List<Int>,
    val fontSize: Float = 64f,
    val color: Int = 0xFF9E9E9E.toInt(),
    val opacity: Float = 0.25f,
    val angleDegrees: Float = 45f,
    val behindContent: Boolean = false,
    val tiled: Boolean = false,
    val style: PdfFontStyle = PdfFontStyle.SANS_BOLD
)

data class PageNumberSpec(
    val pages: List<Int>,
    val format: String = "{page} / {total}",
    val position: StampPosition = StampPosition.BOTTOM_CENTER,
    val startNumber: Int = 1,
    val fontSize: Float = 11f,
    val color: Int = 0xFF000000.toInt(),
    val marginPoints: Float = 28f,
    val style: PdfFontStyle = PdfFontStyle.SANS
)

/**
 * Draws on top of existing pages: annotations, watermarks, page numbers and
 * image stamps. Everything is written straight into the page content stream, so
 * the result looks the same in every PDF reader.
 */
object PdfStamp {

    suspend fun applyAnnotations(
        context: Context,
        file: File,
        annotations: List<PdfAnnotation>
    ) = withContext(Dispatchers.IO) {
        if (annotations.isEmpty()) return@withContext
        PdfIo.edit(context, file) { document ->
            val fonts = HashMap<PdfFontStyle, PDFont>()
            annotations.groupBy { it.page }.toSortedMap().forEach { (pageIndex, pageAnnotations) ->
                val page = document.pageOrNull(pageIndex) ?: return@forEach
                val width = PdfCoords.displayWidth(page)
                val height = PdfCoords.displayHeight(page)
                contentStream(document, page, prepend = false).use { cs ->
                    cs.saveGraphicsState()
                    cs.transform(PdfCoords.displayMatrix(page))
                    pageAnnotations.forEach { annotation ->
                        draw(document, page, cs, annotation, width, height, fonts)
                    }
                    cs.restoreGraphicsState()
                }
                // Sticky notes are real PDF annotations so readers show the comment.
                pageAnnotations.filterIsInstance<PdfAnnotation.Note>().forEach { note ->
                    addNoteAnnotation(page, note, width, height)
                }
            }
        }
    }

    suspend fun addWatermark(context: Context, file: File, spec: WatermarkSpec) =
        withContext(Dispatchers.IO) {
            if (spec.text.isBlank()) throw PdfOperationException("Enter the watermark text.")
            PdfIo.edit(context, file) { document ->
                val font = PdfFonts.load(document, spec.style)
                val text = PdfFonts.sanitize(font, spec.text)
                spec.pages.forEach { index ->
                    val page = document.pageOrNull(index) ?: return@forEach
                    val width = PdfCoords.displayWidth(page)
                    val height = PdfCoords.displayHeight(page)
                    contentStream(document, page, prepend = spec.behindContent).use { cs ->
                        cs.saveGraphicsState()
                        cs.transform(PdfCoords.displayMatrix(page))
                        cs.setGraphicsStateParameters(graphicsState(spec.opacity))
                        applyColor(cs, spec.color, stroking = false)
                        cs.setFont(font, 1f)
                        val textWidth = PdfFonts.widthOf(font, text, spec.fontSize)
                        if (spec.tiled) {
                            val stepX = (textWidth + spec.fontSize * 2f).coerceAtLeast(40f)
                            val stepY = (spec.fontSize * 4f).coerceAtLeast(40f)
                            var y = stepY / 2f
                            while (y < height + stepY) {
                                var x = -stepX / 2f
                                while (x < width + stepX) {
                                    showRotatedText(cs, text, x, y, spec.fontSize, spec.angleDegrees)
                                    x += stepX
                                }
                                y += stepY
                            }
                        } else {
                            val radians = Math.toRadians(spec.angleDegrees.toDouble())
                            val dx = (textWidth / 2f * Math.cos(radians)).toFloat()
                            val dy = (textWidth / 2f * Math.sin(radians)).toFloat()
                            showRotatedText(
                                cs,
                                text,
                                width / 2f - dx,
                                height / 2f + dy,
                                spec.fontSize,
                                spec.angleDegrees
                            )
                        }
                        cs.restoreGraphicsState()
                    }
                }
            }
        }

    suspend fun addPageNumbers(context: Context, file: File, spec: PageNumberSpec) =
        withContext(Dispatchers.IO) {
            PdfIo.edit(context, file) { document ->
                val font = PdfFonts.load(document, spec.style)
                val total = document.numberOfPages
                spec.pages.forEachIndexed { ordinal, index ->
                    val page = document.pageOrNull(index) ?: return@forEachIndexed
                    val width = PdfCoords.displayWidth(page)
                    val height = PdfCoords.displayHeight(page)
                    val label = PdfFonts.sanitize(
                        font,
                        spec.format
                            .replace("{page}", (spec.startNumber + ordinal).toString())
                            .replace("{total}", total.toString())
                            .replace("{index}", (index + 1).toString())
                    )
                    if (label.isEmpty()) return@forEachIndexed
                    val textWidth = PdfFonts.widthOf(font, label, spec.fontSize)
                    val x = when (spec.position) {
                        StampPosition.TOP_LEFT, StampPosition.BOTTOM_LEFT -> spec.marginPoints
                        StampPosition.TOP_RIGHT, StampPosition.BOTTOM_RIGHT ->
                            width - spec.marginPoints - textWidth

                        else -> (width - textWidth) / 2f
                    }
                    val y = when (spec.position) {
                        StampPosition.TOP_LEFT, StampPosition.TOP_CENTER, StampPosition.TOP_RIGHT ->
                            spec.marginPoints + spec.fontSize

                        StampPosition.CENTER -> height / 2f
                        else -> height - spec.marginPoints
                    }
                    contentStream(document, page, prepend = false).use { cs ->
                        cs.saveGraphicsState()
                        cs.transform(PdfCoords.displayMatrix(page))
                        applyColor(cs, spec.color, stroking = false)
                        cs.setFont(font, 1f)
                        showRotatedText(cs, label, x, y, spec.fontSize, 0f)
                        cs.restoreGraphicsState()
                    }
                }
            }
        }

    /** Places [image] inside [rect] on [pageIndex]; used for signatures and logos. */
    suspend fun stampImage(
        context: Context,
        file: File,
        pageIndex: Int,
        rect: NRect,
        image: File,
        opacity: Float = 1f
    ) = withContext(Dispatchers.IO) {
        PdfIo.edit(context, file) { document ->
            val page = document.pageOrNull(pageIndex)
                ?: throw PdfOperationException("That page no longer exists.")
            val width = PdfCoords.displayWidth(page)
            val height = PdfCoords.displayHeight(page)
            val xObject = imageFrom(document, image)
                ?: throw PdfOperationException("That image could not be read.")
            contentStream(document, page, prepend = false).use { cs ->
                cs.saveGraphicsState()
                cs.transform(PdfCoords.displayMatrix(page))
                if (opacity < 1f) cs.setGraphicsStateParameters(graphicsState(opacity))
                val box = rect.ordered()
                cs.drawImage(
                    xObject,
                    PdfCoords.imageMatrix(
                        box.left * width,
                        box.top * height,
                        box.width * width,
                        box.height * height
                    )
                )
                cs.restoreGraphicsState()
            }
        }
    }

    private fun draw(
        document: PDDocument,
        page: PDPage,
        cs: PDPageContentStream,
        annotation: PdfAnnotation,
        width: Float,
        height: Float,
        fonts: MutableMap<PdfFontStyle, PDFont>
    ) {
        when (annotation) {
            is PdfAnnotation.Ink -> {
                if (annotation.points.size < 2) return
                cs.saveGraphicsState()
                cs.setGraphicsStateParameters(graphicsState(annotation.opacity))
                applyColor(cs, annotation.color, stroking = true)
                cs.setLineWidth(annotation.strokeWidth)
                cs.setLineCapStyle(1)
                cs.setLineJoinStyle(1)
                val first = annotation.points.first()
                cs.moveTo(first.x * width, first.y * height)
                annotation.points.drop(1).forEach { point ->
                    cs.lineTo(point.x * width, point.y * height)
                }
                cs.stroke()
                cs.restoreGraphicsState()
            }

            is PdfAnnotation.Markup -> {
                val box = annotation.rect.ordered()
                val left = box.left * width
                val right = box.right * width
                val top = box.top * height
                val bottom = box.bottom * height
                cs.saveGraphicsState()
                when (annotation.kind) {
                    MarkupKind.HIGHLIGHT -> {
                        // Multiply keeps the text underneath readable.
                        cs.setGraphicsStateParameters(
                            graphicsState(annotation.opacity, BlendMode.MULTIPLY)
                        )
                        applyColor(cs, annotation.color, stroking = false)
                        cs.addRect(left, top, right - left, bottom - top)
                        cs.fill()
                    }

                    MarkupKind.UNDERLINE, MarkupKind.STRIKEOUT -> {
                        cs.setGraphicsStateParameters(graphicsState(annotation.opacity))
                        applyColor(cs, annotation.color, stroking = true)
                        val thickness = ((bottom - top) * 0.08f).coerceIn(0.8f, 3f)
                        cs.setLineWidth(thickness)
                        val y = if (annotation.kind == MarkupKind.UNDERLINE) {
                            bottom - thickness
                        } else {
                            (top + bottom) / 2f
                        }
                        cs.moveTo(left, y)
                        cs.lineTo(right, y)
                        cs.stroke()
                    }
                }
                cs.restoreGraphicsState()
            }

            is PdfAnnotation.Shape -> {
                val box = annotation.rect.ordered()
                cs.saveGraphicsState()
                cs.setGraphicsStateParameters(graphicsState(annotation.opacity))
                applyColor(cs, annotation.color, stroking = true)
                applyColor(cs, annotation.color, stroking = false)
                cs.setLineWidth(annotation.strokeWidth)
                cs.setLineCapStyle(1)
                cs.setLineJoinStyle(1)
                val left = box.left * width
                val top = box.top * height
                val right = box.right * width
                val bottom = box.bottom * height
                when (annotation.kind) {
                    ShapeKind.RECTANGLE -> {
                        cs.addRect(left, top, right - left, bottom - top)
                        if (annotation.filled) cs.fill() else cs.stroke()
                    }

                    ShapeKind.OVAL -> {
                        drawOval(cs, left, top, right, bottom)
                        if (annotation.filled) cs.fill() else cs.stroke()
                    }

                    ShapeKind.LINE -> {
                        val start = annotation.rect
                        cs.moveTo(start.left * width, start.top * height)
                        cs.lineTo(start.right * width, start.bottom * height)
                        cs.stroke()
                    }

                    ShapeKind.ARROW -> {
                        val start = annotation.rect
                        drawArrow(
                            cs,
                            start.left * width,
                            start.top * height,
                            start.right * width,
                            start.bottom * height,
                            annotation.strokeWidth
                        )
                    }
                }
                cs.restoreGraphicsState()
            }

            is PdfAnnotation.TextBox -> {
                if (annotation.text.isBlank()) return
                val font = fonts.getOrPut(annotation.style) {
                    PdfFonts.load(document, annotation.style)
                }
                val available = (width - annotation.position.x * width).coerceAtLeast(MIN_TEXT_WIDTH)
                val maxWidth = (annotation.widthFraction * width).coerceIn(MIN_TEXT_WIDTH, available)
                val lines = PdfFonts.wrap(
                    font,
                    PdfFonts.sanitize(font, annotation.text),
                    annotation.fontSize,
                    maxWidth
                )
                cs.saveGraphicsState()
                applyColor(cs, annotation.color, stroking = false)
                cs.setFont(font, 1f)
                var y = annotation.position.y * height + annotation.fontSize
                lines.forEach { line ->
                    showRotatedText(cs, line, annotation.position.x * width, y, annotation.fontSize, 0f)
                    y += annotation.fontSize * 1.25f
                }
                cs.restoreGraphicsState()
            }

            is PdfAnnotation.Note -> {
                val x = annotation.position.x * width
                val y = annotation.position.y * height
                cs.saveGraphicsState()
                applyColor(cs, annotation.color, stroking = false)
                cs.addRect(x, y, NOTE_SIZE, NOTE_SIZE)
                cs.fill()
                applyColor(cs, 0xFF000000.toInt(), stroking = true)
                cs.setLineWidth(0.8f)
                cs.addRect(x, y, NOTE_SIZE, NOTE_SIZE)
                cs.stroke()
                for (line in 1..3) {
                    val lineY = y + NOTE_SIZE * line / 4f
                    cs.moveTo(x + 3f, lineY)
                    cs.lineTo(x + NOTE_SIZE - 3f, lineY)
                }
                cs.stroke()
                cs.restoreGraphicsState()
            }

            is PdfAnnotation.Stamp -> {
                val xObject = imageFrom(document, File(annotation.imagePath)) ?: return
                val box = annotation.rect.ordered()
                cs.saveGraphicsState()
                if (annotation.opacity < 1f) {
                    cs.setGraphicsStateParameters(graphicsState(annotation.opacity))
                }
                cs.drawImage(
                    xObject,
                    PdfCoords.imageMatrix(
                        box.left * width,
                        box.top * height,
                        box.width * width,
                        box.height * height
                    )
                )
                cs.restoreGraphicsState()
            }

            is PdfAnnotation.Redaction -> {
                val box = annotation.rect.ordered()
                cs.saveGraphicsState()
                cs.setNonStrokingColor(0f, 0f, 0f)
                cs.addRect(
                    box.left * width,
                    box.top * height,
                    box.width * width,
                    box.height * height
                )
                cs.fill()
                cs.restoreGraphicsState()
            }
        }
    }

    private fun addNoteAnnotation(
        page: PDPage,
        note: PdfAnnotation.Note,
        width: Float,
        height: Float
    ) {
        runCatching {
            val annotation = PDAnnotationText()
            annotation.name = PDAnnotationText.NAME_NOTE
            annotation.contents = note.text
            annotation.titlePopup = note.author.ifBlank { "asimPDF" }
            annotation.color = pdColor(note.color)
            val topLeft = PdfCoords.displayToUser(page, note.position.x * width, note.position.y * height)
            val bottomRight = PdfCoords.displayToUser(
                page,
                note.position.x * width + NOTE_SIZE,
                note.position.y * height + NOTE_SIZE
            )
            annotation.rectangle = PDRectangle(
                minOf(topLeft[0], bottomRight[0]),
                minOf(topLeft[1], bottomRight[1]),
                Math.abs(bottomRight[0] - topLeft[0]),
                Math.abs(bottomRight[1] - topLeft[1])
            )
            page.annotations.add(annotation)
        }
    }

    private fun drawOval(
        cs: PDPageContentStream,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) {
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val rx = (right - left) / 2f
        val ry = (bottom - top) / 2f
        val k = 0.5523f
        cs.moveTo(cx - rx, cy)
        cs.curveTo(cx - rx, cy - ry * k, cx - rx * k, cy - ry, cx, cy - ry)
        cs.curveTo(cx + rx * k, cy - ry, cx + rx, cy - ry * k, cx + rx, cy)
        cs.curveTo(cx + rx, cy + ry * k, cx + rx * k, cy + ry, cx, cy + ry)
        cs.curveTo(cx - rx * k, cy + ry, cx - rx, cy + ry * k, cx - rx, cy)
        cs.closePath()
    }

    private fun drawArrow(
        cs: PDPageContentStream,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        strokeWidth: Float
    ) {
        cs.moveTo(x1, y1)
        cs.lineTo(x2, y2)
        cs.stroke()
        val angle = Math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val headLength = (strokeWidth * 5f).coerceIn(8f, 40f)
        val spread = Math.toRadians(24.0)
        val leftX = x2 - (headLength * Math.cos(angle - spread)).toFloat()
        val leftY = y2 - (headLength * Math.sin(angle - spread)).toFloat()
        val rightX = x2 - (headLength * Math.cos(angle + spread)).toFloat()
        val rightY = y2 - (headLength * Math.sin(angle + spread)).toFloat()
        cs.moveTo(x2, y2)
        cs.lineTo(leftX, leftY)
        cs.moveTo(x2, y2)
        cs.lineTo(rightX, rightY)
        cs.stroke()
    }

    private fun showRotatedText(
        cs: PDPageContentStream,
        text: String,
        x: Float,
        y: Float,
        fontSize: Float,
        angleDegrees: Float
    ) {
        if (text.isEmpty()) return
        cs.beginText()
        cs.setTextMatrix(PdfCoords.textMatrix(x, y, fontSize, angleDegrees))
        runCatching { cs.showText(text) }
        cs.endText()
    }

    internal fun contentStream(
        document: PDDocument,
        page: PDPage,
        prepend: Boolean
    ): PDPageContentStream = PDPageContentStream(
        document,
        page,
        if (prepend) PDPageContentStream.AppendMode.PREPEND else PDPageContentStream.AppendMode.APPEND,
        true,
        true
    )

    internal fun graphicsState(
        alpha: Float,
        blendMode: BlendMode? = null
    ): PDExtendedGraphicsState = PDExtendedGraphicsState().apply {
        val clamped = alpha.coerceIn(0f, 1f)
        nonStrokingAlphaConstant = clamped
        strokingAlphaConstant = clamped
        if (blendMode != null) setBlendMode(blendMode)
    }

    internal fun applyColor(cs: PDPageContentStream, argb: Int, stroking: Boolean) {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        if (stroking) cs.setStrokingColor(r, g, b) else cs.setNonStrokingColor(r, g, b)
    }

    internal fun pdColor(argb: Int): PDColor = PDColor(
        floatArrayOf(
            ((argb shr 16) and 0xFF) / 255f,
            ((argb shr 8) and 0xFF) / 255f,
            (argb and 0xFF) / 255f
        ),
        PDDeviceRGB.INSTANCE
    )

    internal fun imageFrom(document: PDDocument, file: File): PDImageXObject? {
        if (!file.canRead()) return null
        val bitmap: Bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        return runCatching {
            if (bitmap.hasAlpha()) {
                LosslessFactory.createFromImage(document, bitmap)
            } else {
                JPEGFactory.createFromImage(document, bitmap, 0.9f)
            }
        }.getOrNull()
    }

    private const val NOTE_SIZE = 18f
    private const val MIN_TEXT_WIDTH = 40f
}
