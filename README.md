# G Store — Loja de jogos Android

Loja de jogos para Android feita em **Kotlin + Jetpack Compose** (dark mode),
com **zero servidores próprios**: autenticação e dados vivem no
**Neon** (Neon Auth + Neon Data API com RLS) e os APKs dos jogos no
**GitHub Releases**.

```
App Android ──▶ Neon Auth (login/registo) ──▶ JWT (15 min)
App Android ──▶ Neon Data API (REST/PostgREST, protegida por RLS)
App Android ──▶ GitHub Releases (download direto dos APKs)
GitHub Actions ── CI + APK assinado na tag app-v*
```

## Funcionalidades

- **Catálogo sem login**: destaques, recentes, categorias, busca por nome,
  filtro por categoria, ordenação (recentes / mais baixados).
- **Contas** (Neon Auth): registo com nome/e-mail/senha, login, logout,
  sessão guardada no dispositivo, renovação automática de token.
- **Detalhe do jogo**: ícone, screenshots, descrição, versão, tamanho,
  categoria, versões antigas com release notes.
- **Baixar**: descarrega o APK direto do GitHub Releases (com notificação
  nativa) e regista a descarga (com login).
- **Biblioteca**: jogos baixados (progresso em tempo real) + favoritos na nuvem.
- **Avaliações**: nota 1–5 + comentário, editável, por jogo/utilizador.
- **Área de administração** (só o admin): adicionar/editar jogos e versões
  (nome, slug, descrições, categoria, ícone, screenshots, versão,
  version_code, link do APK no GitHub Releases).
- **Resiliência**: cache offline do catálogo, skeleton loading,
  puxar-para-atualizar, erros reais (código + mensagem) em login/registo/rede.

## Estrutura do repositório

| Pasta | Conteúdo |
| --- | --- |
| `android/` | App Android (Kotlin, Compose, Material 3) |
| `db/migrations/` | Migrações SQL aplicadas no Neon (documentação) |
| `.github/workflows/` | `ci.yml` (testes + build) e `release.yml` (APK assinado) |
| `docs/` | Arquitetura e política de segredos |
| `src/` + `wrangler.toml` | Worker legado (histórico; não usado pelo app) |

## Desenvolvimento

```bash
cd android
gradle testDebugUnitTest   # testes unitários
gradle assembleDebug       # APK de debug
```

Configuração pública (não são segredos) em `android/gradle.properties`:
`NEON_AUTH_URL`, `NEON_DATA_API_URL`, `NEON_AUTH_ORIGIN`.

Release: push de uma tag `app-v*` → o GitHub Actions gera o APK assinado e
anexa à GitHub Release. Os segredos de assinatura estão descritos em
`docs/SECRETS.md`.

## Segurança

- Nenhum segredo no APK: as URLs do Neon são públicas e toda a autorização
  é feita por **RLS no Postgres** (cada JWT só lê/escreve o que as políticas
  permitem).
- Leitura sem login usa um **token anónimo** público (só leitura).
- Escrita no catálogo é exclusiva do admin (definido na tabela `profiles`).
- Detalhes: `docs/ARQUITETURA.md`.
