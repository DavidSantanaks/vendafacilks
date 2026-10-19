# vendaFacil

PDV web multiempresa e multifilial em **Java 25, Spring Boot 4.0.8, PostgreSQL, Spring MVC e Thymeleaf**, com Bootstrap 5 e JavaScript puro.

Este repositório implementa a fundação multiempresa e as **Etapas 2 e 3 do [SDD-VendaFacilKs.md](SDD-VendaFacilKs.md)**: catálogo, preços, estoque, importação, PDV, pagamentos manuais, descontos e caixa por filial. A versão atual é `0.3.0-SNAPSHOT`. **As vendas funcionam em demonstração local; emissão fiscal, devoluções e liberação comercial de produção pertencem às próximas etapas.**

## Executar com Docker

Requisito: Docker Desktop com o mecanismo Linux iniciado e o comando `docker compose` disponível. Java e Node não são necessários para executar os containers.

```powershell
Copy-Item .env.example .env
# Edite .env: configure DB_PASSWORD e DEMO_PASSWORD.
docker compose up -d --build
```

Se o terminal informar que `docker-credential-desktop` não foi encontrado no `PATH`, use o iniciador Windows:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docker-up.ps1
```

O iniciador localiza o Docker Desktop e inclui sua pasta no `PATH` somente do processo que inicia os containers. A configuração de credenciais e o `PATH` permanente do Windows são preservados.

Abra:

| Empresa                           | Endereço                         | Conta fictícia                  |
| --------------------------------- | -------------------------------- | ------------------------------- |
| Mercado Boa Praça                 | http://boa-praca.localhost:8080  | `ana@boa-praca.example`         |
| Mercado Boa Praça, somente Centro | http://boa-praca.localhost:8080  | `caixa@boa-praca.example`       |
| Hortifruti Vila Verde             | http://vila-verde.localhost:8080 | `admin@vila-verde.example`      |
| Administração SaaS                | http://admin.localhost:8080      | `plataforma@vendafacil.example` |

A senha de todas as contas fictícias é o valor de **`DEMO_PASSWORD`** configurado antes da primeira inicialização. O valor de exemplo é `VendaFacil-Local-2026!`. Nenhum dado de empresa, e-mail ou documento fiscal desta demonstração é real. Os dados iniciais só são criados em banco vazio, nos profiles `local` ou `test`; alterar a variável depois não altera senhas já armazenadas.

O Chrome e o Edge resolvem `*.localhost` para o computador local. Se necessário, adicione `127.0.0.1 boa-praca.localhost vila-verde.localhost admin.localhost` ao arquivo `hosts` do Windows. A raiz `localhost:8080` é rejeitada deliberadamente: o subdomínio define a empresa.

```powershell
docker compose logs -f app
docker compose down
```

`down` preserva os dados no volume. O Compose local publica banco e aplicação apenas em `127.0.0.1`.

## Desenvolver com Java local

Requisitos: JDK 25 e Docker para o PostgreSQL. O Maven Wrapper baixa o Maven **3.9.11** na primeira execução. Não é necessário instalar Maven globalmente.

```powershell
Copy-Item .env.example .env
# Configure as senhas no .env.
docker compose up -d db
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start.ps1
```

O script lê `.env` como dados, configura o profile `local` e executa `mvnw.cmd spring-boot:run`. A política `Bypass` é apenas desse processo. Se houver JDK portátil em `.tools/jdk`, ele poderá ser usado quando `JAVA_HOME` não estiver configurado.

Em Linux/macOS, exporte as variáveis do `.env`, ajuste `JAVA_HOME` e execute:

```sh
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

O servidor usa a porta 8080. Para outro banco/porta, defina `DB_URL`, por exemplo `jdbc:postgresql://localhost:5432/vendafacil`. `.env` é lido pelo Docker Compose e pelo script PowerShell; não é lido automaticamente pelo Spring Boot.

## Testes e qualidade

