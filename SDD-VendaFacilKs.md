# vendaFacil — Software Design Document (SDD)

> **Status:** Rascunho consolidado para implementação  
> **Versão do documento:** `1.0.0-draft`  
> **Data de referência:** 18/10/2026  
> **Idioma:** Português do Brasil (`pt-BR`)  
> **Produto:** **vendaFacil** (nome e domínio provisórios)  
> **Responsável pelo produto:** a definir  
> **Primeira UF fiscal:** São Paulo  
> **Cliente-piloto de referência:** minimercado/mercearia, com regime tributário a definir quando houver cliente real

---

## 0. Como utilizar este documento

Este SDD é o contrato inicial de arquitetura e comportamento do produto. Não é prova de conformidade fiscal, nem substitui homologação do provedor fiscal ou orientação de contador responsável.

**Status das decisões:**

- **[APROVADO]**: requisito confirmado durante o planejamento do produto.
- **[PROPOSTO]**: padrão técnico sugerido para iniciar o desenvolvimento; pode ser refinado sem mudar o objetivo do produto.
- **[PENDENTE]**: depende de escolha futura, validação normativa ou cliente real.

**Regra de mudança:** qualquer alteração em requisitos aprovados deve ser registrada em `docs/decisions/` (ADR), atualizada aqui e acompanhada da revisão dos testes de aceitação impactados. O documento deve evoluir junto ao código.

**Definição de V1:** a primeira versão comercial só poderá ser liberada após passar pelos requisitos funcionais aplicáveis, testes de isolamento de dados, fluxos financeiros e homologação fiscal dos estabelecimentos que utilizarem emissão integrada. Módulos opcionais podem permanecer desabilitados, desde que isso não viole obrigações legais do estabelecimento.

---

## 1. Visão, objetivos e público

### 1.1 Visão do produto

O **vendaFacil** será um **PDV web responsivo, multiempresa e com múltiplas filiais**, direcionado inicialmente a pequenos comerciantes e minimercados. Deve oferecer operação simples de produtos, estoque, vendas e caixa e permitir habilitar integração fiscal quando configurada e necessária.

### 1.2 Objetivos [APROVADO]

1. Ter uma única aplicação para diversas empresas clientes, com acesso por subdomínio.
2. Permitir múltiplas filiais por empresa desde a V1.
3. Ser utilizável em desktop, tablet e smartphone, sem aplicativo móvel nativo.
4. Oferecer operação comercial simples, com interface didática e ajuda contextual.
5. Ter um núcleo de vendas e caixa independente do provedor fiscal.
6. Suportar NFC-e modelo 65 e NF-e modelo 55 por **provedor fiscal especializado**.
7. Priorizar segurança, integridade do caixa, auditabilidade e manutenção facilitada.
8. Utilizar código, arquivos e documentação com formatação padronizada e legível.

### 1.3 Não objetivos da V1 [APROVADO/PROPOSTO]

- Integração TEF, adquirentes, gateways de PIX ou liquidação bancária automática.
- Operação inteiramente offline sem conexão com o servidor de aplicação.
- Aplicativos Android/iOS nativos.
- Integração direta com balanças físicas, compra/fornecedor completo, promoções avançadas e fidelidade.
- Transferência de estoque entre filiais, integração ERP externa genérica, assinaturas SaaS e faturamento automático.
- Impressão silenciosa por agente local; a V1 usa impressão via navegador (com testes em impressoras reais).
- Suporte fiscal homologado para todas as UFs desde o primeiro lançamento.

---

## 2. Stack tecnológica

| Camada | Tecnologia | Decisão |
|---|---|---|
| Linguagem | **Java 25 LTS** | **[APROVADO]** |
| Framework | **Spring Boot 4.x**, fixar patch estável no início da implementação | **[APROVADO]** |
| MVC e páginas | Spring MVC + **Thymeleaf** | **[APROVADO]** |
| Frontend | HTML5 + **JavaScript puro** (`fetch`) | **[APROVADO]** |
| Design | **Bootstrap 5 + Bootstrap Icons + CSS próprio** | **[APROVADO]** |
| Banco | PostgreSQL | **[APROVADO]** |
| ORM | Spring Data JPA / Hibernate | **[APROVADO]** |
| Autorização | Spring Security | **[APROVADO]** |
| Migrações | Flyway | **[APROVADO]** |
| Build | Maven + Maven Wrapper | **[APROVADO]** |
| Testes | JUnit + Testcontainers + testes web | **[PROPOSTO]** |
| Infra | Docker Compose + proxy reverso com HTTPS | **[APROVADO]** |
| Imagens/arquivos | Storage S3 compatível em produção; alternativa local só em dev | **[PROPOSTO]** |
| Observabilidade | Spring Boot Actuator + logs estruturados | **[PROPOSTO]** |
| Qualidade | Spotless/Java format; ESLint; Prettier; EditorConfig | **[APROVADO]** |

**Restrições deliberadas:** sem Vue, React, Angular, HTMX, TypeScript, servidor Node.js de frontend, Kafka, Kubernetes ou microserviços no início. Ferramentas de formatação JavaScript instaladas em desenvolvimento/CI **não** significam um servidor Node.js em produção.

**Nota de versões:** versões exatas de plugins e dependências devem ser travadas pelo `pom.xml`/lockfiles de tooling, após verificação de compatibilidade. A documentação oficial do Spring Boot lista os requisitos de runtime: https://docs.spring.io/spring-boot/system-requirements.html.

---

## 3. Arquitetura de alto nível

**Estilo:** monólito modular no mesmo repositório e no mesmo processo Spring Boot. Views Thymeleaf e endpoints JSON convivem no backend; módulos de domínio não dependem de templates nem do fornecedor fiscal.

```text
Navegador desktop / tablet / smartphone
    | HTTPS
    v
DNS wildcard + proxy reverso (Host confiável)
    |
    +-- admin.vendafacil.com.br -> administração do SaaS
    |
    +-- {empresa}.vendafacil.com.br -> PDV da empresa
    v
Spring Boot (monólito modular)
    |-- identity / tenant / membership / permissions
    |-- empresa / filial / configurações
    |-- catálogo / preço / imagens / importação
    |-- estoque
    |-- venda / pagamento / descontos
    |-- caixa / sangria / fechamento
    |-- devolução / reembolso
    |-- recibo / impressão
    |-- fiscal (FiscalGateway)
    |-- auditoria / relatórios
    |
    +--> PostgreSQL (transacional, por tenant)
    +--> Storage de arquivos (imagens, XMLs, comprovantes)
    +--> Adaptador de provedor fiscal (somente onde habilitado)
```

**Princípios de dependência:** controllers chamam serviços; serviços aplicam regras; repositories persistem. Integrações externas são adaptadores atrás de interfaces. Nenhum `Controller` acessa banco diretamente ou implementa regras fiscais. O módulo fiscal não deve obrigar o front a conhecer a API de um fornecedor.

### 3.1 Modo de implantação

- Um servidor web e um banco podem hospedar diversas empresas.
- `*.vendafacil.com.br` é **exemplo**, não domínio adquirido nem disponibilidade verificada.
- O domínio-base vem de configuração; em desenvolvimento usar domínio local controlado/`hosts`.
- Cada empresa tem `slug` estável e exclusivo; `admin` e outros subdomínios reservados não podem ser usados como slug.
- O sistema deve aceitar apenas hosts oficiais/permitidos; o proxy deve limpar/validar cabeçalhos encaminhados (`Forwarded`, `X-Forwarded-*`).
- HTTPS obrigatório em produção, com certificado wildcard ou estratégia equivalente.

---

## 4. Multiempresa, filiais e isolamento

### 4.1 Hierarquia [APROVADO]

