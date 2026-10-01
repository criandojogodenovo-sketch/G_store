# Arquitetura — G Store (v0.3.0)

## Visão geral

A G Store é uma loja de jogos Android que funciona **sem servidor próprio**.
Toda a infraestrutura é gerida por serviços gratuitos:

```
┌──────────────────────┐        ┌─────────────────────────────┐
│   App Android        │        │   Neon Postgres             │
│   Kotlin/Compose     │        │                             │
│   (este repositório) │        │   ┌───────────────────────┐ │
│                      │  JWT   │   │ neon_auth (Better Auth)│ │
│  ┌────────────────┐  │──────▶│   │ login/registo/sessões  │ │
│  │ Neon Auth      │  │        │   └───────────────────────┘ │
│  │ (Better Auth)  │  │        │   ┌───────────────────────┐ │
│  └────────────────┘  │        │   │ public (dados + RLS)  │ │
│                      │  JWT   │   └───────────▲───────────┘ │
│  ┌────────────────┐  │──────▶│               │             │
│  │ Neon Data API  │──┼────REST (PostgREST)────┘             │
│  └────────────────┘  │        └─────────────────────────────┘
│                      │                   ▲
│  ┌────────────────┐  │  APKs (download direto, sem token)
│  │ DownloadManager├─┼─────────────────────┘
│  └────────────────┘  │        ┌─────────────────────────────┐
└──────────────────────┘        │  GitHub                     │
                                │  · Releases = APKs dos jogos│
        ┌───────────────┐      │  · Actions = CI + release   │
        │  Admin (eu)    │─────▶│  · este repositório         │
        └───────────────┘  push└─────────────────────────────┘
```

**Componentes:**

| Componente | Tecnologia | Onde vive |
| --- | --- | --- |
| App Android | Kotlin, Jetpack Compose, Material 3 (dark mode), Retrofit, kotlinx.serialization, Coil, DataStore | `android/` |
| Autenticação | Neon Auth (Managed Better Auth): registo/login/logout por e-mail+senha | Serviço da Neon |
| Dados | Neon Data API (PostgREST sobre o Postgres) protegida por RLS | Serviço da Neon |
| APKs dos jogos | GitHub Releases (URLs guardadas na tabela `game_versions`) | GitHub |
| CI/CD | GitHub Actions: testes, build, APK assinado na tag `app-v*` | `.github/workflows/` |
| Worker legado | Script Cloudflare histórico de migração/seed (não usado pelo app) | `src/` |

Não existe backend próprio: o app fala **diretamente** com o Neon, e é o
**Postgres (RLS)** que decide o que cada pedido pode fazer.

## Autenticação (Neon Auth / Better Auth)

Fluxo no dispositivo:

1. **Registo** — `POST {AUTH_URL}/sign-up/email` `{name, email, password}`.
2. **Login** — `POST {AUTH_URL}/sign-in/email` `{email, password}`.
   Resposta: cookie `__Secure-neon-auth.session_token` (7 dias) + dados do
   utilizador. O cookie é guardado no DataStore privado do app.
3. **JWT de acesso (15 min)** — `GET {AUTH_URL}/get-session` com o cookie;
   o JWT vem no header de resposta **`set-auth-jwt`**. É este JWT que
   autoriza os pedidos à Data API (`Authorization: Bearer`).
4. **Renovação** — transparente: quando o JWT está perto de expirar, o app
   refaz o passo 3 com o cookie ainda válido.
5. **Leitura sem login** — `GET {AUTH_URL}/token/anonymous` devolve um JWT
   público de 1 h com `role=anonymous`, suficiente para ler o catálogo.
6. **Logout** — `POST {AUTH_URL}/sign-out` (invalida a sessão no servidor)
   + limpeza do DataStore.

Todas as chamadas ao Auth incluem o header `Origin: https://gstore.app`,
uma origem registada como confiável no Neon Auth. As URLs são públicas e
vêm no APK; **não existe nenhum segredo no cliente** — a autorização é
sempre decidida pelo Postgres.

## Dados (Neon Data API + RLS)

A Data API é um PostgREST gerido pela Neon. Cada pedido exige um JWT
(utilizador ou anónimo); o PostgREST troca para a role `authenticated` ou
`anonymous` e avalia as políticas RLS linha a linha.

### Tabelas (schema `public`)

| Tabela | Função |
| --- | --- |
| `games` | Catálogo de jogos (nome, slug, descrições, ícone, screenshots, versão, downloads, status) |
| `game_versions` | Versões de cada jogo com o **link do APK no GitHub Releases** |
| `categories` / `game_categories` | Categorias e relação N:N |
| `profiles` | Perfil por utilizador (id = id do Neon Auth, display_name, avatar_url, role) |
| `library` | Jogos adicionados à biblioteca do utilizador |
| `favorites` | Favoritos do utilizador |
| `reviews` | Avaliações 1–5 + comentário (1 por jogo/utilizador) |
| `game_downloads` | Registo de descargas (analytics; imutável) |
| `schema_migrations` | Controlo de migrações (bloqueada por RLS) |

