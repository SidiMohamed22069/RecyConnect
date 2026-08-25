package com.project.RecyConnect.Model;

/** Etat d'un signalement dans la file de moderation. */
public enum ReportStatus {

    /** Recu, pas encore examine. */
    PENDING,

    /** Pris en charge par un moderateur. */
    REVIEWING,

    /** Fonde : le contenu a ete retire, ou le compte sanctionne. */
    ACTIONED,

    /** Examine et non fonde. */
    REJECTED
}
