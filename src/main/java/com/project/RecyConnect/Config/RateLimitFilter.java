package com.project.RecyConnect.Config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * Limite par adresse IP les points d'entree d'authentification.
 *
 * <p>La limite par numero de telephone, plus fine, est appliquee dans
 * {@code AuthController} une fois le corps lu. Ici on ne lit pas le corps :
 * un filtre qui consommerait le flux JSON priverait le controleur de la
 * requete.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Duration ONE_HOUR = Duration.ofHours(1);

    /** Chemin -> (limite, fenetre). */
    private static final Map<String, Rule> RULES = Map.of(
            "/api/auth/login", new Rule(20, TEN_MINUTES),
            "/api/auth/verify-code", new Rule(20, TEN_MINUTES),
            "/api/auth/reset-password", new Rule(10, TEN_MINUTES),
            "/api/auth/register", new Rule(10, ONE_HOUR),
            "/api/auth/send-code", new Rule(10, ONE_HOUR)
    );

    private final RateLimiter limiter;

    public RateLimitFilter(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        Rule rule = RULES.get(request.getRequestURI());
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }
        String key = request.getRequestURI() + ":ip:" + clientIp(request);
        if (!limiter.tryAcquire(key, rule.limit(), rule.window())) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\":\"Trop de tentatives. Veuillez reessayer plus tard.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Derriere le reverse proxy, {@code server.forward-headers-strategy=native}
     * fait deja porter a {@code getRemoteAddr()} l'adresse du client. On lit
     * {@code X-Forwarded-For} en secours pour un proxy non configure.
     */
    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record Rule(int limit, Duration window) {}
}
