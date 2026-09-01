package com.project.RecyConnect.DTO;

import lombok.Data;

/** Les trois interrupteurs de notification d'un compte. */
@Data
public class NotificationPreferencesDTO {
    /** Offres recues, acceptees, refusees, annulees. */
    private Boolean offers;

    /** Messages du service et alertes de recherche. */
    private Boolean system;

    /** Annonces de l'equipe, nouveautes. */
    private Boolean promotions;
}