```powershell
.\mvnw.cmd verify
npm.cmd ci --prefix tooling
npm.cmd run lint --prefix tooling
npm.cmd run format:check --prefix tooling
```

Os testes de integração usam **PostgreSQL real via Testcontainers**. Eles não são ignorados se o Docker estiver indisponível. Como alternativa, aponte para um banco PostgreSQL de teste separado:

```powershell
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/vendafacil_test'
$env:TEST_DB_USER = 'vendafacil'
$env:TEST_DB_PASSWORD = 'senha-do-banco-de-teste'
.\mvnw.cmd verify
```

O Flyway inicializa o banco de teste e o profile `test` cria as fixtures. Os testes executam transações com rollback. Não aponte `TEST_DB_URL` para banco de clientes ou desenvolvimento em uso.

```powershell
.\mvnw.cmd spotless:apply
npm.cmd run format --prefix tooling
npm.cmd run vendor --prefix tooling
```

Spotless/Google Java Format verifica Java e whitespace de SQL/YAML; Prettier formata HTML preservando atributos Thymeleaf, CSS e JS; ESLint verifica JavaScript. A pipeline executa esses controles, os testes de isolamento e o empacotamento. Os assets Bootstrap são servidos localmente, com licenças incluídas; não dependem de CDN. Node é usado somente no desenvolvimento/CI.

## O que está disponível

- Login por empresa com BCrypt, CSRF, limitação de tentativas e sessão host-only.
- Validação de host; cabeçalhos encaminhados não selecionam o tenant.
- Vínculos ativos, papéis e permissões por filial, revalidados em cada acesso.
- Seleção e troca de filial, visão geral, equipe e criação de usuários com perfil limitado ao concedente.
- Criação, edição, ativação e desativação de filiais autorizadas.
- Painel SaaS segregado: criar, ativar e suspender empresas sem acesso implícito aos dados operacionais.
- Auditoria protegida contra alterações, IDs de correlação, respostas de erro e healthcheck.
- Interface responsiva com ajuda acessível por toque e teclado, navegação mobile e estados vazios/erros.
- Flyway com FKs compostas que impedem vínculos entre empresas diferentes.
- Catálogo paginado com busca por nome/SKU/código de barras, categorias, ativação e atributos NCM/CEST opcionais.
- Preços independentes por filial, ativação e histórico imutável de alterações.
- Estoque por filial, unidades UN/KG, mínimo/reposição, entradas e ajustes com histórico imutável.
- Concorrência e idempotência nas movimentações; saldo negativo exige outra pessoa autorizada, senha e justificativa.
- Imagens PNG/JPEG validadas, normalizadas e privadas, incluídas no backup PostgreSQL.
- Importação CSV/XLSX com modelos versão 1, prévia, erros por linha, relatório CSV e confirmação atômica/idempotente.

- PDV com leitor de código de barras como teclado, carrinho UN/KG, descontos por item/venda e cálculo final no servidor.
- Dinheiro, Pix, débito e crédito manuais/mistos; troco somente sobre dinheiro recebido.
- Sessões de caixa, fundo inicial, suprimento, sangria com limite/aprovação e fechamento cego em duas etapas.
- Aprovações individuais por senha, limites acumulados e vínculo à operação, sem trocar a sessão do operador.
- Checkout atômico e idempotente com snapshots, baixa de estoque, pagamentos, caixa e auditoria.
- Recibo de demonstração sem valor fiscal, impressão pelo navegador e relatório paginado/CSV por filial/período.

Devoluções e integração fiscal pertencem à Etapa 4. Não há documento fiscal nem autorização simulada. O servidor bloqueia vendas nos profiles `production` e `staging`, mesmo combinados com `local`. Convites verificados, edição de definições de perfis, MFA, logo por upload e habilitação de módulos ainda estão pendentes. Veja [o estado de implementação](docs/implementation-status.md).

## Venda e caixa em demonstração

