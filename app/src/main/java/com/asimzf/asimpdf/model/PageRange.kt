package com.asimzf.asimpdf.model

/**
 * Parses the page selection syntax people expect from a PDF tool:
 * "1-3, 5, 9-" and "all", with 1-based input and 0-based output.
 */
object PageRange {

    fun parse(input: String, pageCount: Int): List<Int> {
        if (pageCount <= 0) return emptyList()
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.equals("all", ignoreCase = true) || trimmed == "*") {
            return (0 until pageCount).toList()
        }
        if (trimmed.equals("odd", ignoreCase = true)) {
            return (0 until pageCount).filter { it % 2 == 0 }
        }
        if (trimmed.equals("even", ignoreCase = true)) {
            return (0 until pageCount).filter { it % 2 == 1 }
        }
        val result = LinkedHashSet<Int>()
        trimmed.split(',', ';').forEach { rawPart ->
            val part = rawPart.trim()
            if (part.isEmpty()) return@forEach
            when {
                part.contains('-') -> {
                    val from = part.substringBefore('-').trim()
                    val to = part.substringAfter('-').trim()
                    val start = from.toIntOrNull() ?: 1
                    val end = to.toIntOrNull() ?: pageCount
                    val lo = minOf(start, end).coerceIn(1, pageCount)
                    val hi = maxOf(start, end).coerceIn(1, pageCount)
                    for (page in lo..hi) result.add(page - 1)
                }

                else -> part.toIntOrNull()
                    ?.takeIf { it in 1..pageCount }
                    ?.let { result.add(it - 1) }
            }
        }
        return result.sorted()
    }

    fun isValid(input: String, pageCount: Int): Boolean = parse(input, pageCount).isNotEmpty()

    /** Turns 0-based indices back into the compact "1-3, 7" form. */
    fun format(pages: Collection<Int>): String {
        if (pages.isEmpty()) return ""
        val sorted = pages.sorted()
        val parts = mutableListOf<String>()
        var start = sorted.first()
        var previous = start
        for (page in sorted.drop(1)) {
            if (page == previous + 1) {
                previous = page
                continue
            }
            parts.add(rangeText(start, previous))
            start = page
            previous = page
        }
        parts.add(rangeText(start, previous))
        return parts.joinToString(", ")
    }

    private fun rangeText(start: Int, end: Int): String =
        if (start == end) "${start + 1}" else "${start + 1}-${end + 1}"
}
