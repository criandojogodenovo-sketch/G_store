# Relatório Técnico — Base completa do G Store

**Commit:** `9a50ec8` (push para `main`) • **Data:** 2026-10-01 • **Testes: 25/25 passando**

---

## 1. O que foi encontrado no repositório

O repositório **não estava vazio**: havia 1 commit (`1097b58 feat: initialize G Store API`) com
um backend **Cloudflare Worker (JavaScript)** conectado ao Neon, contendo:

- Endpoints somente-leitura: `GET /api/health`, `/api/games`, `/api/games/:slug`,
  `/api/games/:slug/download` (redirect 302 para o GitHub Releases);
- Scripts Node idempotentes de migração (`scripts/migrate.js`) e seed (`scripts/seed.js`)
  — 3 jogos de exemplo já no Neon (`space-runner`, `puzzle-quest`, `neon-racer`);
- Tabelas `games` e `game_versions` com índices e trigger de `updated_at`;
- Boas práticas de segurança já presentes (`.dev.vars` no `.gitignore`, zero secrets no código).

**Não existia:** app Android, autenticação/Appwrite, roles, categorias como tabela,
criação/edição de jogos via API, upload de APK via API, CI/CD, testes automatizados.

## 2. Decisão arquitetural (explicada)

O requisito definiu a stack **Kotlin + Ktor + Android nativo**. O Worker legado não suporta
o fluxo de publicação com upload de APK, roles nem integração Appwrite. Portanto o backend
canônico passou a ser `backend/` (Ktor), **preservando 100% do código legado no lugar**
(`src/index.js`, `scripts/`, `wrangler.toml` continuam funcionais contra o mesmo Neon e são
verificados no CI). Do legado foram **reutilizados**: o schema das tabelas, o padrão de resposta
`{ok:true}` e o conceito de redirect 302 para downloads.

## 3. O que foi implementado

| Área | Entregas |
|------|----------|
| API Ktor | 17 endpoints (catálogo, versões, downloads, categorias, auth/perfil, admin, health) |
| Neon PostgreSQL | Pool HikariCP + `PGSimpleDataSource`; migração SQL idempotente estende o schema legado; aplicada automaticamente no startup |
| Appwrite | Validação de JWT server-side (`X-Appwrite-JWT` em `/account`), sem duplicar autenticação; perfil local com role no Neon |
| GitHub Releases | Criação de releases `game-{slug}-v{versão}`; upload de APK/ícone/screenshots **em streaming**; metadados do asset no Neon; download por redirect 302 (ou stream com token se repo privado) |
| App Android | 14 telas em Compose/Material 3 com dark mode, skeleton loading, empty/error states, navegação inferior |
| Publicação | Developer publica jogo+APK pelo app: "Publicar" → "Enviando APK…" (progresso real) → "Processando…" → "Publicado" |
| Roles | USER / DEVELOPER / ADMIN no Neon; `ADMIN_EMAILS` promove admins; auto-elevação para developer |
| CI/CD | `ci.yml` (backend + worker legado + Android) e `release.yml` (APK na release via tag `app-v*`) |
| Testes | 25 testes: unitários (fake Appwrite/GitHub via HttpServer) + integração real com Neon e GitHub (auto-limpeza) |

## 4. Arquitetura final

```
Android (Compose) ──JWT Bearer──► API Ktor ──► Neon PostgreSQL (dados)
                                     │
                                     ├─ Appwrite (/account + X-Appwrite-JWT) — identidade
                                     └─ GitHub Releases — APKs (streaming upload / 302 download)
```

- Autenticação: fonte única = Appwrite. A API valida o JWT e mapeia para perfil/role local.
- APK nunca fica no PostgreSQL nem na memória da API (multipart → arquivo temporário → stream para o GitHub).

## 5. Estrutura de pastas (novo)

