# NoteAra

Projeto dividido entre a interface web e a API Spring Boot:

```text
Projeto Integrador/
├── frontend/   páginas HTML, estilos e JavaScript
└── backend/    API Spring Boot, conexão MySQL e scripts do banco
```

## Iniciar

1. No VS Code, abra `frontend/index.html` com o Live Server ou Live Preview. O arquivo encaminha para a tela de login.
2. Em outro terminal, abra a pasta `backend` e execute:

   ```bash
   mvn clean spring-boot:run
   ```

3. Mantenha o MySQL ligado. A configuração local usa `localhost:3307` e o banco `projeto_integrador`; as tabelas são criadas quando a API inicia.

A conta demonstrativa desta instalação é `henry@senai.local` / `123456`. Troque a senha na área de usuários. Para detalhes de configuração, consulte [backend/README.md](backend/README.md).
