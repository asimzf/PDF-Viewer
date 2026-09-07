package com.asimzf.asimpdf

import com.asimzf.asimpdf.model.PageRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageRangeTest {

    @Test
    fun `all keywords cover the document`() {
        assertEquals(listOf(0, 1, 2, 3), PageRange.parse("all", 4))
        assertEquals(listOf(0, 1, 2, 3), PageRange.parse("  ", 4))
        assertEquals(listOf(0, 2), PageRange.parse("odd", 4))
        assertEquals(listOf(1, 3), PageRange.parse("even", 4))
    }

    @Test
    fun `ranges and single pages are one based on the way in`() {
        assertEquals(listOf(0, 1, 2, 6), PageRange.parse("1-3, 7", 10))
        assertEquals(listOf(4, 5, 6, 7, 8, 9), PageRange.parse("5-", 10))
        assertEquals(listOf(0, 1), PageRange.parse("-2", 10))
    }

    @Test
    fun `pages outside the document are dropped`() {
        assertEquals(listOf(9), PageRange.parse("10, 42", 10))
        assertEquals(emptyList<Int>(), PageRange.parse("99", 10))
        assertFalse(PageRange.isValid("99", 10))
        assertTrue(PageRange.isValid("1", 10))
    }

    @Test
    fun `reversed ranges are read the sensible way round`() {
        assertEquals(listOf(2, 3, 4), PageRange.parse("5-3", 10))
    }

    @Test
    fun `formatting collapses runs`() {
        assertEquals("1-3, 7", PageRange.format(listOf(0, 1, 2, 6)))
        assertEquals("2", PageRange.format(listOf(1)))
        assertEquals("", PageRange.format(emptyList()))
    }
}
