# Execução, debug e preparação para produção

## Diagnóstico local

Se `docker compose up -d --build` falhar com `docker-credential-desktop: executable file not found in %PATH%`, execute `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docker-up.ps1`. O iniciador adiciona a pasta dos executáveis do Docker Desktop ao PATH do processo antes de chamar o Compose. Isso resolve terminais que não receberam o caminho da instalação. Se preferir usar os comandos Docker diretamente, acrescente a pasta `resources/bin` da instalação ao `$env:Path` do terminal atual ou abra um terminal com o PATH atualizado.

1. `docker compose ps`: banco deve estar healthy e app em execução.
2. `docker compose logs --tail=100 app`: procurar erros de inicialização/Flyway e correlationId.
3. Abrir `http://boa-praca.localhost:8080/login`; host raiz é recusado por projeto.
4. `curl http://127.0.0.1:8080/actuator/health`: retorna apenas estado agregado.
5. Conferir `SPRING_PROFILES_ACTIVE=local`, `APP_BASE_DOMAIN=localhost` e credenciais do banco. Dados demo só são criados em banco vazio.

Migrations aplicadas não devem ser editadas. Criar V2/V3 para mudanças. Nunca usar `ddl-auto=update` ou apagar histórico para resolver uma falha. O teste com banco externo requer banco próprio de teste; o mesmo banco de operação não deve ser reutilizado.

O navegador exibe uma referência e a resposta traz `X-Correlation-ID`. Busque a referência nos logs JSON. Não habilite logging de parâmetros JDBC, senhas, corpos de login ou tokens. Auditoria fica em `audit_logs` e rejeita alterações/exclusões.

## Proxy e HTTPS de referência

O arquivo `docker-compose.production.yml` é independente do Compose local. Banco e aplicação não têm portas públicas. Somente Caddy publica 80/443. Configure DNS dos hosts oficiais e `APP_BASE_DOMAIN` para um domínio controlado; ajuste a lista no `infra/Caddyfile`. Hosts de novos tenants precisam ser adicionados antes do acesso. Certificado wildcard requer integração DNS challenge e gestão de credenciais específica, ainda não configurada.

O proxy mantém o Host oficial, remove `Forwarded`/`X-Forwarded-Host` e informa protocolo HTTPS. Tomcat só confia no IP `172.28.56.10`, fixado no Compose. Fora dessa referência, defina `APP_TRUSTED_PROXY_REGEX` para os proxies efetivos e impeça acesso direto à aplicação. Sem proxy confiável, a aplicação recusa HTTP no profile production. Cookies são Secure/HttpOnly/SameSite=Lax e não recebem Domain compartilhado.

Primeiro Super Admin em banco vazio: definir temporariamente `APP_BOOTSTRAP_ADMIN_ENABLED=true`, `BOOTSTRAP_ADMIN_EMAIL` e `BOOTSTRAP_ADMIN_PASSWORD` em ambiente seguro. A inicialização cria a identidade apenas se não existir Super Admin e audita a criação; nunca altera a senha de identidade existente. Reiniciar depois com o bootstrap desabilitado e remover as duas credenciais do ambiente. Não usar as contas fictícias fora de desenvolvimento/teste.

```sh
docker compose -f docker-compose.production.yml up -d --build
```

Esta referência não substitui endurecimento e homologação: a aplicação continua no incremento de fundação e não opera como PDV comercial. Completar o checklist de produção do SDD, MFA, convites, entrega SMTP validada, backups e restore, privilégios mínimos do banco, limites distribuídos, retenção e observabilidade antes do piloto real.

## Recuperação de acesso

O Compose local inicia Mailpit. Acesse `http://localhost:8025` e localize a mensagem pelo destinatário. O login de cada portal tem “Esqueci minha senha”, inclusive o Super Admin. Também é possível solicitar o envio na gestão de usuários; nenhuma senha temporária é exibida no painel.

Links são de uso único, duram 30 minutos e funcionam apenas no portal solicitado. Novo pedido invalida o anterior. Depois da alteração, a identidade global exige a nova senha em todos os portais e as sessões antigas são recusadas. Recuperação não reativa conta ou permissão bloqueada.

Se a mensagem não chegar, conferir o container Mailpit ou SMTP configurado e as categorias de falha nos logs (`RecoveryDispatcher`/`RecoveryMailer`). Não registrar tokens ou conteúdo dos e-mails. Falhas de envio não têm retentativa automática; após resolver a conexão, pedir um novo link. Em produção, configurar SMTP, remetente verificado, autenticação/TLS e porta pública antes de liberar recuperação.

## Backup e restauração

Definir RPO/RTO, credenciais exclusivas de backup, armazenamento criptografado e retenção antes de produção. Usar `pg_dump` em formato custom e restaurar com `pg_restore` em banco novo de homologação. Conferir versão do Flyway, contagens, FKs e autenticação/isolamento após restauração. O procedimento precisa ser automatizado e ensaiado; AC-22 não está concluído nesta entrega.
