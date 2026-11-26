package br.com.projetointegrador.security;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiTokenFilter extends OncePerRequestFilter {
    private static final Set<String> ALLOWED_ORIGINS = Set.of(
            "http://localhost:5500", "http://127.0.0.1:5500",
            "http://localhost:5501", "http://127.0.0.1:5501",
            "http://localhost:5173", "http://localhost:3000", "http://127.0.0.1:3000");
    private final ApiTokenService tokens;

    public ApiTokenFilter(ApiTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || "/api/health".equals(path) || "/api/auth/login".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        addCorsHeaders(request, response);
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            unauthorized(response);
            return;
        }

        String rawToken = header.substring(7).trim();
        var userId = tokens.findUserId(rawToken);
        if (userId.isEmpty()) {
            unauthorized(response);
            return;
        }

        String path = request.getRequestURI();
        boolean authUtilityRoute = "/api/auth/me".equals(path) || "/api/auth/logout".equals(path);
        if (!authUtilityRoute && !tokens.isAdministrator(userId.getAsLong())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"Acesso reservado a administradores.\"}");
            return;
        }

        request.setAttribute("authenticatedUserId", userId.getAsLong());
        chain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"Token ausente, inválido ou expirado. Entre novamente.\"}");
    }

    private void addCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
            response.setHeader("Vary", "Origin");
        }
    }
}
