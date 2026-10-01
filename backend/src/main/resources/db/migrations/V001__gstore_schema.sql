-- ============================================================
-- G Store — Migração V001
-- Estende o schema legado (games, game_versions) criado pela
-- migração JS original, sem destruir nada: todas as operações
-- são idempotentes (IF NOT EXISTS / DO NOTHING).
-- ============================================================

-- ---------- Tabela users (perfis locais espelhando Appwrite) ----------
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appwrite_user_id TEXT NOT NULL UNIQUE,
    email TEXT UNIQUE,
    display_name TEXT,
    avatar_url TEXT,
    role TEXT NOT NULL DEFAULT 'user' CHECK (role IN ('user', 'developer', 'admin')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);
CREATE INDEX IF NOT EXISTS idx_users_role ON users (role);

-- ---------- Tabela categories ----------
CREATE TABLE IF NOT EXISTS categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    description TEXT,
    icon_url TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_categories_slug ON categories (slug);

-- ---------- Relacionamento N:N jogo <-> categoria ----------
CREATE TABLE IF NOT EXISTS game_categories (
    game_id UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    PRIMARY KEY (game_id, category_id)
);
CREATE INDEX IF NOT EXISTS idx_game_categories_category ON game_categories (category_id);

-- ---------- Registro de downloads (analytics básico) ----------
CREATE TABLE IF NOT EXISTS game_downloads (
    id BIGSERIAL PRIMARY KEY,
    game_id UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    game_version_id UUID REFERENCES game_versions(id) ON DELETE SET NULL,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_game_downloads_game ON game_downloads (game_id);
CREATE INDEX IF NOT EXISTS idx_game_downloads_created ON game_downloads (created_at);

-- ---------- Colunas novas em games (o legado permanece intacto) ----------
ALTER TABLE games ADD COLUMN IF NOT EXISTS developer_id UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE games ADD COLUMN IF NOT EXISTS short_description TEXT;
ALTER TABLE games ADD COLUMN IF NOT EXISTS screenshots TEXT[] NOT NULL DEFAULT '{}';
ALTER TABLE games ADD COLUMN IF NOT EXISTS version_code BIGINT;

-- ---------- Colunas novas em game_versions (metadados do Release) ----------
ALTER TABLE game_versions ADD COLUMN IF NOT EXISTS version_code BIGINT;
ALTER TABLE game_versions ADD COLUMN IF NOT EXISTS release_notes TEXT;
ALTER TABLE game_versions ADD COLUMN IF NOT EXISTS apk_file_name TEXT;
ALTER TABLE game_versions ADD COLUMN IF NOT EXISTS apk_size_bytes BIGINT;
ALTER TABLE game_versions ADD COLUMN IF NOT EXISTS apk_asset_id BIGINT;
ALTER TABLE game_versions ADD COLUMN IF NOT EXISTS release_id BIGINT;
ALTER TABLE game_versions ADD COLUMN IF NOT EXISTS release_tag TEXT;
ALTER TABLE game_versions ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL;

-- ---------- Controle de migrações aplicadas ----------
CREATE TABLE IF NOT EXISTS schema_migrations (
    version TEXT PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------- Categorias padrão (idempotente) ----------
INSERT INTO categories (slug, name, sort_order) VALUES
    ('arcade',     'Arcade',     1),
    ('puzzle',     'Puzzle',     2),
    ('corrida',    'Corrida',    3),
    ('acao',       'Ação',       4),
    ('aventura',   'Aventura',   5),
    ('estrategia', 'Estratégia', 6),
    ('esportes',   'Esportes',   7),
    ('rpg',        'RPG',        8)
ON CONFLICT (slug) DO NOTHING;

-- ---------- Backfill: vincula game_categories pela coluna legada ----------
INSERT INTO game_categories (game_id, category_id)
SELECT g.id, c.id
FROM games g
JOIN categories c ON LOWER(c.name) = LOWER(g.category)
ON CONFLICT (game_id, category_id) DO NOTHING;