Após atualizar com `docker compose up -d --build`, a migration V4 é aplicada automaticamente, preservando o catálogo e os saldos existentes. Faça backup antes de atualizar um banco importante. Não remova volumes para atualizar.

1. Entre como administrador, selecione a filial e abra **Meu caixa**. Cadastre um caixa físico, por exemplo `Caixa 01`.
2. O operador entra em sua própria conta, abre esse caixa com o fundo inicial e acessa **Frente de caixa**.
3. Adicione produtos com preço ativo, informe quantidades/pesos e os pagamentos efetivamente recebidos no teste. Clique em **Calcular valores** para conferir.
4. Se necessário, outra pessoa autorizada preenche a aprovação de desconto/estoque negativo. Confirme a venda e imprima o recibo de teste.
5. Ao encerrar, conte o dinheiro e declare a contagem em **Meu caixa**. Só depois o esperado/diferença ficam disponíveis. Justifique e obtenha aprovação quando exigida, então conclua o fechamento.

Padrões da empresa: operador 0%, gerente 10%, administrador 20%; teto acumulado de 20%; fechamento cego ativo; toda sangria exige aprovação, com máximo de R$ 1.000,00 por operação; qualquer diferença exige aprovação. O administrador ajusta as políticas na página do caixa. Estes valores iniciais são decisões técnicas documentadas, a validar com a operação. Consulte [o roteiro completo de venda e caixa](docs/runbooks/sales-and-cash.md).

## Produtos, preços, estoque e importação

Selecione a filial e abra **Produtos**. Cadastre o produto uma vez para a empresa e defina seu preço na página do produto. Troque de filial para informar outro preço. A inativação preserva o histórico; a busca de venda só retorna produtos ativos com preço ativo na filial atual.

Em **Estoque**, registre entradas ou ajustes como variação do saldo: `+2` é informado como `2` e retira-se com `-2`. UN aceita inteiros; KG aceita até três casas, como `1,250`. Não use separador de milhar. Cada operação tem uma identificação que impede duplicidade no reenvio. Saldo negativo exige credenciais de **outra pessoa**, autorizada nessa filial, e justificativa. Estoque mínimo gera um filtro de reposição.

Em **Importar produtos**, baixe o modelo CSV ou XLSX, gere a prévia e confira o relatório antes de confirmar. O lote importa somente produtos novos; SKU/código repetido é erro, sem atualização silenciosa. Preços e estoque opcional pertencem à filial ativa e exigem suas respectivas permissões. Limites: 5 MB, 1.000 produtos, 20 prévias por pessoa em 24 horas e validade de 24 horas para confirmação. Um erro impede a gravação de todo o lote.

As imagens aceitam PNG/JPEG de até 5 MB, máximo de 4096 pixels por lado e 8 milhões de pixels. São regravadas em PNG de até 1200 pixels por lado e 3 MB e armazenadas no banco. Não é necessário criar um volume de uploads.

O Compose aplica automaticamente a migration **V3**, preservando os dados existentes. Atualize a imagem com `docker compose up -d --build`. Consulte [o manual da Etapa 2](docs/runbooks/catalog-and-stock.md) para permissões, formatos e operação.

## Recuperar senha e administrar os portais

No login de qualquer portal, inclusive `admin.localhost`, clique em **Esqueci minha senha** e informe o e-mail cadastrado. O link é válido por 30 minutos e funciona uma única vez no portal solicitado. Escolha e confirme a nova senha; depois entre normalmente. As sessões antigas são encerradas. Como a identidade é global, a nova senha vale também nos outros portais em que a pessoa tem vínculo.

**Nos testes locais, os e-mails ficam em http://localhost:8025**, na caixa Mailpit incluída no Compose. Abra a mensagem destinada à conta que pediu recuperação e use o link. E-mails de teste não são enviados à internet. A porta da caixa pode ser alterada por `MAILPIT_HTTP_PORT`. Para Java fora do Docker, inicie `docker compose up -d db mailpit`; o SMTP local fica em `localhost:1025`.

