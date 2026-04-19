package com.project.RecyConnect.Model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Entité pour stocker les demandes de connexion en attente de confirmation
 * Quand un nouvel appareil essaie de se connecter, on crée une entrée ici
 * et on attend que l'ancien appareil confirme ou refuse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pending_logins")
public class PendingLogin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Identifiant unique pour cette demande de connexion
    @Column(nullable = false, unique = true)
    private String requestId;

    // Infos sur le nouvel appareil qui essaie de se connecter
    private String newDeviceFcmToken;
    private String newDeviceName;
    private String newDeviceType;

    // Statut: PENDING, APPROVED, REJECTED, EXPIRED
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PendingLoginStatus status;

    private OffsetDateTime createdAt;
    private OffsetDateTime expiresAt;
    private OffsetDateTime respondedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        // Expire après 2 minutes
        expiresAt = OffsetDateTime.now().plusMinutes(2);
        status = PendingLoginStatus.PENDING;
    }

    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(expiresAt);
    }

    public enum PendingLoginStatus {
        PENDING,    // En attente de réponse
        APPROVED,   // Approuvé par l'ancien appareil
        REJECTED,   // Refusé par l'ancien appareil
        EXPIRED     // Expiré (pas de réponse dans le délai)
    }
}