```text
Plataforma vendaFacil
  └── Tenant (empresa cliente)
       ├── Catálogo de produtos compartilhado
       ├── Usuários e papéis associados
       ├── Políticas comerciais gerais
       └── Filiais (1..N)
            ├── CNPJ e configuração fiscal própria
            ├── Preços por produto
            ├── Estoque e movimentações
            ├── Caixas e sessões
            ├── Vendas, devoluções e reembolsos
            └── Documentos fiscais
```

### 4.2 Resolução de contexto

1. Requisição chega por host validado.
2. Middleware resolve o `tenant` pelo subdomínio e verifica se está ativo.
3. Spring Security autentica a pessoa e confirma vínculo **ativo** com o `tenant`.
4. A filial ativa só pode ser selecionada entre as autorizadas ao usuário.
5. Serviços e repositórios recebem contexto de empresa/filial confiável — não confiar em `tenant_id`/`store_id` enviados no corpo JSON para definir escopo.
6. Cada operação grava os identificadores de empresa, filial e usuário para auditoria.

**Usuário com acesso a várias empresas:** terá vínculos distintos. O subdomínio delimita o contexto. Papéis e limites de um vínculo não migram automaticamente para outra empresa.

### 4.3 Modelo de isolamento [PROPOSTO]

Banco compartilhado, tabelas compartilhadas, `tenant_id` obrigatório em entidades empresariais; `store_id` obrigatório quando a entidade pertencer a uma filial. `tenant_id` **não é senha** nem substitui políticas de autorização. Usar chaves/constraints compostas, consultas escopadas e testes de acesso cruzado; avaliar **PostgreSQL Row-Level Security** como defesa adicional após validar aplicação, conexão e migrations.

**Nunca** permitir entidade de uma empresa referenciar produto, venda, filial ou usuário não autorizado de outra empresa. Filial deve pertencer ao mesmo tenant da venda.

### 4.4 Suspensão de empresa

Suspensão impede operações comerciais e acesso comum; não apaga histórico, arquivos fiscais ou dados. Super Admin visualiza metadados administrativos do tenant, mas acesso aos dados do cliente para suporte exige autorização explícita, trilha de auditoria e escopo limitado.

---

## 5. Perfis, autenticação e permissões

### 5.1 Perfis iniciais [APROVADO]

| Perfil | Escopo | Responsabilidade |
|---|---|---|
| Super Admin | Plataforma | Criar/ativar/suspender empresas, monitorar saúde do SaaS |
| Administrador | Empresa/filiais autorizadas | Usuários, produtos, configurações e controles |
| Gerente | Filiais autorizadas | Aprovar exceções, gerenciar operação comercial |
| Operador de caixa | Filiais autorizadas | Vender, abrir/fechar sessão e solicitar operações |
| Estoquista | Filiais autorizadas | Produtos, entradas e ajustes sob permissão |

**Modelo [APROVADO]:** RBAC, com papéis padrão editáveis por empresa e autorização efetiva por filial. A concessão de papéis críticos só pode ser feita por usuários com privilégio de gestão de acessos. Não permitir autoelevação nem aprovar sua própria exceção usando outra sessão do mesmo usuário.

### 5.2 Permissões granulares de referência

`PRODUCT_READ`, `PRODUCT_WRITE`, `PRODUCT_IMPORT`, `PRICE_CHANGE`, `STOCK_READ`, `STOCK_ADJUST`, `SALE_CREATE`, `SALE_CANCEL`, `SALE_RETURN`, `DISCOUNT_APPLY`, `DISCOUNT_APPROVE`, `NEGATIVE_STOCK_APPROVE`, `CASH_OPEN`, `CASH_WITHDRAW`, `CASH_WITHDRAW_APPROVE`, `CASH_REINFORCE`, `CASH_CLOSE`, `CASH_DIFFERENCE_APPROVE`, `REFUND_EXECUTE`, `FISCAL_CONFIGURE`, `FISCAL_ISSUE`, `REPORT_VIEW`, `USER_MANAGE`.

Papéis indicam permissões padrão; a decisão final de autorização inclui escopo, valor-limite, filial, sessão de caixa e política vigente.

### 5.3 Autenticação web [PROPOSTO]

- Sessão servidor-side, cookie `HttpOnly`, `Secure`, `SameSite` adequado e **host-only** por subdomínio (sem cookie compartilhado com `Domain=.vendafacil.com.br` por padrão).
- Senhas com hash forte gerenciado pelo Spring Security; nunca em texto claro.
- CSRF obrigatório em formulários e operações `fetch` que alterem estado; rate limiting e proteção contra força bruta.
- Reautenticação de aprovador em ações excepcionais; sessão do gerente não substitui sessão do operador.
- Encerramento por inatividade configurável; política de troca de senha e recuperação por canal verificado.
- Área de Super Admin segregada; considerar MFA obrigatório para funções de alto risco antes de produção.

---

## 6. Funcionalidades e requisitos identificados

### 6.1 Administração SaaS

- **RF-SAA-001 [APROVADO]**: cadastrar, ativar, suspender e consultar empresas.
- **RF-SAA-002 [APROVADO]**: cada tenant terá subdomínio único e logo próprio.
- **RF-SAA-003 [APROVADO]**: criar, editar e ativar/desativar filiais vinculadas.
- **RF-SAA-004 [PROPOSTO]**: painel de ativação de módulos por empresa/filial, sem cobrança recorrente automática.
- **RF-SAA-005 [APROVADO]**: registrar logs de ações administrativas sensíveis.

### 6.2 Produtos, imagens e importação

- **RF-PRO-001**: cadastro e edição de produto com nome, SKU interno, código de barras opcional, categoria opcional, imagem, unidade, status e atributos fiscais quando aplicáveis.
- **RF-PRO-002**: unidades iniciais `UN` (inteiro) e `KG` (decimal manual com até três casas, ajustável por regra específica); sem balança integrada.
- **RF-PRO-003**: catálogo compartilhado por tenant; **preço de venda por filial**; produto só vendável em filial com preço ativo.
- **RF-PRO-004**: upload individual de imagem com validação de tipo real, tamanho, dimensões e acesso.
- **RF-PRO-005**: importação em massa de CSV/XLSX com etapa **prévia de validação**, visualização de erros e relatório por linha.
- **RF-PRO-006**: pesquisa por nome, SKU e código de barras, inclusive leitor USB operando como teclado.
- **RF-PRO-007**: produto pode ser inativado sem apagar o histórico das vendas.

**Regras de importação [PROPOSTO]:** cabeçalhos versionados, codificação UTF-8 para CSV, campos de preço validados em `pt-BR` ou formato explícito do template, deduplicação por SKU/código quando aplicável e importações rastreadas (`import_job`, `import_row_error`). Processar lotes com commit seguro; falhas parciais devem ser claras e repetíveis sem duplicar itens. Não usar uploads sem limite ou XLSX indiscriminadamente em memória.

### 6.3 Preço e estoque

- **RF-EST-001**: saldo de produto controlado por filial.
- **RF-EST-002**: registrar **movimentos imutáveis** de entrada, ajuste, venda e reversão, com motivo/origem/usuário.
- **RF-EST-003**: bloquear estoque negativo por padrão; permitir apenas **exceção aprovada por gerente autorizado, justificada e vinculada à operação**.
- **RF-EST-004**: estoque mínimo por produto/filial opcional; alertas simples.
- **RF-EST-005**: garantir atomicidade para vendas simultâneas do mesmo estoque.
- **RF-EST-006**: preço atual pode ser alterado por filial sem modificar registros históricos de vendas.

**Regra de concorrência [PROPOSTO]:** serviço de checkout realiza validação e baixa dentro de transação com lock de linha/atualização condicional, com versão de saldo (`optimistic lock`) ou mecanismo equivalente. Autorizações excepcionais precisam ser revalidadas no instante da baixa. Proibir baixa dupla ao repetir requisição.

