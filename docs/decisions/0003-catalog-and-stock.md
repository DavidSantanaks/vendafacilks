# ADR 0003 — Catálogo, importação e estoque por filial

Data: 19/10/2026. Estado: adotada na versão `0.2.0-SNAPSHOT`.

## Contexto

A Etapa 2 exige catálogo compartilhado, preço/saldo por filial, imagens privadas, importação com prévia e estoque concorrente. A solução deve preservar o contexto confiável de tenant/filial e funcionar no Compose existente, sem serviço adicional.

## Decisões

- Migration V3 aditiva, sem reescrever V1/V2 ou apagar dados. Todas as referências empresariais usam FKs compostas.
- Consultas de catálogo/estoque usam `JdbcTemplate`, parâmetros preparados e DTOs; controle de acesso permanece nos serviços. A transação gerenciada pelo JPA usa o mesmo DataSource e inclui os comandos JDBC e a auditoria.
- SKU é normalizado em maiúsculas e único por tenant. Código de barras opcional também é único por tenant; zeros são preservados e não há presunção de certificação GTIN.
- UN aceita inteiros e KG aceita três casas. Valores monetários usam `BigDecimal`/`NUMERIC(19,2)`, quantidades `NUMERIC(19,3)`. Vírgula ou ponto significa decimal; agrupamento de milhares e notação científica são rejeitados.
- Preço é da filial ativa, com versão para detectar edição concorrente e histórico imutável. Busca de venda retorna apenas produtos ativos com preço ativo; saldos ficam ausentes para quem não possui `STOCK_READ`.
- Movimentação bloqueia o produto e o saldo na transação. Isso serializa alterações concorrentes do mesmo produto, inclusive entre filiais; é uma escolha conservadora para esta entrega e pode ser refinada após medição.
- Operação possui UUID e hash de produto/tipo/quantidade/motivo. Repetição idêntica pelo mesmo ator retorna o movimento original; dados divergentes são rejeitados. O banco também exige unicidade por tenant/filial/operação.
- Saldo negativo exige reautenticação de outra identidade ativa com `NEGATIVE_STOCK_APPROVE` na mesma filial, mais justificativa. A autorização é gravada no movimento; não troca a sessão do operador. A limitação de tentativas reutiliza o mecanismo local de login com chave própria.
- Movimentos e histórico de preços recebem a proteção de imutabilidade já usada pela auditoria. Correção de estoque cria um novo ajuste.
- Uma imagem por produto é normalizada em PNG e armazenada em `BYTEA`, com limite de 3 MB após conversão. Isso evita arquivos executáveis, caminhos externos e volumes adicionais e inclui as imagens no dump PostgreSQL. O custo é o aumento do banco/backups; avaliar object storage quando o volume justificar.
- Entrada de imagem limita arquivo a 5 MB, dimensões a 4096 por lado e 8 milhões de pixels antes da decodificação. A saída tem no máximo 1200 pixels por lado, sem reutilizar nome ou metadados do upload.
- Importação versão 1 cria somente produtos novos. Nunca sobrescreve por SKU/código. Categorias novas são criadas ao confirmar; preço, estoque inicial e mínimo são opcionais e limitados à filial/permissão atuais.
- Prévia é persistida no banco com ator, filial, hash do arquivo, linhas e erros. Não retemos o arquivo original. Confirmação usa dados persistidos, revalida cadastro/permissões e executa todo o lote em uma transação. Uma falha desfaz o lote; repetição de lote concluído não o reaplica.
- Limites: 1.000 linhas, 5 MB, 20 prévias por ator/tenant em 24 horas, prévia confirmável por 24 horas. Linhas são paginadas em grupos de 50, com relatório CSV protegido contra fórmulas.
- CSV usa Apache Commons CSV 1.14.1, UTF-8 estrito e ponto e vírgula. XLSX usa Apache POI 5.5.1 com leitura SAX de células, sem carregar `XSSFWorkbook` do usuário ou avaliar fórmulas. Antes de abrir o pacote são verificados até 100 entradas ZIP, até 8 MB por entrada e 20 MB no total descompactado; fórmulas, macros, planilhas múltiplas e estruturas externas não são aceitas.
- `XSSFWorkbook` é usado somente para gerar o pequeno modelo XLSX controlado pela aplicação.

## Limites

Não há venda, baixa de checkout, devolução ou integração fiscal nesta entrega. Os tipos SALE/REVERSAL serão adicionados com seus módulos e referências de origem; não oferecemos baixas de venda fictícias na tela de estoque.

A importação é síncrona e limitada, com confirmação integral. Atualização de catálogo em massa, processamento maior em background e limpeza/retenção automatizada de prévias ficam para evolução posterior. Não é feita captura automática de imagens a partir de URLs em planilhas.

O lock do tenant serializa cadastro/importação e evita disputas de SKU/categoria; o lock de produto favorece consistência em vez de paralelismo máximo. Não há benchmark de escala comercial nesta entrega.

## Referências técnicas

- [SDD: etapas e requisitos](../../SDD-VendaFacilKs.md)
- [Apache Commons CSV: versões](https://commons.apache.org/proper/commons-csv/changes.html)
- [Apache POI: versão estável](https://poi.apache.org/download.html)
- [Apache POI: leitura XSSF/SAX](https://poi.apache.org/components/spreadsheet/how-to.html#xssf_sax_api)
