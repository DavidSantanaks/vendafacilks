# Etapa 2 — produtos, preços, imagens e estoque

Versão `0.2.0-SNAPSHOT`, 19/10/2026. Pré-requisito: entrar no portal da empresa e selecionar uma filial autorizada.

## Atualizar a instalação

Faça backup antes de atualizar uma instalação com dados. O código usa a migration aditiva V3; Flyway preserva empresas, contas, perfis, filiais e auditoria existentes.

No ambiente local:

```sh
docker compose up -d --build
docker compose logs --tail=100 app
```

No servidor, use o Compose independente de produção e o arquivo de ambiente conforme o [guia Ubuntu](../../GUIA-SISTEMA-UBUNTU-SERVER-26.md). Não apague volumes ou altere migrations antigas.

## Produtos e categorias

Abra **Produtos → Novo produto**. Nome, SKU e unidade são obrigatórios. SKU admite letras/números, ponto, barra, hífen e sublinhado e é salvo em maiúsculas. Código de barras é opcional e único na empresa, com zeros iniciais preservados. Não se valida dígito verificador GTIN nesta entrega.

O catálogo e as categorias pertencem à empresa. Preços, saldos e mínimos pertencem à filial ativa. Busca inclui nome, SKU e código de barras; listagens de produtos/estoque têm 20 registros por página.

NCM/CEST opcionais validam formato de 8/7 dígitos, sem afirmar classificação tributária ou habilitar emissão fiscal. A classificação depende da contabilidade.

Inativar produto/categoria preserva histórico. Categoria inativa não aceita novas associações. Produto inativo não permite nova movimentação e sai da busca de venda. Não há exclusão definitiva de produto.

UN representa unidades inteiras; KG aceita peso com até três casas decimais. A unidade é protegida depois de movimentar o estoque ou definir um mínimo para não reinterpretar histórico/configuração.

## Preços

Na página do produto, informe o preço da filial atual. Use `19,90` ou `19.90`, sem separador de milhar. Preço deve ser positivo, com até duas casas. Desative o preço para retirá-lo da busca de venda nessa filial.

Troque de filial para cadastrar outro valor. O histórico conserva autor, data, valor anterior/novo e ativação. Um formulário antigo não pode sobrescrever silenciosamente um preço atualizado por outra pessoa: reabra a página após o aviso.

## Imagens

Depois de cadastrar o produto, envie PNG/JPEG pela página de detalhes. Limites de entrada: 5 MB, 4096 pixels por lado e 8 milhões de pixels. O conteúdo real e a extensão devem corresponder. O arquivo é regravado em PNG sem metadados, até 1200 pixels por lado e 3 MB.

Imagens são acessadas por rota autenticada, com `PRODUCT_READ` e contexto de empresa/filial. Não são colocadas em `/static` nem publicadas por nome de arquivo. A imagem e sua substituição valem para a empresa; removê-la não apaga o produto.

Como as imagens ficam no PostgreSQL, o dump da aplicação já as inclui. Não há novo volume de uploads para configurar no Compose. Monitore o crescimento do banco e dos backups.

## Estoque

Abra **Estoque**, escolha um produto e informe uma movimentação:

- **Entrada:** quantidade positiva adicionada ao saldo.
- **Ajuste:** variação positiva ou negativa; `-2` retira duas unidades, não define o saldo como dois.
- **Motivo:** 5–240 caracteres, obrigatório e preservado no histórico.
- **Mínimo:** opcional, por filial; vazio desliga o alerta. O filtro de reposição inclui saldo igual ou inferior ao mínimo.

UN aceita `2` e rejeita `1,250`. KG aceita `1,250`. Vírgula/ponto é separador decimal; não use separador de milhar ou notação científica.

A confirmação protege contra clique duplo/reenvio. O UUID da operação identifica o lançamento; repetir a mesma operação não duplica a entrada/ajuste. Alterar os dados mantendo o mesmo UUID é erro. Para uma operação nova, reabra o formulário após a conclusão.

Movimentos não são editáveis/excluíveis. Corrija por um novo ajuste com motivo. O histórico mostra saldo após cada movimento, ator e aprovação, quando houver.

### Exceção de saldo negativo

Se o saldo final ficar negativo, abra **Aprovar exceção de estoque negativo** e peça que outra pessoa autorizada informe e-mail, senha e justificativa. Ela precisa estar ativa e ter `NEGATIVE_STOCK_APPROVE` na mesma filial.

Não há autoaprovação, uso de gerente de outra empresa/filial nem troca da sessão atual. A senha não volta ao HTML e não é gravada no histórico. A aprovação fica vinculada à operação. Falhas repetidas têm limitação por empresa/IP, em memória.

## Importação CSV/XLSX

1. Abra **Importar produtos** e baixe o modelo.
2. Preencha os dados, remova o exemplo e preserve a ordem das colunas.
3. Envie o arquivo para gerar a prévia.
4. Confira todas as páginas e baixe o relatório, se necessário.
5. Corrija erros no arquivo e gere outra prévia, ou confirme um lote válido.

