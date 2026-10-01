# Relatório técnico — Migração para Neon (v0.3.0)

Este documento resume o estado técnico do projeto após a migração completa
para a stack **Neon Auth + Neon Data API** e a remoção total da stack
anterior de autenticação/backend.

## O que mudou (resumo executivo)

| Antes (v0.2.0) | Agora (v0.3.0) |
| --- | --- |
| Login/registo por SDK de um fornecedor externo | Neon Auth (Managed Better Auth) por REST direto |
| Backend próprio (Ktor) validava JWT e expunha a API | **Sem backend** — app fala direto à Neon Data API (PostgREST) |
| API key secreta no backend | **Nenhuma secret** em lado nenhum do código |
| Perfis ligados ao id do fornecedor externo | `profiles.id` = id do utilizador Neon Auth |
| Publicação = upload de APK pela API | Admin regista o jogo + o link do APK no GitHub Releases |
| RLS inexistente | RLS ativo em **todas** as tabelas + políticas por tabela |

## Base de dados (schema `public`)

Migração aplicada: `db/migrations/V002__neon_auth_rls.sql` (idempotente).

Novo: `profiles`, `library`, `favorites`, `reviews`, vista `v_categories`,
funções `is_admin()` e `register_download()` (RPC). Removido: tabela
`users` antiga (tinha uma coluna de fornecedor externo e apenas um
utilizador de teste). Reaproveitadas sem alteração destrutiva: `games`,
`game_versions`, `categories`, `game_categories`, `game_downloads`.

### Verificação RLS (consultada ao Postgres real)

- 10/10 tabelas do schema `public` com RLS ativo (incl. `schema_migrations`,
  que fica sem políticas = bloqueada para as roles da API).
- `games`/`game_versions`/`categories`/`game_categories`: SELECT público
  (apenas `published`); escrita apenas `is_admin()`.
- `profiles`/`library`/`favorites`: cada utilizador só vê/edita as suas
  linhas (INSERT com `role='user'` forçado — impossível auto-promover a
  admin pelo app).
- `reviews`: leitura pública; escrita/apagamento só pelo autor.
- `game_downloads`: INSERT pelo próprio; SELECT só admin; nada de
  UPDATE/DELETE (analytics imutável).

### Testes de ponta a ponta executados contra o Neon real

Com token anónimo, com utilizador autenticado normal e como admin:

1. Catálogo legível **sem login** (token anónimo; rascunhos escondidos).
2. `POST /games` anónimo → bloqueado (42501).
3. Login por e-mail/senha → cookie de sessão → JWT (`set-auth-jwt`).
4. Utilizador normal: `POST`/`PATCH`/`DELETE` em `games` → bloqueado
   (0 linhas afetadas / "new row violates row-level security policy");
   biblioteca/favoritos/reviews próprios → permitidos; os dos outros →
   bloqueados.
5. RPC `register_download` → insere e incrementa o contador do jogo.
6. Erros reais preservados: `INVALID_EMAIL_OR_PASSWORD` (401),
   `USER_ALREADY_EXISTS_USE_ANOTHER_EMAIL` (422).
7. Sign-out → sessão invalidada no servidor.

## App Android

- 14 telas mantidas (design e dark mode preservados), com as adições:
  reviews (estrelas + comentário), favoritos, biblioteca com abas,
  pull-to-refresh, banner offline, área de administração com link de APK.
- Sessão: cookie (7 dias) + JWT (15 min, renovado automaticamente) +
  token anónimo (1 h) guardados no DataStore privado.
- Cache offline do catálogo (JSON nos files do app).
- Timeouts generosos (15–30 s) + reenvio automático para redes móveis lentas.
- Testes unitários: 9 (ConfigCheck 7 + Format 2), todos a passar.

## CI/CD

- `ci.yml`: worker legado (checagem de sintaxe) + Android (testes +
  assembleDebug + artefato).
- `release.yml`: na tag `app-v*` valida os 4 secrets, decodifica o keystore,
  `assembleRelease`, `apksigner verify --print-certs`, anexa o APK à
  GitHub Release e apaga o keystore temporário.
- Secrets ativos no repositório: apenas os 4 de assinatura.

## Limitações do Neon (beta) a conhecer

1. **A Data API exige JWT até para leitura** — resolvido com o token
   anónimo público (`/token/anonymous`).
2. **O cache de schema da Data API não atualiza sozinho** — depois de
   aplicar migrações é preciso "Refresh schema cache" na consola Neon
   (Postgres → Data API). As tabelas novas (`profiles`, `library`,
   `favorites`, `reviews`, vista e RPC) só ficam acessíveis depois desse
   clique.
3. **Neon Auth exige Origin confiável** — a origem `https://gstore.app`
   foi registada em `neon_auth.project_config.trusted_origins` e o app
   envia-a em todas as chamadas de autenticação.
4. **Sem MFA/passkeys** no plano gerido — e-mail+senha cobre o necessário
   para a G Store atualmente.
