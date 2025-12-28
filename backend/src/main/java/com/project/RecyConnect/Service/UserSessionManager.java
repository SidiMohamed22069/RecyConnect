package com.project.RecyConnect.Service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * Service pour gérer les sessions WebSocket des utilisateurs connectés
 */
@Service
public class UserSessionManager {
    
    // Map: userId -> Set de sessionIds (un user peut avoir plusieurs sessions)
    private final ConcurrentHashMap<Long, Set<String>> userSessions = new ConcurrentHashMap<>();
    
    /**
     * Enregistre une session WebSocket pour un utilisateur
     */
    public void addSession(Long userId, String sessionId) {
        userSessions.compute(userId, (key, sessions) -> {
            if (sessions == null) {
                sessions = ConcurrentHashMap.newKeySet();
            }
            sessions.add(sessionId);
            return sessions;
        });
    }
    
    /**
     * Supprime une session WebSocket pour un utilisateur
     */
    public void removeSession(Long userId, String sessionId) {
        userSessions.computeIfPresent(userId, (key, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions;
        });
    }
    
    /**
     * Vérifie si un utilisateur est connecté via WebSocket
     */
    public boolean isUserConnected(Long userId) {
        Set<String> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }
    
    /**
     * Obtient toutes les sessions d'un utilisateur
     */
    public Set<String> getUserSessions(Long userId) {
        return userSessions.getOrDefault(userId, ConcurrentHashMap.newKeySet());
    }
    
    /**
     * Supprime toutes les sessions d'un utilisateur (déconnexion)
     */
    public void removeAllSessions(Long userId) {
        userSessions.remove(userId);
    }
}

