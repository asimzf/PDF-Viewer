package com.asimzf.asimpdf.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File

/** The type faces offered anywhere asimPDF writes text into a PDF. */
enum class PdfFontStyle(val label: String) {
    SANS("Sans"),
    SANS_BOLD("Sans Bold"),
    SERIF("Serif"),
    MONO("Mono")
}

/**
 * Text is embedded using the fonts that ship with the device, which keeps the
 * result readable everywhere without downloading anything. When no system font
 * can be embedded we fall back to the PDF standard fonts.
 */
object PdfFonts {

    private val systemFonts: Map<PdfFontStyle, List<String>> = mapOf(
        PdfFontStyle.SANS to listOf(
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/NotoSans-Regular.ttf",
            "/system/fonts/DroidSans.ttf"
        ),
        PdfFontStyle.SANS_BOLD to listOf(
            "/system/fonts/Roboto-Bold.ttf",
            "/system/fonts/NotoSans-Bold.ttf",
            "/system/fonts/DroidSans-Bold.ttf"
        ),
        PdfFontStyle.SERIF to listOf(
            "/system/fonts/NotoSerif-Regular.ttf",
            "/system/fonts/NotoSerif.ttf",
            "/system/fonts/DroidSerif-Regular.ttf"
        ),
        PdfFontStyle.MONO to listOf(
            "/system/fonts/RobotoMono-Regular.ttf",
            "/system/fonts/DroidSansMono.ttf",
            "/system/fonts/CutiveMono.ttf"
        )
    )

    private val fallbacks: Map<PdfFontStyle, PDFont> = mapOf(
        PdfFontStyle.SANS to PDType1Font.HELVETICA,
        PdfFontStyle.SANS_BOLD to PDType1Font.HELVETICA_BOLD,
        PdfFontStyle.SERIF to PDType1Font.TIMES_ROMAN,
        PdfFontStyle.MONO to PDType1Font.COURIER
    )

    fun load(document: PDDocument, style: PdfFontStyle): PDFont {
        systemFonts[style].orEmpty().forEach { path ->
            val file = File(path)
            if (file.canRead()) {
                val embedded = runCatching { PDType0Font.load(document, file) }.getOrNull()
                if (embedded != null) return embedded
            }
        }
        return fallbacks[style] ?: PDType1Font.HELVETICA
    }

    /** Drops characters the font cannot encode so showText never blows up. */
    fun sanitize(font: PDFont, text: String): String {
        val builder = StringBuilder(text.length)
        text.forEach { character ->
            val candidate = when (character) {
                '\r', '\n', '\t' -> ' '
                else -> character
            }
            val encodable = runCatching { font.encode(candidate.toString()) }.isSuccess
            builder.append(if (encodable) candidate else '?')
        }
        return builder.toString()
    }

    /** Width of [text] in points at [fontSize]. */
    fun widthOf(font: PDFont, text: String, fontSize: Float): Float =
        runCatching { font.getStringWidth(text) / 1000f * fontSize }.getOrDefault(
            text.length * fontSize * 0.5f
        )

    /** Greedy word wrap used by text boxes and stamps. */
    fun wrap(font: PDFont, text: String, fontSize: Float, maxWidth: Float): List<String> {
        if (maxWidth <= 0f) return text.split('\n')
        val lines = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            var current = StringBuilder()
            paragraph.split(' ').forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (widthOf(font, candidate, fontSize) <= maxWidth || current.isEmpty()) {
                    current = StringBuilder(candidate)
                } else {
                    lines.add(current.toString())
                    current = StringBuilder(word)
                }
            }
            lines.add(current.toString())
        }
        return lines
    }
}
