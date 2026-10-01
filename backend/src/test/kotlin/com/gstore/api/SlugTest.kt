package com.gstore.api

import com.gstore.api.repositories.CategoryRepository
import com.gstore.api.util.Slug
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SlugTest {

    @Test
    fun `slugify converte nome para slug`() {
        assertEquals("space-runner", Slug.slugify("Space Runner"))
        assertEquals("jogo-acao", Slug.slugify("Jogo Ação"))
        assertEquals("corrida-3d", Slug.slugify("Corrida 3D!"))
        assertEquals("meu-jogo", Slug.slugify("  Meu Jogo  "))
    }

    @Test
    fun `slugify nunca retorna vazio`() {
        assertEquals("jogo", Slug.slugify("###"))
    }

    @Test
    fun `sanitizeVersion remove caracteres perigosos`() {
        assertEquals("1.2.3", Slug.sanitizeVersion("1.2.3"))
        assertEquals("1-0-beta", Slug.sanitizeVersion("1 0 beta/"))
        assertTrue(Slug.sanitizeVersion("..long".repeat(50)).length <= 40)
    }

    @Test
    fun `uniqueSlug adiciona sufixo quando existe`() {
        val existentes = mutableSetOf("jogo", "jogo-2")
        val result = Slug.uniqueSlug("jogo") { it in existentes }
        assertEquals("jogo-3", result)
    }

    @Test
    fun `uniqueSlug aceita primeiro disponível`() {
        val result = Slug.uniqueSlug("novo-jogo") { false }
        assertEquals("novo-jogo", result)
    }

    @Test
    fun `slugify de categoria funciona com acentos`() {
        assertEquals("estrategia", CategoryRepository.slugify("Estratégia"))
        assertEquals("acao", CategoryRepository.slugify("Ação"))
    }
}