### 6.4 PDV e vendas

- **RF-VEN-001**: pesquisar/adicionar/remover produtos, editar quantidade, peso manual e total.
- **RF-VEN-002**: buscar preço **da filial ativa**; snapshots de SKU, descrição, unidade, preço, descontos e tributos aplicáveis por item na venda.
- **RF-VEN-003**: registro manual de recebimentos em **dinheiro, PIX, débito e crédito**, incluindo pagamentos mistos.
- **RF-VEN-004**: cálculo e registro de troco apenas sobre valores recebidos em dinheiro; sem integração com adquirentes na V1.
- **RF-VEN-005**: exigir caixa aberto e usuário autorizado para concluir venda.
- **RF-VEN-006**: cliente/consumidor opcional conforme fluxo; identificação de destinatário conforme exigência fiscal.
- **RF-VEN-007**: distinguir venda em elaboração, recebida, pendente fiscal, concluída, cancelada e com devolução; não confundir estado comercial com estado fiscal.
- **RF-VEN-008**: recibo comercial com aviso de que **não é documento fiscal**.

**Valor monetário:** Java `BigDecimal`, PostgreSQL `NUMERIC`, regras explícitas de arredondamento em BRL (2 casas na totalização) e quantidade decimal sem ponto flutuante binário. Valores dos itens devem reconciliar com o total de pagamentos/reembolso e XML fiscal conforme regras exigidas pelo provedor.

### 6.5 Descontos [APROVADO]

| Perfil padrão | Limite de desconto |
|---|---:|
| Operador | **0%** |
| Gerente | **10%** |
| Administrador | **20%** |

- Configuráveis **por empresa**; devem respeitar teto de política da empresa.
- Desconto por item **e** por venda, em percentual **ou** valor monetário.
- Validação do **desconto efetivo acumulado**, não somente do último desconto aplicado.
- Acima do limite: autorização individual de usuário com `DISCOUNT_APPROVE`, respeitando teto do próprio autorizador, com justificativa e auditoria.
- Desconto de **100% bloqueado por padrão**; valor total não pode ficar negativo.
- Alterar preço-base na tela do caixa não é substituto para desconto registrado.
- Recalcular no servidor; valores do JavaScript são apenas prévia.

### 6.6 Caixa [APROVADO]

- **RF-CAI-001**: caixa físico pertence a filial; **no máximo uma sessão ativa por caixa**.
- **RF-CAI-002**: abertura com operador responsável e fundo inicial.
- **RF-CAI-003**: sangria com justificativa, política configurável, limite por operação e **aprovação gerencial por padrão**.
- **RF-CAI-004**: reforço de caixa com motivo e auditoria.
- **RF-CAI-005**: fechamento **cego por padrão**, configurável por empresa; operador declara primeiro os valores contados.
- **RF-CAI-006**: diferença de caixa exige justificativa; acima do limite definido pela empresa exige aprovação gerencial.
- **RF-CAI-007**: movimentações não são excluídas nem sobrescritas; correções geram novos registros.
- **RF-CAI-008**: um operador encerra sua sessão antes que outro assuma o mesmo caixa (sem transferência de sessão na V1).
- **RF-CAI-009**: pagamentos digitais são conciliados em relatórios, mas não aumentam o dinheiro físico da gaveta.
- **RF-CAI-010**: reembolso em dinheiro é movimentação específica, **diferente de sangria**.

**Fechamento cego:** o total esperado não pode ser incluído no HTML/JSON enviado ao operador antes da declaração; ocultar somente via CSS **não** atende à regra. Conferência e diferenças devem ser calculadas no servidor após o envio.

### 6.7 Cancelamento, devolução e reembolso [APROVADO]

- **RF-DEV-001**: operador pode descartar rascunho; cancelamento de venda concluída é configurável, **gerente por padrão**.
- **RF-DEV-002**: nunca apagar venda concluída; manter referências e eventos de cancelamento.
- **RF-DEV-003**: devolução **total e parcial** vinculada à venda original; limitar quantidade/valor já devolvidos.
- **RF-DEV-004**: verificar condição do item antes de repor estoque disponível; produto danificado não retorna automaticamente ao saldo vendável.
- **RF-DEV-005**: reembolso separado da devolução, manual e auditável, com método e autorização.
- **RF-DEV-006**: cancelamento comercial e cancelamento fiscal são fluxos diferentes, com estados e resultados relacionados.
- **RF-DEV-007**: devoluções exigem tratamento fiscal adequado, incluindo NF-e de entrada quando aplicável, por provedor especializado.
- **RF-DEV-008**: valor reembolsável respeita o efetivamente pago, os descontos históricos e os valores já reembolsados.

**Limite da V1 [PROPOSTO]:** devolução na filial da venda original; entre filiais fica para etapa futura. Não simular cancelamento de NFC-e antiga quando legislação exigir outro procedimento.

---

## 7. Módulo fiscal opcional: arquitetura e conformidade

### 7.1 Decisão fundamental [APROVADO]

**Habilitar a integração fiscal será opcional tecnicamente por estabelecimento. Cumprir a obrigação fiscal do comerciante NÃO é opcional por escolha do software.**

O objetivo é vender o vendaFacil a pequenos comércios, mantendo núcleo simples e permitindo integrar emissão fiscal quando configurada. Isso **não** autoriza emitir apenas recibo onde a legislação exige documento fiscal. Para uso comercial em São Paulo, a equipe deve validar o fluxo tributário do estabelecimento com contador e provedor antes de ativar a operação em produção.

### 7.2 Modalidades por filial [PROPOSTO]

| Modo | Comportamento | Restrição obrigatória |
|---|---|---|
| `INTEGRATED` | O vendaFacil solicita NFC-e/NF-e ao provedor, consulta e arquiva resultados | Exige credenciais e homologação válidas |
| `EXTERNAL` | O emissor fiscal é externo, e o vendaFacil registra referência, status e comprovante da emissão | Exige processo operacional de emissão fiscal externa compatível com a obrigação real; não declarar automaticamente como fiscalizado/autorizado |
| `NOT_APPLICABLE` | Sem emissão integrada para cenário legalmente dispensado ou ambiente de demonstração | Só habilitar em produção mediante enquadramento validado; **não** constitui declaração automática de isenção |

**Bloqueio de conformidade:** sem modo fiscal válido e procedimento aprovado, o estabelecimento obrigado a emitir documento fiscal **não deve receber autorização de entrada em operação comercial no vendaFacil**. O sistema deve exibir pendência clara, e não permitir a simples impressão de recibo como substituto da nota. A obrigação legal pode subsistir mesmo quando o SaaS está na modalidade Essencial.

**A nomenclatura comercial Essencial/Fiscal é provisória.** São modalidades de recursos, não qualificações tributárias.

### 7.3 Documentos e UF [APROVADO]

- Primeiro estado: **São Paulo**.
- **NFC-e modelo 65** para operações a que se aplica.
- **NF-e modelo 55** para operações apropriadas, inclusive as que exigirem documento de entrada por devolução, conforme enquadramento.
- Recibo comum e DANFE/documento auxiliar fiscal conforme o documento autorizado e regras vigentes.
- Fiscal deve aceitar expansão por UF, sem assumir que São Paulo equivale a todas as UFs.
- **Regime tributário do primeiro cliente real: [PENDENTE].** Cadastro e regras fiscais devem suportar extensão, mas nenhum regime será presumido como homologado.

### 7.4 Contrato do adaptador fiscal [PROPOSTO]

```java
public interface FiscalGateway {
    FiscalSubmissionResult submit(FiscalSubmission command);
    FiscalStatusResult query(FiscalDocumentReference reference);
    FiscalCancellationResult cancel(FiscalCancellationRequest request);
    FiscalArtifactResult downloadArtifacts(FiscalDocumentReference reference);
}
```

