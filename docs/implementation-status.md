# Estado da implementação

Atualizado em 18/10/2026. Entrega: `0.1.0-SNAPSHOT`, fundação executável. Escopo guiado pela seção 23 do SDD.

| Etapa | Estado |
| --- | --- |
| 0 — Repositório e infraestrutura | Implementado: Maven/Java, profiles, Flyway, Docker/Compose, layout, erros, ajuda, formatação, CI e documentação |
| 1 — Multiempresa e autorização | Núcleo implementado e testado: host, login, recuperação de senha, membership, RBAC por filial, seleção, atribuição de perfis, bloqueio/restauração, filiais e gestão SaaS |
| 2 — Catálogo e estoque | Pendente |
| 3 — Venda, caixa e descontos | Pendente |
| 4 — Fiscal e devoluções | Pendente; depende também de provedor e homologação |
| 5 — Operação e produção | Pendente |

## Aceites da fundação

- AC-01: credencial só da empresa B é negada no host A.
- AC-02: consulta e alteração por UUID de filial alheia são negadas; cenários equivalentes de produtos/vendas aguardam seus módulos.
- AC-03: operador só da Matriz não seleciona Jardim ou filial de outra empresa.
- Sessão A reutilizada no host B é rejeitada e invalidada.
- Suspensão, usuário inativo, vínculo revogado e grant retirado passam a valer no acesso seguinte.
- Mesma identidade em empresas distintas recebe papéis e limites próprios.
- Super Admin não recebe acesso implícito a dados empresariais.
- Download CSV filtra empresa/filiais autorizadas.
- FKs compostas rejeitam referências cruzadas; auditoria rejeita exclusão.
- AC-20: ajuda por teclado/toque implementada e conferida em navegador.
- AC-21: verificadores Java/JS/HTML/CSS configurados no CI.

## Antes de considerar a Etapa 1 completa para produção

Implementar convites e alteração verificada de e-mail, edição das definições de perfis, política ampliada de senhas, MFA/admin seguro, logo com armazenamento validado e paginação nas demais listas. Recuperação por e-mail, atribuição de perfis, bloqueio/restauração e paginação da gestão SaaS de usuários já estão implementados. Configurar SMTP real e confirmar a entrega antes de produção. Não há suporte com impersonação: o painel SaaS não oferece entrada nos dados dos clientes.

## Próximo incremento

Etapa 2: catálogo compartilhado, preço/saldo por filial, unidade UN/KG, imagens validadas, importação CSV/XLSX com prévia e livro de estoque concorrente. A infraestrutura de tenant e permissões deve ser usada em cada operação e download.

Os demais ACs comerciais/fiscais do SDD permanecem pendentes. Nenhum recibo ou documento fiscal é emitido por esta versão.
