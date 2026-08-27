package com.project.RecyConnect.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String deviceId;

    private String deviceName;

    @Column(nullable = false)
    private String fcmToken;

    @Column(nullable = false)
    private Long sessionVersion;

    /**
     * Empreinte SHA-256 du jeton de rafraichissement en cours (cf. H4 de
     * l'audit mobile).
     *
     * <p>Le jeton lui-meme n'est jamais stocke: il vaut un mot de passe, il
     * ouvre une session a lui seul. Une fuite de la base ne doit pas suffire a
     * rejouer les sessions ouvertes.
     *
     * <p>Nul tant qu'aucun jeton n'a ete emis, et remis a nul des que la
     * session est remplacee ou le jeton perime. La contrainte d'unicite est
     * ce qui permet de retrouver la session a partir du jeton presente ;
     * PostgreSQL ne fait pas entrer les valeurs nulles en conflit.
     */
    @Column(length = 64, unique = true)
    private String refreshTokenHash;

    /** Fin de validite du jeton ci-dessus. */
    private OffsetDateTime refreshTokenExpiresAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
