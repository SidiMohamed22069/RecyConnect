package com.project.RecyConnect.DTO;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class CategoryDTO {
    private Long id;
    private OffsetDateTime createdAt;

    /** Identifiant stable (PLASTIC, PAPER, IRON...), nul hors categories d'amorcage. */
    private String code;

    /** Nom canonique, en anglais. */
    private String name;

    // Libelles traduits, servis tels quels: c'est au client de choisir selon sa
    // langue. Le serveur ne connait pas la locale de l'appelant, et une meme
    // reponse peut etre mise en cache pour des utilisateurs de langues
    // differentes.
    private String nameFr;
    private String nameAr;
    private String nameEn;

    private String description;
}
