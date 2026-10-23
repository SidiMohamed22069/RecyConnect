package com.project.RecyConnect.Config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CORS : seules les origines de {@code app.client.url} (separees par des
 * virgules) obtiennent les en-tetes. L'ancienne version reflechissait
 * l'origine recue, quelle qu'elle soit, et la liste blanche annoncee par la
 * configuration n'etait jamais lue.
 *
 * <p>L'application mobile n'envoie pas d'en-tete {@code Origin} et n'est donc
 * pas concernee ; seul le panneau d'administration (navigateur) l'est.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SimpleCorsFilter implements Filter {

    private final Set<String> allowedOrigins;

    public SimpleCorsFilter(@Value("${app.client.url:}") String clientAppUrls) {
        this.allowedOrigins = parseOrigins(clientAppUrls);
    }

    static Set<String> parseOrigins(String raw) {
        if (raw == null) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !"*".equals(s))
                .map(s -> s.endsWith("/") ? s.substring(0, s.length() - 1) : s)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;
        HttpServletRequest request = (HttpServletRequest) req;
        String origin = request.getHeader("Origin");
        boolean allowed = origin != null && allowedOrigins.contains(origin.toLowerCase());
        if (allowed) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Vary", "Origin");
            response.setHeader("Access-Control-Allow-Methods", "POST, GET, PUT, PATCH, OPTIONS, DELETE");
            response.setHeader("Access-Control-Max-Age", "3600");
            response.setHeader("Access-Control-Allow-Headers",
                    "Authorization, Content-Type, Accept, X-Device-Id, X-Requested-With");
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(allowed ? HttpServletResponse.SC_OK : HttpServletResponse.SC_FORBIDDEN);
        } else {
            chain.doFilter(req, res);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // rien a initialiser
    }

    @Override
    public void destroy() {
        // rien a liberer
    }
}
