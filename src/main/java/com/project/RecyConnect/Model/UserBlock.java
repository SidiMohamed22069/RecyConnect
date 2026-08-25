package com.project.RecyConnect.Model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Un utilisateur en a bloque un autre.
 *
 * <p>Le reglement "Contenu genere par les utilisateurs" de Google Play exige
 * qu'un utilisateur puisse en bloquer un autre et cesser de voir ce qu'il
 * publie. L'application mobile tient deja une liste locale pour que le blocage
 * soit immediat ; cette table est ce qui lui permet de survivre a un
 * changement d'appareil, et au serveur de filtrer a la source.
 *
 * <p>La relation est orientee : {@code blocker} ne veut plus voir
 * {@code blocked}. Le filtrage, lui, joue dans les deux sens — un utilisateur
 * bloque ne doit pas davantage pouvoir atteindre celui qui l'a bloque.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "user_blocks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_block_pair",
                columnNames = {"blocker_id", "blocked_id"}))
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_id", nullable = false)
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id", nullable = false)
    private User blocked;

    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
