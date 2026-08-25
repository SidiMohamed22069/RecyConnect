package com.project.RecyConnect.Model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Le signalement d'une annonce ou d'un compte.
 *
 * <p>Google Play attend d'une application qui publie du contenu d'utilisateurs
 * un dispositif de signalement <em>dans</em> l'application, et une moderation
 * effective derriere. Cette table est la trace de l'un et le point de depart de
 * l'autre : chaque ligne garde qui a signale, quoi, pourquoi, et ce qui en a
 * ete fait.
 *
 * <p>{@code reporter} est volontairement annulable : quand un compte est
 * supprime, le signalement reste — c'est ce que la politique de
 * confidentialite annonce ("la trace d'un signalement traite est conservee") —
 * mais il ne designe plus personne.
 *
 * <p>{@code targetId} n'est pas une cle etrangere : le contenu signale peut
 * disparaitre (retire, ou supprime par son auteur) sans que le signalement,
 * lui, doive disparaitre avec.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reports")
public class Report {

    /** Valeurs admises pour {@link #targetType}. */
    public static final String TARGET_PRODUCT = "PRODUCT";
    public static final String TARGET_USER = "USER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Auteur du signalement. Null si son compte a ete supprime depuis. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @Column(nullable = false)
    private String targetType;

    @Column(nullable = false)
    private Long targetId;

    /**
     * L'auteur du contenu signale, releve au moment du signalement.
     *
     * <p>Sans lui, un moderateur qui traite un signalement apres la
     * disparition de l'annonce n'aurait plus aucun moyen de savoir qui
     * sanctionner.
     */
    private Long targetUserId;

    @Column(nullable = false)
    private String reason;

    @Column(columnDefinition = "text")
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    private OffsetDateTime createdAt;

    // --- Traitement ------------------------------------------------------

    private OffsetDateTime handledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private User handledBy;

    @Column(columnDefinition = "text")
    private String resolution;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (status == null) {
            status = ReportStatus.PENDING;
        }
    }
}
