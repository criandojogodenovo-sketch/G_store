package com.gstore.api.util

/**
 * Utilidades de slug/versão usadas na publicação de jogos.
 */
object Slug {

    fun slugify(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("[áàãâä]"), "a")
        .replace(Regex("[éèêë]"), "e")
        .replace(Regex("[íìîï]"), "i")
        .replace(Regex("[óòõôö]"), "o")
        .replace(Regex("[úùûü]"), "u")
        .replace("ç", "c")
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(80)
        .ifEmpty { "jogo" }

    /** Garante slug único consultando o repositório (sufixos -2, -3, ...). */
    fun uniqueSlug(base: String, exists: (String) -> Boolean): String {
        var candidate = base
        var n = 2
        while (exists(candidate)) {
            candidate = "$base-$n"
            n++
            if (n > 100) return "$base-${java.util.UUID.randomUUID().toString().take(8)}"
        }
        return candidate
    }

    /** Normaliza versão para uso em tags/assets (remove espaços e caracteres perigosos). */
    fun sanitizeVersion(value: String): String = value
        .trim()
        .replace(Regex("[^A-Za-z0-9._+-]"), "-")
        .trim('-')
        .take(40)
        .ifEmpty { "0.0.0" }
}
