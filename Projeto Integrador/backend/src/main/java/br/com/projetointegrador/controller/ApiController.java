package br.com.projetointegrador.controller;

import java.sql.Date;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import br.com.projetointegrador.security.ApiTokenService;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final JdbcTemplate db;
    private final ApiTokenService tokens;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public ApiController(JdbcTemplate db, ApiTokenService tokens) {
        this.db = db;
        this.tokens = tokens;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String email = required(body, "email").toLowerCase();
        String password = required(body, "password");
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT id, name, email, password_hash, role FROM app_user WHERE email = ? AND active = TRUE", email);
        if (rows.isEmpty() || !passwordEncoder.matches(password, (String) rows.get(0).get("password_hash"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos.");
        }
        Map<String, Object> user = rows.get(0);
        if (!"Administrador".equals(user.get("role"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso permitido apenas para administradores.");
        }
        ApiTokenService.IssuedToken token = tokens.issue(((Number) user.get("id")).longValue());
        Map<String, Object> response = Map.of(
                "accessToken", token.value(),
                "tokenType", "Bearer",
                "expiresAt", token.expiresAt().toString(),
                "user", Map.of("id", user.get("id"), "name", user.get("name"), "email", user.get("email"), "role", user.get("role"))
        );
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(response);
    }

    @PostMapping("/auth/logout")
    public void logout(HttpServletRequest request) {
        tokens.revoke(bearerToken(request));
    }

    @GetMapping("/auth/me")
    public Map<String, Object> currentUser(HttpServletRequest request) {
        long userId = authenticatedUserId(request);
        return db.queryForMap("SELECT id, name, email, role FROM app_user WHERE id = ? AND active = TRUE", userId);
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return Map.of(
                "notebooks", count("SELECT COUNT(*) FROM notebook"),
                "available", count("SELECT COUNT(*) FROM notebook WHERE status = 'DISPONIVEL'"),
                "onLoan", count("SELECT COUNT(*) FROM notebook WHERE status = 'EMPRESTADO'"),
                "users", count("SELECT COUNT(*) FROM app_user WHERE active = TRUE"),
                "overdue", count("SELECT COUNT(*) FROM loan WHERE returned_at IS NULL AND due_date < CURRENT_DATE")
        );
    }

    @GetMapping("/notebooks")
    public List<Map<String, Object>> notebooks() {
        return db.queryForList("SELECT * FROM notebook ORDER BY id DESC");
    }

    @PostMapping("/notebooks")
    public Map<String, Object> addNotebook(@RequestBody Map<String, String> body) {
        db.update("INSERT INTO notebook(asset_tag, serial_number, brand, model, status, location, notes) VALUES (?, ?, ?, ?, 'DISPONIVEL', ?, ?)",
                required(body, "assetTag"), body.getOrDefault("serialNumber", ""), required(body, "brand"),
                required(body, "model"), body.getOrDefault("location", ""), body.getOrDefault("notes", ""));
        return db.queryForMap("SELECT * FROM notebook WHERE id = LAST_INSERT_ID()");
    }

    @PutMapping("/notebooks/{id}")
    public Map<String, Object> editNotebook(@PathVariable long id, @RequestBody Map<String, String> body) {
        int changed = db.update("UPDATE notebook SET asset_tag=?, serial_number=?, brand=?, model=?, location=?, notes=? WHERE id=? AND status <> 'EMPRESTADO'",
                required(body, "assetTag"), body.getOrDefault("serialNumber", ""), required(body, "brand"),
                required(body, "model"), body.getOrDefault("location", ""), body.getOrDefault("notes", ""), id);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Notebook não encontrado ou está emprestado.");
        return db.queryForMap("SELECT * FROM notebook WHERE id = ?", id);
    }

    @DeleteMapping("/notebooks/{id}")
    public void deleteNotebook(@PathVariable long id) {
        try {
            if (db.update("DELETE FROM notebook WHERE id = ? AND status <> 'EMPRESTADO'", id) == 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Notebook não encontrado ou ainda está emprestado.");
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este notebook possui histórico e não pode ser excluído.");
        }
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        return db.queryForList("SELECT id, name, email, role, active, created_at FROM app_user ORDER BY name");
    }

    @PostMapping("/users")
    public Map<String, Object> addUser(@RequestBody Map<String, String> body, HttpServletRequest request) {
        requireAdministrator(request);
        String password = requireStrongPassword(required(body, "password"));
        db.update("INSERT INTO app_user(name, email, password_hash, role) VALUES (?, ?, ?, ?)",
                required(body, "name"), required(body, "email").toLowerCase(),
                passwordEncoder.encode(password), body.getOrDefault("role", "Colaborador"));
        return db.queryForMap("SELECT id, name, email, role, active, created_at FROM app_user WHERE id = LAST_INSERT_ID()");
    }

    @PutMapping("/users/{id}")
    public Map<String, Object> editUser(@PathVariable long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        requireAdministrator(request);
        String password = body.getOrDefault("password", "").trim();
        if (password.isEmpty()) {
            db.update("UPDATE app_user SET name=?, email=?, role=? WHERE id=?", required(body, "name"),
                    required(body, "email").toLowerCase(), body.getOrDefault("role", "Colaborador"), id);
        } else {
            password = requireStrongPassword(password);
            db.update("UPDATE app_user SET name=?, email=?, role=?, password_hash=? WHERE id=?", required(body, "name"),
                    required(body, "email").toLowerCase(), body.getOrDefault("role", "Colaborador"), passwordEncoder.encode(password), id);
            db.update("DELETE FROM api_token WHERE user_id = ?", id);
        }
        return db.queryForMap("SELECT id, name, email, role, active, created_at FROM app_user WHERE id = ?", id);
    }

    @DeleteMapping("/users/{id}")
    public void deactivateUser(@PathVariable long id, HttpServletRequest request) {
        requireAdministrator(request);
        if (db.update("UPDATE app_user SET active=FALSE WHERE id=?", id) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado.");
        db.update("DELETE FROM api_token WHERE user_id = ?", id);
    }

    @GetMapping("/loans")
    public List<Map<String, Object>> loans() {
        return db.queryForList("SELECT l.id, l.notebook_id AS notebookId, l.user_id AS userId, " +
                "n.asset_tag AS assetTag, n.brand, n.model, u.name AS userName, u.email AS userEmail, " +
                "l.checkout_at AS checkoutAt, l.due_date AS dueDate, l.returned_at AS returnedAt, l.notes " +
                "FROM loan l JOIN notebook n ON n.id=l.notebook_id JOIN app_user u ON u.id=l.user_id ORDER BY l.id DESC");
    }

    @PostMapping("/loans")
    @Transactional
    public Map<String, Object> addLoan(@RequestBody Map<String, String> body) {
        long notebookId = requiredLong(body, "notebookId");
        long userId = requiredLong(body, "userId");
        int reserved = db.update("UPDATE notebook SET status='EMPRESTADO' WHERE id=? AND status='DISPONIVEL'", notebookId);
        if (reserved == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Notebook indisponível.");
        Date dueDate;
        try { dueDate = Date.valueOf(required(body, "dueDate")); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data de devolução inválida."); }
        db.update("INSERT INTO loan(notebook_id, user_id, due_date, notes) VALUES (?, ?, ?, ?)",
                notebookId, userId, dueDate, body.getOrDefault("notes", ""));
        return db.queryForMap("SELECT l.id, l.notebook_id AS notebookId, l.user_id AS userId, n.asset_tag AS assetTag, " +
                "n.brand, n.model, u.name AS userName, l.checkout_at AS checkoutAt, l.due_date AS dueDate, " +
                "l.returned_at AS returnedAt, l.notes FROM loan l JOIN notebook n ON n.id=l.notebook_id " +
                "JOIN app_user u ON u.id=l.user_id WHERE l.id=LAST_INSERT_ID()");
    }

    @PostMapping("/loans/{id}/return")
    @Transactional
    public Map<String, Object> returnLoan(@PathVariable long id) {
        List<Map<String, Object>> rows = db.queryForList("SELECT notebook_id FROM loan WHERE id=? AND returned_at IS NULL FOR UPDATE", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Empréstimo não encontrado ou já devolvido.");
        long notebookId = ((Number) rows.get(0).get("notebook_id")).longValue();
        db.update("UPDATE loan SET returned_at=CURRENT_TIMESTAMP WHERE id=?", id);
        db.update("UPDATE notebook SET status='DISPONIVEL' WHERE id=?", notebookId);
        return db.queryForMap("SELECT * FROM loan WHERE id=?", id);
    }

    private long count(String sql) { return db.queryForObject(sql, Long.class); }

    private long authenticatedUserId(HttpServletRequest request) {
        Object value = request.getAttribute("authenticatedUserId");
        if (value instanceof Long id) return id;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticação necessária.");
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : "";
    }

    private void requireAdministrator(HttpServletRequest request) {
        List<String> roles = db.queryForList("SELECT role FROM app_user WHERE id = ? AND active = TRUE",
                String.class, authenticatedUserId(request));
        String role = roles.isEmpty() ? null : roles.get(0);
        if (!"Administrador".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas administradores podem gerenciar usuários.");
        }
    }

    private String required(Map<String, String> body, String field) {
        String value = body.get(field);
        if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo obrigatório: " + field);
        return value.trim();
    }

    private String requireStrongPassword(String password) {
        if (password.length() < 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha precisa ter pelo menos 10 caracteres.");
        }
        return password;
    }

    private long requiredLong(Map<String, String> body, String field) {
        try { return Long.parseLong(required(body, field)); }
        catch (NumberFormatException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador inválido: " + field); }
    }
}
