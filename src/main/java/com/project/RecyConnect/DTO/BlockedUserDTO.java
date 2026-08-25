package com.project.RecyConnect.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Un compte bloque, tel que l'ecran "Utilisateurs bloques" l'affiche.
 *
 * <p>Ni le numero de telephone ni la photo n'y figurent : la liste sert a
 * lever un blocage, pas a garder sous la main les donnees de quelqu'un qu'on
 * ne veut plus voir.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlockedUserDTO {
    private Long id;
    private String username;
    private OffsetDateTime blockedAt;
}
