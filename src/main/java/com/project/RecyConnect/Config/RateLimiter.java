package com.project.RecyConnect.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compteur a fenetre glissante, en memoire, par cle arbitraire.
 *
 * <p>Suffisant pour une instance unique : il s'agit de rendre la force brute
 * sur un code SMS a six chiffres (10 minutes de validite) et l'envoi de SMS en
 * masse impraticables, pas de facturer finement. Les cles sont de la forme
 * {@code "login:ip:1.2.3.4"} ou {@code "verify:phone:22123456"}.
 *
 * <p>Desactivable par {@code app.rate-limit.enabled=false} (tests).
 */
@Component
public class RateLimiter {

    private static final int MAX_KEYS = 50_000;

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final boolean enabled;

    public RateLimiter(@Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Enregistre une tentative et dit si elle est encore dans la limite.
     *
     * @return {@code false} quand {@code limit} tentatives ont deja eu lieu
     *         dans la fenetre.
     */
    public boolean tryAcquire(String key, int limit, Duration window) {
        if (!enabled) {
            return true;
        }
        long now = System.currentTimeMillis();
        long threshold = now - window.toMillis();
        if (hits.size() > MAX_KEYS) {
            // Protection memoire : on prefere oublier que grossir sans fin.
            hits.clear();
        }
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < threshold) {
                q.pollFirst();
            }
            if (q.size() >= limit) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }

    /** Tentative comptee ; leve 429 si la limite est atteinte. */
    public void checkOrThrow(String key, int limit, Duration window) {
        if (!tryAcquire(key, limit, window)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "Trop de tentatives. Veuillez reessayer plus tard.");
        }
    }

    /** Pour les tests : repart de zero. */
    public void reset() {
        hits.clear();
    }
}