```
backend/src/main/kotlin/com/gstore/api/{Application,Module}.kt
  ├─ config/AppConfig.kt          # 100% variáveis de ambiente
  ├─ db/{Database,Migrator}.kt    # pool + migrações idempotentes (dollar-quoting safe)
  ├─ auth/AuthSupport.kt          # validação JWT Appwrite → perfil local
  ├─ models/Models.kt             # modelos + DTOs serializáveis
  ├─ repositories/{Game,Category,User,Download}Repository.kt
  ├─ services/{Appwrite,GitHub,Publish}Service.kt
  ├─ routes/{Health,Game,Version,Download,Category,Auth,Admin}Routes.kt + RouteHelpers
  └─ util/Slug.kt
backend/src/main/resources/db/migrations/V001__gstore_schema.sql
backend/src/test/kotlin/com/gstore/api/  (5 classes de teste)
android/app/src/main/java/com/gstore/app/{data,ui,screens}/…
.github/workflows/{ci,release}.yml
docs/{ARQUITETURA,SECRETS}.md • .env.example
```

## 6. Endpoints (resumo)

Públicos: `GET /api/health?deep=1`, `GET /api/games` (q/category/sort/limit/offset),
`GET /api/games/{slug}`, `GET /api/games/{id}/versions`, `GET /api/games/{slug}/download` (302),
`GET /api/categories`, `GET /api/categories/{slug}`.
Developer/Admin: `POST /api/games`, `PUT|DELETE /api/games/{id}`,
`POST /api/games/{id}/versions` (multipart: apk, icon, screenshots, version, version_code, release_notes),
`GET /api/games/{id}/downloads` (estatísticas).
Auth: `POST /api/auth/sync`, `GET|PUT /api/profile`, `POST /api/profile/become-developer`.
Admin: `GET /api/admin/games`, `GET /api/admin/users`, `PUT /api/admin/users/{id}/role`.

## 7. Tabelas do Neon

- **Legadas (preservadas):** `games`, `game_versions`
- **Novas:** `users` (perfil + role), `categories`, `game_categories` (N:N), `game_downloads` (analytics), `schema_migrations`
- **Colunas novas:** `games` (+developer_id, short_description, screenshots[], version_code);
  `game_versions` (+version_code, release_notes, apk_file_name, apk_size_bytes, apk_asset_id, release_id, release_tag, created_by)
- Índices e backfill de `game_categories` a partir da coluna legada `games.category`.

## 8. Integração Appwrite

- App: registro/login via SDK oficial (`Account.create/createEmailPasswordSession`), JWT via `account.createJWT()`.
- API: valida `Authorization: Bearer` chamando `GET {APPWRITE_ENDPOINT}/account` com `X-Appwrite-JWT` (cache 60 s).
- Sem `APPWRITE_PROJECT_ID` no app ou sem endpoint configurado, mensagens de erro claras são exibidas (nada é inventado).
- **Pendente (configuração externa):** criar o projeto no Appwrite Cloud, registrar a plataforma Android
  (package `com.gstore.app`) e configurar `APPWRITE_PROJECT_ID` (app) + `APPWRITE_API_KEY` (backend).

## 9. Integração GitHub

- Camada isolada (`GitHubService`) com base URLs injetáveis (testabilidade).
- Fluxo validado de ponta a ponta: create release → upload asset em streaming (1 MB de teste + APK real de teste) → metadados no Neon → cleanup.
- Download público: **302** para `browser_download_url` + contador + registro em `game_downloads`.
- Repositório privado: `GITHUB_RELEASES_PRIVATE=true` → stream server-side com o token (nunca exposto).

## 10. Secrets necessários

Ver **`docs/SECRETS.md`**. Resumo:

- Backend/API: `DATABASE_URL`, `APPWRITE_ENDPOINT`, `APPWRITE_PROJECT_ID`, `APPWRITE_API_KEY`,
  `GITHUB_TOKEN`, `GITHUB_RELEASES_REPO`, `GITHUB_RELEASES_PRIVATE`, `ADMIN_EMAILS`, `MAX_UPLOAD_MB`, `PORT`.
