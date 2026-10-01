/**
 * Migração do banco — G Store API
 *
 * Cria as tabelas `games` e `game_versions`, os índices e o trigger de
 * updated_at. Idempotente: pode ser executada várias vezes sem erro.
 *
 * Uso: npm run db:migrate
 * Credencial: variável de ambiente DATABASE_URL (ou .dev.vars local).
 */
import { neon } from '@neondatabase/serverless';
import { loadDatabaseUrl } from './db-url.js';

const STATEMENTS = [
  {
    label: 'tabela games',
    sql: `CREATE TABLE IF NOT EXISTS games (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  slug TEXT NOT NULL UNIQUE,
  description TEXT,
  developer TEXT,
  version TEXT,
  category TEXT,
  icon_url TEXT,
  download_url TEXT,
  downloads BIGINT NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published', 'unpublished')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
)`,
  },
  {
    label: 'tabela game_versions',
    sql: `CREATE TABLE IF NOT EXISTS game_versions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  game_id UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
  version TEXT NOT NULL,
  apk_url TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (game_id, version)
)`,
  },
  {
    label: 'indice idx_games_slug',
    sql: 'CREATE INDEX IF NOT EXISTS idx_games_slug ON games (slug)',
  },
  {
    label: 'indice idx_games_status',
    sql: 'CREATE INDEX IF NOT EXISTS idx_games_status ON games (status)',
  },
  {
    label: 'indice idx_game_versions_game_id',
    sql: 'CREATE INDEX IF NOT EXISTS idx_game_versions_game_id ON game_versions (game_id)',
  },
  {
    label: 'indice idx_game_versions_created_at',
    sql: 'CREATE INDEX IF NOT EXISTS idx_game_versions_created_at ON game_versions (created_at)',
  },
  {
    label: 'funcao set_games_updated_at',
    sql: `CREATE OR REPLACE FUNCTION set_games_updated_at() RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql`,
  },
  {
    label: 'trigger trg_games_set_updated_at',
    sql: 'DROP TRIGGER IF EXISTS trg_games_set_updated_at ON games',
  },
  {
    label: 'trigger trg_games_set_updated_at (criacao)',
    sql: `CREATE TRIGGER trg_games_set_updated_at
BEFORE UPDATE ON games
FOR EACH ROW EXECUTE FUNCTION set_games_updated_at()`,
  },
];

const sql = neon(loadDatabaseUrl());

console.log('Executando migracao...');
for (const step of STATEMENTS) {
  await sql.query(step.sql);
  console.log(`  ok - ${step.label}`);
}

// Verificação do resultado (sem expor credenciais).
const tables = await sql`
  SELECT table_name
  FROM information_schema.tables
  WHERE table_schema = 'public' AND table_name IN ('games', 'game_versions')
  ORDER BY table_name
`;
const indexes = await sql`
  SELECT indexname
  FROM pg_indexes
  WHERE schemaname = 'public' AND tablename IN ('games', 'game_versions')
  ORDER BY indexname
`;

console.log('\nTabelas no banco:');
for (const t of tables) console.log(`  - ${t.table_name}`);
console.log('\nIndices:');
for (const i of indexes) console.log(`  - ${i.indexname}`);

console.log('\n[OK] Migracao concluida com sucesso.');
