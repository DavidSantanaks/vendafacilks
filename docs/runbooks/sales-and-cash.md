# Venda e caixa — Etapa 3

Versão `0.3.0-SNAPSHOT`. Execução comercial de teste, sem integração de pagamento, balança ou provedor fiscal. Não use os dados de demonstração como registro de uma venda real.

## Atualizar e preparar

Faça backup do PostgreSQL antes da atualização. Execute `docker compose up -d --build` no ambiente local. O Flyway aplica V4 e mantém os produtos, preços, saldos, usuários e históricos anteriores. Não altere V1–V3 e não apague volumes.

O PDV só aceita vendas com demonstração habilitada em `local`/`test`. A presença de `production` ou `staging` bloqueia busca comercial, prévia e checkout, mesmo combinada com `local`. O Compose de produção continua destinado à preparação da infraestrutura e aos módulos disponíveis; a integração fiscal da Etapa 4 condiciona a operação comercial.

Selecione a filial. O administrador abre **Meu caixa**, cadastra um nome para o caixa físico e configura as políticas da empresa, quando necessário. Não há caixas criados automaticamente nem dados comerciais fictícios adicionados à atualização.

## Rotina do operador

1. Entre com sua conta e abra **Meu caixa**. Escolha o caixa físico e declare o fundo inicial da gaveta.
2. Acesse **Frente de caixa**. A busca aceita nome, SKU e código de barras; um código de barras exato seguido de Enter adiciona 1 UN ou solicita o peso KG. O leitor deve funcionar como teclado. Não há leitura de etiqueta com peso embutido nem integração com balança.
3. Ajuste quantidades: UN inteira; KG até três casas, por exemplo `0,350`. Remova itens pelo botão correspondente. O carrinho fica na memória da página e é descartado ao sair; venda concluída fica no banco.
4. Informe descontos por item e/ou venda em `%` ou `R$`. O servidor confere a combinação e distribui o desconto global em centavos pelo maior resto, com desempate determinístico pela ordem dos produtos.
5. Preencha dinheiro entregue e recebimentos já confirmados em Pix, débito ou crédito. Estes campos registram o teste; não cobram, transferem, autorizam cartão ou consultam banco.
6. Clique em **Calcular valores**. A prévia do servidor mostra total, troco e necessidade de aprovação. O checkout repete todas as validações.
7. Outra pessoa autorizada informa e-mail, senha e motivo nos campos de aprovação. A senha não é gravada nem reutilizada como token; os campos são limpos após a tentativa. A conta e a sessão do operador continuam as mesmas.
8. Confirme o total/troco. O recibo apresenta **RECIBO NÃO FISCAL · DEMONSTRAÇÃO** e pode ser impresso pelo navegador. Layout pensado para impressão comum; impressora térmica física não foi homologada.

Os preços vêm da filial ativa. Modificação de cadastro/preço depois da inclusão exige remover e buscar o produto novamente. Não existe edição de preço-base no caixa. Produto inativo ou sem preço ativo não pode ser vendido.

## Regras de valores e aprovação

| Regra                                        | Padrão                                                             |
| -------------------------------------------- | ------------------------------------------------------------------ |
| Limite de operador / gerente / administrador | 0% / 10% / 20%                                                     |
| Teto da empresa                              | 20% acumulados por item, ajustável para baixo                      |
| Estoque negativo                             | Outra pessoa com `NEGATIVE_STOCK_APPROVE`, senha e motivo          |
| Sangria                                      | `CASH_WITHDRAW`, motivo e outra pessoa com `CASH_WITHDRAW_APPROVE` |
| Máximo de sangria por operação               | R$ 1.000,00, configurável                                          |
| Fechamento                                   | Cego, configurável                                                 |
| Diferença sem aprovação                      | R$ 0,00; acima disso exige `CASH_DIFFERENCE_APPROVE`               |

As definições iniciais de R$ 1.000,00 e tolerância zero concretizam P-009 conservadoramente e precisam ser conferidas com o responsável pela operação. Não representam uma regra fiscal. Políticas são alteradas por `USER_MANAGE`, em **Meu caixa**, e valem para toda a empresa. A edição das definições de perfis continua fora desta entrega; o limite efetivo também respeita os perfis já concedidos ao usuário na filial.