Em **Equipe e acessos**, o administrador da filial pode pesquisar pessoas, alterar o perfil, bloquear/restaurar acesso e enviar recuperação ao e-mail cadastrado. A alteração vale apenas para a filial ativa e respeita os limites do concedente; seu próprio acesso precisa ser gerenciado por outro administrador autorizado.

No **painel SaaS**, os cards permitem abrir o portal da empresa em outra aba, editar o nome, consultar usuários e ativar/suspender a empresa. A página **Usuários** tem busca, filtro por empresa e paginação; permite editar nome, bloquear/restaurar a conta global e solicitar recuperação. Abrir um portal leva ao login normal da empresa.

Para SMTP real, configure `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_SMTP_AUTH` e `MAIL_STARTTLS`. O Compose local usa `mailpit:1025` por padrão; o de produção exige SMTP/remetente configurados. Ajuste `PUBLIC_PORT` quando a porta pública diferir da padrão. Consulte [a decisão de recuperação e administração](docs/decisions/0002-recovery-and-administration.md).

## Configuração

| Variável                                            | Uso                                                                                  |
| --------------------------------------------------- | ------------------------------------------------------------------------------------ |
| `SPRING_PROFILES_ACTIVE`                            | `local`, `test`, `staging` ou `production`; sem profile, a configuração é restritiva |
| `DB_URL`                                            | URL JDBC PostgreSQL                                                                  |
| `DB_USER`, `DB_PASSWORD`                            | Credenciais fora do repositório                                                      |
| `APP_PORT`                                          | Porta do servidor local, padrão 8080                                                 |
| `APP_BASE_DOMAIN`                                   | Domínio-base controlado; `localhost` em desenvolvimento                              |
| `DEMO_PASSWORD`                                     | Senha das fixtures locais, de 12 a 64 caracteres e até 72 bytes UTF-8                |
| `APP_TRUSTED_PROXY_REGEX`                           | IPs de proxy confiáveis, obrigatório em produção                                     |
| `APP_BOOTSTRAP_ADMIN_ENABLED`                       | `true` somente para provisionar o primeiro Super Admin                               |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | Conta inicial da plataforma; remover depois do provisionamento                       |

Produção exige domínio controlado, HTTPS, banco sem dados fictícios e proxy confiável. Há um [Compose de referência](docker-compose.production.yml) e um [guia de operação](docs/runbooks/local-and-production.md). Esses arquivos não representam homologação de produção. A configuração fiscal e o piloto real continuam sujeitos aos critérios do SDD.

Para instalar em um servidor, consulte o [guia completo do sistema e da infraestrutura no Ubuntu Server 26.04 LTS](GUIA-SISTEMA-UBUNTU-SERVER-26.md), com DNS, HTTPS, SMTP, primeiro administrador, backup, restauração e atualização.

## Estrutura e decisões

O código está organizado por funcionalidade: `tenant`, `identity`, `store`, `administration`, `catalog`, `catalog/importing`, `inventory` e `shared`. Controllers chamam serviços; os módulos de catálogo/estoque usam JDBC parametrizado na mesma transação de banco que a auditoria JPA. Os próximos módulos comerciais serão adicionados nas entregas correspondentes.

- [Arquitetura e limites de confiança](docs/architecture/foundation.md)
- [Contratos HTTP implementados](docs/api/foundation.md)
- [ADRs](docs/decisions/0001-foundation.md)
- [Verificação desta entrega](docs/verification.md)
- [Debug e operação](docs/runbooks/local-and-production.md)
- [Catálogo, importação e estoque](docs/runbooks/catalog-and-stock.md)
- [Decisão de arquitetura da Etapa 2](docs/decisions/0003-catalog-and-stock.md)

O diretório `.tools` contém apenas ferramentas portáteis usadas nesta máquina durante a implementação e está ignorado pelo Git. `.local` contém caches e bancos temporários. O projeto distribuível não depende desses diretórios.
