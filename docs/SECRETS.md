# Secrets necessários no GitHub

Configure em **Settings → Secrets and variables → Actions** (ou no
ambiente de deploy da API). Nenhum secret vai para o código.

## Secrets (Settings → Secrets → Actions)

| Nome | Uso | Onde obter |
|------|-----|------------|
| `DATABASE_URL` | Connection string do Neon PostgreSQL. Usada APENAS nos testes de integração do CI (se habilitados). | Console do Neon → Connection string (com `-pooler`) |
| `G_STORE_GITHUB_TOKEN` | Token GitHub (escopo `repo`) para testes de integração com Releases. O nome não pode começar com `GITHUB_` (reservado). | GitHub → Settings → Developer settings → Personal access tokens |

> O `GITHUB_TOKEN` padrão injetado pelos Actions já é usado no workflow
> `release.yml` para anexar APKs — não precisa configurar nada.

## Repository variables (Settings → Secrets → Variables)

| Nome | Valor esperado |
|------|----------------|
| `RUN_INTEGRATION_TESTS` | `true` para ligar os testes de integração no CI (exige os secrets acima). Ausente/`false` = pula os testes de integração. |

## Ambiente de produção da API (onde a API Ktor rodar)

| Variável | Descrição |
|----------|-----------|
| `DATABASE_URL` | Connection string do Neon (obrigatória) |
| `PORT` | Porta HTTP (padrão 8080) |
| `APPWRITE_ENDPOINT` | `https://cloud.appwrite.io/v1` (padrão do Appwrite Cloud) |
| `APPWRITE_PROJECT_ID` | ID do projeto no Appwrite |
| `APPWRITE_API_KEY` | API key do Appwrite (segredo; escopos: `users.read`) |
| `GITHUB_TOKEN` | PAT com escopo `repo` para criar releases/assets dos jogos |
| `GITHUB_RELEASES_REPO` | `owner/repo` onde os APKs ficam (padrão: este repositório) |
| `GITHUB_RELEASES_PRIVATE` | `true` se o repositório de releases for privado |
| `ADMIN_EMAILS` | E-mails separados por vírgula que nascem ADMIN |
| `MAX_UPLOAD_MB` | Limite de upload do APK (padrão 200) |
| `AUTH_DISABLED` | `false` sempre em produção (nunca `true`) |

## Configuração do app Android

Valores públicos (não são segredos), definidos em `android/gradle.properties`
ou via `-P` no CI:

- `GSTORE_API_BASE_URL` — URL pública da API (padrão `http://10.0.2.2:8080` para emulador)
- `APPWRITE_ENDPOINT` — endpoint do Appwrite
- `APPWRITE_PROJECT_ID` — ID do projeto Appwrite

A `APPWRITE_API_KEY` e o `GITHUB_TOKEN` NUNCA são compilados no app.
