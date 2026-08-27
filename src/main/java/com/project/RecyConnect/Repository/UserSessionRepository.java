package com.project.RecyConnect.Repository;

import com.project.RecyConnect.Model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    /**
     * Retrouve la session a partir de l'empreinte du jeton de rafraichissement
     * presente. C'est le seul chemin d'entree de {@code POST /api/auth/refresh}:
     * l'appelant n'a plus de jeton d'acces valide a ce moment-la.
     */
    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);
}