- GitHub Actions: secret `G_STORE_GITHUB_TOKEN` (opcional, testes de integração) e
  variável `RUN_INTEGRATION_TESTS=true` para ligá-los. `DATABASE_URL` nos secrets é usada só por esses testes.
- App Android (público, não-secret): `GSTORE_API_BASE_URL`, `APPWRITE_ENDPOINT`, `APPWRITE_PROJECT_ID`.

## 11. Workflow criado

- **ci.yml**: 3 jobs — `backend` (build + 25 testes; integração opcional com secrets),
  `worker-legacy` (verificação sintática do Worker), `android` (testes unitários + assembleDebug + artefato APK).
- **release.yml**: em tag `app-v*`, constrói o APK e anexa à GitHub Release (permissão `contents: write`; não destrutivo).

## 12. Testes executados e resultados

```
AppwriteServiceTest      3 testes — validação JWT (fake Appwrite)
GitHubServiceTest        3 testes — release/upload streaming/erros (fake GitHub)
InfraUnitTest            3 testes — URL JDBC, splitter SQL dollar-quoting
IntegrationApiTest      10 testes — health+db, listagem, busca, 404, categorias,
                         401 sem token, fluxo completo de publicação REAL
                         (release criada e removida no GitHub), stats, 400s
SlugTest                 6 testes — slugify/sanitize/unique
─────────────────────────────────────────────────────────────────
TOTAL: 25 testes | 0 falhas | 0 pulados (com credenciais reais)
```

Bugs encontrados e corrigidos durante os testes: driver JDBC não aceita credenciais na URL
(split user/pass no DataSource), `respondData` exigia reificação, enums precisavam de
`@SerialName` lowercase, handler `status(404)` do StatusPages sobrescrevia 404s explícitos,
usuário de dev não persistido (FK violation), header `Content-Length` restrito no HttpClient,
comment-splitting do Migrator.

## 13. Commits realizados

| Commit | Conteúdo |
|--------|----------|
| `1097b58` | (pré-existente) Worker legado inicial |
| `9a50ec8` | Base completa: backend Ktor (87 arquivos), app Android, migrações Neon, integrações Appwrite/GitHub, 25 testes, CI/CD, docs |

## 14. Pendências (configuração externa / não automática)

1. **Appwrite**: criar o projeto no Cloud, registrar plataforma Android (`com.gstore.app`),
   preencher `APPWRITE_PROJECT_ID` no app e `APPWRITE_API_KEY` no backend.
2. **Deploy da API**: escolher hospedagem (Railway/Fly.io/VPS etc.) e configurar as env vars do item 10 —
   o CI **não** faz deploy automático (evitado por segurança, conforme solicitado).
3. **Secrets no GitHub** (opcionais, p/ integração no CI): `DATABASE_URL`, `G_STORE_GITHUB_TOKEN`, `RUN_INTEGRATION_TESTS=true`.
4. **Segurança do token**: o GitHub PAT e as credenciais fornecidas em texto no chat já foram
   expostas fora do ambiente seguro — recomenda-se **rotacioná-los** após configurar os secrets.

## 15. Próximos passos recomendados

1. Criar projeto no Appwrite e conectar o app (desbloqueia login/publish end-to-end no app real).
2. Fazer deploy da API e apontar `GSTORE_API_BASE_URL` para o domínio público (HTTPS).
3. Ativar `RUN_INTEGRATION_TESTS=true` + secrets no GitHub para CI completo.
4. Rodar o app no Android Studio (emulador: API em `http://10.0.2.2:8080`).
5. Evoluções: painel ADMIN completo, ratings/reviews, paginação cursória, cache HTTP no app,
   screenshots/ícone no fluxo Publish (endpoints já aceitam), atualizações incrementais de APK.
