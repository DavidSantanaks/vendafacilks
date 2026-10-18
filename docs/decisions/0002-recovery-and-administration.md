# ADR 0002 — Recuperação de senha e gestão administrativa

Data: 18/10/2026. Estado: adotado. Solicitado pelo usuário após o teste inicial do portal; caixa de e-mails local escolhida para os testes.

## Comportamento

Todos os portais têm “Esqueci minha senha”. A recuperação confirma acesso ao e-mail já cadastrado; o administrador pode solicitar o mesmo envio, sem conhecer ou escolher a senha da pessoa. A resposta pública é a mesma para conta existente, inexistente ou sem vínculo com o portal. A consulta e o envio acontecem em executor limitado, fora da resposta HTTP.

Tokens usam 32 bytes de SecureRandom; somente SHA-256 é persistido. Cada token tem contexto de empresa/plataforma, expira em 30 minutos e é invalidado após uso ou novo pedido. O domínio e a porta dos links vêm da configuração; o Host/Forwarded recebido não monta o link. Páginas de redefinição usam no-referrer/no-store. Formulários exigem CSRF e há limites por IP/conta.

Redefinir incrementa a versão de credencial da identidade global. O guard invalida sessões antigas em todos os portais. A conta não é reativada por recuperação; papéis e vínculos continuam sendo verificados. O e-mail não pode ser alterado pela gestão inicial, evitando redirecionar recuperações sem verificação de posse. A nova senha só é definida após o link e a confirmação; não há login automático. A pessoa recebe também aviso de conclusão, sem a senha.

O administrador da empresa altera perfil ou bloqueia/restaura o grant somente na filial ativa, com limites do concedente e sem editar o próprio acesso. O Super Admin gerencia nomes e bloqueio global de identidades, consulta vínculos e solicita recuperação em um portal vinculado. O painel SaaS continua sem impersonação e sem acesso a vendas/estoque/caixa. Cards de empresa mostram contagens e links diretos ao login e à gestão dos usuários daquela empresa. Nomes de empresas podem ser alterados sem mudar o slug estável.

## Entrega de e-mails

O Compose local usa Mailpit 1.31.6, acessível apenas em loopback: interface 8025 e SMTP 1025. Captura os e-mails inclusive das contas fictícias e não envia à internet. Produção usa SMTP configurado em variáveis e remetente verificado, com autenticação/TLS conforme o provedor.

O envio ocorre após commit, de modo assíncrono, com timeouts. Falhas geram categoria de erro nos logs sem e-mail/token/conteúdo. Não há fila persistente ou retentativa automática de e-mails neste incremento: após falha ou reinício durante o envio, solicitar novo link. Dados de recuperação usados/expirados ainda precisam de rotina de retenção.

Fontes: [OWASP Forgot Password](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html), [Spring Boot Mail](https://docs.spring.io/spring-boot/reference/io/email.html), [Mailpit Docker](https://mailpit.axllent.org/docs/install/docker/).
