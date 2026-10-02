package com.gstore.app

import com.gstore.app.data.repo.countLabel
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

    @Test
    fun `countLabel pluraliza corretamente`() {
        assertEquals("1 app", countLabel(1, "app", "apps"))
        assertEquals("2 apps", countLabel(2, "app", "apps"))
        assertEquals("1 jogo", countLabel(1, "jogo", "jogos"))
        assertEquals("2 jogos", countLabel(2, "jogo", "jogos"))
        assertEquals("1 download", countLabel(1, "download", "downloads"))
        assertEquals("0 downloads", countLabel(0, "download", "downloads"))
        assertEquals("10 downloads", countLabel(10, "download", "downloads"))
    }
}
