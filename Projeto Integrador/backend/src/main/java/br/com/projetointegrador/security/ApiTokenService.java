package br.com.projetointegrador.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.OptionalLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ApiTokenService {
    private final JdbcTemplate db;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long ttlMinutes;

    public ApiTokenService(JdbcTemplate db,
            @Value("${app.auth.token-ttl-minutes:90}") long ttlMinutes) {
        this.db = db;
        this.ttlMinutes = Math.max(5, ttlMinutes);
    }

    public IssuedToken issue(long userId) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES);

        db.update("DELETE FROM api_token WHERE user_id = ? AND expires_at <= CURRENT_TIMESTAMP", userId);
        db.update("INSERT INTO api_token(token_hash, user_id, expires_at) VALUES (?, ?, ?)",
                hash(rawToken), userId, java.sql.Timestamp.from(expiresAt));
        return new IssuedToken(rawToken, expiresAt);
    }

    public OptionalLong findUserId(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) return OptionalLong.empty();
        List<Long> userIds = db.queryForList("SELECT t.user_id FROM api_token t JOIN app_user u ON u.id = t.user_id " +
                        "WHERE t.token_hash = ? AND t.expires_at > CURRENT_TIMESTAMP AND u.active = TRUE",
                Long.class, hash(rawToken));
        return userIds.isEmpty() ? OptionalLong.empty() : OptionalLong.of(userIds.get(0));
    }

    public boolean isAdministrator(long userId) {
        Integer count = db.queryForObject("SELECT COUNT(*) FROM app_user WHERE id = ? AND active = TRUE AND role = 'Administrador'",
                Integer.class, userId);
        return count != null && count > 0;
    }

    public void revoke(String rawToken) {
        if (rawToken != null && !rawToken.isBlank() && rawToken.length() <= 200) {
            db.update("DELETE FROM api_token WHERE token_hash = ?", hash(rawToken));
        }
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record IssuedToken(String value, Instant expiresAt) {}
}