### Modelo versão 1

| Coluna            | Obrigatória | Regra                                                    |
| ----------------- | ----------- | -------------------------------------------------------- |
| `versao`          | Sim         | Valor `1` em cada linha                                  |
| `sku`             | Sim         | Código novo, único na empresa e no arquivo               |
| `nome`            | Sim         | Até 160 caracteres                                       |
| `codigo_barras`   | Não         | Texto; único quando informado                            |
| `categoria`       | Não         | Nome de categoria ativa; cria a categoria se não existir |
| `unidade`         | Sim         | `UN` ou `KG`                                             |
| `preco`           | Não         | Positivo, duas casas; exige `PRICE_CHANGE`               |
| `estoque_inicial` | Não         | Não negativo; exige `STOCK_ADJUST`                       |
| `estoque_minimo`  | Não         | Não negativo; exige `STOCK_ADJUST`                       |
| `ncm`             | Não         | Oito dígitos ou vazio                                    |
| `cest`            | Não         | Sete dígitos ou vazio                                    |

CSV: UTF-8, com ou sem BOM, delimitador `;`, aspas duplas quando necessário. XLSX: uma aba, até 11 colunas, sem linhas vazias intermediárias, fórmulas, macros ou vínculos externos. SKU/código de barras devem ser células de texto no XLSX.

Limites: arquivo de 5 MB, 1.000 produtos, até 256 caracteres por célula e limites específicos de cada campo. XLSX tem limites adicionais de conteúdo descompactado. Arquivos criptografados, `.xls` e `.xlsm` não são aceitos.

Há limite de 20 prévias por pessoa/empresa em 24 horas. Uma prévia pode ser confirmada por 24 horas, apenas pela conta criadora e na mesma filial. A prévia registra dados e erros no banco, mas não guarda o arquivo original.

O relatório CSV protege células que possam ser interpretadas como fórmulas. Ele contém número do registro/linha, SKU, nome, situação e erros.

### O que acontece na confirmação

O sistema revalida os dados e permissões. O lote é uma única transação: categorias, produtos, preços, saldo/movimentos e auditoria são confirmados juntos. Erro implica nenhum produto importado no lote.

O processo cria somente produtos novos. SKU/código existente gera erro em vez de atualização silenciosa. Repetir a confirmação de um lote concluído não duplica cadastros. Enviar o mesmo arquivo novamente pode produzir outra prévia, mas ela será rejeitada por duplicidade se os produtos já tiverem sido cadastrados.

Preços e estoque informados valem somente para a filial selecionada no envio. Não é possível trocar a filial para confirmar os valores em outro lugar. Quem não possui permissão de preço/estoque deve deixar essas colunas vazias.

## Permissões

| Ação                                                | Permissão                                                                     |
| --------------------------------------------------- | ----------------------------------------------------------------------------- |
| Consultar produtos, categorias de seleção e imagens | `PRODUCT_READ`                                                                |
| Criar/editar/inativar produto, categoria e imagem   | `PRODUCT_WRITE`, além do acesso de consulta da tela                           |
| Alterar preço                                       | `PRICE_CHANGE`                                                                |
| Consultar saldos/movimentos                         | `STOCK_READ`                                                                  |
| Registrar entrada/ajuste e definir mínimo           | `STOCK_ADJUST`                                                                |
| Aprovar saldo negativo                              | `NEGATIVE_STOCK_APPROVE`, com reautenticação de outra pessoa                  |
| Importar                                            | `PRODUCT_IMPORT` + `PRODUCT_WRITE`, além das permissões dos valores opcionais |
| Busca de produto para venda                         | `PRODUCT_READ` ou `SALE_CREATE`; não implementa checkout                      |

O operador de caixa inicial pode consultar produtos/preços, mas não recebe saldos ou acesso ao estoque. O estoquista inicial pode importar cadastro/estoque, mas não preços: `PRICE_CHANGE` não integra seu perfil padrão.

## Diagnóstico

- **SKU/código duplicado:** corrija o arquivo ou edite individualmente o produto existente.
- **Mudou desde a prévia:** gere outra prévia depois de conferir o catálogo/permissões.
- **Preço/mínimo mudou:** reabra a página; há versão de concorrência para evitar sobrescrita.
- **Erro de imagem:** confirme conteúdo, extensão, tamanho e dimensões.
- **Saldo negativo:** confira quantidade, filial e autorização de outra pessoa.
- **Arquivo grande ou XLSX inválido:** divida em lotes menores usando o modelo simples.
- **403 em UUID/imagem/relatório:** confira portal, filial e conta; não remova o filtro de tenant.

Use o `X-Correlation-ID` e logs categorizados, sem registrar senhas de aprovação, conteúdo dos arquivos ou dados pessoais desnecessários.

Venda/caixa/desconto continuam na Etapa 3; documentos fiscais/devoluções, na Etapa 4. Não existe baixa automática de venda nesta entrega.
