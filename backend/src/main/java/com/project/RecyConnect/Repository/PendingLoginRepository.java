package com.project.RecyConnect.Repository;

import com.project.RecyConnect.Model.PendingLogin;
import com.project.RecyConnect.Model.PendingLogin.PendingLoginStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PendingLoginRepository extends JpaRepository<PendingLogin, Long> {
    
    Optional<PendingLogin> findByRequestId(String requestId);
    
    List<PendingLogin> findByUserIdAndStatus(Long userId, PendingLoginStatus status);
    
    Optional<PendingLogin> findTopByUserIdAndStatusOrderByCreatedAtDesc(Long userId, PendingLoginStatus status);
    
    // Supprimer les demandes expirées
    @Modifying
    @Query("DELETE FROM PendingLogin p WHERE p.expiresAt < :now")
    void deleteExpired(OffsetDateTime now);
    
    // Supprimer toutes les demandes d'un utilisateur
    void deleteByUserId(Long userId);
    
    // Compter les demandes en attente pour un utilisateur
    long countByUserIdAndStatus(Long userId, PendingLoginStatus status);
}
