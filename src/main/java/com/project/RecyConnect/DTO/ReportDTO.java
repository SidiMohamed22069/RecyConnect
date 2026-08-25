package com.project.RecyConnect.DTO;

import com.project.RecyConnect.Model.ReportStatus;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Un signalement, a l'aller comme au retour.
 *
 * <p>A la creation, l'application n'envoie que {@code targetType},
 * {@code targetId}, {@code reason} et, facultativement, {@code details} : le
 * reste est etabli par le serveur. L'auteur du signalement n'est jamais lu
 * depuis le corps de la requete, il vient du jeton.
 */
@Data
public class ReportDTO {

    private Long id;
    private Long reporterId;
    private String reporterName;

    private String targetType;
    private Long targetId;

    /** L'auteur du contenu signale, releve par le serveur. */
    private Long targetUserId;
    private String targetUserName;

    private String reason;
    private String details;

    private ReportStatus status;
    private OffsetDateTime createdAt;

    private OffsetDateTime handledAt;
    private String handledByName;
    private String resolution;
}
