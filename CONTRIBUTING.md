# Desenvolvimento

A branch inicial é `main`. Trabalhe em branches de tarefa e abra pull request com problema resolvido, comportamento resultante e validação executada. Não integrar alterações com falhas no CI.

Requisitos aprovados no SDD não são alterados silenciosamente: criar ADR em `docs/decisions/`, atualizar o documento e os aceites afetados. Cada módulo novo deve ter autorização no serviço, escopo de tenant/filial e testes de acesso cruzado.

Antes da revisão, executar `mvnw.cmd verify`, ESLint e Prettier conforme o README. Não versionar credenciais, dados reais, `.env`, dumps, caches ou diretórios `.tools`/`.local`. Migrations aplicadas são imutáveis; adicionar a próxima versão.

## Padrão dos commits

Usar formato semântico, com o título inteiro limitado a **50 caracteres**, incluindo o prefixo e o escopo. Tipos aceitos: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore` e `revert`. O corpo pode detalhar o que mudou, o motivo e os testes; separar título e corpo com uma linha em branco.

```text
feat(tema): aplica roxo e off-white

Atualiza a paleta de login, painéis e formulários.
Centraliza as cores nos tokens do tema.

Validação: formatação e inspeção em desktop e celular.
```

Ative a validação local uma vez por checkout, com Node disponível no PATH:

```sh
git config --local core.hooksPath .githooks
```

O hook `commit-msg` rejeita títulos fora do padrão ou mensagens sem separação correta. Para validar uma mensagem antes de commitar, use `node tooling/validate-commit.mjs caminho-da-mensagem.txt`. O validador usa apenas o Node, sem dependências instaladas. Seus testes são executados com `node --test tooling/validate-commit.test.mjs`.

Proteção de branch e revisores obrigatórios dependem do repositório remoto, ainda não configurado. Commits são locais; push e publicação remota dependem de autorização.
