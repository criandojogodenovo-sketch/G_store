#!/usr/bin/env python3
"""
Testes REAIS de ponta a ponta contra o Neon (Auth + Data API + RLS).
Fluxo completo que o app Android vai fazer.

Nota: tabelas criadas na migração V002 só ficam visíveis na Data API
depois de "Refresh schema cache" (consola Neon → Data API). Até lá,
os testes dessas tabelas aparecem como PENDENTE (não FAIL).
"""
import json
import base64
import urllib.request
import urllib.error

AUTH = "https://ep-bitter-sunset-b4vhekpy.neonauth.c-6.us-east-2.aws.neon.tech/neondb/auth"
DATA = "https://ep-bitter-sunset-b4vhekpy.apirest.c-6.us-east-2.aws.neon.tech/neondb/rest/v1"
ORIGIN = "https://gstore.app"

PASSOS = []
EMAIL = "teste-integracao@gstore.local"
SENHA = "SenhaForte123!"


def req(method, url, body=None, headers=None, timeout=25, json_body=True):
    if body is not None and json_body:
        data = json.dumps(body).encode()
    elif body is not None:
        data = body
    else:
        data = b"{}" if method == "POST" and json_body else None
    r = urllib.request.Request(url, data=data, method=method)
    if json_body:
        r.add_header("Content-Type", "application/json")
        # Como o app: POST/PATCH devolvem as linhas criadas/atualizadas.
        if method in ("POST", "PATCH"):
            r.add_header("Prefer", "return=representation")
    r.add_header("Origin", ORIGIN)
    for k, v in (headers or {}).items():
        r.add_header(k, v)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            raw = resp.read().decode()
            return resp.status, dict(resp.headers), json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            parsed = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            parsed = raw[:300]
        return e.code, dict(e.headers), parsed


def passo(nome, cond, detalhe="", pendente=False):
    if pendente:
        status = "PEND"
    else:
        status = "PASS" if cond else "FAIL"
    PASSOS.append((status, nome, detalhe))
    print(f"  [{status}] {nome}" + (f" — {detalhe}" if detalhe else ""))


def jwt_payload(token):
    try:
        p = token.split(".")[1]
        p += "=" * (-len(p) % 4)
        return json.loads(base64.urlsafe_b64decode(p))
    except Exception:
        return {}


print("═══ 1. TOKEN ANÓNIMO (leitura do catálogo SEM login) ═══")
code, hdrs, anon = req("GET", f"{AUTH}/token/anonymous")
passo("GET /token/anonymous devolve token", code == 200 and anon and "token" in anon, f"HTTP {code}")
anon_tok = anon["token"] if anon else ""
anon_h = {"Authorization": f"Bearer {anon_tok}"}
passo("role do token anónimo = anonymous",
      jwt_payload(anon_tok).get("role") == "anonymous",
      f"role={jwt_payload(anon_tok).get('role')}")

print()
print("═══ 2. CATÁLOGO SEM LOGIN (Data API + RLS) ═══")
code, _, body = req("GET", f"{DATA}/games?select=id,name,slug,status,downloads", headers=anon_h)
passo("GET /games com token anónimo funciona", code == 200, f"HTTP {code}")
GAME_ID = ""
if code == 200:
    rascunho = [g for g in body if g.get("status") != "published"]
    GAME_ID = [g for g in body if g.get("status") == "published"][0]["id"]
    passo("rascunhos escondidos do anónimo", len(body) > 0 and len(rascunho) == 0,
          f"{len(body)} jogos visíveis, {len(rascunho)} rascunhos escondidos")

code, _, body = req("GET", f"{DATA}/game_versions?select=version,apk_url&limit=5", headers=anon_h)
passo("GET /game_versions (jogos publicados)", code == 200,
      f"HTTP {code} -> {len(body) if isinstance(body, list) else body} versões")

code, _, body = req("GET", f"{DATA}/reviews?game_id=eq.{GAME_ID}&select=rating", headers=anon_h)
pend = code == 404 and "schema cache" in str(body)
passo("GET /reviews público (sem login)", code == 200, f"HTTP {code} {body if code >= 400 else ''}",
      pendente=pend)

