package com.gstore.app.data.remote

import retrofit2.HttpException
import java.io.IOException

/**
 * Converte qualquer erro de rede/API numa mensagem legível com o ERRO REAL
 * (código + mensagem do corpo da resposta), em vez de genéricos "HTTP 403".
 *
 * Formatos conhecidos:
 *  - Data API (PostgREST): {"code":"42501","message":"permission denied for table profiles","hint":...}
 *  - Neon Auth (Better Auth): {"code":"INVALID_ORIGIN","message":"..."}
 */
object ApiErrors {

    fun readable(t: Throwable): String = when (t) {
        is ApiException -> t.message // já vem "[403/CÓDIGO] mensagem real"
        is HttpException -> readableHttp(t)
        is IOException -> "Sem ligação ao Neon (${t.message ?: "erro de rede"}). Verifique a internet e tente novamente."
        else -> t.message ?: "Erro desconhecido"
    }

    /** Lê o CORPO real da resposta de erro da Data API. */
    fun readableHttp(e: HttpException): String {
        val corpo = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val parsed = corpo?.let { runCatching { ApiClient.json.decodeFromString(ApiErrorBody.serializer(), it) }.getOrNull() }
        val codigo = parsed?.code ?: "${e.code()}"
        val mensagem = parsed?.message ?: e.message() ?: ""
        val detalhe = listOfNotNull(parsed?.hint, parsed?.details?.take(120)).joinToString(" — ")
        return buildString {
            append("HTTP ${e.code()}: [$codigo] $mensagem")
            if (detalhe.isNotBlank()) append(" ($detalhe)")
        }.take(300)
    }
}
