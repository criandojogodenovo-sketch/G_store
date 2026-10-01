# G Store

Loja de jogos para Android: **app nativo (Kotlin + Compose)**, **API em
Kotlin/Ktor**, **Neon PostgreSQL**, **Appwrite (autenticação)** e
**GitHub Releases (hospedagem de APKs)**.

> Documentação detalhada: [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md) •
> [`docs/SECRETS.md`](docs/SECRETS.md)

## Componentes

| Componente | Tecnologia | Pasta |
|------------|------------|-------|
| App Android | Kotlin, Jetpack Compose, Material 3 (dark mode), Retrofit, Appwrite SDK | `android/` |
| API (canônica) | Kotlin, Ktor, JDBC/HikariCP, kotlinx.serialization | `backend/` |
| Banco de dados | Neon PostgreSQL (migrações idempotentes no startup) | `backend/src/main/resources/db/migrations/` |
| Autenticação | Appwrite (login/registro no app; API valida o JWT) | — |
| APKs | GitHub Releases (upload em streaming pela API; download por 302) | — |
| Worker legado | Cloudflare Worker (preservado do estágio inicial; segue funcionando) | `src/index.js` |

## Endpoints da API

| Método | Rota | Autenticação | Descrição |
|--------|------|--------------|-----------|
| GET | `/api/health` (`?deep=1` valida o Neon) | pública | Health check |
| GET | `/api/games?q=&category=&sort=&limit=&offset=` | pública | Lista jogos publicados |
| GET | `/api/games/{slug}` | pública | Detalhes de um jogo |
| GET | `/api/games/{id}/versions` | pública | Histórico de versões |
| GET | `/api/games/{id}/downloads` | developer dono / admin | Estatísticas de download |
| POST | `/api/games` | developer/admin | Cria jogo (rascunho) |
| PUT | `/api/games/{id}` | developer dono / admin | Edita jogo |
| DELETE | `/api/games/{id}` | developer dono / admin | Remove jogo |
| POST | `/api/games/{id}/versions` | developer dono / admin | **Publica versão (multipart: apk, icon, screenshots)** |
| GET | `/api/games/{slug}/download` | pública | **302** para o APK mais recente + contador |
| GET | `/api/categories` / `/api/categories/{slug}` | pública | Categorias |
| POST | `/api/auth/sync` | usuário (JWT Appwrite) | Valida JWT e cria/atualiza perfil local |
| GET/PUT | `/api/profile` | usuário | Perfil |
| POST | `/api/profile/become-developer` | usuário | Auto-elevação para developer |
| GET/PUT | `/api/admin/users…`, `/api/admin/games` | admin | Gerenciamento (base futura) |

Respostas: `{ "ok": true, ... }` / `{ "ok": false, "error": "...", "message": "..." }`.

## Como rodar localmente

### API (Ktor)

```bash
cd backend
export DATABASE_URL="postgresql://usuario:senha@endpoint-pooler.neon.tech/neondb?sslmode=require"
export GITHUB_TOKEN="pat-com-escopo-repo"        # publicação de APKs
export APPWRITE_ENDPOINT="https://cloud.appwrite.io/v1"
export APPWRITE_PROJECT_ID="seu-project-id"
export ADMIN_EMAILS="voce@exemplo.com"           # papel admin no primeiro login
gradle run
# API em http://localhost:8080 — migrações aplicadas no startup
```

Desenvolvimento sem Appwrite configurado: `AUTH_DISABLED=true` cria um
usuário admin local (NUNCA usar em produção).

### App Android

Abra `android/` no Android Studio, ajuste `gradle.properties`:

```
GSTORE_API_BASE_URL=http://10.0.2.2:8080   # emulador aponta para localhost
APPWRITE_ENDPOINT=https://cloud.appwrite.io/v1
APPWRITE_PROJECT_ID=seu-project-id
```

No Appwrite Console, registre a plataforma Android do app
(package `com.gstore.app`) para as sessões funcionarem.

### Testes

```bash
cd backend
gradle test                                  # unitários (sem segredos)
DATABASE_URL=... GITHUB_TEST_TOKEN=... gradle test --rerun-tasks   # + integração
```

Os testes de integração rodam contra o Neon/GitHub reais e se auto-limpam;
sem as variáveis, são pulados automaticamente.

## Fluxo de publicação (developer)

1. Perfil → "Quero publicar jogos" (vira `developer`).
2. Developer Dashboard → **Publicar novo jogo**.
3. Preenche nome/descrição/categoria/versão/notes e **seleciona o APK**.
4. "Enviando APK..." (progresso real) → "Processando..." → "Publicado".
5. A API criou a Release `game-{slug}-v{versão}`, subiu o APK como asset,
   registrou `game_versions` no Neon e publicou o jogo na loja.

O developer nunca abre o GitHub; o GitHub token vive só no backend.

## Segurança

- Nenhuma credencial em código ou arquivos versionados (ver
  [`.env.example`](.env.example) e [`docs/SECRETS.md`](docs/SECRETS.md)).
- O app Android não recebe `GITHUB_TOKEN` nem `APPWRITE_API_KEY`.
- A API nunca imprime segredos em logs (ver `logback.xml` e handlers).

## CI/CD

- `.github/workflows/ci.yml` — build + testes do backend, verificação do
  Worker legado e build APK do Android a cada push/PR.
- `.github/workflows/release.yml` — ao criar tag `app-v*`, anexa o APK do
  app à GitHub Release (estrutura pronta para releases futuras).

## Status

- [x] API Ktor com catálogo, versões, downloads, categorias, auth/roles
- [x] Migrações do Neon (extensão do schema legado, sem quebrar nada)
- [x] Integração Appwrite (JWT validado server-side, sem duplicar auth)
- [x] Integração GitHub Releases (upload em streaming + redirect)
- [x] App Android completo (14 telas, dark mode, skeleton/empty/error)
- [x] Publicação de jogos com APK direto do app
- [x] Testes automatizados (unitários + integração real com limpeza)
- [x] Workflows de CI e release
- [ ] Deploy da API em ambiente de produção (requer hospedagem escolhida)
- [ ] Projeto Appwrite criado/registrado (configuração externa manual)
- [ ] Painel ADMIN completo (base já preparada nas rotas)
