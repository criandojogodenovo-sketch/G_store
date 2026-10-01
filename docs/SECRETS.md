# Secrets necessários no GitHub

Configure em **Settings → Secrets and variables → Actions** (ou no
ambiente de deploy da API). Nenhum secret vai para o código.

## Secrets (Settings → Secrets → Actions)

| Nome | Uso | Onde obter |
|------|-----|------------|
| `DATABASE_URL` | Connection string do Neon PostgreSQL. Usada APENAS nos testes de integração do CI (se habilitados). | Console do Neon → Connection string (com `-pooler`) |
| `G_STORE_GITHUB_TOKEN` | Token GitHub (escopo `repo`) para testes de integração com Releases. O nome não pode começar com `GITHUB_` (reservado). | GitHub → Settings → Developer settings → Personal access tokens |
| `APPWRITE_API_KEY` | API key do Appwrite — **segredo exclusivo do backend** (nunca vai no app Android). Usada para operações admin (ex.: smoke tests reais). | Appwrite Console → Overview → Integrations → API keys |
| `KEYSTORE_BASE64` | Keystore de assinatura de release em base64 (`base64 -w0 gstore-release.jks`). | Gerado 1x com `keytool` — ver abaixo |
| `KEYSTORE_PASSWORD` | Senha do keystore de release | Guardada pelo criador do keystore |
| `KEY_ALIAS` | Alias da chave de release (ex.: `gstore-release`) | Definido na criação do keystore |
| `KEY_PASSWORD` | Senha da chave de release | Guardada pelo criador do keystore |

> O `GITHUB_TOKEN` padrão injetado pelos Actions já é usado no workflow
> `release.yml` para anexar APKs — não precisa configurar nada.

### ⚠️ Keystore de release — regras de ouro

- O ficheiro `.jks` **nunca** é commitado (o `.gitignore` já bloqueia `*.jks`).
- **Se perder o keystore (ou as senhas), nunca mais consegue publicar
  atualizações do app com a mesma identidade** — o Android só aceita
  atualizações assinadas com a MESMA chave. Guarde o keystore e as senhas
  em pelo menos dois sítios seguros (ex.: pen + cofre de passwords).
- Gerar um novo keystore: `keytool -genkeypair -v -keystore gstore-release.jks
  -alias gstore-release -keyalg RSA -keysize 2048 -validity 10000`
- Para renovar no GitHub: `base64 -w0 gstore-release.jks` e atualizar o
  secret `KEYSTORE_BASE64`.

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