O contrato representa **capacidades** e não impõe que cada provedor suporte todas as operações de maneira idêntica. Para NF-e de devolução, a aplicação terá operações de domínio especializadas, que serão traduzidas pelo adaptador. Implementar provedor fictício em desenvolvimento/testes; integração concreta somente após definição do fornecedor.

**[PENDENTE]**: provedor, contratos comerciais, ambientes, SLAs, formatos, webhook/polling, CSC, certificados e limites de API.

### 7.5 Máquina de estados fiscal [PROPOSTO]

`NOT_REQUESTED`, `READY`, `SUBMITTED`, `AWAITING_RESPONSE`, `AUTHORIZED`, `REJECTED`, `CONTINGENCY_PENDING`, `CANCEL_REQUESTED`, `CANCELLED`, `FAILED_RECONCILIATION`.

Não transformar erro de rede em `REJECTED` nem tratar timeout como inexistência de nota. Persistir `provider_request_id`, chave de acesso, série, número, ambiente, protocolo, XML/documento auxiliar, timestamps e retornos relevantes **sem dados secretos nos logs**.

### 7.6 Idempotência e entrega [PROPOSTO]

1. Receber venda finalizada comercialmente em transação, com identificador único da operação e pagamentos registrados.
2. Persistir evento pendente na **outbox do PostgreSQL** na mesma transação (sem Kafka/RabbitMQ na V1).
3. Consumidor interno entrega ao provedor com chave estável de idempotência e grava tentativa.
4. Timeout/estado desconhecido: **consultar** a situação do documento antes de retransmitir.
5. Documentos autorizados são gravados e os artefatos disponibilizados para impressão.
6. Erro persistente gera pendência operacional visível a gerente/administrador, sem recriar a venda ou baixar estoque outra vez.
7. Estados comerciais e fiscais são reconciliados; mudanças definitivas exigem invariantes e autorização adequadas.

### 7.7 Contingência [PENDENTE de detalhamento com provedor]

Contingência NFC-e em São Paulo só será acionada se o provedor suportar o processo previsto nas regras vigentes e a operação atender aos requisitos legais. **Contingência fiscal não implica operação offline do site.** Definir fluxos, prazos de regularização, emissão de DANFE e monitoramento antes de homologar produção.

### 7.8 Credenciais e arquivos fiscais

- Credenciais fiscais/certificados **criptografados em repouso**, acesso mínimo necessário, rotação e auditoria.
- Nunca guardar CSC, senhas de certificado ou tokens em templates, JavaScript ou logs.
- XMLs, protocolos e artefatos: storage com acesso restrito por tenant/filial, integridade e retenção conforme obrigações legais a confirmar.
- Expor ao usuário somente arquivos autorizados e controles de permissão compatíveis.
- Atualizações de legislação/nota técnica devem ser registradas no backlog de manutenção fiscal.

### 7.9 Fontes oficiais de referência

- SEFAZ-SP, NFC-e: https://portal.fazenda.sp.gov.br/servicos/nfce/
- Portal Nacional da NF-e, manuais MOC e DANFE: https://www.nfe.fazenda.gov.br/portal/listaConteudo.aspx?tipoConteudo=ndIjl+iEFdE%3D
- SEFAZ-SP, perguntas frequentes de NF-e: https://portal.fazenda.sp.gov.br/servicos/nfe/Paginas/perguntas-frequentes.aspx

**Obrigação de verificação:** consultar sempre a versão vigente de manuais, portarias e notas técnicas e obter validação de especialista fiscal antes de colocar estabelecimento em produção. Em 2026 a SEFAZ-SP informa obrigatoriedade da NFC-e no varejo paulista desde 01/01/2026. O presente SDD **não certifica exceções para comerciantes informais ou pequenos**.

---

## 8. Impressão e experiência de recibo

- **RF-IMP-001 [APROVADO]**: gerar recibo comum claro, com logo da empresa/filial, itens, quantidades, descontos, pagamentos, troco e identificação da operação.
- **RF-IMP-002 [APROVADO]**: destacar **“RECIBO NÃO FISCAL”** em qualquer comprovante comercial sem autorização fiscal.
- **RF-IMP-003 [APROVADO]**: exibir/imprimir DANFE/documento auxiliar somente com status fiscal adequado, seguindo especificações.
- **RF-IMP-004 [APROVADO]**: oferecer HTML e CSS `@media print` adequados a impressoras comuns e térmicas, a validar em 58/80 mm quando aplicável.
- **RF-IMP-005 [PROPOSTO]**: permitir reimpressão controlada e log de quem reimprimiu.
- **RF-IMP-006 [PENDENTE]**: modelos de impressoras homologados do cliente real.

Não prometer impressão silenciosa pelo navegador. A funcionalidade de impressão deve indicar falha de diálogo/impressora, sem alterar status fiscal ou recriar venda.

---

## 9. UX/UI, ajuda contextual e acessibilidade

### 9.1 Design system [APROVADO]

- Bootstrap 5, Bootstrap Icons e tema próprio centralizado em tokens CSS (`--vf-color-*`, `--vf-spacing-*`, `--vf-radius-*`).
- Design responsivo **mobile-first**, com áreas de toque adequadas e navegação por teclado.
- Layout com menu principal simples; no PDV, foco na busca de produtos, carrinho, subtotal/total e finalização.
- Mostrar somente funcionalidades habilitadas e autorizadas; backend continua verificando todas as permissões.
- Mensagens em português claro; usar `R$`, `kg`, `un.` e formato brasileiro para valores.

### 9.2 Ajuda contextual: requisito obrigatório [APROVADO]

**RNF-UX-001:** todo campo que possa causar dúvida deve ter tooltip/popover de ajuda contextual **perto do rótulo**, com linguagem simples, exemplo e explicação do efeito da informação. Exemplos: SKU, GTIN/EAN, NCM, CFOP, estoque mínimo, preço de custo, tipo de desconto, sangria, diferença de caixa, tipo de documento fiscal, regime e CSC.

**RNF-UX-002:** balões de ajuda devem abrir também por **clique/toque** e **teclado**, não depender de hover. Usar comportamento acessível com identificação descritiva; não esconder instruções indispensáveis exclusivamente dentro do balão.

**RNF-UX-003:** campos com erro mostram o problema ao lado do campo e informam como corrigir; não apenas mudança de cor.

**RNF-UX-004:** exibir confirmação antes de sangrias, estornos, fechamento, cancelamentos e operações fiscais sensíveis.

**RNF-UX-005:** estado de carregamento, erro, vazio, sucesso e acesso negado devem estar especificados para cada tela.

### 9.3 Telas da V1

| ID | Tela | Elementos essenciais |
|---|---|---|
| UI-01 | Login por subdomínio | Logo, identificação da empresa, autenticação, recuperação |
| UI-02 | Escolha da filial | Exibir apenas filiais autorizadas |
| UI-03 | Dashboard | Resumo, atalhos, pendências visíveis ao perfil |
| UI-04 | Produtos | Lista, busca, CRUD, imagens, ajuda contextual |
| UI-05 | Importação | Template, upload, prévia de validação, relatório |
| UI-06 | Estoque | Saldos, entrada, ajuste, movimentações |
| UI-07 | PDV | Busca/leitor, carrinho, peso manual, descontos, pagamento |
| UI-08 | Caixa | Abrir, sangrar, reforçar, fechar, consultar sessão |
| UI-09 | Autorização | Login do gerente, justificativa, escopo específico |
| UI-10 | Devoluções | Buscar venda, selecionar itens, motivo, estoque, reembolso |
| UI-11 | Documentos fiscais | Status, pendências, consulta, recibos/DANFE |
| UI-12 | Configurações | Empresa, filiais, logo, políticas, fiscal |
| UI-13 | Usuários e permissões | Perfis, vínculos, alcance por filial |
| UI-14 | Relatórios | Vendas, caixa, produtos e estoque básicos |
| UI-15 | Admin SaaS | Empresas, subdomínios, ativação, suporte autorizado |

