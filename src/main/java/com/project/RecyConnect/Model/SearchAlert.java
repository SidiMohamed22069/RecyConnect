package com.project.RecyConnect.Model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Une veille posee par un utilisateur: "prevenez-moi quand du cuivre est
 * publie a moins de 300 MRU/kg".
 *
 * <p>Le canal de notification etait deja en place et eprouve; il ne manquait
 * que la regle cote serveur. Pour un recycleur professionnel, c'est le
 * meilleur motif de revenir dans l'application.
 *
 * <p>Tous les criteres sont facultatifs et se cumulent: une alerte sans aucun
 * critere previent de toute nouvelle annonce.
 */
@Entity
@Table(name = "search_alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private OffsetDateTime createdAt;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** Mot recherche dans le titre de l'annonce. */
    private String keyword;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    /** Prix unitaire maximal accepte. */
    private Double maxPrice;

    /** Quantite disponible minimale. */
    private Long minQuantity;

    @Enumerated(EnumType.STRING)
    private Moughataa location;

    /**
     * Une alerte desactivee est conservee mais ne declenche plus rien.
     *
     * <p>Le retour d'un interrupteur doit etre immediat et reversible; la
     * supprimer obligerait a la ressaisir.
     */
    private Boolean active;

    @PrePersist
    void applyDefaults() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (active == null) {
            active = true;
        }
    }
}
