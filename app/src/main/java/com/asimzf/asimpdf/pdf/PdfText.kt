package com.asimzf.asimpdf.pdf

import com.asimzf.asimpdf.model.NRect
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/** One match of a search, with the boxes to draw over the page. */
data class SearchHit(
    val page: Int,
    val rects: List<NRect>,
    val snippet: String
)

/** Text extraction and in-document search, both done locally. */
object PdfText {

    suspend fun extract(file: File, pages: Collection<Int>? = null): String =
        withContext(Dispatchers.IO) {
            PdfIo.read(file) { document ->
                val stripper = PDFTextStripper()
                stripper.sortByPosition = true
                val wanted = pages?.sorted() ?: (0 until document.numberOfPages).toList()
                buildString {
                    wanted.forEach { index ->
                        if (index !in 0 until document.numberOfPages) return@forEach
                        stripper.startPage = index + 1
                        stripper.endPage = index + 1
                        append(stripper.getText(document).trimEnd())
                        append("\n\n")
                    }
                }.trimEnd()
            }
        }

    suspend fun pageText(document: PDDocument, pageIndex: Int): String =
        withContext(Dispatchers.IO) {
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            stripper.startPage = pageIndex + 1
            stripper.endPage = pageIndex + 1
            stripper.getText(document)
        }

    /**
     * Streams matches page by page so the first results show up immediately on a
     * long document.
     */
    fun search(file: File, query: String): Flow<SearchHit> = flow {
        val needle = query.trim()
        if (needle.isEmpty()) return@flow
        PdfIo.load(file).use { document ->
            for (index in 0 until document.numberOfPages) {
                currentCoroutineContext().ensureActive()
                val page = document.pageOrNull(index) ?: continue
                val width = PdfCoords.displayWidth(page).coerceAtLeast(1f)
                val height = PdfCoords.displayHeight(page).coerceAtLeast(1f)
                val stripper = PositionStripper()
                stripper.sortByPosition = true
                stripper.startPage = index + 1
                stripper.endPage = index + 1
                runCatching { stripper.getText(document) }
                val text = stripper.builder.toString()
                if (text.isEmpty()) continue
                var from = 0
                var found = text.indexOf(needle, from, ignoreCase = true)
                var guard = 0
                while (found >= 0 && guard < MAX_HITS_PER_PAGE) {
                    guard++
                    val positions = (found until found + needle.length)
                        .mapNotNull { stripper.positions.getOrNull(it) }
                    val rects = boxesFor(positions, width, height)
                    emit(
                        SearchHit(
                            page = index,
                            rects = rects,
                            snippet = snippetAround(text, found, needle.length)
                        )
                    )
                    from = found + needle.length
                    if (from >= text.length) break
                    found = text.indexOf(needle, from, ignoreCase = true)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Groups the glyph boxes into one rectangle per text line. */
    private fun boxesFor(
        positions: List<TextPosition>,
        width: Float,
        height: Float
    ): List<NRect> {
        if (positions.isEmpty()) return emptyList()
        val rows = mutableListOf<MutableList<TextPosition>>()
        positions.forEach { position ->
            val row = rows.lastOrNull()
            val reference = row?.lastOrNull()
            if (reference != null &&
                Math.abs(reference.yDirAdj - position.yDirAdj) < reference.heightDir * 0.6f
            ) {
                row.add(position)
            } else {
                rows.add(mutableListOf(position))
            }
        }
        return rows.mapNotNull { row ->
            val left = row.minOf { it.xDirAdj }
            val right = row.maxOf { it.xDirAdj + it.widthDirAdj }
            val bottom = row.maxOf { it.yDirAdj }
            val top = row.minOf { it.yDirAdj - it.heightDir }
            if (right <= left || bottom <= top) return@mapNotNull null
            NRect(
                (left / width).coerceIn(0f, 1f),
                (top / height).coerceIn(0f, 1f),
                (right / width).coerceIn(0f, 1f),
                (bottom / height).coerceIn(0f, 1f)
            )
        }
    }

    private fun snippetAround(text: String, index: Int, length: Int): String {
        val start = (index - 32).coerceAtLeast(0)
        val end = (index + length + 32).coerceAtMost(text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return prefix + text.substring(start, end).replace('\n', ' ').trim() + suffix
    }

    /**
     * Rebuilds the page text while remembering which glyph produced each
     * character, which is what turns a match index into a highlight box.
     */
    private class PositionStripper : PDFTextStripper() {

        val builder = StringBuilder()
        val positions = ArrayList<TextPosition?>()

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            textPositions.forEach { position ->
                val unicode = position.unicode ?: ""
                if (unicode.isEmpty()) return@forEach
                builder.append(unicode)
                repeat(unicode.length) { positions.add(position) }
            }
        }

        override fun writeLineSeparator() {
            builder.append('\n')
            positions.add(null)
        }

        override fun writeWordSeparator() {
            builder.append(' ')
            positions.add(null)
        }

        override fun writeParagraphSeparator() {
            builder.append('\n')
            positions.add(null)
        }
    }

    private const val MAX_HITS_PER_PAGE = 200
}
