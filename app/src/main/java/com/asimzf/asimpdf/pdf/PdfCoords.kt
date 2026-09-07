package com.asimzf.asimpdf.pdf

import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.util.Matrix

/**
 * Bridges between what the user sees and what PDF content streams expect.
 *
 * On screen a page is upright, the origin is the top-left corner and y grows
 * downwards. In a PDF the origin is the bottom-left corner of the crop box,
 * y grows upwards, and the page may carry a /Rotate entry. Every stamping
 * operation therefore pushes [displayMatrix] onto the content stream and then
 * draws in plain "what the user sees" coordinates.
 */
object PdfCoords {

    /** Normalised page rotation, one of 0, 90, 180 or 270. */
    fun rotation(page: PDPage): Int {
        val r = page.rotation % 360
        return if (r < 0) r + 360 else r
    }

    /** Width of the page as displayed, in points. */
    fun displayWidth(page: PDPage): Float {
        val box = page.cropBox
        return if (rotation(page) % 180 == 0) box.width else box.height
    }

    /** Height of the page as displayed, in points. */
    fun displayHeight(page: PDPage): Float {
        val box = page.cropBox
        return if (rotation(page) % 180 == 0) box.height else box.width
    }

    /**
     * Maps display space (top-left origin, y down, [displayWidth] x [displayHeight])
     * onto the page's user space. Note that the mapping mirrors the y axis, so
     * text and images need the counter-flips in [textMatrix] and [imageMatrix].
     */
    fun displayMatrix(page: PDPage): Matrix {
        val box = page.cropBox
        val llx = box.lowerLeftX
        val lly = box.lowerLeftY
        val w = box.width
        val h = box.height
        return when (rotation(page)) {
            90 -> Matrix(0f, 1f, 1f, 0f, llx, lly)
            180 -> Matrix(-1f, 0f, 0f, 1f, w + llx, lly)
            270 -> Matrix(0f, -1f, -1f, 0f, w + llx, h + lly)
            else -> Matrix(1f, 0f, 0f, -1f, llx, h + lly)
        }
    }

    /** Same mapping as [displayMatrix] but applied to a single point. */
    fun displayToUser(page: PDPage, dx: Float, dy: Float): FloatArray {
        val box = page.cropBox
        val llx = box.lowerLeftX
        val lly = box.lowerLeftY
        val w = box.width
        val h = box.height
        return when (rotation(page)) {
            90 -> floatArrayOf(dy + llx, dx + lly)
            180 -> floatArrayOf(w - dx + llx, dy + lly)
            270 -> floatArrayOf(w - dy + llx, h - dx + lly)
            else -> floatArrayOf(dx + llx, h - dy + lly)
        }
    }

    /** Text matrix that keeps glyphs upright under [displayMatrix]. */
    fun textMatrix(x: Float, y: Float, fontSize: Float, degrees: Float = 0f): Matrix {
        val radians = Math.toRadians(degrees.toDouble())
        val cos = Math.cos(radians).toFloat()
        val sin = Math.sin(radians).toFloat()
        // Rotate by -degrees on screen, then mirror y so the flip cancels out.
        return Matrix(
            fontSize * cos, -fontSize * sin,
            -fontSize * sin, -fontSize * cos,
            x, y
        )
    }

    /** Image matrix that draws an image upright inside the display rect. */
    fun imageMatrix(x: Float, y: Float, width: Float, height: Float): Matrix =
        Matrix(width, 0f, 0f, -height, x, y + height)
}
