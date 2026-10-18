# Contratos implementados

Host válido é obrigatório. Operações POST exigem token CSRF. Não há `tenant_id` aceito como escopo em formulários/JSON.

| Método | URL | Comportamento |
| --- | --- | --- |
| GET/POST | `/login` | Login com `username` e `password` no host da empresa ou plataforma |
| GET/POST | `/recuperar-senha` | Solicitar link no e-mail cadastrado; resposta genérica, CSRF e limites |
| GET/POST | `/redefinir-senha` | Conferir token e definir/confirmar nova senha; uso único e expiração |
| POST | `/logout` | Encerra a sessão |
| GET | `/` | Dashboard ou redirecionamento para seleção/plataforma |
| GET | `/filiais` | Somente filiais ativas autorizadas |
| POST | `/filiais/ativa` | Valida `storeId`, audita e seleciona na sessão |
| GET | `/api/v1/filiais` | Lista JSON de filiais ativas autorizadas |
| GET | `/api/v1/filiais/{id}` | Consulta filial autorizada; ID alheio retorna 403 genérico |
| GET | `/api/v1/filiais/exportacao` | CSV apenas de filiais autorizadas; exige USER_MANAGE; protege fórmulas CSV |
| GET | `/configuracoes` | Resumo; exige USER_MANAGE na filial ativa |
| GET/POST | `/configuracoes/filiais` | Consultar/criar filial; exige USER_MANAGE |
| POST | `/configuracoes/filiais/{id}` | Editar/ativar/desativar filial; exige permissão na filial alvo |
| GET/POST | `/usuarios` | Consultar/criar acesso na filial ativa; exige USER_MANAGE |
| POST | `/usuarios/acessos/{id}` | Alterar perfil/ativação do grant na filial ativa, respeitando o concedente |
| POST | `/usuarios/acessos/{id}/recuperacao` | Solicitar recuperação do usuário visível à administração da filial |
| GET/POST | `/admin/empresas` | Listar/criar empresa; somente Super Admin no host admin |
| POST | `/admin/empresas/{id}/status` | Ativar/suspender com auditoria |
| POST | `/admin/empresas/{id}` | Editar nome da empresa, preservando slug |
| GET | `/admin/usuarios` | Buscar por `q`, filtrar por `empresa` e paginar com `page` (20 contas) |
| POST | `/admin/usuarios/{id}` | Editar nome e ativação global da identidade; protege o próprio Super Admin |
| POST | `/admin/usuarios/{id}/recuperacao` | Recuperação no portal da plataforma ou em `tenantId` vinculado |
| GET | `/actuator/health` | Somente estado agregado UP/DOWN; sem detalhes internos |

Erros produzidos por serviços e autenticação em `/api/` usam Problem Details: `type`, `title`, `status`, `detail`, `correlationId`. Sessão ausente recebe 401; acesso negado/CSRF recebe 403; filial ainda não escolhida recebe 409. Cabeçalho `X-Correlation-ID` identifica requisições. Erros anteriores ao MVC por host inválido/empresa indisponível usam uma página genérica.

CSRF de formulários é inserido pelo Thymeleaf. O layout disponibiliza token/header em metatags; `static/js/shared/http.js` fornece `requestJson` para os módulos seguintes, com CSRF e tratamento de expiração. Não há endpoint de venda/caixa/fiscal nesta entrega.

A gestão SaaS de usuários possui paginação de servidor. As demais listas ainda são voltadas ao conjunto pequeno de filiais/equipe do piloto; adicionar paginação antes de ampliar essas operações.
