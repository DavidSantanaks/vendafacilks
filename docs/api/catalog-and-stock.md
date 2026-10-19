# Contratos da Etapa 2

Todas as rotas usam o host validado e a filial ativa da sessão. UUIDs de produto/categoria/lote não substituem a validação de tenant. POST exige CSRF. Super Admin não tem acesso operacional implícito.

| Método   | URL                                           | Uso                                                            |
| -------- | --------------------------------------------- | -------------------------------------------------------------- |
| GET      | `/produtos?q=&status=active&category=&page=0` | Catálogo HTML, 20 itens; situação active/inactive/all          |
| GET      | `/produtos/novo`                              | Formulário de produto                                          |
| POST     | `/produtos`                                   | Criar produto compartilhado pela empresa                       |
| GET      | `/produtos/{id}`                              | Produto e preço da filial, saldo se autorizado                 |
| GET      | `/produtos/{id}/editar`                       | Edição do cadastro                                             |
| POST     | `/produtos/{id}`                              | Alterar cadastro com versão; inativar preserva histórico       |
| GET/POST | `/produtos/categorias`                        | Consultar/criar categoria                                      |
| POST     | `/produtos/categorias/{id}`                   | Nome, ativação e versão                                        |
| POST     | `/produtos/{id}/preco`                        | `amount`, `active`, `version`; preço da filial atual           |
| GET      | `/produtos/{id}/imagem`                       | PNG privado, `Cache-Control: no-store`                         |
| POST     | `/produtos/{id}/imagem`                       | Multipart `image`; PNG/JPEG validado                           |
| POST     | `/produtos/{id}/imagem/remover`               | Remover somente a imagem                                       |
| GET      | `/api/v1/produtos/busca?q=`                   | Até 20 produtos ativos com preço ativo na filial               |
| GET      | `/estoque?q=&low=false&page=0`                | Saldos por filial; filtro de mínimo/reposição                  |
| GET      | `/estoque/{id}`                               | Saldo, mínimo e últimas movimentações                          |
| POST     | `/estoque/{id}/movimentacoes`                 | Entrada/ajuste com `operationId`, `type`, `quantity`, `reason` |
| POST     | `/estoque/{id}/minimo`                        | `minimum` opcional e `version`                                 |
| GET      | `/estoque/movimentacoes?product=&page=0`      | Histórico paginado da filial                                   |
| GET      | `/produtos/importacoes`                       | Formulário e últimos 10 lotes da conta/filial                  |
| POST     | `/produtos/importacoes`                       | Multipart `file`; gera somente prévia                          |
| GET      | `/produtos/importacoes/modelo.csv`            | Modelo CSV versão 1                                            |
| GET      | `/produtos/importacoes/modelo.xlsx`           | Modelo XLSX versão 1                                           |
| GET      | `/produtos/importacoes/{id}?page=0`           | Prévia com 50 registros por página                             |
| POST     | `/produtos/importacoes/{id}/confirmar`        | Revalidação e gravação integral/idempotente                    |
| GET      | `/produtos/importacoes/{id}/relatorio`        | Relatório CSV com proteção contra fórmulas                     |

## Busca JSON

Exige `PRODUCT_READ` ou `SALE_CREATE`. Retorna `id`, `sku`, `name`, `barcode`, `unit`, `price` e `quantity`. `quantity` é nulo sem `STOCK_READ`; não se retorna o saldo obtido do banco a quem não pode consultá-lo. `price` corresponde ao preço por UN/KG na filial atual. Essa rota não conclui vendas.

## Movimentações

`type` é `ENTRY` ou `ADJUSTMENT`. Quantidade é uma variação do saldo, com vírgula/ponto decimal e até três casas para KG; UN exige inteiros. ENTRY exige positivo; ADJUSTMENT permite positivo/negativo, nunca zero.

`operationId` é UUID gerado no formulário. O mesmo UUID só pode ser reapresentado pelo mesmo ator com produto/tipo/quantidade/motivo idênticos. O resultado anterior é reutilizado; não se cria movimento duplicado.

Quando o saldo final é negativo, são exigidos `approverEmail`, `approverPassword` e `approvalReason`. Outra pessoa deve autenticar com `NEGATIVE_STOCK_APPROVE` na mesma filial. A senha nunca é persistida, devolvida no HTML ou usada para trocar a sessão do solicitante.

## Formatos e erros

Preço usa duas casas e deve ser positivo. Quantidade usa três casas no banco; vírgula/ponto são decimais e não se aceita agrupamento de milhares, notação científica ou ponto flutuante binário para cálculo.

Validações de formulário reapresentam os campos com mensagem em português; conflitos de versão pedem atualização. Falhas de autorização não revelam dados de outra empresa. As rotas JSON seguem Problem Details da fundação. Arquivo acima do limite responde 413; limites de conteúdo e do modelo são rejeitados com mensagem de validação.

Veja [operação e formato de importação](../runbooks/catalog-and-stock.md) e [ADR 0003](../decisions/0003-catalog-and-stock.md).
