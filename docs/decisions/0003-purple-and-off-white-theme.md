# ADR 0003 — Paleta roxa e off-white

Data: 18/10/2026. Estado: adotado por solicitação do usuário.

A identidade visual usa roxo como cor de ação, tons de ameixa no menu e nas áreas de apresentação, lilás nos destaques e off-white nas superfícies. A mudança cobre login, recuperação, seleção de filial, dashboards, formulários, gestão SaaS e favicon.

As cores da aplicação ficam centralizadas em `static/css/tokens.css`. `app.css` referencia os tokens, incluindo foco, estados de botão, bordas, texto secundário e cores do menu. Variáveis Bootstrap e alertas também acompanham o tema. Verde, âmbar e vermelho ficam restritos a indicações semânticas de estado, alerta e erro.

O modo de impressão continua com fundo branco e texto preto. A alteração visual não muda regras de autenticação, permissões ou dados.
