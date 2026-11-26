# Backend do NoteAra

API Java com Spring Boot e MySQL para o sistema de inventário de notebooks, usuários e empréstimos.

## Requisitos

- JDK 17 ou superior
- Maven 3.6.3 ou superior
- MySQL em execução

## Preparar o banco

No MySQL, crie o banco:

```sql
CREATE DATABASE projeto_integrador CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

## Configurar conexão

O padrão local é `localhost:3307`, banco `projeto_integrador`, usuário `root` e senha `root`, conforme configurado para esta instalação. Para usar outros dados, defina as variáveis de ambiente `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` antes de iniciar a aplicação. Em outros ambientes, prefira variáveis de ambiente em vez de guardar senhas no projeto.

As tabelas são criadas automaticamente ao iniciar. A conta demonstrativa inicial desta instalação usa `henry@senai.local` e `123456`; troque essa senha na tela **Usuários** antes de usar com dados reais. Essa conta é apenas para desenvolvimento local.

## Iniciar

Dentro desta pasta, execute:

```bash
mvn clean spring-boot:run
```

O endpoint `GET http://localhost:8080/api/health` retorna o estado da API e testa uma consulta ao MySQL. Abra `../frontend/index.html` por um servidor local (Live Server em `http://localhost:5500` ou Live Preview em `http://localhost:3000`), não diretamente como arquivo.

## Rotas usadas pelo front

- `POST /api/auth/login` e `POST /api/auth/logout`
- `GET /api/dashboard`
- `GET/POST /api/notebooks`, `PUT/DELETE /api/notebooks/{id}`
- `GET/POST /api/users`, `PUT/DELETE /api/users/{id}`
- `GET/POST /api/loans`, `POST /api/loans/{id}/return`

O front fica na pasta `../frontend` e foi separado em `login.html`/`login.js` e `admin.html`/`admin.js`; `index.html` encaminha para o login. Após autenticar, a API entrega um token Bearer aleatório válido por 90 minutos (ajustável por `ACCESS_TOKEN_TTL_MINUTES`). O token fica apenas no `sessionStorage` da aba, é enviado no cabeçalho `Authorization` e pode ser revogado ao sair. O banco guarda somente o hash SHA-256 do token. Senhas de usuários usam BCrypt.

Todas as rotas de dados exigem token e perfil Administrador. Desativar um usuário ou trocar sua senha invalida os tokens dele. O CORS permite as portas locais configuradas em `WebConfig.java` (Live Server 5500/5501, Vite 5173 e Live Preview/localhost 3000); ajuste a lista se o servidor do front usar outra porta.

Este back-end foi preparado para execução local de demonstração. Antes de publicar em um servidor, configure as credenciais por variáveis de ambiente e use HTTPS.
