# Estado da implementação

Atualizado em 19/10/2026. Entrega: `0.3.0-SNAPSHOT`, fundação e Etapas 2 e 3. Escopo guiado pelas seções 17 e 23 do SDD.

| Etapa                            | Estado                                                                                                                                                                      |
| -------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 0 — Repositório e infraestrutura | Implementado: Maven/Java, profiles, Flyway, Docker/Compose, layout, erros, ajuda, formatação, CI e documentação                                                             |
| 1 — Multiempresa e autorização   | Núcleo implementado e testado: host, login, recuperação de senha, membership, RBAC por filial, seleção, atribuição de perfis, bloqueio/restauração, filiais e gestão SaaS   |
| 2 — Catálogo e estoque           | Implementado: categorias/produtos, UN/KG, imagens, preço/histórico por filial, estoque/movimentos/mínimo, aprovação negativa e importação CSV/XLSX com prévia e confirmação |
| 3 — Venda, caixa e descontos     | Implementado em demonstração: PDV UN/KG, pagamentos mistos, troco, descontos acumulados/aprovações, checkout atômico, caixa e fechamento cego, recibo e relatório/CSV       |
| 4 — Fiscal e devoluções          | Pendente; depende também de provedor e homologação                                                                                                                          |
| 5 — Operação e produção          | Pendente                                                                                                                                                                    |

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

## Aceites da Etapa 2

- AC-02 ampliado: produtos, imagens, categorias e importações rejeitam IDs de outro tenant.
- AC-04: mudar preço em uma filial preserva o valor de outra filial.
- AC-05: arquivos com erros mostram prévia/relatório e não gravam produtos; reenvio da confirmação não duplica registros.
- AC-06: quantidade KG preserva três casas; venda usa BRL em duas casas e HALF_UP por item.
- AC-07: retiradas e checkouts concorrentes do último item não criam saldo negativo sem aprovação.
- Livro de estoque e histórico de preços protegidos contra UPDATE/DELETE.
- Estoque mínimo e listagem de reposição por filial; preços e saldos não migram entre unidades.
- Upload PNG/JPEG com validação de conteúdo/dimensões, normalização e acesso autenticado.
- Aprovação de saldo negativo exige outro usuário ativo, com vínculo/permissão na filial e senha válida, sem autoaprovação.

## Aceites da Etapa 3

- AC-06/07: KG e arredondamento explícito; lock de sessão e de produtos em ordem determinística; concorrência na última unidade.
- AC-08/09: operador 0%, gerente 10%, administrador 20%; desconto por item e venda, em percentual/valor, com limite efetivo acumulado por item.
- AC-10: mesma chave e mesmo conteúdo retornam a venda já registrada; conteúdo alterado com a mesma chave é rejeitado.
- AC-11: esperado e movimentos que permitiriam reconstruí-lo não são enviados no HTML/JSON de caixa cego antes da contagem.
- AC-12/13: sangria aprovada por outra pessoa e limitada por operação; diferença acima da tolerância exige aprovação e motivo.
- AC-14: contagem interrompe vendas/movimentos; caixa fechado não pode ser reutilizado sem nova sessão.
- AC-19: recibo identificado como demonstração, sem valor fiscal; fiscal separado como `NOT_ISSUED`.
- Pagamentos manuais/mistos em dinheiro/Pix/débito/crédito; valores digitais não entram na gaveta e não geram troco.
- Snapshots de itens, NCM/CEST cadastrais, preços, descontos, pagamentos e aprovações imutáveis; auditoria na mesma transação.
- Relatório por filial/período em São Paulo, paginação, totais por método/unidade e CSV protegido contra fórmulas.
- Venda permitida somente em `local`/`test` com demonstração habilitada; `production`/`staging` prevalecem e bloqueiam o PDV.

As aprovações precisam de usuário ativo, vínculo e permissão na filial, senha, motivo e limite suficiente; não aceitam autoaprovação. Não existe transferência de sessão de caixa. Contagens declaradas não são editáveis; correções em venda concluída dependem dos fluxos da Etapa 4.

## Próximo incremento

Etapa 4: escolher o provedor fiscal e configurar homologação, NF-e/NFC-e, tributos e ciclo de retorno, consulta, artefatos e cancelamento; implementar devoluções totais/parciais e reembolsos auditáveis. O regime e o enquadramento do primeiro cliente real continuam pendentes.

Os ACs de devolução/fiscal e operação comercial permanecem pendentes. Esta versão emite apenas recibo comercial de demonstração, sem documento fiscal.
