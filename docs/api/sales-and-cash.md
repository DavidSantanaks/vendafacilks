# Contratos de venda e caixa

Versão `0.3.0-SNAPSHOT`. Host da empresa, sessão autenticada, filial selecionada e CSRF para todos os POSTs. Identificadores são UUID; valores no JSON de entrada são strings decimais, ponto ou vírgula, sem milhar/expoente. Erros seguem ProblemDetail com referência de correlação. Respostas de sessão autenticada não devem ser armazenadas em caches compartilhados.

| Método e rota                                                 | Uso / permissão                                                                                                         |
| ------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| GET `/pdv`                                                    | Página do PDV; `SALE_CREATE`                                                                                            |
| GET `/api/pdv/produtos?q=`                                    | Até 20 produtos ativos/preço ativo na filial; `SALE_CREATE`, demonstração                                               |
| POST `/api/pdv/previsao`                                      | Calcula carrinho/pagamentos e indica aprovações; aceita lista de pagamentos vazia na prévia                             |
| POST `/api/pdv/vendas`                                        | Checkout atômico, retorna `{ "id": "UUID" }`; `SALE_CREATE`, caixa próprio aberto, demonstração                         |
| GET `/vendas/{id}/recibo`                                     | Própria venda com `SALE_CREATE` ou outra venda com `REPORT_VIEW`; filial ativa                                          |
| GET `/caixa`, `/caixa/{id}`                                   | Caixas, sessões e detalhe; `CASH_OPEN`                                                                                  |
| POST `/api/caixa/registros`                                   | `{ "name": "Caixa 01" }`; `USER_MANAGE`                                                                                 |
| POST `/api/caixa/sessoes`                                     | `operationId`, `registerId`, `initial`; `CASH_OPEN`                                                                     |
| GET `/api/caixa/{id}`                                         | Detalhe próprio ou `REPORT_VIEW`; `expected`/`count` nulos e `events` vazio antes da declaração cega                    |
| POST `/api/caixa/{id}/movimentos`                             | `operationId`, `type` SUPPLY/WITHDRAW, `amount`, `reason`, `approval`; `CASH_REINFORCE`/`CASH_WITHDRAW`, sessão própria |
| POST `/api/caixa/{id}/contagem`                               | `{ "counted": "150.00" }`; `CASH_CLOSE`, sessão própria; encerra aceitação de operações                                 |
| POST `/api/caixa/{id}/fechamento`                             | `operationId`, `reason`, `approval`; `CASH_CLOSE`, sessão própria contada                                               |
| POST `/api/caixa/politicas`                                   | `blind`, `withdrawalApproval`, `differenceLimit`, `withdrawalLimit`, `maximumDiscount`; `USER_MANAGE`                   |
| GET `/relatorios/vendas?from=YYYY-MM-DD&to=YYYY-MM-DD&page=0` | Período até 93 dias, 20 por página; `REPORT_VIEW`                                                                       |
| GET `/relatorios/vendas.csv?from=YYYY-MM-DD&to=YYYY-MM-DD`    | Até 10.000 vendas, CSV UTF-8/BOM; `REPORT_VIEW`                                                                         |

## Checkout e prévia

```json
{
  "operationId": "00000000-0000-4000-8000-000000000001",
  "sessionId": "00000000-0000-4000-8000-000000000002",
  "customer": "Cliente de teste",
  "items": [
    {
      "productId": "00000000-0000-4000-8000-000000000003",
      "quantity": "1.250",
      "unitPrice": "19.90",
      "productVersion": 0,
      "discount": { "type": "PERCENT", "value": "0" }
    }
  ],
  "discount": { "type": "AMOUNT", "value": "0" },
  "payments": [{ "method": "CASH", "amount": "30.00" }]
}
```

IDs do exemplo são marcadores; use os retornados por cadastro/busca/abertura. `unitPrice` e `productVersion` precisam corresponder à busca atual; não alteram o preço do banco. `items` tem de 1 a 100 produtos distintos. UN exige inteiro; KG até três casas. Checkout exige pagamentos positivos e suficientes em até quatro métodos distintos: `CASH`, `PIX`, `DEBIT`, `CREDIT`. A prévia retorna `totals`, `change`, `cashNet`, `discountApprovalRequired`, `negativeStockApprovalRequired` e `items` com `productId`, `subtotal`, `discount`, `total` de cada produto, para associar os valores à linha correta. Valores JSON são decimais numéricos apenas para exibição; a interface atualiza a prévia automaticamente e o checkout mantém a validação final.

Se necessário, inclua `discountApproval` e/ou `stockApproval`, cada um com `email`, `password`, `reason`. Movimentos e fechamento usam o mesmo objeto no campo `approval`. Nunca registrar payload completo com credenciais em logs, screenshots, analytics ou histórico de requisições. Os records de credenciais têm `toString` redigido e não são persistidos.

Mesmo `operationId` e conteúdo normalizado não duplicam venda. Mudança de operador, sessão, itens, preço, desconto, cliente ou pagamentos com a mesma chave é recusada. As credenciais não entram no hash; replay válido de venda já concluída não pede nova aprovação. A prévia não reserva estoque; somente checkout confirma baixa e revalida permissões.

O campo fiscal do recibo continua `NOT_ISSUED`, modo `DEMO`. Não há endpoint de emitir nota, devolver, reembolsar, apagar ou alterar venda concluída. Os profiles `production`/`staging` bloqueiam busca de PDV, prévia e checkout no servidor.
