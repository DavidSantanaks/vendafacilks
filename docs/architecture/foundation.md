# Fundação e limites de confiança

Na versão `0.3.0-SNAPSHOT`, o módulo `commerce` acrescenta checkout, caixa, descontos/aprovações, recibo de demonstração e relatórios. A transação compartilha o DataSource com catálogo/estoque e auditoria. V4 preserva V1–V3. Veja [ADR 0004](../decisions/0004-sales-and-cash.md) e [contratos de venda/caixa](../api/sales-and-cash.md). O servidor mantém vendas bloqueadas em produção/staging até a integração fiscal.

```text
Host oficial → TenantHostFilter → sessão Spring Security → MembershipGuardFilter
    → Controller → serviço → AccessService → repository escopado → PostgreSQL
```

O primeiro filtro valida o host e a empresa ativa. `admin` é contexto de plataforma separado, sem tenant operacional. Nenhum `X-Forwarded-Host` define a empresa. Na referência de produção, somente o IP fixo do proxy pode informar o protocolo HTTPS.

O login valida senha e vínculo na empresa resolvida. A sessão é host-only, HttpOnly, SameSite=Lax e Secure por padrão; `local` permite HTTP. Após login, o ID da sessão muda. A cada requisição autenticada, o guard valida identidade ativa, tenant do principal e vínculo ativo. A suspensão da empresa impede todas as rotas de operação.

`AccessService` valida o principal também na camada de serviço. A filial da sessão é apenas uma preferência: seu vínculo e status são revalidados antes do acesso. Consultas de grants incluem empresa, membership e filial. Papéis de outra empresa não são usados. A criação de usuário concede um único papel da empresa na filial ativa, limitado às permissões e teto do concedente.

As chaves compostas `(tenant_id, membership_id)`, `(tenant_id, store_id)` e `(tenant_id, role_id)` impedem grants cruzados mesmo diante de erro no código. O banco não usa `ddl-auto=update`: somente Flyway cria/altera o esquema.

Auditoria é gravada na mesma transação das alterações administrativas. A trigger bloqueia UPDATE/DELETE. Logs de requisição incluem correlationId, tenant, status e duração, sem corpo, parâmetros, senhas ou tokens. Auditoria visível no dashboard é restrita à filial ativa e à permissão de administração.

A limitação de login é local ao processo: dez falhas por empresa/IP em quinze minutos, memória limitada. Para múltiplas réplicas, aplicar também limite distribuído no proxy. O proxy precisa definir os IPs confiáveis; endereços arbitrários de headers não são aceitos.

Permissões são persistidas, com perfis padrão 0%/10%/20%, aplicados no checkout e na aprovação de descontos acumulados da Etapa 3. Políticas de caixa e teto de desconto são configuráveis por empresa com `USER_MANAGE`; edição das definições de papéis continua no backlog.

A gestão de acessos pode ativar/desativar grants e reatribuir um perfil permitido ao concedente, somente na filial ativa e sem autoalteração. A administração SaaS gerencia dados de identidade e metadados de empresa sem impersonação. As URLs dos cards usam configuração de domínio/protocolo/porta pública.

Recuperação usa hash de token com validade/contexto no PostgreSQL e envio SMTP após commit em executor limitado. Requisições públicas são processadas em segundo plano para evitar consulta síncrona que revele a existência da conta. Após redefinição ou bloqueio global, `credential_version` invalida os principais de sessão anteriores. O Mailpit é exclusivo do Compose local; SMTP real depende de configuração. Ver ADR 0002 para decisões e limites da entrega de e-mails.

A Etapa 2 amplia esse mesmo contexto para categorias, produtos, preços, imagens, estoque e importações. Serviços verificam permissões e usam consultas JDBC parametrizadas no mesmo DataSource/transação da auditoria JPA. As FKs compostas impedem referências cruzadas; locks de produto/saldo e UUIDs de operação protegem concorrência e reenvios. Imagens normalizadas ficam no PostgreSQL e não recebem URL pública em `/static`. Detalhes e limites estão na [ADR 0003](../decisions/0003-catalog-and-stock.md).
