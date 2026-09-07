package com.asimzf.asimpdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.asimzf.asimpdf.util.Images
import com.asimzf.asimpdf.util.Workspace
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** How an image is laid out on the page it is placed on. */
enum class ImageFit(val label: String) {
    FIT("Fit inside page"),
    FILL("Fill page"),
    ORIGINAL("Page matches image")
}

enum class ExportImageFormat(val label: String, val extension: String) {
    PNG("PNG", "png"),
    JPEG("JPEG", "jpg")
}

/** Conversions between PDFs, images and plain text. */
object PdfConvert {

    /** Builds a PDF out of the pictures the user picked, one image per page. */
    suspend fun imagesToPdf(
        context: Context,
        images: List<Uri>,
        paper: PaperSize,
        landscape: Boolean,
        fit: ImageFit,
        marginPoints: Float,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        if (images.isEmpty()) throw PdfOperationException("Pick at least one image.")
        PdfIo.blank().use { document ->
            images.forEachIndexed { index, uri ->
                onProgress(index, images.size)
                val bitmap = Images.decode(context, uri)
                    ?: throw PdfOperationException("One of the images could not be read.")
                val pageSize = when (fit) {
                    ImageFit.ORIGINAL -> PDRectangle(
                        bitmap.width * POINTS_PER_PIXEL,
                        bitmap.height * POINTS_PER_PIXEL
                    )

                    else -> paper.rectangle(landscape)
                }
                val page = PDPage(pageSize)
                document.addPage(page)
                val image = if (bitmap.hasAlpha()) {
                    LosslessFactory.createFromImage(document, bitmap)
                } else {
                    JPEGFactory.createFromImage(document, bitmap, 0.92f)
                }
                val margin = if (fit == ImageFit.ORIGINAL) 0f else marginPoints
                val boxWidth = pageSize.width - margin * 2
                val boxHeight = pageSize.height - margin * 2
                val scale = when (fit) {
                    ImageFit.FILL -> maxOf(boxWidth / image.width, boxHeight / image.height)
                    else -> minOf(boxWidth / image.width, boxHeight / image.height)
                }
                val drawWidth = image.width * scale
                val drawHeight = image.height * scale
                val x = (pageSize.width - drawWidth) / 2f
                val y = (pageSize.height - drawHeight) / 2f
                PdfStamp.contentStream(document, page, prepend = false).use { cs ->
                    cs.drawImage(image, x, y, drawWidth, drawHeight)
                }
                bitmap.recycle()
            }
            onProgress(images.size, images.size)
            PdfIo.saveNew(context, document)
        }
    }

    /** Renders pages to image files in the app's export folder. */
    suspend fun pagesToImages(
        context: Context,
        file: File,
        pages: Collection<Int>,
        dpi: Int,
        format: ExportImageFormat,
        baseName: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<File> = withContext(Dispatchers.IO) {
        val renderer = PageRenderer(file)
        try {
            val wanted = pages.sorted().filter { it in 0 until renderer.pageCount }
            val outputs = mutableListOf<File>()
            wanted.forEachIndexed { position, index ->
                onProgress(position, wanted.size)
                val size = renderer.sizeOf(index)
                val widthPx = (size.width / 72f * dpi).toInt().coerceAtLeast(64)
                val bitmap = renderer.render(index, widthPx) ?: return@forEachIndexed
                val target = File(
                    Workspace.exportDir(context),
                    "${Workspace.baseName(baseName)}_p${index + 1}.${format.extension}"
                )
                val compressFormat = when (format) {
                    ExportImageFormat.PNG -> Bitmap.CompressFormat.PNG
                    ExportImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
                }
                Images.save(bitmap, target, compressFormat, 92)
                bitmap.recycle()
                outputs.add(target)
            }
            onProgress(wanted.size, wanted.size)
            outputs
        } finally {
            renderer.close()
        }
    }

    /** Writes the document text to a .txt file the user can share. */
    suspend fun toTextFile(context: Context, file: File, baseName: String): File =
        withContext(Dispatchers.IO) {
            val text = PdfText.extract(file)
            if (text.isBlank()) {
                throw PdfOperationException(
                    "No text found. This PDF is probably a scan, so its pages are images."
                )
            }
            val target = File(Workspace.exportDir(context), "${Workspace.baseName(baseName)}.txt")
            target.writeText(text)
            target
        }

    /**
     * Replaces the selected pages with a picture of themselves. Text, form
     * fields and anything hidden underneath a black box stop existing, which is
     * what makes a redaction final.
     */
    suspend fun flattenToImages(
        context: Context,
        file: File,
        pages: Collection<Int>,
        dpi: Int = 200,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val targetPages = pages.toSortedSet()
        if (targetPages.isEmpty()) return@withContext
        val renderer = PageRenderer(file)
        val rendered = HashMap<Int, File>()
        try {
            targetPages.forEachIndexed { position, index ->
                onProgress(position, targetPages.size)
                if (index !in 0 until renderer.pageCount) return@forEachIndexed
                val size = renderer.sizeOf(index)
                val widthPx = (size.width / 72f * dpi).toInt().coerceIn(64, 4096)
                val bitmap = renderer.render(index, widthPx) ?: return@forEachIndexed
                val target = Workspace.newWorkFile(context, ".jpg")
                Images.save(bitmap, target, Bitmap.CompressFormat.JPEG, 88)
                bitmap.recycle()
                rendered[index] = target
            }
        } finally {
            renderer.close()
        }
        PdfIo.edit(context, file) { document ->
            rendered.forEach { (index, imageFile) ->
                val original = document.pageOrNull(index) ?: return@forEach
                val width = PdfCoords.displayWidth(original)
                val height = PdfCoords.displayHeight(original)
                val replacement = PDPage(PDRectangle(width, height))
                val image = PdfStamp.imageFrom(document, imageFile) ?: return@forEach
                PdfStamp.contentStream(document, replacement, prepend = false).use { cs ->
                    cs.drawImage(image, 0f, 0f, width, height)
                }
                val tree = document.pages
                tree.insertBefore(replacement, original)
                tree.remove(original)
            }
        }
        rendered.values.forEach { it.delete() }
        onProgress(targetPages.size, targetPages.size)
    }

    private const val POINTS_PER_PIXEL = 72f / 96f
}
