package com.gstore.app

import com.gstore.app.ui.components.formatDownloads
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testes unitários das funções utilitárias da UI.
 */
class FormatTest {

    @Test
    fun `formatDownloads exibe números simples`() {
        assertEquals("0", formatDownloads(0))
        assertEquals("42", formatDownloads(42))
        assertEquals("999", formatDownloads(999))
    }

    @Test
    fun `formatDownloads abrevia milhares e milhões`() {
        assertEquals("1.0k", formatDownloads(1_000))
        assertEquals("12.5k", formatDownloads(12_500))
        assertEquals("1.0M", formatDownloads(1_000_000))
    }
}