O desconto efetivo considera os centavos realmente concedidos. Em R$ 100,00, item com 10% e venda com 10% produzem R$ 19,00 de desconto acumulado (19%): gerente precisa de aprovação de administrador. Aprovação não pode superar o teto da empresa nem o limite de quem aprova. Desconto de 100%, item gratuito ou total negativo é rejeitado.

Exemplo de pagamento: total R$ 34,97; Pix R$ 20,00 e dinheiro entregue R$ 20,00. Troco R$ 5,03; gaveta recebe líquido R$ 14,97. Pix acima do total é rejeitado; não gera troco em espécie.

Quantidades são `NUMERIC(19,3)` e dinheiro `NUMERIC(19,2)`/BigDecimal, arredondamento HALF_UP por item. Venda limitada a 100 produtos distintos e quatro métodos de pagamento; quantidade até 999.999,999 KG/999.999 UN e total até R$ 999.999.999,99. Não há aprovação de item por preço manual.

## Movimentos e fechamento

Somente o operador responsável movimenta a sessão. Administrador/gerente com relatório pode consultá-la, mas não assume a sessão de outra pessoa. Há no máximo uma sessão `OPEN`/`COUNTED` por caixa e uma por operador na filial.

Suprimento registra entrada física com motivo e `CASH_REINFORCE`. Sangria registra saída física, limita o valor por operação e exige aprovação por padrão. Operador básico só abre, vende e fecha; para movimentos precisa receber um perfil com a permissão correspondente. A saída nunca pode superar o dinheiro disponível. Não use sangria para representar reembolso.

Para fechar, conte o dinheiro e envie **Declarar contagem e conferir**. O servidor registra a contagem e o esperado de forma imutável, muda para `COUNTED` e bloqueia operações novas. Só então HTML/JSON mostram esperado, contado, diferença e movimentos. Quando o fechamento cego está desligado, esperado/movimentos podem ser consultados antes da contagem.

Informe a observação do fechamento. Qualquer diferença exige motivo; diferença absoluta acima da tolerância exige aprovação de outra pessoa. Após aprovação, a sessão passa para `CLOSED`. O próximo operador precisa abrir uma nova sessão. Contagem declarada não tem edição ou reabertura nesta etapa; erro de contagem fica registrado e justificado no fechamento. Fluxos de estorno/devolução serão adicionados na Etapa 4, sem apagar o histórico.

## Repetições e concorrência

O checkout trava a sessão e os produtos em ordem estável. Venda, itens, pagamentos, baixa, movimento de dinheiro, aprovações e auditoria confirmam juntos; falha desfaz a transação. Cada página/operação gera UUID. Mesmo UUID e conteúdo normalizado retorna a venda existente; conteúdo diferente é rejeitado. O servidor não aceita o total enviado pelo cliente como fonte de cálculo.

Se a conexão cair ao confirmar, tente novamente na mesma página com os mesmos dados. Confira o recibo/relatório antes de abrir outra venda caso o resultado fique desconhecido. Recarregar o PDV descarta o carrinho e gera outra chave. Não há recuperação automática de carrinho ou suporte offline.

## Relatórios e conservação

**Relatório de vendas** exige `REPORT_VIEW`, filtra a filial ativa e datas no fuso `America/Sao_Paulo`, até 93 dias por consulta. Exibe 20 vendas por página, líquidos por método e quantidades UN/KG separadas. CSV aceita até 10.000 vendas, inclui identificação de demonstração e neutraliza fórmulas em células externas. Reduza o período para exportações maiores.

Backup completo PostgreSQL inclui os novos livros e snapshots; não há arquivos comerciais novos fora do banco. Tabelas `sales`, `sale_items`, `sale_payments`, `sale_approvals`, `cash_events` e `cash_counts` rejeitam UPDATE/DELETE. Triggers não substituem controle do administrador do banco, backups e privilégios mínimos de produção.

Problemas de permissão: confira empresa do host, filial ativa, vínculo e perfil. Caixa ocupado: confira sessões abertas/contadas e conclua o fechamento com o operador responsável. Preço alterado: remova e adicione o produto outra vez. Aprovação recusada: confira conta ativa, filial, permissão, limite, senha e motivo; dez falhas de aprovação em quinze minutos bloqueiam temporariamente novas tentativas naquela empresa/origem.
