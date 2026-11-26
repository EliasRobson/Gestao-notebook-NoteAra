package br.com.projetointegrador.controller;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public HealthController(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> check() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(Map.of(
                    "api", "online",
                    "banco", "conectado"
            ));
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "api", "online",
                    "banco", "indisponivel",
                    "mensagem", "Verifique se o MySQL está ativo e se as credenciais estão corretas."
            ));
        }
    }
}
