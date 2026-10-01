-- ============================================================================
-- G Store — Migração V002: Neon Auth + Data API com RLS
--
-- Aplicada diretamente no Neon Postgres (owner: neondb_owner).
-- Arquitetura: app Android + Neon Auth (Better Auth) + Neon Data API
-- (PostgREST). Não existe backend próprio.
--
-- Sumário:
--   1. Tabela `profiles` (liga ao id do utilizador do Neon Auth)
--   2. Tabelas `library`, `favorites`, `reviews`
--   3. RLS em TODAS as tabelas do schema public + políticas por tabela
--   4. GRANTs para as roles `authenticated` e `anonymous` (Data API)
--   5. Funções auxiliares: is_admin() e RPC register_download()
--   6. Vista v_categories (categorias com contagem de jogos publicados)
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. PROFILES — substitui a antiga tabela `users` (estava ligada ao id de
--    um fornecedor de autenticação externo que deixou de ser usado).
--    O id É o id do utilizador no Neon Auth (schema neon_auth,
--    gerido pela Neon — por isso sem FK física, apenas lógica).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS profiles (
    id UUID PRIMARY KEY,                       -- = neon_auth."user".id
    display_name TEXT NOT NULL DEFAULT 'Jogador',
    avatar_url TEXT,
    role TEXT NOT NULL DEFAULT 'user' CHECK (role IN ('user', 'developer', 'admin')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_profiles_role ON profiles (role);

-- Remove as FKs antigas que apontavam para `users` e apaga a tabela legada
-- (só continha um utilizador de teste 'dev-local' da era do backend local).
ALTER TABLE games DROP CONSTRAINT IF EXISTS games_developer_id_fkey;
ALTER TABLE game_versions DROP CONSTRAINT IF EXISTS game_versions_created_by_fkey;
ALTER TABLE game_downloads DROP CONSTRAINT IF EXISTS game_downloads_user_id_fkey;
DROP TABLE IF EXISTS users;

-- Nova FK: games.developer_id -> profiles.id
DO $$ BEGIN
    ALTER TABLE games ADD CONSTRAINT games_developer_id_fkey
        FOREIGN KEY (developer_id) REFERENCES profiles(id) ON DELETE SET NULL;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- ----------------------------------------------------------------------------
-- 2. LIBRARY / FAVORITES / REVIEWS
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS library (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,                    -- = Neon Auth user id
    game_id UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    game_version_id UUID REFERENCES game_versions(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, game_id)
);
CREATE INDEX IF NOT EXISTS idx_library_user ON library (user_id);

CREATE TABLE IF NOT EXISTS favorites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,                    -- = Neon Auth user id
    game_id UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, game_id)
);
CREATE INDEX IF NOT EXISTS idx_favorites_user ON favorites (user_id);

