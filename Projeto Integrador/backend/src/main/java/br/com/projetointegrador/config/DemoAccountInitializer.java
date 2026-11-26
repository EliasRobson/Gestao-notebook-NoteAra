package br.com.projetointegrador.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DemoAccountInitializer implements CommandLineRunner {
    private final JdbcTemplate db;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Value("${app.demo.enabled:true}") private boolean enabled;
    @Value("${app.demo.email}") private String email;
    @Value("${app.demo.password}") private String password;

    public DemoAccountInitializer(JdbcTemplate db) { this.db = db; }

    @Override
    public void run(String... args) {
        if (!enabled) return;
        Integer exists = db.queryForObject("SELECT COUNT(*) FROM app_user WHERE email = ?", Integer.class, email);
        if (exists == null || exists == 0) {
            db.update("INSERT INTO app_user(name, email, password_hash, role) VALUES (?, ?, ?, ?)",
                    "Henry", email, encoder.encode(password), "Administrador");
        }
    }
}
