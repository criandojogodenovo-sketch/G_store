#!/usr/bin/env python3
"""
Testa o fluxo de ADMIN contra o Neon real: promover a conta de teste a
admin (via BD), criar/editar jogo+versão pela Data API, confirmar que o
utilizador normal continua bloqueado, e reverter tudo no final.
"""
import json
import base64
import urllib.request
import urllib.error
import psycopg2

AUTH = "https://ep-bitter-sunset-b4vhekpy.neonauth.c-6.us-east-2.aws.neon.tech/neondb/auth"
DATA = "https://ep-bitter-sunset-b4vhekpy.apirest.c-6.us-east-2.aws.neon.tech/neondb/rest/v1"
ORIGIN = "https://gstore.app"
EMAIL = "teste-integracao@gstore.local"
SENHA = "SenhaForte123!"
DB_URL = open("/home/z/my-project/.secrets/database_url").read().strip()

PASSOS = []


def req(method, url, body=None, headers=None, timeout=25):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    if body is not None:
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


def passo(nome, cond, detalhe=""):
    PASSOS.append((nome, cond))
    print(f"  [{'PASS' if cond else 'FAIL'}] {nome}" + (f" — {detalhe}" if detalhe else ""))


# 1. Promover a conta de teste a admin (o que o owner fará pelo script)
conn = psycopg2.connect(DB_URL)
conn.autocommit = True
cur = conn.cursor()
cur.execute('SELECT id FROM neon_auth."user" WHERE lower(email) = %s', (EMAIL,))
row = cur.fetchone()
if not row:
    print("A conta de teste ainda não existe — corre primeiro o teste_integracao_neon.py")
    raise SystemExit(1)
USER_ID = row[0]
cur.execute(
    """INSERT INTO profiles (id, display_name, role)
       VALUES (%s, 'Teste Admin', 'admin')
       ON CONFLICT (id) DO UPDATE SET role = 'admin', updated_at = now()""",
    (USER_ID,),
)
print("Conta de teste promovida a admin na BD (como o owner fará).")

# 2. Login e JWT
code, hdrs, body = req("POST", f"{AUTH}/sign-in/email", body={"email": EMAIL, "password": SENHA})
cookie = hdrs.get("set-cookie", "").split("__Secure-neon-auth.session_token=")[1].split(";")[0]
code, hdrs, body = req("GET", f"{AUTH}/get-session",
                       headers={"Cookie": f"__Secure-neon-auth.session_token={cookie}"})
jwt = hdrs.get("set-auth-jwt", "")
admin_h = {"Authorization": f"Bearer {jwt}"}
passo("login como admin + JWT", code == 200 and jwt.startswith("eyJ"), f"HTTP {code}")

print()
print("═══ ADMIN: criar/editar jogo e versão ═══")

# 3. Criar jogo (rascunho)
code, _, jogo = req("POST", f"{DATA}/games",
                    body={"name": "Jogo Teste Admin", "slug": "jogo-teste-admin",
                          "description": "Criado pelo teste de admin", "status": "draft"},
                    headers=admin_h)
if isinstance(jogo, list) and jogo:
    jogo = jogo[0]
passo("POST /games como admin", code in (200, 201) and jogo and jogo.get("id"),
      f"HTTP {code} {jogo if code >= 400 else ''}")
GAME_ID = jogo.get("id") if isinstance(jogo, dict) else None

# 4. Admin vê os rascunhos
code, _, lista = req("GET", f"{DATA}/games?select=id,name,status&slug=eq.jogo-teste-admin", headers=admin_h)
passo("admin VÊ o rascunho recém-criado", code == 200 and len(lista) == 1 and lista[0]["status"] == "draft",
      f"HTTP {code} -> {lista}")

# 5. Anónimo NÃO vê o rascunho
code, _, anon = req("GET", f"{AUTH}/token/anonymous")
anon_h = {"Authorization": f"Bearer {anon['token']}"}
code, _, lista = req("GET", f"{DATA}/games?select=id&slug=eq.jogo-teste-admin", headers=anon_h)
passo("anónimo NÃO vê o rascunho", code == 200 and lista == [], f"HTTP {code} -> {lista}")

# 6. Adicionar versão
code, _, ver = req("POST", f"{DATA}/game_versions",
                   body={"game_id": GAME_ID, "version": "1.0.0", "version_code": 1,
                         "apk_url": "https://github.com/criandojogodenovo-sketch/G_store/releases/download/app-v0.3.0/app-release.apk",
                         "release_notes": "teste"},
                   headers=admin_h)
if isinstance(ver, list) and ver:
    ver = ver[0]
passo("POST /game_versions como admin", code in (200, 201) and ver and ver.get("id"),
      f"HTTP {code} {ver if code >= 400 else ''}")

# 7. Editar jogo + publicar
code, _, upd = req("PATCH", f"{DATA}/games?id=eq.{GAME_ID}",
                   body={"status": "published", "short_description": "Publicado pelo teste"},
                   headers=admin_h)
code, _, lista = req("GET", f"{DATA}/games?select=status&slug=eq.jogo-teste-admin", headers=anon_h)
passo("PATCH /games (publicar) e anónimo passa a ver",
      code == 200 and lista and lista[0]["status"] == "published", f"HTTP {code} -> {lista}")

# 8. Estatísticas de downloads visíveis para admin
code, _, dl = req("GET", f"{DATA}/game_downloads?game_id=eq.{GAME_ID}&select=id", headers=admin_h)
passo("admin lê game_downloads", code == 200, f"HTTP {code} -> {len(dl) if isinstance(dl, list) else dl} linhas")

# 9. RPC de registo como admin (jogo criado)
code, _, _ = req("POST", f"{DATA}/rpc/register_download", body={"p_game_id": GAME_ID}, headers=admin_h)
passo("rpc/register_download como admin", code in (200, 201, 204), f"HTTP {code}")

# 10. Limpeza: apagar o jogo de teste
code, _, _ = req("DELETE", f"{DATA}/games?id=eq.{GAME_ID}", headers=admin_h)
code, _, lista = req("GET", f"{DATA}/games?select=id&slug=eq.jogo-teste-admin", headers=admin_h)
passo("DELETE /games (limpeza)", code == 200 and lista == [], f"HTTP {code} -> {lista}")

# 11. Reverter a promoção (deixa a conta como user normal)
cur.execute("UPDATE profiles SET role = 'user' WHERE id = %s", (USER_ID,))
print("\nConta de teste revertida para role='user'.")

# 12. Confirmar que, sem admin, o utilizador volta a estar bloqueado
code, _, body = req("POST", f"{DATA}/games",
                    body={"name": "Nao Deve Passar", "slug": "nao-deve-passar", "status": "published"},
                    headers=admin_h)
passo("depois de reverter, escrita volta a ser BLOQUEADA", code in (401, 403), f"HTTP {code}")

cur.close()
conn.close()

print()
falhas = [n for n, ok in PASSOS if not ok]
print(f"RESULTADO: {len(PASSOS) - len(falhas)}/{len(PASSOS)} passaram")
if falhas:
    print("FALHAS:", falhas)
