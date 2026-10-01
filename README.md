# G Store API

Backend da **G Store** (loja de jogos para Android), implementado como
**Cloudflare Worker** conectado ao **Neon PostgreSQL**.

## Arquitetura

| Componente          | Tecnologia          | Papel                                              |
| ------------------- | ------------------- | -------------------------------------------------- |
| App Android         | APK (cliente)       | Descobrir e baixar jogos                           |
| API/Backend         | Cloudflare Worker   | Este repositório (`src/index.js`)                  |
| Banco de dados      | Neon PostgreSQL     | Catálogo de jogos e versões                        |
| Hospedagem de APKs  | GitHub Releases     | Assets das releases (o Worker apenas redireciona)  |

> Cloudflare R2 **não** é utilizado nesta fase. Nenhum APK é armazenado
> ou processado pelo Worker — ele apenas redireciona o cliente para o
> asset hospedado no GitHub Releases.

## Endpoints

| Método | Rota                          | Descrição                                                             |
| ------ | ----------------------------- | --------------------------------------------------------------------- |
| GET    | `/api/health`                 | Health check → `{"ok":true,"service":"g-store-api"}`                  |
| GET    | `/api/games`                  | Lista os jogos com `status = 'published'`                             |
| GET    | `/api/games/:slug`            | Detalhes de um jogo publicado (404 se não existir ou for rascunho)    |
| GET    | `/api/games/:slug/download`   | **302** para o APK mais recente no GitHub Releases + contador         |

Exemplos:

```bash
curl https://<worker-domain>/api/health
curl https://<worker-domain>/api/games
curl -i https://<worker-domain>/api/games/space-runner/download
```

Respostas seguem o formato `{ "ok": true, ... }` / `{ "ok": false, "error": "..." }`.

## Estrutura do projeto

```
G_store/
├── src/
│   └── index.js          # Cloudflare Worker (rotas da API)
├── scripts/
│   ├── db-url.js         # Carrega DATABASE_URL (env ou .dev.vars) sem expor segredos
│   ├── migrate.js        # Cria tabelas, índices e trigger (idempotente)
│   ├── seed.js           # Insere jogos de exemplo (idempotente)
│   └── smoke-test.mjs    # Testa todos os endpoints contra o banco real
├── .dev.vars.example     # Modelo das variáveis locais (sem valores reais)
├── .gitignore            # Ignora .dev.vars, .env, node_modules, .wrangler, dist
├── package.json
└── wrangler.toml         # Configuração do Worker (sem segredos)
```

## Banco de dados (Neon PostgreSQL)

### Tabela `games`

| Coluna         | Tipo        | Observação                                        |
| -------------- | ----------- | ------------------------------------------------- |
| `id`           | UUID PK     | Gerado automaticamente (`gen_random_uuid()`)      |
| `name`         | TEXT        | Nome de exibição                                  |
| `slug`         | TEXT UNIQUE | Identificador na URL (`space-runner`)             |
| `description`  | TEXT        | Descrição do jogo                                 |
| `developer`    | TEXT        | Estúdio/desenvolvedor                             |
| `version`      | TEXT        | Versão mais recente                               |
| `category`     | TEXT        | Categoria (Arcade, Puzzle, ...)                   |
| `icon_url`     | TEXT        | URL do ícone                                      |
| `download_url` | TEXT        | URL direta do APK mais recente                    |
| `downloads`    | BIGINT      | Contador de downloads (incrementado pela API)     |
| `status`       | TEXT        | `draft` \| `published` \| `unpublished`           |
| `created_at`   | TIMESTAMPTZ | Preenchido automaticamente                        |
| `updated_at`   | TIMESTAMPTZ | Atualizado por trigger em qualquer UPDATE         |

### Tabela `game_versions`

| Coluna       | Tipo        | Observação                                    |
| ------------ | ----------- | --------------------------------------------- |
| `id`         | UUID PK     | Gerado automaticamente                        |
| `game_id`    | UUID FK     | Referência a `games(id)`, `ON DELETE CASCADE` |
| `version`    | TEXT        | Único por jogo (`UNIQUE (game_id, version)`)  |
| `apk_url`    | TEXT        | URL do asset no GitHub Releases               |
| `created_at` | TIMESTAMPTZ | Usada para ordenar a versão mais recente      |

Índices: `idx_games_slug`, `idx_games_status`, `idx_game_versions_game_id`,
`idx_game_versions_created_at`.

## Como rodar localmente

```bash
npm install

# 1. Configure a credencial local (NUNCA comite o .dev.vars)
cp .dev.vars.example .dev.vars
#   edite .dev.vars e preencha DATABASE_URL com sua connection string do Neon

# 2. Crie as tabelas e (opcional) insira jogos de exemplo
npm run db:migrate
npm run db:seed

# 3. Rode o Worker localmente (http://localhost:8787)
npm run dev

# 4. Valide todos os endpoints
npm test

# 5. Verifique o build do Worker (dry-run, sem deploy)
npm run build
```

## Segurança

- A connection string do Neon é **secret** (`DATABASE_URL`) e nunca aparece
  em código, README, logs ou respostas da API.
- No Cloudflare: `npx wrangler secret put DATABASE_URL` (não usar `[vars]`).
- Localmente: arquivo `.dev.vars` (ignorado pelo `.gitignore`).
- Tokens do GitHub, API keys e senhas seguem a mesma regra: **apenas
  variáveis/secrets de ambiente**.
- Os endpoints públicos expõem apenas dados de catálogo (nenhum dado sensível).

## Publicando um jogo (fluxo GitHub Releases)

1. Crie uma **Release** no repositório e anexe o `.apk` como asset.
2. Insira/atualize o jogo no banco (scripts de inserção virão na fase de
   painel administrativo):

```sql
INSERT INTO games (name, slug, description, developer, version, category, icon_url, download_url, status)
VALUES ('Meu Jogo', 'meu-jogo', 'Descrição', 'Dev', '1.0.0', 'Arcade',
        'https://.../icon.png',
        'https://github.com/<org>/<repo>/releases/download/v1.0.0/meu-jogo-1.0.0.apk',
        'published');

INSERT INTO game_versions (game_id, version, apk_url)
SELECT id, '1.0.0',
       'https://github.com/<org>/<repo>/releases/download/v1.0.0/meu-jogo-1.0.0.apk'
FROM games WHERE slug = 'meu-jogo';
```

3. A API passa a expor o jogo imediatamente (`/api/games`) e o endpoint
   `/api/games/meu-jogo/download` redireciona para o asset da release.

## Status da fase

- [x] Worker com `/api/health`, `/api/games`, `/api/games/:slug`
- [x] Estrutura de `/api/games/:slug/download` (redirect para GitHub Releases)
- [x] Migrações do Neon (tabelas, índices, trigger)
- [x] Seed de teste e smoke tests
- [ ] Deploy no Cloudflare (próxima etapa — requer `wrangler secret put DATABASE_URL`)
