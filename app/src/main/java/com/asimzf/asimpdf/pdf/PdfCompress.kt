package com.asimzf.asimpdf.pdf

import android.content.Context
import com.asimzf.asimpdf.util.Images
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** How hard to squeeze; the labels are what the tool screen shows. */
enum class CompressionLevel(
    val label: String,
    val maxImageDimension: Int,
    val jpegQuality: Float,
    val description: String
) {
    LIGHT("Light", 2200, 0.85f, "Barely visible change, modest saving"),
    BALANCED("Balanced", 1600, 0.7f, "Good for sharing and email"),
    STRONG("Strong", 1100, 0.55f, "Smallest file, softer images")
}

data class CompressionResult(val beforeBytes: Long, val afterBytes: Long) {
    val savedBytes: Long get() = (beforeBytes - afterBytes).coerceAtLeast(0)
    val savedPercent: Int
        get() = if (beforeBytes <= 0) 0 else ((savedBytes * 100) / beforeBytes).toInt()
}

/**
 * Shrinks a document by re-encoding the pictures inside it. Text, vectors and
 * the page structure are left alone.
 */
object PdfCompress {

    suspend fun compress(
        context: Context,
        file: File,
        level: CompressionLevel,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): CompressionResult = withContext(Dispatchers.IO) {
        val before = file.length()
        PdfIo.edit(context, file) { document ->
            // Keyed by the underlying stream so an image shared by many pages is
            // re-encoded once and then reused.
            val processed = HashMap<Any, PDImageXObject>()
            val total = document.numberOfPages
            for (index in 0 until total) {
                onProgress(index, total)
                val page = document.pageOrNull(index) ?: continue
                shrinkResources(document, page.resources, level, processed, depth = 0)
            }
            onProgress(total, total)
        }
        CompressionResult(before, file.length())
    }

    private fun shrinkResources(
        document: PDDocument,
        resources: PDResources?,
        level: CompressionLevel,
        processed: MutableMap<Any, PDImageXObject>,
        depth: Int
    ) {
        if (resources == null || depth > MAX_DEPTH) return
        resources.xObjectNames.toList().forEach { name ->
            val xObject = runCatching { resources.getXObject(name) }.getOrNull() ?: return@forEach
            when (xObject) {
                is PDFormXObject ->
                    shrinkResources(document, xObject.resources, level, processed, depth + 1)

                is PDImageXObject -> {
                    val key: Any = xObject.cosObject
                    val cached = processed[key]
                    if (cached != null) {
                        resources.put(name, cached)
                        return@forEach
                    }
                    // Transparency and stencil masks do not survive a JPEG round trip.
                    if (xObject.isStencil || xObject.softMask != null || xObject.mask != null) {
                        return@forEach
                    }
                    if (maxOf(xObject.width, xObject.height) < MIN_DIMENSION) return@forEach
                    val bitmap = runCatching { xObject.image }.getOrNull() ?: return@forEach
                    val scaled = Images.scaleTo(bitmap, level.maxImageDimension)
                    val replacement = runCatching {
                        JPEGFactory.createFromImage(document, scaled, level.jpegQuality)
                    }.getOrNull()
                    if (replacement != null) {
                        processed[key] = replacement
                        resources.put(name, replacement)
                    }
                    if (scaled !== bitmap) scaled.recycle()
                    bitmap.recycle()
                }

                else -> Unit
            }
        }
    }

    private const val MAX_DEPTH = 3
    private const val MIN_DIMENSION = 200
}
