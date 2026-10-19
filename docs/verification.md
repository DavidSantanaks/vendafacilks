# Verificação das entregas

## Etapa 2 — versão 0.2.0

Data: 19/10/2026. Ambiente: Windows, JDK Temurin 25.0.4.1, Maven 3.9.11, Spring Boot 4.0.8, Docker Desktop e PostgreSQL 17.11 via Testcontainers.

- Maven `spotless:apply verify`: **BUILD SUCCESS**, **70 testes**, zero falhas, erros ou ignorados. JAR: `target/vendafacil-0.2.0-SNAPSHOT.jar`.
- 18 cenários de integração novos: catálogo/preços/saldos por escopo, UUIDs estrangeiros, permissões, imagens, histórico imutável, controle de versão, aprovação negativa, idempotência e importação integral.
- Retiradas concorrentes do último item: somente uma é efetivada; a outra exige aprovação, sem saldo negativo não autorizado.
- Reenvios simultâneos de entrada: um único movimento e uma única alteração de saldo.
- Importação revalida conflito surgido depois da prévia, expiração, filial e permissão de preço revogada, sem gravação parcial.
- Migração real V2 → V3 em schema isolado preserva empresa e identidade já cadastradas e cria o catálogo vazio sem reescrever migrations anteriores.
- Sete testes de entrada: UN/KG, casas decimais, imagem falsa/extensão/dimensões, CSV UTF-8/BOM/aspas/limite, XLSX com fórmulas/identificador numérico, ZIP expandido e proteção contra fórmula no CSV.
- Os 45 testes da fundação continuam passando. Oito testes do validador de commits também passaram.
- Docker: imagem multiestágio `0.2.0-SNAPSHOT` construída e iniciada em projeto separado, com banco próprio e somente portas loopback de homologação. Não foram usados dados do ambiente existente.
- Chrome real headless: cadastro de categoria/produto, preço da filial, imagem, entrada, mínimo, erro de quantidade fracionada em UN, ajuste negativo com saldo não negativo, importação CSV confirmada, modelo XLSX validado e download de relatório.
- Navegador: troca de filial confirma ausência do preço de outra unidade; conta de operador não vê menu de estoque e recebe saldo nulo na busca JSON.
- Viewports desktop 1440 × 1050 e mobile 390 × 844; catálogo, produto, estoque, importação e formulário verificados. Rolagem horizontal fica restrita à tabela, sem ampliar a página.
- Tema roxo/off-white preservado; ajuda por Enter/Escape conferida. Capturas e dados do ensaio permanecem em `.tools` e nos recursos Docker exclusivos de teste.
- Documentação de sistema, Ubuntu, implementação, contratos, operação e ADR atualizada. Os 43 blocos Bash do guia Ubuntu passaram na conferência de sintaxe, e os links locais foram conferidos.

Esta verificação não inclui PDV, venda, caixa, emissão fiscal, impressora física, SMTP externo, servidor Ubuntu real, carga comercial ou restore operacional do servidor. O ensaio V2 → V3 verifica evolução de schema e preservação de cadastro, não substitui um teste completo de recuperação de desastre.

## Histórico — entrega 0.1.0

Data: 18/10/2026. Ambiente: Windows, JDK Temurin 25.0.4.1, Maven Wrapper/Maven 3.9.11, Spring Boot 4.0.8, PostgreSQL real 17.11.

### Resultados da versão 0.1.0

- `mvnw.cmd spotless:apply verify`: **BUILD SUCCESS**; **45 testes**, zero falhas, erros ou ignorados; JAR executável gerado em `target/vendafacil-0.1.0-SNAPSHOT.jar`.
- 32 cenários de integração MVC/banco: isolamento, sessão, CSRF, credenciais, permissões, grants, auditoria, suspensão, cadastro, desativação/reativação, identidade em múltiplas empresas, recuperação por token e gestão administrativa.
- 12 cenários parametrizados de resolução de host e um teste de limitação de login.
- ESLint e Prettier: aprovados. Google Java Format/Spotless: aprovado.
- `npm audit`: zero vulnerabilidades reportadas nesta execução.
- Compose local e produção: sintaxe/interpolação estrutural validadas por `docker compose config --no-interpolate`. A imagem `postgres:17.11-alpine` foi confirmada no registry oficial.
- Chrome instalado, headless: login real HTTP, escolha de filial, dashboard, menu mobile, busca e estados vazios, ajuda por Enter/Escape e ausência de erro JavaScript/CSP.
- Viewports: 1440 × 1050 e 390 × 844, com inspeção das capturas desktop/mobile e sem overflow horizontal no dashboard mobile.
- Cookie real conferido: HttpOnly, SameSite=Lax e domínio `boa-praca.localhost`, sem compartilhamento com `vila-verde.localhost`.
- JAR final iniciado novamente; cadastro, desativação e reativação de filial conferidos também pelos formulários reais no Chrome.
- Git local na branch `main`, com histórico local autorizado pelo usuário e autor configurado somente neste repositório.
- Validação posterior com Docker Desktop ativo: `scripts/docker-up.ps1` construiu a imagem e iniciou os containers; PostgreSQL saudável, Flyway aplicado, `/actuator/health` com HTTP 200/UP e login da Boa Praça com HTTP 200. O iniciador corrige o PATH do processo para localizar os auxiliares de credenciais do Docker Desktop.
- Recuperação e administração: 45 testes executados com PostgreSQL via Testcontainers, incluindo token com hash/contexto, expiração, uso único, substituição por novo pedido, recusa de conta bloqueada, invalidação de sessões e limites da gestão de acessos.
- Chrome/Mailpit: entrega SMTP local após commit, pedido pelo administrador e pelo próprio usuário, redefinição e confirmação de senha, mensagem de conclusão, token consumido recusado e senha nova funcionando. Recuperação do portal SaaS também entregou link para o subdomínio correto, sem redefinir a senha existente do administrador da plataforma.
- Cards SaaS: abertura do portal em outra aba, filtro de usuários por empresa e atribuição de perfil pela interface conferidos. Ajuste de layout mobile confirmado em 390 × 844: página sem transbordamento horizontal, com rolagem restrita à tabela.
- Três contas fictícias adicionais usadas no ensaio de navegador foram bloqueadas pela própria gestão SaaS ao final, mantendo auditoria e dados de teste.
- Tema roxo/off-white conferido no Chrome em login, recuperação, dashboard, equipe e gestão SaaS, com capturas desktop/mobile, cores calculadas, foco de teclado e ausência de transbordamento da página no viewport 390 × 844. ESLint, Prettier e build Docker aprovados para a alteração visual.
- Padrão de commits: oito testes do validador aprovados. Hook `commit-msg` ativo no repositório e rejeição real conferida para título acima de 50 caracteres e corpo sem linha em branco. Autor local: David Santana.

### Limites da verificação da versão 0.1.0

Na verificação inicial, o mecanismo Docker Desktop estava parado e foi usado um PostgreSQL portátil exclusivo de testes. Depois da correção de PATH, o build da imagem Docker e a inicialização por Compose foram validados. A suíte ampliada de 45 testes também foi executada via Testcontainers com Docker nesta máquina. Certificados/proxy HTTPS, SMTP externo e a execução remota do workflow GitHub ainda não foram verificados.

Não foram executados cenários comerciais, fiscais, impressora física, carga, restauração ou homologação: os módulos correspondentes ainda não integram esta entrega. Os arquivos de infraestrutura de produção são referências preparatórias.

Ferramentas, logs, screenshots, caches e dados transitórios desta execução ficam em `.tools`/`.local` e não entram no repositório.
