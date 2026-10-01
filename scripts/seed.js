/**
 * Seed de teste — G Store API
 *
 * Insere jogos de EXEMPLO para validar a API localmente.
 * Idempotente: pode ser executado várias vezes (upsert por slug).
 *
 * Os apk_url apontam para o padrão de assets do GitHub Releases deste
 * repositório — substitua pelas URLs reais ao publicar cada release.
 *
 * Uso: npm run db:seed
 */
import { neon } from '@neondatabase/serverless';
import { loadDatabaseUrl } from './db-url.js';

const RELEASES_BASE =
  'https://github.com/criandojogodenovo-sketch/G_store/releases';

const SEED_GAMES = [
  {
    name: 'Space Runner',
    slug: 'space-runner',
    description:
      'Corra pelo espaço desviando de asteroides neste arcade infinito com dificuldade progressiva.',
    developer: 'G Store Studio',
    version: '1.0.0',
    category: 'Arcade',
    icon_url: `${RELEASES_BASE}/download/space-runner-v1.0.0/space-runner-icon.png`,
    download_url: `${RELEASES_BASE}/download/space-runner-v1.0.0/space-runner-1.0.0.apk`,
    status: 'published',
    versions: [
      {
        version: '1.0.0',
        apk_url: `${RELEASES_BASE}/download/space-runner-v1.0.0/space-runner-1.0.0.apk`,
      },
    ],
  },
  {
    name: 'Puzzle Quest',
    slug: 'puzzle-quest',
    description:
      'Resolva enigmas progressivos e avance por centenas de fases desafiadoras.',
    developer: 'G Store Studio',
    version: '0.9.1',
    category: 'Puzzle',
    icon_url: `${RELEASES_BASE}/download/puzzle-quest-v0.9.1/puzzle-quest-icon.png`,
    download_url: `${RELEASES_BASE}/download/puzzle-quest-v0.9.1/puzzle-quest-0.9.1.apk`,
    status: 'published',
    versions: [
      {
        version: '0.9.0',
        apk_url: `${RELEASES_BASE}/download/puzzle-quest-v0.9.0/puzzle-quest-0.9.0.apk`,
      },
      {
        version: '0.9.1',
        apk_url: `${RELEASES_BASE}/download/puzzle-quest-v0.9.1/puzzle-quest-0.9.1.apk`,
      },
    ],
  },
  {
    name: 'Neon Racer',
    slug: 'neon-racer',
    description:
      'Corridas futuristas em alta velocidade (jogo em desenvolvimento, ainda oculto na loja).',
    developer: 'G Store Studio',
    version: '0.1.0',
    category: 'Corrida',
    icon_url: null,
    download_url: null,
    status: 'draft',
    versions: [],
  },
];

const sql = neon(loadDatabaseUrl());

console.log('Inserindo jogos de exemplo...');
for (const game of SEED_GAMES) {
  const [row] = await sql`
    INSERT INTO games (name, slug, description, developer, version, category,
                       icon_url, download_url, status)
    VALUES (${game.name}, ${game.slug}, ${game.description}, ${game.developer},
            ${game.version}, ${game.category}, ${game.icon_url},
            ${game.download_url}, ${game.status})
    ON CONFLICT (slug) DO UPDATE SET
      name = EXCLUDED.name,
      description = EXCLUDED.description,
      developer = EXCLUDED.developer,
      version = EXCLUDED.version,
      category = EXCLUDED.category,
      icon_url = EXCLUDED.icon_url,
      download_url = EXCLUDED.download_url,
      status = EXCLUDED.status,
      updated_at = now()
    RETURNING id
  `;

  for (const v of game.versions) {
    await sql`
      INSERT INTO game_versions (game_id, version, apk_url)
      VALUES (${row.id}, ${v.version}, ${v.apk_url})
      ON CONFLICT (game_id, version) DO NOTHING
    `;
  }

  console.log(`  ok - ${game.slug} (${game.status})`);
}

console.log('\n[OK] Seed concluido. Jogos de exemplo disponiveis para testar a API.');