### 9.4 Interações sem framework JS pesado

- Páginas e formulários administrativos via Thymeleaf/Spring MVC.
- Carrinho, pesquisa dinâmica, autorização e status fiscal via módulos JavaScript pequenos, `fetch` e endpoints JSON.
- JavaScript modularizado por tela/função; não espalhar lógica de negócio crítica pelo navegador.
- Anti-CSRF integrado aos formulários e `fetch`; tratar expiração de sessão com mensagem clara.

---

## 10. Modelo de dados conceitual

### 10.1 Entidades principais

| Tabela | Campos-chave de referência | Observações |
|---|---|---|
| `tenants` | `id`, `slug`, `name`, `status` | Slug global único |
| `stores` | `id`, `tenant_id`, `name`, `cnpj`, `status` | Filiais dentro do tenant |
| `users` | `id`, `email`, `password_hash`, `status` | Identidade global de login |
| `memberships` | `id`, `tenant_id`, `user_id`, `status` | Vínculo usuário-empresa |
| `membership_store_roles` | `membership_id`, `store_id`, `role_id` | Permissão por filial |
| `roles`, `permissions`, `role_permissions` | IDs e vínculos | RBAC configurável por tenant |
| `tenant_policies` | `tenant_id`, limites e opções | Configuração comercial |
| `products` | `id`, `tenant_id`, `sku`, `name`, `unit`, `status` | Catálogo compartilhado |
| `product_images` | `id`, `tenant_id`, `product_id`, `storage_key` | Imagens no storage |
| `store_prices` | `tenant_id`, `store_id`, `product_id`, `amount` | Preço por filial |
| `stock_balances` | `tenant_id`, `store_id`, `product_id`, `quantity`, `version` | Saldo materializado e concorrência |
| `stock_movements` | `tenant_id`, `store_id`, `product_id`, `type`, `quantity` | Livro imutável de movimentos |
| `cash_registers` | `tenant_id`, `store_id`, `code` | Caixa físico/lógico |
| `cash_sessions` | `tenant_id`, `store_id`, `cash_register_id`, `operator_id`, `status` | Sessão aberta/fechada |
| `cash_movements` | `tenant_id`, `cash_session_id`, `type`, `amount` | Sangria/reforço/reembolso |
| `sales` | `tenant_id`, `store_id`, `cash_session_id`, `status`, `total` | Venda com chave idempotente |
| `sale_items` | `tenant_id`, `sale_id`, `product_id`, `quantity`, `unit_price`, `discount` | Snapshot da operação |
| `sale_payments` | `tenant_id`, `sale_id`, `method`, `amount` | Pagamentos manuais |
| `approval_events` | `tenant_id`, `store_id`, `operation_id`, `approver_id`, `reason` | Exceções autorizadas |
| `returns`, `return_items` | `tenant_id`, `store_id`, `sale_id`, `status` | Devoluções totais/parciais |
| `refunds` | `tenant_id`, `return_id`, `method`, `amount`, `status` | Reembolso distinto da devolução |
| `store_fiscal_settings` | `tenant_id`, `store_id`, `mode`, `environment` | Sem credenciais em claro |
| `fiscal_documents` | `tenant_id`, `store_id`, `sale_id`, `model`, `status`, `access_key` | NFC-e/NF-e e versões de eventos |
| `fiscal_events` | `tenant_id`, `fiscal_document_id`, `type`, `result` | Retornos/autorizações/cancelamentos |
| `outbox_events` | `id`, `tenant_id`, `type`, `payload_ref`, `status` | Entrega idempotente a integrações |
| `import_jobs`, `import_row_errors` | `tenant_id`, `file_ref`, `status` | Importação rastreável |
| `audit_logs` | `tenant_id`, `store_id`, `actor_id`, `action`, `correlation_id` | Auditoria permanente |

### 10.2 Regras de esquema e integridade [PROPOSTO]

- Identificadores UUID; chaves compostas ou FKs compostas para garantir coerência de `tenant_id` e `store_id`.
- Valores monetários `NUMERIC(19,2)` quando representarem dinheiro final; quantidade `NUMERIC(19,3)` para estoque/unidade de peso (ajustar por requisito fiscal); preço unitário pode exigir escala maior para reconciliação fiscal.
- `UNIQUE(tenant_id, sku)` em produtos; códigos de barras com regra documentada para ausência e duplicidade conforme contexto.
- `UNIQUE(tenant_id, store_id, product_id)` em preço e saldo.
- Índices por tenant/filial/data para pesquisa de vendas, caixa, documentos fiscais e auditoria.
- `CHECK` para quantidades e estados válidos; exceção auditada de estoque negativo é **regra de negócio**, não ausência indiscriminada de constraints.
- Nunca usar cascata que apague histórico financeiro/fiscal.
- Migrações versionadas e reversões planejadas; evitar editar migration já aplicada.
- Colunas de criação, alteração e versão de concorrência onde couber; auditoria de domínio não depende apenas de `updated_at`.

### 10.3 Relacionamentos essenciais

```text
Tenant 1:N Store
Tenant 1:N Product
Tenant 1:N Membership N:1 User
Membership 1:N MembershipStoreRole N:1 Store
Store 1:N StorePrice N:1 Product
Store 1:N StockBalance N:1 Product
Store 1:N CashRegister 1:N CashSession
CashSession 1:N Sale 1:N SaleItem
Sale 1:N SalePayment
Sale 1:N Return 1:N ReturnItem
Return 1:N Refund
Sale 0:N FiscalDocument 1:N FiscalEvent
Operation 0:N ApprovalEvent
```

---

## 11. Contratos HTTP e organização das URLs [PROPOSTO]

**Princípio:** páginas HTML retornam Thymeleaf; endpoints assíncronos retornam JSON. Todas as rotas privadas resolvem tenant pelo host e store pela sessão autorizada. Exemplos abaixo são contratos iniciais, não código implementado.

| Método | Rota | Finalidade | Permissão mínima |
|---|---|---|---|
| `GET` | `/login` | Login contextualizado | Público por host válido |
| `GET` | `/filiais` | Seleção de filial | Membro do tenant |
| `POST` | `/filiais/ativa` | Trocar filial ativa | Acesso à filial |
| `GET` | `/produtos` | Listagem HTML | `PRODUCT_READ` |
| `POST` | `/produtos` | Criar produto | `PRODUCT_WRITE` |
| `POST` | `/produtos/importacoes` | Iniciar importação | `PRODUCT_IMPORT` |
| `GET` | `/api/v1/produtos/busca` | Busca de produtos do PDV | `SALE_CREATE` |
| `POST` | `/api/v1/vendas` | Criar/finalizar venda com chave idempotente | `SALE_CREATE` |
| `POST` | `/api/v1/vendas/{id}/cancelamento` | Solicitar cancelamento | `SALE_CANCEL` ou autorização |
| `POST` | `/api/v1/vendas/{id}/devolucoes` | Registrar devolução | `SALE_RETURN` ou autorização |
| `POST` | `/api/v1/caixas/{id}/abertura` | Abrir sessão | `CASH_OPEN` |
| `POST` | `/api/v1/caixas/sessoes/{id}/sangrias` | Sangria | Política de sangria |
| `POST` | `/api/v1/caixas/sessoes/{id}/fechamento` | Declarar e fechar | `CASH_CLOSE` |
| `POST` | `/api/v1/autorizacoes` | Gerar aprovação contextual | Perfil/aprovador autorizado |
| `GET` | `/api/v1/fiscal/documentos/{id}` | Consultar estado fiscal | Permissão fiscal/da venda |
| `POST` | `/api/v1/fiscal/documentos/{id}/consultar` | Reconciliar com provedor | Permissão fiscal |
| `GET` | `/admin/empresas` | Gerenciar tenants | Super Admin |

