# Arquitetura G Store

```
Android (Kotlin + Compose)
        │
        │  HTTPS/HTTP (JSON)
        ▼
API Ktor (backend/)                       Appwrite (auth)
   │    │    │                          login/registro no app
   │    │    └── Appwrite ── valida JWT (X-Appwrite-JWT)
   │    │
   │    └────── GitHub Releases ── APKs (redirect 302 / stream)
   │
   └─────────── Neon PostgreSQL (dados)
```

## Regra de ouro

- **Autenticação**: única fonte de verdade é o **Appwrite**. O app faz
  login/registro pelo SDK oficial do Appwrite e envia o JWT à API
  (`Authorization: Bearer <jwt>`). A API valida o JWT chamando
  `GET {endpoint}/account` com o header `X-Appwrite-JWT` e mapeia o
  usuário para um perfil local no Neon (`users`), onde mora o **papel**
  (`user` / `developer` / `admin`).
- **APKs**: nunca armazenados no PostgreSQL nem em memória da API.
  O developer envia o APK pelo app → a API cria a GitHub Release
  (tag `game-{slug}-v{versão}`) → upload do asset **em streaming** →
  metadados (asset id, tamanho, tag) são registrados no Neon.
  Download público = redirect 302 para o asset; repositório privado =
  stream com token server-side (`GITHUB_RELEASES_PRIVATE=true`).
- **Nenhuma secret no cliente Android.** Endpoint/project id do Appwrite
  e URL da API são as únicas configurações do app.

## Estrutura de pastas

```
G_store/
├── backend/                  # API Ktor (canônica)
│   ├── src/main/kotlin/com/gstore/api/
│   │   ├── Application.kt    # entrada: plugins, CORS, StatusPages, rotas
│   │   ├── Module.kt         # injeção manual de dependências
│   │   ├── config/           # AppConfig (100% variáveis de ambiente)
│   │   ├── db/               # HikariCP + PGSimpleDataSource, Migrator
│   │   ├── auth/             # AuthSupport (validação JWT Appwrite)
│   │   ├── models/           # modelos + DTOs (kotlinx.serialization)
│   │   ├── repositories/     # acesso JDBC puro ao Neon
│   │   ├── services/         # AppwriteService, GitHubService, PublishService
│   │   ├── routes/           # endpoints REST
│   │   └── util/             # slug/version helpers
│   ├── src/main/resources/db/migrations/  # SQL idempotente (V001...)
│   └── src/test/kotlin/      # testes unitários + integração
├── android/                  # app Android nativo (Compose, dark mode)
│   └── app/src/main/java/com/gstore/app/
│       ├── data/remote/      # Retrofit + kotlinx.serialization
│       ├── data/local/       # DataStore (sessão)
│       ├── data/repo/        # GStoreRepository, DownloadRepository
│       ├── ui/theme|components/
│       └── screens/          # splash/home/search/categories/details/
│                             # library/auth/profile/developer
├── .github/workflows/        # CI (backend+worker+android) e release
├── src/ + scripts/ + wrangler.toml   # Worker legado (preservado)
├── docs/                     # SECRETS.md, ARQUITECTURE.md
└── .env.example              # nomes das variáveis (sem valores)
```

## Decisões principais

| Decisão | Motivo |
|---------|--------|
| Ktor no lugar do Worker para a API canônica | Requisito do produto: autenticação, roles, upload de APK e workflow completo não cabem no Worker legado (que continua no repo, funcional) |
| JDBC puro + HikariCP (sem ORM) | Controle total do SQL, menos dependências, compatível com pooler do Neon |
| Migrações SQL embutidas e idempotentes | Startup seguro, sem serviço externo de migração; preserva tabelas legadas |
| Roles no Neon (não no Appwrite) | Papéis são dados de aplicação; o Appwrite continua sendo a identidade |
| Upload em streaming (multipart → arquivo temporário → GitHub) | APK nunca carregado inteiro na memória; compatível com o pooler e limites de memória |
| Download por redirect 302 | Sem custo de banda na API; o contador continua sendo registrado |