Vista `v_categories` (security_invoker): categorias com contagem de jogos
publicados.

### RLS — resumo das políticas

Todas as tabelas têm RLS ativo. Nenhuma tabela fica "aberta sem política":

| Tabela | SELECT | INSERT | UPDATE | DELETE |
| --- | --- | --- | --- | --- |
| `games` | público (só `published`; admin vê tudo) | admin | admin | admin |
| `game_versions` | versões de jogos publicados (admin vê tudo) | admin | admin | admin |
| `categories` | público | admin | admin | admin |
| `game_categories` | público (dos publicados) | admin | — | admin |
| `profiles` | só a própria linha | própria linha, `role='user'` forçado | própria linha, sem poder mudar `role` | bloqueado |
| `library` | só o dono | próprio id | — | só o dono |
| `favorites` | só o dono | próprio id | — | só o dono |
| `reviews` | público | própria linha | própria linha | própria linha |
| `game_downloads` | só admin | próprio id | — | — |
| `schema_migrations` | bloqueada (sem políticas) | — | — | — |

"Admin" = linha em `profiles` com `role='admin'`, verificada pela função
`is_admin()` (SECURITY DEFINER, sem recursão de RLS). O admin é definido
diretamente no Postgres (UPDATE à mão) — não há forma de virar admin pelo app.

### RPC

- `register_download(p_game_id, p_version_id)` (SECURITY DEFINER) — insere
  em `game_downloads` com o `auth.uid()` do JWT e incrementa
  `games.downloads`. Só aceita utilizadores autenticados.

### GRANTs (equivalente manual ao "Grant public schema access")

As roles `authenticated` e `anonymous` têm USAGE nos schemas `public` e
`auth`, SELECT nas tabelas públicas e os INSERT/UPDATE/DELETE mínimos que
as políticas acima deixam passar (o RLS corta o resto). Tudo aplicado na
migração `db/migrations/V002__neon_auth_rls.sql`.

## App Android — estrutura

```
android/app/src/main/java/com/gstore/app/
├── ConfigCheck.kt            # valida as URLs públicas do Neon no arranque
├── GStoreApp.kt / MainActivity.kt
├── data/
│   ├── local/SessionStore.kt     # cookie + JWT + token anónimo (DataStore)
│   ├── remote/ApiClient.kt       # Retrofit p/ Auth API e Data API
│   ├── remote/Dtos.kt           # DTOs (Better Auth + PostgREST)
│   └── repo/GStoreRepository.kt # lógica: sessão, tokens, catálogo, RLS-friendly
└── screens/                   # 14 telas Compose (ver abaixo)
```

Telas: splash, home (destaques/recentes/pull-to-refresh/skeleton), busca
(nome + categoria + ordenação), categorias, detalhe do jogo (screenshots,
versões, reviews, favorito, download), biblioteca (baixados + favoritos),
login, registo, perfil (renomear, logout), área de administração
(adicionar/editar jogos e versões — só admin).

Comportamentos de rede:

- **Cache offline**: o último catálogo lido é gravado em JSON nos files do
  app; se a rede falhar, a loja mostra o cache com aviso "offline".
- **Dados móveis lentos**: timeouts 15–30 s + reenvio automático do OkHttp.
- **Erros reais**: login/registo/rede mostram o código e mensagem devolvidos
  pelo Neon (ex.: `INVALID_EMAIL_OR_PASSWORD`, `USER_ALREADY_EXISTS`).

## Release e CI

- `ci.yml` — push/PR no `main`: worker legado (`node --check`) + Android
  (testes unitários + assembleDebug + artefato).
- `release.yml` — tag `app-v*`: valida os 4 secrets de assinatura, decodifica
  o keystore, `assembleRelease`, `apksigner verify --print-certs`, publica o
  APK na GitHub Release da tag e apaga o keystore temporário.

Segredos usados no CI (todos de assinatura): `KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (ver `docs/SECRETS.md`).

## Limitações conhecidas do Neon (beta) e como foram tratadas

1. **A Data API exige JWT para QUALQUER pedido** (mesmo leitura pública).
   Solução adotada: token anónimo público (`/token/anonymous`, 1 h) — o
   catálogo continua legível sem login, sem expor escrita.
2. **O cache de schema da Data API não atualiza sozinho** após criar/alterar
   tabelas (o NOTIFY do PostgREST não atravessa o pooler da Neon). Depois de
   aplicar migrações é preciso clicar **"Refresh schema cache"** na consola
   Neon (Postgres → Data API) — ou `neon data-api refresh-schema` no CLI.
3. **Neon Auth exige Origin confiável** nos pedidos de registo/login. A origem
   `https://gstore.app` foi registada na configuração do projeto
   (`neon_auth.project_config.trusted_origins`) e o app envia-a em todas as
   chamadas de autenticação.
4. **JWT de 15 min sem refresh token no cliente** — renovado via get-session
   com o cookie de 7 dias (implementado no repositório).