**API:** usar status HTTP correto, validação com Bean Validation, paginação nas listas, mensagens `pt-BR`, envelope estável de erro (`ProblemDetail`/RFC 9457 ou equivalente) e `correlationId`. Erros de autorização não devem revelar existência de dados de outros tenants. Nunca aceitar alteração de preço ou limite apenas porque veio no payload; o servidor recalcula.

---

## 12. Fluxos transacionais e invariantes

### 12.1 Fluxo de venda

1. Operador autenticado e caixa aberto; filial ativa validada.
2. Front carrega produtos/preços/estoque daquela filial.
3. Front envia itens, quantidades, descontos propostos e formas de pagamento com `Idempotency-Key` estável.
4. Backend busca novamente preços, quantidades disponíveis e limites; valida autorizações específicas.
5. Transação grava venda, itens/snapshots, pagamentos, movimentação de estoque e evento de outbox fiscal quando necessário.
6. Se emissão integrada obrigatória para aquele fluxo, venda fica com situação comercial/fiscal explícita até autorização/contingência válida. Erro de emissão não cria nova venda.
7. Liberar recibo comercial ou documento fiscal conforme situação e permissões, com rótulos corretos.

### 12.2 Fluxo de sangria

1. Operador informa valor e justificativa.
2. Política do tenant decide se precisa autorização e verifica saldo físico estimado.
3. Gerente aprova com autenticação própria quando exigido.
4. Banco cria movimento imutável e registra aprovação/auditoria na mesma transação.
5. Nunca editar nem excluir movimento já lançado; fazer correção por evento compensatório.

### 12.3 Fluxo de fechamento cego

1. Operador declara valores contados **sem receber saldo esperado pelo HTML/JSON**.
2. Backend calcula diferença pelo livro de movimentos e vendas em dinheiro.
3. Se diferença maior que limite: exige autorização gerencial; se menor/igual e não zero: exige justificativa.
4. Fecha sessão atomicamente; impede vendas posteriores vinculadas a ela.
5. Relatórios mostram saldo esperado, contado, diferença, justificativa e aprovador.

### 12.4 Fluxo de devolução

1. Localiza venda original e quantidades não devolvidas.
2. Seleciona devolução total/parcial, condição física, motivo e usuário autorizador.
3. Determina valor proporcional realmente pago e situação fiscal necessária.
4. Registra devolução e atualização de estoque somente conforme condição recebida.
5. Registra **reembolso como operação própria** e documento fiscal de devolução quando exigido.
6. Reconcilia pendências comerciais/fiscais com status explícitos; não apagar a venda original.

### 12.5 Invariantes globais

- Uma venda/caixa/devolução pertence a um único tenant e uma única filial.
- Nenhum operador pode usar limite de outra empresa ou filial.
- Requisição repetida de finalização não duplica pagamentos, estoque ou documento fiscal.
- Uma venda não pode retornar mais quantidade/valor do que foi vendido/pago e ainda não devolvido.
- Não emitir DANFE como documento autorizado sem autorização/condição legal de contingência válida.
- Uma sessão de caixa fechada não pode receber novas vendas ou movimentos ordinários.
- Falha externa não deve provocar transação bancária ou fiscal presumidamente bem-sucedida.

---

## 13. Organização de código e arquivos — requisito obrigatório

**RNF-COD-001 [APROVADO]:** todo código novo ou alterado precisa de **formatação consistente, legibilidade e organização para debug por outros desenvolvedores**. O build/CI deve rejeitar violações de formatação, lint ou testes aplicáveis. Não aceitar apenas “o código funciona”.

### 13.1 Estrutura do repositório [PROPOSTO]

```text
vendaFacil/
├── .editorconfig
├── .gitignore
├── README.md
├── pom.xml
├── mvnw
├── mvnw.cmd
├── docker-compose.yml
├── Dockerfile
├── .github/workflows/ci.yml
├── docs/
│   ├── SDD.md
│   ├── architecture/
│   ├── api/
│   ├── runbooks/
│   └── decisions/                 # ADRs
├── tooling/                       # ESLint/Prettier, sem servidor frontend
└── src/
    ├── main/
    │   ├── java/br/com/vendafacil/
    │   │   ├── VendaFacilApplication.java
    │   │   ├── shared/
    │   │   │   ├── config/
    │   │   │   ├── security/
    │   │   │   ├── exception/
    │   │   │   └── audit/
    │   │   ├── identity/
    │   │   ├── tenant/
    │   │   ├── store/
    │   │   ├── product/
    │   │   ├── stock/
    │   │   ├── sale/
    │   │   ├── cash/
    │   │   ├── returnflow/
    │   │   ├── fiscal/
    │   │   │   ├── application/
    │   │   │   ├── domain/
    │   │   │   ├── port/
    │   │   │   └── adapter/
    │   │   ├── printing/
    │   │   ├── reports/
    │   │   └── administration/
    │   └── resources/
    │       ├── application.yml
    │       ├── db/migration/
    │       ├── templates/
    │       │   ├── fragments/
    │       │   ├── layouts/
    │       │   ├── auth/
    │       │   ├── products/
    │       │   ├── sales/
    │       │   ├── cash/
    │       │   ├── fiscal/
    │       │   └── admin/
    │       └── static/
    │           ├── css/
    │           │   ├── tokens.css
    │           │   ├── app.css
    │           │   └── print.css
    │           ├── js/
    │           │   ├── shared/
    │           │   ├── pos/
    │           │   └── products/
    │           └── img/
    └── test/
        └── java/br/com/vendafacil/
```

**Padrão dos módulos:** organizar por funcionalidade (`product`, `sale`, `cash` etc.), podendo conter `controller`, `service`, `repository`, `entity`, `dto` e `exception` dentro de cada módulo. Evitar pacotes globais gigantes de `controllers` e `services` desconectados.

### 13.2 Regras de estilo

| Recurso | Regra obrigatória |
|---|---|
| Java | Spotless + formatador Java escolhido; indentação automática; nomes descritivos; injeção por construtor; métodos coesos |
| JS | ESLint e Prettier; `const`/`let`; módulos por responsabilidade; sem script inline extenso |
| HTML/Thymeleaf | indentação consistente, fragments reutilizáveis, templates sem regra de negócio; configurar formatador **compatível com atributos Thymeleaf** |
| CSS | Prettier, tokens e classes próprias com prefixo `vf-` onde fizer sentido |
| SQL | Flyway com migrations imutáveis, nomes claros, `NOT NULL`/FK/índices e comentários para decisões não óbvias |
| Geral | UTF-8, `.editorconfig`, newline final, sem trailing whitespace, segredos fora do repositório |
| Exceções | Tratamento padronizado; mensagens adequadas ao usuário, detalhes diagnósticos somente em logs seguros |
| Logs | `correlationId`, tenant, store, operação, resultado e tempo; sem senhas, CSC, tokens, XML íntegro ou CPF desnecessário |
| Documentação | `README` executável, documentação de decisões e guias de operação/debug |

**Qualidade:** não aprovar classes “Deus”, SQL escondido em controllers, queries sem escopo de tenant, regras fiscais hardcoded em JavaScript, blocos massivos de código comentado, nomes obscuros, `catch(Exception)` silencioso ou logs insuficientes. Comentários explicam **por que**, não repetem literalmente o código.

### 13.3 Requisitos para debug

- Ao reportar erro, usuário recebe mensagem clara e **identificador de referência**, sem stack trace.
- Logs estruturados permitem buscar o fluxo completo por `correlationId`.
- Cada integração externa grava tentativa, resultado resumido, erro categorizado e tempo de resposta.
- Testes reproduzem falhas de concorrência, autorização e timeout fiscal.
- README documenta inicialização, variáveis, comandos de teste, portas, profiles, uso de banco de desenvolvimento e como reproduzir bugs.
- Proibir PII e dados secretos em logs, especialmente em logs do gateway fiscal.

