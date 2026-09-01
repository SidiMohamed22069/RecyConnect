package com.project.RecyConnect.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * La politique de version de l'application mobile, telle que la lit le client
 * au demarrage ({@code GET /api/app/version}).
 *
 * <p>Chaque champ peut valoir {@code null}: une valeur mal configuree est
 * volontairement omise plutot que transmise telle quelle. Cote mobile, une
 * politique incomplete ne bloque personne — c'est la seule facon d'eviter
 * qu'une faute de frappe dans une variable d'environnement ne rende
 * l'application inutilisable pour tout le parc installe.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppVersionDTO {

    /** Derniere version publiee sur les magasins, ex. {@code "1.3.0"}. */
    private String latestVersion;

    /** Version minimale encore supportee, ex. {@code "1.2.0"}. */
    private String minimumVersion;

    /** Fiche Play Store, en {@code https}. */
    private String androidUrl;

    /** Fiche App Store, en {@code https}. */
    private String iosUrl;
}
