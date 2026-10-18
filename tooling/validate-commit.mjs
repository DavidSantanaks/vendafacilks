import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const semanticTitle =
  /^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\([a-z0-9][a-z0-9/-]*\))?!?: \S.*$/u;

export function validateCommitMessage(message) {
  const lines = message
    .replace(/^\uFEFF/u, '')
    .split(/\r?\n/u)
    .filter((line) => !line.startsWith('#'));
  const title = lines[0] ?? '';
  const errors = [];

  if (!semanticTitle.test(title)) {
    errors.push('Use um título semântico: feat: descrição ou fix(escopo): descrição.');
  }
  if ([...title].length > 50) {
    errors.push(`O título tem ${[...title].length} caracteres; o máximo é 50.`);
  }
  if (title !== title.trim()) {
    errors.push('O título não deve começar ou terminar com espaços.');
  }
  if (lines.length > 1 && lines[1].trim() !== '') {
    errors.push('Separe o título e o corpo com uma linha em branco.');
  }
  return errors;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const file = process.argv[2];
  if (!file) {
    console.error('Uso: node tooling/validate-commit.mjs <arquivo-da-mensagem>');
    process.exitCode = 1;
  } else {
    const errors = validateCommitMessage(readFileSync(file, 'utf8'));
    if (errors.length) {
      console.error(errors.join('\n'));
      process.exitCode = 1;
    }
  }
}