---

## 14. Segurança, privacidade e continuidade

- **RNF-SEC-001:** HTTPS; cookies seguros, CSRF, controle de sessão e cabeçalhos de segurança.
- **RNF-SEC-002:** validação de tenant **em cada acesso** a dados, inclusive downloads, imagens privadas, relatórios e exports.
- **RNF-SEC-003:** parametrização de consultas, validação de entrada, escaping de saída Thymeleaf, restrição de upload e proteção contra XSS/CSRF/IDOR.
- **RNF-SEC-004:** credenciais em secret manager/variáveis de ambiente seguras; criptografia de credenciais fiscais.
- **RNF-SEC-005:** trilha de auditoria de operações críticas e revisões por usuários autorizados.
- **RNF-SEC-006:** backups automatizados, restore testado, RPO/RTO **[PENDENTE]**.
- **RNF-SEC-007:** segregação de ambientes `local`, `test`, `staging` e `production`; dados reais não podem ser usados em testes indiscriminadamente.
- **RNF-SEC-008:** princípios da LGPD: minimização, controle de acesso, transparência, retenção e mecanismos de atendimento de solicitações compatíveis com obrigações legais.
- **RNF-SEC-009:** monitoramento de falhas de emissão fiscal e pendências não reconciliadas.

**Uploads:** validar tipo de arquivo real, extensão permitida, tamanho, autenticação, tenant e propriedade; gerar nome de armazenamento não previsível. Imagens não devem ter permissão de executar scripts no mesmo domínio da aplicação.

---

## 15. Relatórios básicos [APROVADO]

1. Vendas por período/filial/operador, discriminadas por forma de pagamento e situação.
2. Produtos vendidos por quantidade e valor (UN e KG corretamente rotulados).
3. Saldo de estoque por filial e alertas de estoque mínimo.
4. Histórico de entradas/ajustes/estoque negativo autorizado.
5. Abertura, sangria, reforço, fechamento, diferença e reembolso de caixa.
6. Pendências fiscais: aguardando resposta, rejeitadas, contingência, cancelamento pendente, devolução fiscal pendente.

Filtros e exportação ficam sujeitos a permissões e isolamento. Exportação CSV simples **[PROPOSTO]**; dashboards analíticos sofisticados fora da V1.

---

## 16. Estratégia de testes e critérios de aceite

### 16.1 Pirâmide de testes [PROPOSTO]

- **Unidade:** cálculos monetários, descontos efetivos, limites, estados de caixa e elegibilidade de devolução.
- **Integração (Testcontainers):** PostgreSQL real, Flyway, constraints, estoque concorrente e transações.
- **MVC/segurança:** autenticação, CSRF, subdomínio, permissões, filial autorizada e negação de acesso cruzado.
- **Contrato fiscal:** adaptador mock e cenários de timeout, consulta, rejeição, autorização e retransmissão idempotente.
- **Interface:** formulários responsivos, ajuda contextual em toque e teclado, impressão e mensagens de erro.
- **Aceite fiscal:** homologação real com provedor e credenciais de teste válidas antes de liberar `INTEGRATED` em produção.

### 16.2 Cenários de aceitação obrigatórios

| ID | Cenário | Esperado |
|---|---|---|
| AC-01 | Acessar subdomínio A com usuário só de B | Negado, sem vazamento |
| AC-02 | Tentar adivinhar ID de venda/produto de outro tenant | Negado |
| AC-03 | Usuário autorizado em uma filial tenta operar outra | Negado |
| AC-04 | Alterar preço do mesmo produto na filial A | Filial B inalterada |
| AC-05 | Importar CSV com linhas inválidas | Prévia/relatório, sem duplicidade silenciosa |
| AC-06 | Vender `1,250 kg` com preço por kg | Quantidade e arredondamento corretos |
| AC-07 | Duas vendas simultâneas para último item | Sem saldo inconsistente; exceção só com aprovação |
| AC-08 | Operador com limite 0% solicita desconto | Exige aprovação |
| AC-09 | Descontos de item + venda superam teto | Bloqueia ou exige aprovação válida |
| AC-10 | Repetir requisição de venda por timeout | Uma única venda/pagamento/baixa |
| AC-11 | Operador vê fechamento cego antes de declarar | Não recebe o saldo esperado |
| AC-12 | Sangria acima da política sem gerente | Bloqueada |
| AC-13 | Diferença de caixa acima do limite | Justificativa e aprovação obrigatórias |
| AC-14 | Tentativa de operar caixa já fechado | Negada |
| AC-15 | Devolução parcial repetida | Nunca excede itens/valores originais |
| AC-16 | Produto danificado devolvido | Não retorna automaticamente ao saldo vendável |
| AC-17 | Falha do provedor após enviar NFC-e | Consultar antes de retransmitir; sem duplicação |
| AC-18 | Modo fiscal não integrado com obrigação fiscal sem procedimento válido | Não liberar operação comercial irregular como se estivesse regular |
| AC-19 | Recibo comercial impresso | Exibe “não fiscal”; sem chave/protocolo inventados |
| AC-20 | Tooltip em campo confuso no smartphone/teclado | Ajuda visível e navegável |
| AC-21 | Fonte/SQL/HTML fora do padrão | CI sinaliza erro de qualidade |
| AC-22 | Restaurar backup em ambiente de teste | Integridade validada |

**Definição de pronto (DoD) por tarefa:** comportamento implementado, autorização/tenant validados, testes relevantes verdes, lint/format OK, documentação atualizada, mensagens e balões de ajuda conferidos, logs diagnósticos presentes e ausência de segredos no commit.

---

## 17. Backlog de implementação por entregas

### Etapa 0 — Preparação do repositório [PROPOSTO]

- `pom.xml`, Java 25, Spring Boot, profiles e Maven Wrapper.
- Docker Compose PostgreSQL; configurações seguras em `.env.example` sem credenciais reais.
- Flyway inicial, `.editorconfig`, formatadores/lint/CI, README e ADRs.
- Página de erro, layout Bootstrap, tokens CSS e componente reutilizável de balão de ajuda.

**Aceite:** projeto sobe localmente com comando documentado; CI executa testes/lint/formatação.

### Etapa 1 — Multiempresa e autorização

- Tenant, subdomínio, usuário, membership, filial, RBAC e políticas.
- Login, seleção de filial, tela admin SaaS básica e proteção de rotas.
- Testes de isolamento cruzado, inclusive serviços, repositórios e downloads.

**Aceite:** Empresa A e B coexistem sem acesso cruzado; usuário só acessa filial permitida.

### Etapa 2 — Catálogo, preços e estoque

- Produtos, imagem, categorias, unidade UN/KG, preço por filial.
- Upload e importação CSV/XLSX com prévia/erros.
- Movimentos/saldo, autorizações para estoque negativo e concorrência.

**Aceite:** cadastrar uma vez, variar preços e estoques por filial, importar com erros legíveis.

### Etapa 3 — Venda, caixa e descontos

- PDV com busca/leitor, peso manual, carrinho, pagamento manual e troco.
- Regras de desconto 0%/10%/20%, autorização contextual.
- Sessões de caixa, sangria, reforço e fechamento cego/diferença.
- Recibo comercial, relatórios operacionais iniciais.

**Aceite:** ciclo comercial completo sem integração fiscal, **somente em cenário de desenvolvimento/demonstração ou fluxo legalmente validado**.

### Etapa 4 — Integração fiscal e devoluções