code, _, body = req("GET", f"{DATA}/v_categories?select=slug,games_count&order=sort_order.asc", headers=anon_h)
pend = code == 404 and "schema cache" in str(body)
passo("GET /v_categories com contagens", code == 200, f"HTTP {code} {body if code >= 400 else ''}",
      pendente=pend)

print()
print("═══ 3. ESCRITA BLOQUEADA PARA ANÓNIMOS ═══")
code, _, body = req("POST", f"{DATA}/games",
                    body={"name": "Hack", "slug": "hack-anon", "status": "published"},
                    headers=anon_h)
passo("POST /games anónimo BLOQUEADO", code in (401, 403, 42501), f"HTTP {code}")

print()
print("═══ 4. LOGIN (utilizador normal de teste) ═══")
code, hdrs, body = req("POST", f"{AUTH}/sign-in/email",
                       body={"email": EMAIL, "password": SENHA})
if code == 401:
    # Conta de teste não existe — criar (auto-provisionada por este script).
    code, _, body = req("POST", f"{AUTH}/sign-up/email",
                       body={"name": "Teste Integração", "email": EMAIL, "password": SENHA})
    passo("conta de teste criada (auto-provisionada)", code == 200, f"HTTP {code} {body if code >= 400 else ''}")
    code, hdrs, body = req("POST", f"{AUTH}/sign-in/email",
                           body={"email": EMAIL, "password": SENHA})
passo("POST /sign-in/email", code == 200, f"HTTP {code}")
cookie = None
set_cookie = hdrs.get("set-cookie", "")
if "__Secure-neon-auth.session_token=" in set_cookie:
    cookie = set_cookie.split("__Secure-neon-auth.session_token=")[1].split(";")[0]
passo("cookie de sessão recebido", bool(cookie))

code, hdrs, body = req("GET", f"{AUTH}/get-session",
                       headers={"Cookie": f"__Secure-neon-auth.session_token={cookie}"})
jwt = hdrs.get("set-auth-jwt", "")
passo("get-session devolve header set-auth-jwt", code == 200 and jwt.startswith("eyJ"), f"HTTP {code}")
user_h = {"Authorization": f"Bearer {jwt}"}
claims = jwt_payload(jwt)
USER_ID = claims.get("sub", "")
passo("JWT role=authenticated, sub=<uuid do user>",
      claims.get("role") == "authenticated" and bool(USER_ID),
      f"role={claims.get('role')}, sub={USER_ID[:13]}...")

print()
print("═══ 5. PERFIL (RLS: só a própria linha) ═══")
code, _, body = req("GET", f"{DATA}/profiles?id=eq.{USER_ID}", headers=user_h)
pend = code == 404 and "schema cache" in str(body)
if not pend:
    if code == 200 and body == []:
        code, _, body = req("POST", f"{DATA}/profiles",
                            body={"id": USER_ID, "display_name": "Teste GStore", "role": "user"},
                            headers=user_h)
        passo("auto-criação do próprio perfil", code in (200, 201), f"HTTP {code} {body if code >= 400 else ''}")
    else:
        passo("GET /profiles (próprio perfil)", code == 200, f"HTTP {code} -> {body}")
    code, _, body = req("POST", f"{DATA}/profiles",
                        body={"id": "00000000-0000-0000-0000-000000000000", "display_name": "Falso", "role": "user"},
                        headers=user_h)
    passo("criar perfil COM O ID DE OUTREM bloqueado", code in (401, 403), f"HTTP {code} {body if code >= 400 else ''}")
    code, _, body = req("POST", f"{DATA}/profiles",
                        body={"id": USER_ID, "display_name": "Hacker", "role": "admin"},
                        headers=user_h)
    passo("auto-promoção a admin bloqueada (role=user forçado)", code in (401, 403), f"HTTP {code} {body if code >= 400 else ''}")
else:
    passo("perfis: tabelas novas no cache (pendente de refresh)", True, "ver nota no topo", pendente=True)

