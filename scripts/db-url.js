/**
 * Carrega a DATABASE_URL a partir de:
 *   1) variável de ambiente DATABASE_URL (recomendado em CI/servidores)
 *   2) arquivo .dev.vars na raiz (apenas desenvolvimento local; é ignorado pelo Git)
 *
 * Esta rotina NUNCA deve imprimir o valor da connection string.
 */
import { existsSync, readFileSync } from 'node:fs';

export function loadDatabaseUrl() {
  const envUrl = (process.env.DATABASE_URL ?? '').trim();
  // Aceita a variável de ambiente apenas se for uma URL PostgreSQL válida
  // (evita herdar placeholders de outros ambientes).
  if (/^postgres(ql)?:\/\//.test(envUrl)) {
    return envUrl;
  }

  const devVarsPath = new URL('../.dev.vars', import.meta.url);
  if (existsSync(devVarsPath)) {
    for (const rawLine of readFileSync(devVarsPath, 'utf8').split(/\r?\n/)) {
      const line = rawLine.trim();
      if (line === '' || line.startsWith('#')) continue;
      const m = line.match(/^DATABASE_URL\s*=\s*(.*)$/);
      if (m) return m[1].trim().replace(/^["']|["']$/g, '');
    }
  }

  console.error(
    'ERRO: DATABASE_URL nao definida.\n' +
      'Opcoes:\n' +
      '  1) export DATABASE_URL="postgres..." (recomendado em CI)\n' +
      '  2) crie .dev.vars na raiz com DATABASE_URL="postgres..." (ignorado pelo Git)'
  );
  process.exit(1);
}
