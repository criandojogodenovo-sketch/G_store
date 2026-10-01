# Segredos — G Store (v0.3.0)

## Princípio geral

O app Android **não contém nenhum segredo**. As URLs do Neon (Auth e Data
API) e a origem confiável são dados públicos — vão dentro de qualquer APK e
não permitem nenhuma operação privilegiada: toda a autorização é decidida no
servidor pelas políticas RLS do Postgres, que avaliam o JWT de cada pedido.

## Secrets do GitHub Actions (Settings → Secrets and variables → Actions)

Usados **apenas** para assinar o APK de release (`release.yml`):

| Secret | Para quê | Como obter |
| --- | --- | --- |
| `KEYSTORE_BASE64` | Keystore PKCS12 em base64 (decodificado no runner e apagado no fim do job) | `base64 -w0 gstore-release.jks` |
| `KEYSTORE_PASSWORD` | Senha do keystore | Definida ao criar o keystore |
| `KEY_ALIAS` | Alias da chave de assinatura | Definido ao criar o keystore |
| `KEY_PASSWORD` | Senha da chave | Definida ao criar o keystore |

Não existe mais nenhum secret no repositório. (Os antigos secret de
autenticação e de ligação à BD foram removidos — o backend que os usava
foi apagado e a migração da BD é feita à mão pelo owner.)

## Valores públicos do app (android/gradle.properties)

| Propriedade | Exemplo | Nota |
| --- | --- | --- |
| `NEON_AUTH_URL` | `https://ep-xxx.neonauth.<região>.aws.neon.tech/neondb/auth` | Neon Console → Auth |
| `NEON_DATA_API_URL` | `https://ep-xxx.apirest.<região>.aws.neon.tech/neondb/rest/v1` | Neon Console → Data API |
| `NEON_AUTH_ORIGIN` | `https://gstore.app` | Tem de estar nos *trusted origins* do Neon Auth |

## O que NUNCA vai para o Git nem para o APK

- `DATABASE_URL` (connection string do Postgres) — usada apenas por quem
  aplica migrações/gerencia a BD à mão.
- Token pessoal do GitHub (`ghp_...`) — apenas para administração local.
- As 4 senhas de assinatura (ficam só nos secrets do GitHub + backup
  pessoal do keystore).
