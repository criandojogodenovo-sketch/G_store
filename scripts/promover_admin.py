#!/usr/bin/env python3
"""
Promove um utilizador a ADMIN da G Store.

Uso:
    python3 scripts/promover_admin.py

Pede o E-MAIL da conta (a criada no app com o Neon Auth), procura o id
no schema neon_auth e garante que o perfil em `profiles` tem role='admin'.

Requisitos: pip install psycopg2-binary e a variável DATABASE_URL no
ambiente (connection string do Neon — NUNCA a commitar no repositório).
"""
import os
import sys

try:
    import psycopg2
except ImportError:
    sys.exit("Falta o psycopg2: pip install psycopg2-binary")

DB_URL = os.environ.get("DATABASE_URL")
if not DB_URL:
    sys.exit("Defina DATABASE_URL no ambiente antes de correr este script.")

email = input("E-mail da conta a promover a admin: ").strip().lower()
if not email or "@" not in email:
    sys.exit("E-mail inválido.")

conn = psycopg2.connect(DB_URL)
conn.autocommit = True
cur = conn.cursor()

cur.execute('SELECT id, name FROM neon_auth."user" WHERE lower(email) = %s', (email,))
row = cur.fetchone()
if not row:
    cur.close()
    conn.close()
    sys.exit("Não existe nenhuma conta com esse e-mail no Neon Auth "
             "(a conta é criada no registo, dentro do app).")

user_id, name = row
print(f"Conta encontrada: {name or '(sem nome)'} ({user_id})")

# O app cria o perfil no primeiro login; se ainda não existir, criamos já
# com role admin (o utilizador não consegue criar perfis de outrem, mas o
# owner da BD pode).
cur.execute(
    """
    INSERT INTO profiles (id, display_name, avatar_url, role)
    VALUES (%s, COALESCE(%s, 'Jogador'), NULL, 'admin')
    ON CONFLICT (id) DO UPDATE SET role = 'admin', updated_at = now()
    """,
    (user_id, name),
)
print("Perfil garantido com role='admin'.")

cur.execute("SELECT id, display_name, role FROM profiles WHERE id = %s", (user_id,))
print("Resultado:", cur.fetchone())

cur.close()
conn.close()
print("\nFeito. Ao entrar no app, a conta vê a 'Área de administração' no Perfil.")
