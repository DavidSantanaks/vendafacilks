# ADR 0004 — Venda de demonstração e caixa

Estado: aceito para Etapa 3, em 19/10/2026.

## Contexto

O catálogo/estoque já separa empresa e filial. A Etapa 3 precisa demonstrar o ciclo comercial antes da integração fiscal, preservando o histórico e impedindo baixa dupla, desconto acumulado indevido e exposição antecipada do caixa cego.

## Decisão

- Módulo `commerce` com JDBC e transações Spring no mesmo DataSource/JPA de auditoria. V4 acrescenta sessões, eventos, contagens, vendas, itens, pagamentos, aprovações e referência da baixa à venda; V1–V3 permanecem intactas.
- Checkout trava primeiro a sessão e depois produtos por UUID, em ordem determinística, compatível com os locks do catálogo/estoque. Recalcula preço da filial, UN/KG, descontos e recebimentos; todas as gravações são atômicas.
- Chave UUID por operação e hash SHA-256 do conteúdo canônico, vinculado a empresa/filial/ator. Senhas ficam fora do hash e da persistência. Repetição do conteúdo retorna a venda existente; alteração conflita.
- Dinheiro em BigDecimal/NUMERIC(19,2); KG em três casas; HALF_UP por linha; desconto global distribuído pelo maior resto em centavos. Limite efetivo por linha impede esconder desconto excessivo em outro item.
- Aprovação por senha de outro usuário ativo e autorizado na filial. Desconto exige limite suficiente; estoque negativo reutiliza a validação da Etapa 2. Aprovações ficam vinculadas à venda ou movimento de caixa, com motivo e auditoria.
- `OPEN → COUNTED → CLOSED`: declarar contagem interrompe movimentos e vendas. Contagem e esperado persistem imutáveis antes de enviar a diferença. Caixa cego não devolve saldo calculado nem movimentos no HTML/JSON antes dessa declaração.
- Índices parciais garantem uma sessão em andamento por caixa e por operador/filial. Sem transferência, reabertura ou edição de contagem.
- Default P-009: sangria máxima de R$ 1.000,00 por operação e tolerância zero para diferença; aprovação de sangria e fechamento cego habilitados. `USER_MANAGE` configura esses valores na empresa. Teto comercial até 20% nesta etapa.
- Recebimentos manuais: digital não aumenta gaveta; líquido de dinheiro é recebido menos troco. Sem gateway de pagamento e sem fiscal simulado.
- Vendas permitidas apenas com demonstração em `local`/`test`; `production` e `staging` bloqueiam mesmo em combinação de profiles. Estados comercial/fiscal separados: `COMPLETED` e `NOT_ISSUED`; recibos identificam demonstração e ausência de valor fiscal.

## Consequências

O ciclo completo pode ser testado sem liberar operação real. Relatórios e backups incluem o novo livro; correções futuras precisam de eventos de devolução/estorno e tratamento fiscal. Carrinho fica na memória da página; não há offline, reserva de estoque, sessão transferida ou recuperação automática de requisição após recarregar a página. Impressão usa o navegador; impressora física não é homologada. Limites de memória de rate limit e sessão continuam próprios de uma instância.
