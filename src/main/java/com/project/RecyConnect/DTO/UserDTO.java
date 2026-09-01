package com.project.RecyConnect.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.RecyConnect.Model.Role;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String username;
    private Long phone;
    private Role role;
    private String imageData;

    /** Nulle pour les comptes anterieurs a l'ajout de la colonne. */
    private OffsetDateTime createdAt;

    /**
     * Langue des notifications ("fr", "ar", "en"), en lecture seule.
     *
     * <p>Toujours renseignee, meme pour un compte anterieur a la colonne: le
     * service normalise en francais a la lecture, de sorte que le mobile n'ait
     * jamais a interpreter une absence.
     *
     * <p>Ce champ n'est pas ecrit par {@code PUT/PATCH /api/users/{id}}. Le
     * seul chemin d'ecriture est {@code PUT /api/users/me/language}, qui ne
     * peut agir que sur le compte appelant — un utilisateur n'a aucune raison
     * de choisir la langue d'un autre.
     */
    private String preferredLanguage;

    /**
     * Mot de passe en clair, uniquement a l'entree.
     *
     * <p>Sert a la creation d'un compte depuis le panneau d'administration
     * ({@code POST /api/users}), ou l'administrateur choisit lui-meme le mot de
     * passe : contrairement a l'inscription mobile, aucun code SMS ne part vers
     * le futur utilisateur, que l'administrateur n'a pas sous la main.
     *
     * <p>{@code WRITE_ONLY} garantit qu'il ne ressort jamais dans une reponse,
     * y compris quand le meme DTO sert a serialiser un compte existant.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
}
