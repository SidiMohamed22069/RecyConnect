package com.project.RecyConnect.Model;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private OffsetDateTime createdAt;

    /**
     * Identifiant stable de la categorie, independant de l'auto-increment.
     *
     * <p>L'application mobile associait ses libelles traduits a l'identifiant
     * numerique: une base reseedee dans un autre ordre, ou une categorie
     * supprimee puis recreee, affichait "Bois" sur du plastique. Le code, lui,
     * ne bouge pas.
     *
     * <p>Nul pour une categorie creee depuis l'admin, qui porte alors ses
     * propres traductions.
     */
    @Column(unique = true)
    private String code;

    /** Nom canonique, en anglais. Sert de recours quand la traduction manque. */
    private String name;

    private String nameFr;
    private String nameAr;
    private String nameEn;

    private String description;

    @OneToMany(mappedBy = "category")
    private List<Product> products;

    /**
     * Le nom de la categorie dans la langue [lang] ("fr", "ar", "en").
     *
     * <p>A defaut de traduction dans cette langue, l'anglais puis le nom
     * canonique: une categorie ajoutee sans ses trois libelles reste lisible
     * plutot que de laisser une ligne vide a l'ecran.
     */
    public String getLocalizedName(String lang) {
        String translated = switch (lang == null ? "" : lang.toLowerCase()) {
            case "fr" -> nameFr;
            case "ar" -> nameAr;
            case "en" -> nameEn;
            default -> null;
        };
        if (translated != null && !translated.isBlank()) {
            return translated;
        }
        if (nameEn != null && !nameEn.isBlank()) {
            return nameEn;
        }
        return name;
    }
}