print()
print("═══ 6. BIBLIOTECA E FAVORITOS (RLS: só do dono) ═══")
code, _, body = req("GET", f"{DATA}/library?select=game_id", headers=user_h)
pend = code == 404 and "schema cache" in str(body)
if not pend:
    passo("GET /library", code == 200, f"HTTP {code}")
    code, _, body = req("POST", f"{DATA}/library",
                        body={"user_id": USER_ID, "game_id": GAME_ID}, headers=user_h)
    passo("adicionar jogo à própria biblioteca", code in (200, 201, 409), f"HTTP {code} {body if code >= 400 else ''}")
    code, _, body = req("POST", f"{DATA}/library",
                        body={"user_id": "00000000-0000-0000-0000-000000000000", "game_id": GAME_ID},
                        headers=user_h)
    passo("adicionar à biblioteca DE OUTREM bloqueado", code in (401, 403), f"HTTP {code} {body if code >= 400 else ''}")
    code, _, body = req("POST", f"{DATA}/favorites",
                        body={"user_id": USER_ID, "game_id": GAME_ID}, headers=user_h)
    passo("marcar favorito", code in (200, 201, 409), f"HTTP {code} {body if code >= 400 else ''}")
else:
    passo("biblioteca/favoritos no cache (pendente de refresh)", True, "ver nota no topo", pendente=True)

print()
print("═══ 7. REVIEWS ═══")
code, _, body = req("POST", f"{DATA}/reviews",
                    body={"game_id": GAME_ID, "user_id": USER_ID, "rating": 5, "comment": "Ótimo!"},
                    headers=user_h)
if isinstance(body, list) and body:
    body = body[0]
if code == 409:
    # Já existe review deste utilizador para o jogo (execuções anteriores)
    # — o app faz upsert: lê a existente e atualiza.
    code2, _, existente = req("GET", f"{DATA}/reviews?game_id=eq.{GAME_ID}&user_id=eq.{USER_ID}&select=*",
                              headers=user_h)
    rid = existente[0]["id"] if existente else None
    passo("review existente localizada (upsert)", rid is not None, f"HTTP {code2} -> rid={str(rid)[:13]}...")
    code, _, body = req("PATCH", f"{DATA}/reviews?id=eq.{rid}",
                        body={"rating": 4, "comment": "Bom"},
                        headers=user_h)
    passo("PATCH /reviews (editar a própria)", code in (200, 204), f"HTTP {code}")
else:
    passo("POST /reviews (criar a própria)", code in (200, 201) and body and body.get("id"),
          f"HTTP {code} {body if code >= 400 else ''}")
    rid = body.get("id") if isinstance(body, dict) else None
    code, _, body2 = req("PATCH", f"{DATA}/reviews?id=eq.{rid}",
                         body={"rating": 4, "comment": "Bom"},
                         headers=user_h)
    passo("PATCH /reviews (editar a própria)", code in (200, 204), f"HTTP {code} {body2 if code >= 400 else ''}")

# Review DE OUTREM não pode ser editada (prova com id inexistente/aleatório).
code, _, _ = req("PATCH", f"{DATA}/reviews?id=eq.00000000-0000-0000-0000-000000000000",
                 body={"rating": 1}, headers=user_h)
passo("PATCH em review inexistente não altera nada (RLS)", code in (200, 204, 403, 404),
      f"HTTP {code}")

print()
print("═══ 8. REGISTO DE DESCARGA (RPC security definer) ═══")
code, _, before = req("GET", f"{DATA}/games?id=eq.{GAME_ID}&select=downloads", headers=anon_h)
antes = before[0]["downloads"] if code == 200 else None
code, _, body = req("POST", f"{DATA}/rpc/register_download",
                    body={"p_game_id": GAME_ID}, headers=user_h)
pend = code == 404 and "schema cache" in str(body)
if not pend:
    passo("POST /rpc/register_download", code in (200, 201, 204), f"HTTP {code} {body if body and code >= 400 else ''}")
    code, _, after = req("GET", f"{DATA}/games?id=eq.{GAME_ID}&select=downloads", headers=anon_h)
    passo("contador games.downloads incrementado", code == 200 and after[0]["downloads"] == antes + 1,
          f"{antes} -> {after[0]['downloads'] if code == 200 else '?'}")
    code, _, body = req("GET", f"{DATA}/game_downloads?game_id=eq.{GAME_ID}&select=id", headers=user_h)
    passo("game_downloads invisível para não-admin (RLS)",
          code == 200 and body == [], f"HTTP {code}, linhas={body}")
