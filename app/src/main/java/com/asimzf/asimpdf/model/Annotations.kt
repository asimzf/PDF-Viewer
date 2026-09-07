package com.asimzf.asimpdf.model

import com.asimzf.asimpdf.pdf.PdfFontStyle

/** A point on a page, 0..1 across and down the page as the user sees it. */
data class NPoint(val x: Float, val y: Float)

/** A rectangle in the same normalised, top-left based space as [NPoint]. */
data class NRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {

    val width: Float get() = right - left
    val height: Float get() = bottom - top

    /** Same rectangle with left < right and top < bottom. */
    fun ordered(): NRect = NRect(
        minOf(left, right),
        minOf(top, bottom),
        maxOf(left, right),
        maxOf(top, bottom)
    )

    fun contains(point: NPoint): Boolean {
        val r = ordered()
        return point.x in r.left..r.right && point.y in r.top..r.bottom
    }

    companion object {
        fun of(a: NPoint, b: NPoint) = NRect(a.x, a.y, b.x, b.y).ordered()
    }
}

enum class MarkupKind { HIGHLIGHT, UNDERLINE, STRIKEOUT }

enum class ShapeKind(val label: String) {
    RECTANGLE("Rectangle"),
    OVAL("Oval"),
    LINE("Line"),
    ARROW("Arrow")
}

/**
 * Everything the annotation layer can hold. Annotations live in memory while the
 * user works and are burned into the PDF when they save.
 */
sealed class PdfAnnotation {

    abstract val id: Long
    abstract val page: Int

    data class Ink(
        override val id: Long,
        override val page: Int,
        val points: List<NPoint>,
        val color: Int,
        val strokeWidth: Float,
        val opacity: Float = 1f
    ) : PdfAnnotation()

    data class Markup(
        override val id: Long,
        override val page: Int,
        val rect: NRect,
        val kind: MarkupKind,
        val color: Int,
        val opacity: Float = 0.45f
    ) : PdfAnnotation()

    data class Shape(
        override val id: Long,
        override val page: Int,
        val rect: NRect,
        val kind: ShapeKind,
        val color: Int,
        val strokeWidth: Float,
        val filled: Boolean = false,
        val opacity: Float = 1f
    ) : PdfAnnotation()

    data class TextBox(
        override val id: Long,
        override val page: Int,
        val position: NPoint,
        val text: String,
        val fontSize: Float,
        val color: Int,
        val style: PdfFontStyle = PdfFontStyle.SANS,
        val widthFraction: Float = 0.5f
    ) : PdfAnnotation()

    data class Note(
        override val id: Long,
        override val page: Int,
        val position: NPoint,
        val text: String,
        val author: String,
        val color: Int
    ) : PdfAnnotation()

    data class Stamp(
        override val id: Long,
        override val page: Int,
        val rect: NRect,
        val imagePath: String,
        val opacity: Float = 1f
    ) : PdfAnnotation()

    data class Redaction(
        override val id: Long,
        override val page: Int,
        val rect: NRect
    ) : PdfAnnotation()
}

/** Which drawing tool the annotation toolbar currently has selected. */
enum class AnnotationTool(val label: String) {
    NONE("Move"),
    INK("Draw"),
    HIGHLIGHT("Highlight"),
    UNDERLINE("Underline"),
    STRIKEOUT("Strikeout"),
    SHAPE("Shape"),
    TEXT("Text"),
    NOTE("Note"),
    SIGNATURE("Signature"),
    IMAGE("Image"),
    REDACT("Redact"),
    ERASER("Erase")
}
