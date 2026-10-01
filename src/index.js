/**
 * G Store API — Cloudflare Worker
 *
 * Backend inicial da G Store (loja de jogos para Android).
 * - Neon PostgreSQL como banco de dados (driver serverless @neondatabase/serverless)
 * - APKs hospedados no GitHub Releases: o Worker apenas REDIRECIONA o cliente
 *   para o asset correspondente (nunca armazena ou carrega o APK na memória).
 *
 * Segurança:
 * - A connection string do Neon vem do secret `DATABASE_URL` (nunca no código).
 * - Nenhum valor sensível é retornado pela API.
 */

import { neon } from '@neondatabase/serverless';

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type',
};

/** Resposta JSON padronizada. */
function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json; charset=utf-8', ...CORS_HEADERS },
  });
}

/** Obtém o client SQL do Neon a partir do secret DATABASE_URL. */
function getSql(env) {
  if (!env || !env.DATABASE_URL) {
    throw new Error(
      'DATABASE_URL nao configurada (use `wrangler secret put DATABASE_URL` ou o arquivo .dev.vars local).'
    );
  }
  return neon(env.DATABASE_URL);
}

/** decodeURIComponent protegido contra strings malformadas. */
function safeDecode(value) {
  try {
    return decodeURIComponent(value);
  } catch {
    return null;
  }
}

/** GET /api/games — lista de jogos publicados. */
async function handleListGames(env) {
  const sql = getSql(env);
  const games = await sql`
    SELECT id, name, slug, description, developer, version, category,
           icon_url, download_url, downloads, status, created_at, updated_at
    FROM games
    WHERE status = 'published'
    ORDER BY created_at DESC
  `;
  return json({ ok: true, count: games.length, games });
}

/** GET /api/games/:slug — informações de um jogo publicado específico. */
async function handleGetGame(env, slug) {
  const sql = getSql(env);
  const games = await sql`
    SELECT id, name, slug, description, developer, version, category,
           icon_url, download_url, downloads, status, created_at, updated_at
    FROM games
    WHERE slug = ${slug} AND status = 'published'
    LIMIT 1
  `;
  if (games.length === 0) {
    return json({ ok: false, error: 'game_not_found' }, 404);
  }
  return json({ ok: true, game: games[0] });
}

/**
 * GET /api/games/:slug/download
 * Localiza a versão mais recente publicada do jogo e REDIRECIONA (302)
 * o cliente para o asset do APK hospedado no GitHub Releases.
 * O APK não passa pelo Worker (sem armazenamento, sem buffer em memória).
 */
async function handleDownload(env, ctx, slug) {
  const sql = getSql(env);
  const rows = await sql`
    SELECT gv.apk_url, gv.version, g.id AS game_id
    FROM game_versions gv
    JOIN games g ON g.id = gv.game_id
    WHERE g.slug = ${slug} AND g.status = 'published'
    ORDER BY gv.created_at DESC
    LIMIT 1
  `;
  if (rows.length === 0) {
    return json({ ok: false, error: 'download_not_found' }, 404);
  }

  const { apk_url, version, game_id } = rows[0];

  // Valida que a URL de destino é absoluta (asset do GitHub Releases).
  try {
    new URL(apk_url);
  } catch {
    return json({ ok: false, error: 'invalid_apk_url' }, 500);
  }

  // Contador de downloads incrementado em segundo plano (não atrasa o redirect).
  const counter = sql`UPDATE games SET downloads = downloads + 1 WHERE id = ${game_id}`;
  if (ctx && typeof ctx.waitUntil === 'function') {
    ctx.waitUntil(counter.catch(() => {}));
  } else {
    await counter;
  }

  return new Response(null, {
    status: 302,
    headers: {
      Location: apk_url,
      'X-G-Store-Version': version,
      ...CORS_HEADERS,
    },
  });
}

export default {
  async fetch(request, env, ctx) {
    // Preflight CORS (reservado para um futuro painel web).
    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }

    if (request.method !== 'GET') {
      return json({ ok: false, error: 'method_not_allowed' }, 405);
    }

    const url = new URL(request.url);
    const path = url.pathname;

    try {
      // GET /api/health
      if (path === '/api/health') {
        return json({ ok: true, service: 'g-store-api' });
      }

      // GET /api/games
      if (path === '/api/games') {
        return await handleListGames(env);
      }

      // GET /api/games/:slug/download
      let match = path.match(/^\/api\/games\/([^/]+)\/download$/);
      if (match) {
        const slug = safeDecode(match[1]);
        if (slug === null) return json({ ok: false, error: 'invalid_slug' }, 400);
        return await handleDownload(env, ctx, slug);
      }

      // GET /api/games/:slug
      match = path.match(/^\/api\/games\/([^/]+)$/);
      if (match) {
        const slug = safeDecode(match[1]);
        if (slug === null) return json({ ok: false, error: 'invalid_slug' }, 400);
        return await handleGetGame(env, slug);
      }

      return json({ ok: false, error: 'not_found' }, 404);
    } catch (err) {
      // Registra apenas a mensagem no log do Worker; nunca expõe detalhes ao cliente.
      console.error('[g-store-api] erro:', err && err.message ? err.message : String(err));
      const msg = String(err && err.message);
      if (msg.includes('DATABASE_URL')) {
        return json({ ok: false, error: 'server_misconfigured' }, 500);
      }
      return json({ ok: false, error: 'internal_error' }, 500);
    }
  },
};
