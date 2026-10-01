/**
 * Smoke test da G Store API.
 *
 * Executa o Worker diretamente (sem o runtime do wrangler) contra o banco
 * configurado em DATABASE_URL (variável de ambiente ou .dev.vars local).
 * Valida todos os endpoints, incluindo casos de erro (404/405) e o
 * redirect de download para o GitHub Releases.
 *
 * Uso: npm test
 */
import worker from '../src/index.js';
import { loadDatabaseUrl } from './db-url.js';

const env = { DATABASE_URL: loadDatabaseUrl() };
const ctx = {
  waitUntil(promise) {
    if (promise && typeof promise.catch === 'function') promise.catch(() => {});
  },
};

let passed = 0;
let failed = 0;

function check(name, condition, extra = '') {
  if (condition) {
    passed++;
    console.log(`  [OK] ${name}`);
  } else {
    failed++;
    console.error(`  [FALHA] ${name}${extra ? ` -> ${extra}` : ''}`);
  }
}

async function call(path) {
  return worker.fetch(new Request(`https://api.g-store.test${path}`), env, ctx);
}

console.log('Executando smoke test da G Store API...\n');

// 1. Health check
{
  const res = await call('/api/health');
  const body = await res.json();
  check('GET /api/health -> status 200', res.status === 200);
  check(
    'GET /api/health -> corpo {"ok":true,"service":"g-store-api"}',
    body.ok === true && body.service === 'g-store-api',
    JSON.stringify(body)
  );
}

// 2. Lista de jogos publicados
let firstSlug = null;
{
  const res = await call('/api/games');
  const body = await res.json();
  check('GET /api/games -> status 200', res.status === 200);
  check('GET /api/games -> retorna array de jogos', Array.isArray(body.games));
  check(
    'GET /api/games -> apenas jogos "published" na lista',
    (body.games ?? []).every((g) => g.status === 'published')
  );
  check('GET /api/games -> possui jogos de exemplo', (body.games ?? []).length > 0);
  firstSlug = body.games?.[0]?.slug ?? null;
}

// 3. Jogo específico
if (firstSlug) {
  const res = await call(`/api/games/${firstSlug}`);
  const body = await res.json();
  check(`GET /api/games/${firstSlug} -> status 200`, res.status === 200);
  check('GET /api/games/:slug -> slug corresponde ao solicitado', body.game?.slug === firstSlug);
} else {
  check('GET /api/games/:slug -> teste pulado (nenhum jogo publicado)', false);
}

// 4. Jogo em rascunho NÃO pode aparecer publicamente
{
  const res = await call('/api/games/neon-racer');
  check('GET /api/games/neon-racer (rascunho) -> 404 oculto do publico', res.status === 404);
}

// 5. Jogo inexistente
{
  const res = await call('/api/games/nao-existe-123');
  check('GET /api/games/nao-existe-123 -> 404', res.status === 404);
}

// 6. Download (redirect 302 para o asset no GitHub Releases)
if (firstSlug) {
  const res = await call(`/api/games/${firstSlug}/download`);
  const location = res.headers.get('Location') ?? '';
  check(`GET /api/games/${firstSlug}/download -> status 302`, res.status === 302);
  check('Download -> header Location e URL absoluta https', location.startsWith('https://'), location);
  check('Download -> header de versao presente', !!res.headers.get('X-G-Store-Version'));
}

// 7. Download inexistente
{
  const res = await call('/api/games/nao-existe-123/download');
  check('GET /api/games/nao-existe-123/download -> 404', res.status === 404);
}

// 8. Método não permitido
{
  const res = await worker.fetch(
    new Request('https://api.g-store.test/api/games', { method: 'POST' }),
    env,
    ctx
  );
  check('POST /api/games -> 405 method_not_allowed', res.status === 405);
}

// 9. Rota desconhecida
{
  const res = await call('/api/rota-inexistente');
  check('GET /api/rota-inexistente -> 404', res.status === 404);
}

console.log(`\nResultado: ${passed} passaram, ${failed} falharam`);
process.exit(failed === 0 ? 0 : 1);
