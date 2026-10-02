-- V003 — Apps + Jogos, e correções da v0.3.1
-- ═══════════════════════════════════════════════════════════════════
-- BUG 2 (detalhe 403 para anónimo):
--   O pedido `GET /reviews?select=*,profiles(...)` falhava com
--   42501 "permission denied for table profiles" porque o role
--   `anonymous` não tinha GRANT nenhum em profiles. O app passa a ler
--   os autores diretamente das colunas PÚBLICAS de profiles com o
--   token anónimo — aqui damos esse acesso mínimo (3 colunas, zero
--   escrita). O RLS continua a proteger as restantes colunas (o
--   anonymous só vê o que a política deixar, e nunca escreve).
--
-- MUDANÇA DE PRODUTO (apps E jogos):
--   - games.type e categories.type com restrição (app | game)
--   - 7 categorias de apps (jogos mantêm as suas)
--   - v_categories expõe o type
--
-- Nota: aplicado manualmente em produção a 2026-10-02 (ver
-- scripts/corrigir_e_testar.py + verificar_estado.py); este ficheiro
-- documenta o estado e é idempotente para reexecução segura.

BEGIN;

-- ── (a) BUG 2: leitura pública APENAS das 3 colunas públicas ──
GRANT SELECT (id, display_name, avatar_url) ON public.profiles TO anonymous;

DROP POLICY IF EXISTS profiles_public_read ON public.profiles;
CREATE POLICY profiles_public_read ON public.profiles
    FOR SELECT TO anonymous USING (true);

-- ── (b) tipo do item do catálogo: app | game ──
ALTER TABLE public.games ADD COLUMN IF NOT EXISTS type TEXT NOT NULL DEFAULT 'game';
DO $$ BEGIN
    ALTER TABLE public.games ADD CONSTRAINT games_type_check CHECK (type IN ('app','game'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

ALTER TABLE public.categories ADD COLUMN IF NOT EXISTS type TEXT NOT NULL DEFAULT 'game';
DO $$ BEGIN
    ALTER TABLE public.categories ADD CONSTRAINT categories_type_check CHECK (type IN ('app','game'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- ── (c) categorias de APPS (as de jogos mantêm-se) ──
INSERT INTO public.categories (slug, name, description, sort_order, type) VALUES
  ('ferramentas',    'Ferramentas',    'Utilitários e apps de manutenção', 101, 'app'),
  ('produtividade',  'Produtividade',  'Notas, agendas e foco',             102, 'app'),
  ('social',         'Social',         'Redes e comunidades',               103, 'app'),
  ('educacao',       'Educação',       'Aprender todos os dias',           104, 'app'),
  ('entretenimento', 'Entretenimento', 'Vídeo, música e mais',              105, 'app'),
  ('utilitarios',    'Utilitários',    'Ferramentas do dia a dia',          106, 'app'),
  ('comunicacao',    'Comunicação',    'Mensagens e chamadas',              107, 'app')
ON CONFLICT (slug) DO NOTHING;

-- ── (d) v_categories com o type (DROP+CREATE: muda a lista de colunas) ──
DROP VIEW IF EXISTS public.v_categories;
CREATE VIEW public.v_categories WITH (security_invoker = true) AS
SELECT c.id, c.slug, c.name, c.description, c.icon_url, c.sort_order, c.type,
       COUNT(g.id) AS games_count
FROM categories c
LEFT JOIN game_categories gc ON gc.category_id = c.id
LEFT JOIN games g ON g.id = gc.game_id AND g.status = 'published'
GROUP BY c.id, c.slug, c.name, c.description, c.icon_url, c.sort_order, c.type;
GRANT SELECT ON public.v_categories TO authenticated, anonymous;

-- ── (e) limpeza: vista experimental não usada pelo app ──
DROP VIEW IF EXISTS public.v_profiles_public;

COMMIT;

-- Recarregar o schema cache da Data API (Neon: pode demorar ~1h a
-- autorrefrescar; o NOTIFY direto normalmente acelera).
NOTIFY pgrst, 'reload schema';
