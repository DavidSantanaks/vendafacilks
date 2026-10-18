# ADR 0001 — Fundação multiempresa e primeiro incremento

Data: 18/10/2026. Estado: adotado para implementação, conforme autorização para seguir o SDD.

Evolução posterior: recuperação de senha e gestão de acessos implementadas conforme [ADR 0002](0002-recovery-and-administration.md); as pendências abaixo descrevem a entrega inicial.

## Contexto e escopo

A seção 23 do SDD pede iniciar a Etapa 0 e entregar primeiro autenticação em duas empresas com seleção de filiais isolada. Esta entrega implementa a infraestrutura da Etapa 0 e o núcleo de acesso da Etapa 1. Não altera os requisitos aprovados nem apresenta a V1 como completa.

## Decisões

- Namespace inicial `br.com.vendafacil`, conforme sugestão do SDD; não afirma propriedade do domínio comercial.
- Java 25; Spring Boot 4.0.8, patch estável da linha 4.0 verificado na documentação oficial; Maven Wrapper 3.3.4/Maven 3.9.11; PostgreSQL 17.11. Dependências Spring são fixadas pelo BOM do Boot.
- Monólito modular, Spring MVC/Thymeleaf, HTML/JS puro, Bootstrap 5.3.8 e Bootstrap Icons 1.13.1. Não há servidor Node na aplicação.
- Identidades globais e vínculos por empresa; o principal carrega o tenant autenticado, mas permissões são consultadas por filial a cada operação.
- Host original validado antes da autenticação; em desenvolvimento somente `{slug}.localhost`. Sem fallback que escolha uma empresa para `localhost`.
- Banco compartilhado com FKs compostas para grants. RLS fica pendente de avaliação específica; não é alegado como implementado.
- `USER_MANAGE` controla a administração inicial de filiais e equipe. Em filiais novas, o criador recebe somente os papéis que já possuía na filial ativa. A edição de uma filial exige a permissão nela própria.
- E-mails já existentes não são redefinidos/vinculados por cadastro administrativo; convites com verificação de posse são um incremento futuro. Evita apropriação de identidades globais.
- Sem logo de usuário nesta etapa: identidade visual padrão local; upload virá com a camada de arquivos e validações do SDD.
- Audit trail append-only protegido por trigger. Dados de suporte da empresa não são expostos ao Super Admin.
- Ajuda contextual usa `details/summary` nativo: toque, Enter, espaço, foco e Escape, sem hover obrigatório, scripts inline ou dependência de Popper.
- Prettier 3.6.2 com parser HTML e `htmlWhitespaceSensitivity: ignore`; atributos `th:*` são preservados e templates são renderizados na suíte MVC.
- BCrypt custo 12 e limite explícito de 72 bytes UTF-8. Senhas iniciais: 12 a 64 caracteres. Recuperação, convite e MFA precisam ser implementados antes de uso comercial.

## Consequências

`INTEGRATED`, vendas e caixa não são ativados ou simulados. Sem requisito de provedor fiscal nesta fundação. Novos módulos devem reutilizar `AccessService`, contexto do host e consultas escopadas; IDs de empresa recebidos em payload não definem autorização.

Fontes verificadas: [requisitos Spring Boot](https://docs.spring.io/spring-boot/4.0/system-requirements.html), [Spring Security Form Login](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html), [Maven Wrapper](https://maven.apache.org/tools/wrapper/).