CREATE TABLE IF NOT EXISTS reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (game_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_reviews_game ON reviews (game_id);
CREATE INDEX IF NOT EXISTS idx_reviews_user ON reviews (user_id);

-- FK reviews.user_id -> profiles (permite o embed `profiles(...)` na Data API)
DO $$ BEGIN
    ALTER TABLE reviews ADD CONSTRAINT reviews_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES profiles(id) ON DELETE CASCADE;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- updated_at automático em UPDATE
CREATE OR REPLACE FUNCTION public.set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_profiles_updated_at ON profiles;
CREATE TRIGGER trg_profiles_updated_at BEFORE UPDATE ON profiles
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

DROP TRIGGER IF EXISTS trg_reviews_updated_at ON reviews;
CREATE TRIGGER trg_reviews_updated_at BEFORE UPDATE ON reviews
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- ----------------------------------------------------------------------------
-- 3. FUNÇÕES AUXILIARES
--    is_admin(): SECURITY DEFINER — lê profiles sem recursão de RLS
--    (o owner das tabelas não está sujeito a RLS sem FORCE).
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.is_admin() RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
    AS $$ SELECT EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin') $$;

REVOKE ALL ON FUNCTION public.is_admin() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_admin() TO authenticated, anonymous;

-- RPC usada pelo app para registar a descarga (tabela game_downloads +
-- contador games.downloads). Só funciona com utilizador autenticado.
CREATE OR REPLACE FUNCTION public.register_download(p_game_id uuid, p_version_id uuid DEFAULT NULL)
    RETURNS void
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = public
    AS $$
DECLARE
    v_user uuid;
BEGIN
    v_user := auth.uid();
    IF v_user IS NULL THEN
        RAISE EXCEPTION 'not_authenticated';
    END IF;
    INSERT INTO game_downloads (game_id, game_version_id, user_id)
    VALUES (p_game_id, p_version_id, v_user);
    UPDATE games SET downloads = downloads + 1, updated_at = now() WHERE id = p_game_id;
END $$;

REVOKE ALL ON FUNCTION public.register_download(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.register_download(uuid, uuid) TO authenticated;

-- ----------------------------------------------------------------------------
-- 4. RLS EM TODAS AS TABELAS DO SCHEMA public
-- ----------------------------------------------------------------------------
ALTER TABLE games           ENABLE ROW LEVEL SECURITY;
ALTER TABLE game_versions   ENABLE ROW LEVEL SECURITY;
ALTER TABLE categories      ENABLE ROW LEVEL SECURITY;
ALTER TABLE game_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE game_downloads  ENABLE ROW LEVEL SECURITY;
ALTER TABLE schema_migrations ENABLE ROW LEVEL SECURITY;  -- sem políticas: bloqueada
ALTER TABLE profiles        ENABLE ROW LEVEL SECURITY;
ALTER TABLE library         ENABLE ROW LEVEL SECURITY;
ALTER TABLE favorites       ENABLE ROW LEVEL SECURITY;
ALTER TABLE reviews          ENABLE ROW LEVEL SECURITY;

-- ---- games: leitura pública (rascunhos só p/ admin); escrita só admin ----
DROP POLICY IF EXISTS games_select ON games;
CREATE POLICY games_select ON games FOR SELECT TO authenticated, anonymous
    USING (is_admin() OR status = 'published');

DROP POLICY IF EXISTS games_insert ON games;
CREATE POLICY games_insert ON games FOR INSERT TO authenticated
    WITH CHECK (is_admin());

DROP POLICY IF EXISTS games_update ON games;
CREATE POLICY games_update ON games FOR UPDATE TO authenticated
    USING (is_admin()) WITH CHECK (is_admin());

DROP POLICY IF EXISTS games_delete ON games;
CREATE POLICY games_delete ON games FOR DELETE TO authenticated
    USING (is_admin());

-- ---- game_versions: leitura de versões de jogos publicados; escrita admin ----
DROP POLICY IF EXISTS versions_select ON game_versions;
CREATE POLICY versions_select ON game_versions FOR SELECT TO authenticated, anonymous
    USING (EXISTS (
        SELECT 1 FROM games g
        WHERE g.id = game_versions.game_id AND (is_admin() OR g.status = 'published')
    ));

DROP POLICY IF EXISTS versions_insert ON game_versions;
CREATE POLICY versions_insert ON game_versions FOR INSERT TO authenticated
    WITH CHECK (is_admin());

DROP POLICY IF EXISTS versions_update ON game_versions;
CREATE POLICY versions_update ON game_versions FOR UPDATE TO authenticated
    USING (is_admin()) WITH CHECK (is_admin());

DROP POLICY IF EXISTS versions_delete ON game_versions;
CREATE POLICY versions_delete ON game_versions FOR DELETE TO authenticated
    USING (is_admin());

-- ---- categories: leitura pública; escrita admin ----
DROP POLICY IF EXISTS categories_select ON categories;
CREATE POLICY categories_select ON categories FOR SELECT TO authenticated, anonymous
    USING (true);

DROP POLICY IF EXISTS categories_insert ON categories;
CREATE POLICY categories_insert ON categories FOR INSERT TO authenticated
    WITH CHECK (is_admin());

DROP POLICY IF EXISTS categories_update ON categories;
CREATE POLICY categories_update ON categories FOR UPDATE TO authenticated
    USING (is_admin()) WITH CHECK (is_admin());

DROP POLICY IF EXISTS categories_delete ON categories;
CREATE POLICY categories_delete ON categories FOR DELETE TO authenticated
    USING (is_admin());

-- ---- game_categories: leitura pública; escrita admin ----
DROP POLICY IF EXISTS game_categories_select ON game_categories;
CREATE POLICY game_categories_select ON game_categories FOR SELECT TO authenticated, anonymous
    USING (EXISTS (
        SELECT 1 FROM games g
        WHERE g.id = game_categories.game_id AND (is_admin() OR g.status = 'published')
    ));

DROP POLICY IF EXISTS game_categories_insert ON game_categories;
CREATE POLICY game_categories_insert ON game_categories FOR INSERT TO authenticated
    WITH CHECK (is_admin());

DROP POLICY IF EXISTS game_categories_delete ON game_categories;
CREATE POLICY game_categories_delete ON game_categories FOR DELETE TO authenticated
    USING (is_admin());

-- ---- profiles: cada utilizador só lê/cria/edita a SUA linha ----
DROP POLICY IF EXISTS profiles_select_own ON profiles;
CREATE POLICY profiles_select_own ON profiles FOR SELECT TO authenticated
    USING (id = auth.uid());

DROP POLICY IF EXISTS profiles_insert_own ON profiles;
CREATE POLICY profiles_insert_own ON profiles FOR INSERT TO authenticated
    WITH CHECK (id = auth.uid() AND role = 'user');

DROP POLICY IF EXISTS profiles_update_own ON profiles;
CREATE POLICY profiles_update_own ON profiles FOR UPDATE TO authenticated
    USING (id = auth.uid()) WITH CHECK (id = auth.uid() AND role = 'user');
-- DELETE: propositalmente sem política (o perfil não se auto-elimina).

-- ---- library: só o dono ----
DROP POLICY IF EXISTS library_select_own ON library;
CREATE POLICY library_select_own ON library FOR SELECT TO authenticated
    USING (user_id = auth.uid());

DROP POLICY IF EXISTS library_insert_own ON library;
CREATE POLICY library_insert_own ON library FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS library_delete_own ON library;
CREATE POLICY library_delete_own ON library FOR DELETE TO authenticated
    USING (user_id = auth.uid());

-- ---- favorites: só o dono ----
DROP POLICY IF EXISTS favorites_select_own ON favorites;
CREATE POLICY favorites_select_own ON favorites FOR SELECT TO authenticated
    USING (user_id = auth.uid());

DROP POLICY IF EXISTS favorites_insert_own ON favorites;
CREATE POLICY favorites_insert_own ON favorites FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS favorites_delete_own ON favorites;
CREATE POLICY favorites_delete_own ON favorites FOR DELETE TO authenticated
    USING (user_id = auth.uid());

-- ---- reviews: leitura pública; cada utilizador só escreve as suas ----
DROP POLICY IF EXISTS reviews_select_public ON reviews;
CREATE POLICY reviews_select_public ON reviews FOR SELECT TO authenticated, anonymous
    USING (true);

DROP POLICY IF EXISTS reviews_insert_own ON reviews;
CREATE POLICY reviews_insert_own ON reviews FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS reviews_update_own ON reviews;
CREATE POLICY reviews_update_own ON reviews FOR UPDATE TO authenticated
    USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS reviews_delete_own ON reviews;
CREATE POLICY reviews_delete_own ON reviews FOR DELETE TO authenticated
    USING (user_id = auth.uid());

-- ---- game_downloads: inserção pelo próprio; leitura só do admin ----
DROP POLICY IF EXISTS downloads_insert_own ON game_downloads;
CREATE POLICY downloads_insert_own ON game_downloads FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS downloads_select_admin ON game_downloads;
CREATE POLICY downloads_select_admin ON game_downloads FOR SELECT TO authenticated
    USING (is_admin());
-- UPDATE/DELETE: sem política = bloqueados (analytics imutável).

-- ----------------------------------------------------------------------------
-- 5. VISTA v_categories — categorias com nº de jogos publicados.
--    security_invoker=true para respeitar a RLS das tabelas base.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_categories WITH (security_invoker = true) AS
SELECT c.id,
       c.slug,
       c.name,
       c.description,
       c.icon_url,
       c.sort_order,
       COUNT(g.id) AS games_count
FROM categories c
LEFT JOIN game_categories gc ON gc.category_id = c.id
LEFT JOIN games g ON g.id = gc.game_id AND g.status = 'published'
GROUP BY c.id, c.slug, c.name, c.description, c.icon_url, c.sort_order;

GRANT SELECT ON v_categories TO authenticated, anonymous;

-- ----------------------------------------------------------------------------
-- 6. GRANTs para a Data API (roles usadas pelo PostgREST).
--    (Equivalente manual ao "Grant public schema access" da consola Neon,
--     mas tabela a tabela e coluna a coluna.)
-- ----------------------------------------------------------------------------
GRANT USAGE ON SCHEMA public TO authenticated, anonymous;
GRANT USAGE ON SCHEMA auth TO authenticated, anonymous;  -- p/ auth.uid() nas políticas

-- Catálogo (leitura pública)
GRANT SELECT ON games, game_versions, categories, game_categories, reviews
    TO authenticated, anonymous;

-- Perfis / biblioteca / favoritos / reviews (dono)
GRANT SELECT, INSERT, UPDATE ON profiles TO authenticated;
GRANT SELECT, INSERT, DELETE ON library, favorites TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON reviews TO authenticated;

-- Downloads (registo pelo utilizador; leitura barrada pela RLS a não-admins)
GRANT SELECT, INSERT ON game_downloads TO authenticated;

-- Escrita do catálogo (só passa pela RLS quem é admin)
GRANT INSERT, UPDATE, DELETE ON games, game_versions, categories, game_categories
    TO authenticated;

COMMIT;

-- Registo da migração (fora da transação, a tabela tem RLS mas o owner
-- contorna-a).
INSERT INTO schema_migrations (version)
VALUES ('V002__neon_auth_rls')
ON CONFLICT (version) DO NOTHING;