- `FiscalGateway`, adaptador concreto escolhido, homologação NFC-e 65 / NF-e 55.
- Pendências, retentativas idempotentes, consultas, cancelamentos, contingência onde suportada.
- DANFE, XMLs, configurações e armazenamento seguro.
- Devolução total/parcial, reembolso e procedimento fiscal correspondente.

**Aceite:** cenários fiscais testados em homologação e aprovados com suporte fiscal/contábil antes de produção.

### Etapa 5 — Operação comercial e produção

- Testes ponta a ponta, segurança, carga básica, impressão em equipamento real.
- Backup/restore, monitoramento, alertas fiscais, documentação de suporte.
- Políticas de privacidade, retenção, operação e suporte ao cliente.
- Piloto assistido com empresa real e regime tributário validado.

**Aceite:** checklist AC completo conforme módulos ativados, homologação tributária validada e plano operacional de suporte.

---

## 18. Riscos, trade-offs e mitigações

| Risco | Impacto | Mitigação |
|---|---|---|
| Isolamento multiempresa incorreto | Crítico | Contexto obrigatório, constraints, testes cruzados, possível RLS |
| Pequeno comerciante operar sem documento fiscal obrigatório | Crítico | Diferenciar modo técnico e obrigação legal; bloquear ativação irregular |
| Provedor fiscal indisponível | Alto | Outbox, consulta de status, estados explícitos, contingência válida |
| Concorrência no estoque | Alto | Transações, locks/revisão, teste simultâneo |
| Retentativa duplicar venda/reembolso | Alto | Idempotency key e restrições únicas |
| Fechamento cego expor valor esperado | Médio/alto | Nunca enviar saldo antes da declaração |
| Perda de XMLs/certificados | Crítico | Storage seguro, backup, verificação e recuperação |
| Frontend crescer desorganizado | Médio | Templates/fragments, JS modular, lint, padrões de UX |
| Código ilegível para suporte | Alto | Formatadores no CI, logs, README, ADR e revisão |
| Escopo fiscal expandir para toda UF cedo demais | Alto | SP primeiro; arquitetura por adaptador/UF |

---

## 19. Pendências que NÃO impedem a escrita do SDD

| ID | Pendência | Quando resolver |
|---|---|---|
| P-001 | Domínio comercial definitivo e disponibilidade | Antes de DNS/produção |
| P-002 | Nome comercial definitivo do produto | Antes do lançamento público |
| P-003 | Provedor fiscal e contrato de integração | Antes da Etapa 4 |
| P-004 | Regime real do cliente-piloto | Antes de homologação fiscal real |
| P-005 | Certificado digital, credenciamento, CSC e dados tributários | Antes de emitir em produção |
| P-006 | Regras por produto (NCM, CFOP, CSOSN/CST, IBS/CBS etc.) | Configuração/homologação contábil |
| P-007 | Modelo real de impressoras térmicas | Antes do piloto físico |
| P-008 | Política de hospedagem, disponibilidade, custo, backup e RPO/RTO | Antes da produção |
| P-009 | Limites numéricos iniciais de sangria e divergência | Na configuração padrão a validar |
| P-010 | Modelo exato de acesso de suporte e MFA | Antes da produção |
| P-011 | Confirmação documental de hipóteses fiscais do modo `EXTERNAL`/`NOT_APPLICABLE` | Antes de ativar cada estabelecimento |
| P-012 | Compatibilidade de formatter HTML com Thymeleaf | Etapa 0 |

---

## 20. Registro das decisões aprovadas

| ID | Decisão |
|---|---|
| D-001 | Produto com nome provisório **vendaFacil** |
| D-002 | URL por empresa: `{slug}.vendafacil.com.br` (exemplo de domínio) |
| D-003 | Múltiplas filiais desde a V1 |
| D-004 | Catálogo compartilhado por empresa |
| D-005 | Preços e estoque independentes por filial |
| D-006 | Produtos por `UN` e `KG` manualmente |
| D-007 | Estoque negativo bloqueado por padrão, exceção gerencial justificada |
| D-008 | Venda por caixa e registro manual de pagamentos |
| D-009 | Sangria configurável, gerente por padrão |
| D-010 | Fechamento configurável, **cego por padrão** |
| D-011 | Divergência de caixa com limite configurável e autorização acima do limite |
| D-012 | Cancelamento configurável, gerente por padrão |
| D-013 | Devolução total/parcial na V1, com reembolso, estoque e fiscal |
| D-014 | Desconto por item/venda, percentual/valor, acumulado e auditado |
| D-015 | Descontos padrão: operador **0%**, gerente **10%**, administrador **20%** |
| D-016 | Desconto de 100% bloqueado por padrão |
| D-017 | São Paulo como primeira UF fiscal |
| D-018 | NFC-e 65 e NF-e 55 via **provedor fiscal especializado** |
| D-019 | Integração fiscal **tecnicamente opcional por filial**, sem dispensar obrigações legais |
| D-020 | Recibo comum separado de DANFE/documento fiscal |
| D-021 | Java 25, Spring Boot 4, PostgreSQL, Thymeleaf, JavaScript puro, Bootstrap 5 e CSS personalizado |
| D-022 | Monólito modular, sem Vue/HTMX/microserviços |
| D-023 | Código e arquivos obrigatoriamente bem formatados e legíveis para debug |
| D-024 | Balões de ajuda acessíveis em campos que geram dúvidas |
| D-025 | Cliente-piloto: minimercado/mercearia; regime tributário a definir |

---

## 21. Checklist antes da primeira linha de implementação

- [ ] Aprovar este SDD como base de trabalho (sem confundir aprovação técnica com fiscal).
- [ ] Escolher package Java definitivo: `br.com.vendafacil` ou namespace sob domínio efetivamente controlado (ajustar se necessário).
- [ ] Fixar versões exatas de Java, Spring Boot, PostgreSQL e plugins de build.
- [ ] Criar Git e políticas de branches/revisão.
- [ ] Montar pipeline de testes, lints, formatação e validações de segurança.
- [ ] Criar migrations iniciais de tenant, filial, usuário, vínculo e papéis.
- [ ] Implementar login por subdomínio e isolamento em teste.
- [ ] Criar layout padrão e componente acessível de tooltip/popover.
- [ ] Definir processo e requisitos de onboarding fiscal para cada filial.

---

## 22. Referências públicas

1. **Spring Boot — requisitos de sistema:** https://docs.spring.io/spring-boot/system-requirements.html
2. **SEFAZ-SP — NFC-e, obrigatoriedade, credenciamento e CSC:** https://portal.fazenda.sp.gov.br/servicos/nfce/
3. **Portal Nacional da NF-e — Manual de Orientação ao Contribuinte e DANFE:** https://www.nfe.fazenda.gov.br/portal/listaConteudo.aspx?tipoConteudo=ndIjl+iEFdE%3D
4. **SEFAZ-SP — perguntas frequentes NF-e e regras de devolução:** https://portal.fazenda.sp.gov.br/servicos/nfe/Paginas/perguntas-frequentes.aspx
5. **SEFAZ-SP — cancelamento extemporâneo NFC-e:** https://portal.fazenda.sp.gov.br/servicos/nfce/Paginas/Guia-Cancel-Extemp-NFCe.aspx

> As regras legais e versões de manuais mudam. Validar links e textos vigentes na data da implementação/homologação. Nenhum endereço, empresa fictícia ou número de documento incluído no projeto deve ser confundido com credencial real.

---

## 23. Próxima ação técnica

Iniciar a **Etapa 0**, sem tentar implementar toda a V1 de uma vez. O primeiro incremento verificável será:

> **“Duas empresas podem acessar seus próprios subdomínios, autenticar usuários e selecionar filiais, sem compartilhar dados ou permissões.”**

A partir dessa fundação, avançar para produtos, preços e estoque. Só depois implementar PDV e fiscal. O documento permanece versionado e as decisões novas devem ser registradas antes de alterar fluxos críticos.

