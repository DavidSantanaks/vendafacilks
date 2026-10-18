# Instruções do repositório

## Commits

- Usar commits semânticos: `tipo: descrição` ou `tipo(escopo): descrição`.
- Tipos permitidos: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore` e `revert`.
- O título inteiro deve ter no máximo **50 caracteres**, incluindo tipo, escopo, espaços e pontuação.
- O corpo é opcional e pode detalhar a alteração, o motivo e as verificações realizadas.
- Separar o título do corpo com **uma linha em branco**. Separar também parágrafos do corpo com linhas em branco.
- Preferir descrições claras em português e commits que representem uma alteração coerente.
- Validar a mensagem com `node tooling/validate-commit.mjs <arquivo>`; o hook `commit-msg` usa o mesmo validador. Não ignorar a validação sem instrução explícita do usuário.
- Criar commits locais quando autorizado. Publicação, push ou alteração de histórico exigem autorização correspondente.
- Não incluir `.env`, credenciais, bancos, caches, `.tools`, `.local` ou dependências instaladas nos commits.
