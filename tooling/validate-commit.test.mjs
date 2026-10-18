import assert from 'node:assert/strict';
import test from 'node:test';
import { validateCommitMessage } from './validate-commit.mjs';

test('aceita tipos semânticos com escopo e corpo separado', () => {
  for (const type of [
    'feat',
    'fix',
    'docs',
    'style',
    'refactor',
    'perf',
    'test',
    'build',
    'ci',
    'chore',
    'revert',
  ]) {
    assert.deepEqual(
      validateCommitMessage(`${type}(tema): ajusta a paleta\n\nDetalhes da alteração.\n`),
      [],
    );
  }
});

test('aceita exatamente 50 caracteres, incluindo o prefixo', () => {
  assert.deepEqual(validateCommitMessage(`feat: ${'á'.repeat(44)}\n`), []);
});

test('rejeita 51 caracteres, incluindo acentos como caracteres', () => {
  assert.ok(validateCommitMessage(`feat: ${'á'.repeat(45)}`).some((error) => error.includes('51')));
});

test('o corpo pode ser detalhado sem consumir o limite do título', () => {
  assert.deepEqual(
    validateCommitMessage(
      `fix: corrige acesso\n\n${'Detalhe. '.repeat(50)}\n\nValidação executada.\n`,
    ),
    [],
  );
});

test('rejeita corpo sem linha em branco depois do título', () => {
  assert.ok(
    validateCommitMessage('feat: adiciona tema\nDetalhes sem separação.').some((error) =>
      error.includes('linha em branco'),
    ),
  );
});

test('rejeita título sem tipo, tipo desconhecido e descrição vazia', () => {
  for (const message of ['Atualiza o projeto', 'feature: adiciona tema', 'feat:', 'fix: ', '']) {
    assert.ok(validateCommitMessage(message).length > 0);
  }
});

test('aceita fim de linha Windows e comentários do editor Git', () => {
  assert.deepEqual(
    validateCommitMessage(
      'chore: configura commits\r\n\r\nDocumenta o padrão.\r\n# Comentário do Git\r\n',
    ),
    [],
  );
});

test('rejeita espaços nas extremidades do título', () => {
  assert.ok(
    validateCommitMessage('feat: adiciona tema ').some((error) => error.includes('espaços')),
  );
});
