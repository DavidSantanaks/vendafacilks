# vendaFacil

PDV web multiempresa e multifilial em **Java 25, Spring Boot 4.0.8, PostgreSQL, Spring MVC e Thymeleaf**, com Bootstrap 5 e JavaScript puro.

Este repositório implementa o **primeiro incremento verificável definido na seção 23 do [SDD-VendaFacilKs.md](SDD-VendaFacilKs.md)**: duas empresas acessam seus subdomínios, autenticam usuários e selecionam somente as filiais autorizadas, sem compartilhar dados ou permissões. A preparação da Etapa 0 e a fundação de acesso da Etapa 1 estão implementadas. **Não é a V1 comercial completa.**

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

| Empresa | Endereço | Conta fictícia |
| --- | --- | --- |
| Mercado Boa Praça | http://boa-praca.localhost:8080 | `ana@boa-praca.example` |
| Mercado Boa Praça, somente Centro | http://boa-praca.localhost:8080 | `caixa@boa-praca.example` |
| Hortifruti Vila Verde | http://vila-verde.localhost:8080 | `admin@vila-verde.example` |
| Administração SaaS | http://admin.localhost:8080 | `plataforma@vendafacil.example` |

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

Catálogo, importação, uploads, estoque, PDV, caixa, vendas, descontos, devoluções, relatórios comerciais e integração fiscal pertencem às próximas etapas do SDD. Não há emissão fiscal nem autorização simulada. Convites verificados, criação/edição de definições de perfis, MFA, logo por upload e habilitação de módulos ainda estão pendentes. A atribuição de perfis, bloqueio/restauração de acessos e recuperação de senha já estão disponíveis. Veja [o estado de implementação](docs/implementation-status.md).

## Recuperar senha e administrar os portais

No login de qualquer portal, inclusive `admin.localhost`, clique em **Esqueci minha senha** e informe o e-mail cadastrado. O link é válido por 30 minutos e funciona uma única vez no portal solicitado. Escolha e confirme a nova senha; depois entre normalmente. As sessões antigas são encerradas. Como a identidade é global, a nova senha vale também nos outros portais em que a pessoa tem vínculo.

**Nos testes locais, os e-mails ficam em http://localhost:8025**, na caixa Mailpit incluída no Compose. Abra a mensagem destinada à conta que pediu recuperação e use o link. E-mails de teste não são enviados à internet. A porta da caixa pode ser alterada por `MAILPIT_HTTP_PORT`. Para Java fora do Docker, inicie `docker compose up -d db mailpit`; o SMTP local fica em `localhost:1025`.

Em **Equipe e acessos**, o administrador da filial pode pesquisar pessoas, alterar o perfil, bloquear/restaurar acesso e enviar recuperação ao e-mail cadastrado. A alteração vale apenas para a filial ativa e respeita os limites do concedente; seu próprio acesso precisa ser gerenciado por outro administrador autorizado.

No **painel SaaS**, os cards permitem abrir o portal da empresa em outra aba, editar o nome, consultar usuários e ativar/suspender a empresa. A página **Usuários** tem busca, filtro por empresa e paginação; permite editar nome, bloquear/restaurar a conta global e solicitar recuperação. Abrir um portal leva ao login normal da empresa.

Para SMTP real, configure `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_SMTP_AUTH` e `MAIL_STARTTLS`. O Compose local usa `mailpit:1025` por padrão; o de produção exige SMTP/remetente configurados. Ajuste `PUBLIC_PORT` quando a porta pública diferir da padrão. Consulte [a decisão de recuperação e administração](docs/decisions/0002-recovery-and-administration.md).

## Configuração

| Variável | Uso |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `local`, `test`, `staging` ou `production`; sem profile, a configuração é restritiva |
| `DB_URL` | URL JDBC PostgreSQL |
| `DB_USER`, `DB_PASSWORD` | Credenciais fora do repositório |
| `APP_PORT` | Porta do servidor local, padrão 8080 |
| `APP_BASE_DOMAIN` | Domínio-base controlado; `localhost` em desenvolvimento |
| `DEMO_PASSWORD` | Senha das fixtures locais, de 12 a 64 caracteres e até 72 bytes UTF-8 |
| `APP_TRUSTED_PROXY_REGEX` | IPs de proxy confiáveis, obrigatório em produção |
| `APP_BOOTSTRAP_ADMIN_ENABLED` | `true` somente para provisionar o primeiro Super Admin |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | Conta inicial da plataforma; remover depois do provisionamento |

Produção exige domínio controlado, HTTPS, banco sem dados fictícios e proxy confiável. Há um [Compose de referência](docker-compose.production.yml) e um [guia de operação](docs/runbooks/local-and-production.md). Esses arquivos não representam homologação de produção. A configuração fiscal e o piloto real continuam sujeitos aos critérios do SDD.

Para instalar em um servidor, consulte o [guia completo do sistema e da infraestrutura no Ubuntu Server 26.04 LTS](GUIA-SISTEMA-UBUNTU-SERVER-26.md), com DNS, HTTPS, SMTP, primeiro administrador, backup, restauração e atualização.

## Estrutura e decisões

O código está organizado por funcionalidade: `tenant`, `identity`, `store`, `administration` e `shared`. Controllers chamam serviços; o acesso a dados fica nos serviços/repositórios. Os módulos comerciais serão adicionados nas entregas correspondentes, evitando classes vazias que aparentem funcionalidades concluídas.

- [Arquitetura e limites de confiança](docs/architecture/foundation.md)
- [Contratos HTTP implementados](docs/api/foundation.md)
- [ADRs](docs/decisions/0001-foundation.md)
- [Verificação desta entrega](docs/verification.md)
- [Debug e operação](docs/runbooks/local-and-production.md)

O diretório `.tools` contém apenas ferramentas portáteis usadas nesta máquina durante a implementação e está ignorado pelo Git. `.local` contém caches e bancos temporários. O projeto distribuível não depende desses diretórios.
