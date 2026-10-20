# Venda e caixa — Etapa 3

Versão `0.3.0-SNAPSHOT`. Execução comercial de teste, sem integração de pagamento, balança ou provedor fiscal. Não use os dados de demonstração como registro de uma venda real.

## Atualizar e preparar

Faça backup do PostgreSQL antes da atualização. Execute `docker compose up -d --build` no ambiente local. O Flyway aplica V4 e mantém os produtos, preços, saldos, usuários e históricos anteriores. Não altere V1–V3 e não apague volumes.

O PDV só aceita vendas com demonstração habilitada em `local`/`test`. A presença de `production` ou `staging` bloqueia busca comercial, prévia e checkout, mesmo combinada com `local`. O Compose de produção continua destinado à preparação da infraestrutura e aos módulos disponíveis; a integração fiscal da Etapa 4 condiciona a operação comercial.

Selecione a filial. O administrador abre **Meu caixa**, cadastra um nome para o caixa físico e configura as políticas da empresa, quando necessário. Não há caixas criados automaticamente nem dados comerciais fictícios adicionados à atualização.

## Rotina do operador

1. Entre com sua conta e abra **Meu caixa**. Escolha o caixa físico e declare o fundo inicial da gaveta.
2. Acesse **Frente de caixa**. A lista fica à esquerda e o resumo/pagamento à direita. Digite parte do nome/SKU para receber sugestões automaticamente, ou leia um código e pressione Enter. Leitura exata adiciona UN; KG abre uma janela para informar o peso. Leituras seguidas entram em fila. O leitor funciona como teclado, sem etiqueta com peso embutido ou integração com balança.
3. Ajuste quantidades na própria linha, usando o campo ou **−/+**: UN inteira; KG até três casas, como `0,350`. O total por item e o total da venda são atualizados automaticamente. Remova pelo **×** ou use **Limpar venda** para descartar o rascunho. O carrinho fica na memória da página e é descartado ao sair.
4. Use **Desconto** na linha para um item ou no resumo para a venda inteira, em `%` ou `R$`. O servidor confere a combinação e distribui o desconto global em centavos pelo maior resto. Cliente é opcional e fica recolhido em **Identificar cliente**.
5. Escolha Dinheiro, Pix, Débito ou Crédito à direita. O valor exato é sugerido; ajuste para o efetivamente recebido. Para dividir, informe uma parte, clique em **+** e escolha a próxima forma. Cada pagamento adicionado pode ser removido. Confira o troco. São registros de teste, sem cobrança, transferência, autorização de cartão ou consulta ao banco.
6. Clique em **Finalizar venda**. O checkout repete as validações de preços, descontos, estoque e pagamentos. Não há etapa manual de calcular valores.
7. Quando exigida, uma janela pede a aprovação de outra pessoa autorizada na filial: e-mail, senha e motivo. Somente as aprovações necessárias aparecem. Cancelar essa janela preserva o carrinho e os pagamentos; uma senha incorreta mantém a janela aberta e limpa o campo de senha. A conta do operador continua a mesma.
8. Após concluir, o recibo apresenta **RECIBO NÃO FISCAL · DEMONSTRAÇÃO** e pode ser impresso pelo navegador. Layout pensado para impressão comum; impressora térmica física não foi homologada.

Atalhos: **F2** busca, **F4** desconto na venda, **F9** finalizar e **Esc** fechar janela/sugestões. No desktop, a lista rola internamente e o total/botão final continuam visíveis. No celular, a barra inferior leva ao pagamento e mantém o total acessível.

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

As etiquetas arredondadas mostram o estado do caixa também no cabeçalho da frente de caixa:

- **Verde — Caixa aberto:** permite vendas e movimentos autorizados.
- **Amarelo — Contagem declarada:** novas operações estão bloqueadas; use **Concluir fechamento** para conferir e encerrar.
- **Cinza — Caixa fechado:** sessão encerrada, disponível para consulta.
- **Cinza — Sem caixa aberto:** o operador precisa abrir uma sessão para vender.

O texto acompanha a cor para identificar o estado sem depender apenas dela. Nos demais cadastros, verde indica ativo/concluído, amarelo indica pendência, vermelho indica bloqueio/erro ou falta de estoque, e cinza indica inativo/encerrado.

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
