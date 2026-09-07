package com.asimzf.asimpdf.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/** Page geometry in PDF points, already rotated the way the page is displayed. */
data class PageSize(val width: Float, val height: Float) {
    val aspect: Float get() = if (height > 0f) width / height else 1f
}

/**
 * Rasterises pages with the platform PDF engine.
 *
 * [PdfRenderer] only allows one open page at a time and is not thread safe, so
 * every call funnels through a mutex and runs off the main thread.
 */
class PageRenderer(private val file: File) : Closeable {

    private val mutex = Mutex()
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var closed = false

    val pageCount: Int
    private val sizes: List<PageSize>

    init {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val r = PdfRenderer(pfd)
        descriptor = pfd
        renderer = r
        pageCount = r.pageCount
        sizes = (0 until r.pageCount).map { index ->
            r.openPage(index).use { page ->
                PageSize(page.width.toFloat(), page.height.toFloat())
            }
        }
    }

    fun sizeOf(index: Int): PageSize = sizes.getOrElse(index) { PageSize(612f, 792f) }

    /**
     * Renders page [index] into a bitmap [widthPx] wide. The width is clamped so
     * a deep zoom on a large page cannot exhaust the heap.
     */
    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.Default) {
        val size = sizeOf(index)
        val width = widthPx.coerceIn(MIN_WIDTH, MAX_WIDTH)
        var height = (width / size.aspect).toInt().coerceAtLeast(1)
        var scaledWidth = width
        val maxPixels = MAX_PIXELS
        if (scaledWidth.toLong() * height > maxPixels) {
            val factor = Math.sqrt(maxPixels.toDouble() / (scaledWidth.toDouble() * height))
            scaledWidth = (scaledWidth * factor).toInt().coerceAtLeast(MIN_WIDTH)
            height = (height * factor).toInt().coerceAtLeast(1)
        }
        mutex.withLock {
            val active = renderer ?: return@withLock null
            if (closed || index !in 0 until pageCount) return@withLock null
            val bitmap = try {
                Bitmap.createBitmap(scaledWidth, height, Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                return@withLock null
            }
            // PDFs are transparent by default; paper should look like paper.
            Canvas(bitmap).drawColor(Color.WHITE)
            try {
                active.openPage(index).use { page ->
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            } catch (e: Exception) {
                bitmap.recycle()
                return@withLock null
            }
            bitmap
        }
    }

    override fun close() {
        closed = true
        runCatching { renderer?.close() }
        runCatching { descriptor?.close() }
        renderer = null
        descriptor = null
    }

    companion object {
        private const val MIN_WIDTH = 32
        private const val MAX_WIDTH = 4096
        private const val MAX_PIXELS = 16_000_000L
    }
}

/** Small LRU of already rasterised pages, keyed by page index and render width. */
class PageBitmapCache(maxBytes: Int = defaultSize()) {

    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(page: Int, width: Int): Bitmap? = cache.get(key(page, width))?.takeIf { !it.isRecycled }

    fun put(page: Int, width: Int, bitmap: Bitmap) {
        cache.put(key(page, width), bitmap)
    }

    fun clear() = cache.evictAll()

    private fun key(page: Int, width: Int) = "$page@$width"

    companion object {
        fun defaultSize(): Int {
            val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
            return (maxMemory / 4).coerceAtLeast(8 * 1024) * 1024
        }
    }
}