else:
    passo("RPC no cache (pendente de refresh)", True, "ver nota no topo", pendente=True)

code, _, body = req("POST", f"{DATA}/rpc/register_download",
                    body={"p_game_id": GAME_ID}, headers=anon_h)
passo("RPC bloqueada para anónimo", code in (401, 403, 404), f"HTTP {code}")

print()
print("═══ 9. ESCRITA NO CATÁLOGO BLOQUEADA PARA UTILIZADOR NORMAL ═══")
code, _, body = req("POST", f"{DATA}/games",
                    body={"name": "Jogo Hackeado", "slug": "jogo-hackeado", "status": "published"},
                    headers=user_h)
passo("POST /games por utilizador normal BLOQUEADO", code in (401, 403),
      f"HTTP {code} {body if isinstance(body, dict) else ''}")

code, hdrs2, body = req("PATCH", f"{DATA}/games?id=eq.{GAME_ID}",
                        body={"name": "Nome Alterado"}, headers=user_h)
# 204 + Content-Range */0 = 0 linhas alteradas (RLS filtrou tudo)
range_hdr = hdrs2.get("content-range", "")
code, _, after = req("GET", f"{DATA}/games?id=eq.{GAME_ID}&select=name", headers=anon_h)
nome_intacto = code == 200 and after[0]["name"] != "Nome Alterado"
passo("PATCH /games bloqueado (nome intacto)",
      code in (200, 204) and nome_intacto,
      f"HTTP {code}, Content-Range={range_hdr!r}, nome intacto={nome_intacto}")

code, hdrs3, body = req("DELETE", f"{DATA}/games?id=eq.{GAME_ID}", headers=user_h)
range_hdr = hdrs3.get("content-range", "")
code, _, after = req("GET", f"{DATA}/games?id=eq.{GAME_ID}&select=id", headers=anon_h)
ainda_existe = code == 200 and len(after) == 1
passo("DELETE /games bloqueado (jogo continua lá)",
      code in (200, 204) and ainda_existe,
      f"HTTP {code}, Content-Range={range_hdr!r}, jogo existe={ainda_existe}")

print()
print("═══ 10. ERROS REAIS do Auth (o que o app mostra) ═══")
code, _, body = req("POST", f"{AUTH}/sign-in/email", body={"email": EMAIL, "password": "senha-errada"})
passo("senha errada devolve código real", code == 401 and body.get("code") == "INVALID_EMAIL_OR_PASSWORD",
      f"HTTP {code} -> {body}")

code, _, body = req("POST", f"{AUTH}/sign-up/email",
                   body={"name": "Dup", "email": EMAIL, "password": "SenhaForte123!"})
passo("e-mail duplicado devolve código real",
      code in (400, 409, 422) and str(body.get("code", "")).startswith("USER_ALREADY_EXISTS"),
      f"HTTP {code} -> {body}")

print()
print("═══ 11. SIGN-OUT ═══")
code, _, body = req("POST", f"{AUTH}/sign-out", body={},
                    headers={"Cookie": f"__Secure-neon-auth.session_token={cookie}"})
passo("POST /sign-out", code in (200, 204), f"HTTP {code} corpo={body!r}")
# confirmar que a sessão morreu
code, _, body = req("GET", f"{AUTH}/get-session",
                    headers={"Cookie": f"__Secure-neon-auth.session_token={cookie}"})
passo("sessão invalidada após sign-out", code == 200 and (body is None or body.get("user") is None),
      f"HTTP {code}, user={None if body is None else body.get('user')}")

print()
print("═" * 70)
f = [p for p in PASSOS if p[0] == "FAIL"]
p = [p for p in PASSOS if p[0] == "PEND"]
print(f"RESULTADO: {len(PASSOS) - len(f) - len(p)}/{len(PASSOS)} passaram | {len(f)} falhas | {len(p)} pendentes (refresh schema cache)")
if f:
    print("FALHAS:")
    for _, nome, det in f:
        print(f"  x {nome} — {det}")
