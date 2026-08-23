package com.project.RecyConnect.Config;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.project.RecyConnect.Model.Category;
import com.project.RecyConnect.Repository.CategoryRepository;

/**
 * Amorce le catalogue des categories de dechets recyclables.
 *
 * <p>Ce catalogue n'est pas une donnee de demonstration mais une donnee de
 * reference: sans lui, le filtre de l'accueil est vide et le formulaire de
 * depot d'annonce n'offre aucun choix. Il est donc actif par defaut, en local
 * comme en production.
 *
 * <p>Le seeder est idempotent et ne detruit rien. Une categorie deja presente
 * est reconnue par son {@link Category#getCode() code}, ou a defaut par son nom
 * canonique: les bases anterieures a l'ajout du code sont ainsi adoptees et
 * completees de leurs traductions au lieu d'etre dupliquees. Les libelles deja
 * saisis a la main ne sont jamais ecrases.
 *
 * <p>L'ordre d'insertion reproduit celui des identifiants 1 a 5 attendus par
 * les versions de l'application mobile anterieures aux traductions servies par
 * l'API.
 */
@Component
@Order(10)
public class CategorySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CategorySeeder.class);

    /** Une categorie du catalogue de reference, dans les trois langues de l'application. */
    public record Seed(String code, String nameEn, String nameFr, String nameAr, String description) {}

    /** Le catalogue de reference. L'ordre fixe les identifiants d'une base neuve. */
    public static final List<Seed> CATALOGUE = List.of(
            new Seed("PLASTIC", "Plastics", "Plastiques", "البلاستيك",
                    "Bouteilles, bidons, films et emballages en plastique"),
            new Seed("PAPER", "Cardboard & Paper", "Carton & Papier", "الكرتون والورق",
                    "Cartons d'emballage, papier de bureau, journaux et revues"),
            new Seed("IRON", "Iron", "Fer", "الحديد",
                    "Ferraille, barres, toles et pieces metalliques"),
            new Seed("WOOD", "Wood", "Bois", "الخشب",
                    "Palettes, chutes de menuiserie et bois de construction"),
            new Seed("ELECTRONICS", "Electronics", "Électronique", "إلكترونيات",
                    "Appareils hors service, cables, batteries et cartes electroniques"));

    private final CategoryRepository categoryRepository;
    private final boolean enabled;

    public CategorySeeder(CategoryRepository categoryRepository,
                          @Value("${app.category-seed.enabled:true}") boolean enabled) {
        this.categoryRepository = categoryRepository;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        int created = 0;
        int completed = 0;

        for (Seed seed : CATALOGUE) {
            Optional<Category> existing = findExisting(seed);

            if (existing.isEmpty()) {
                Category saved = categoryRepository.save(Category.builder()
                        .code(seed.code())
                        .name(seed.nameEn())
                        .nameEn(seed.nameEn())
                        .nameFr(seed.nameFr())
                        .nameAr(seed.nameAr())
                        .description(seed.description())
                        .createdAt(OffsetDateTime.now())
                        .build());
                created++;
                log.debug("Categorie creee: id={}, code={}", saved.getId(), saved.getCode());
                continue;
            }

            if (complete(existing.get(), seed)) {
                completed++;
            }
        }

        if (created > 0 || completed > 0) {
            log.info("Catalogue des categories amorce: {} creee(s), {} completee(s) sur {}.",
                    created, completed, CATALOGUE.size());
        }
    }

    /**
     * La ligne correspondant a [seed], si elle existe deja.
     *
     * <p>Le code d'abord. A defaut, le nom canonique: les categories creees
     * avant l'ajout du code n'en portent aucun, et les retrouver par leur nom
     * evite de creer un doublon a cote de celles auxquelles les annonces
     * existantes sont rattachees.
     */
    private Optional<Category> findExisting(Seed seed) {
        Optional<Category> byCode = categoryRepository.findByCode(seed.code());
        if (byCode.isPresent()) {
            return byCode;
        }
        return categoryRepository.findAll().stream()
                .filter(c -> c.getCode() == null || c.getCode().isBlank())
                .filter(c -> matchesName(c, seed))
                .findFirst();
    }

    private static boolean matchesName(Category category, Seed seed) {
        String name = category.getName();
        if (name == null) {
            return false;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        return normalized.equals(seed.nameEn().toLowerCase(Locale.ROOT))
                || normalized.equals(seed.nameFr().toLowerCase(Locale.ROOT));
    }

    /**
     * Complete les champs vides de [category] a partir de [seed].
     *
     * <p>Ne remplace jamais une valeur deja saisie: un administrateur qui a
     * renomme une categorie doit garder son libelle au redemarrage suivant.
     *
     * @return vrai si la ligne a ete modifiee.
     */
    private boolean complete(Category category, Seed seed) {
        boolean changed = false;

        if (isBlank(category.getCode())) {
            category.setCode(seed.code());
            changed = true;
        }
        if (isBlank(category.getName())) {
            category.setName(seed.nameEn());
            changed = true;
        }
        if (isBlank(category.getNameEn())) {
            category.setNameEn(seed.nameEn());
            changed = true;
        }
        if (isBlank(category.getNameFr())) {
            category.setNameFr(seed.nameFr());
            changed = true;
        }
        if (isBlank(category.getNameAr())) {
            category.setNameAr(seed.nameAr());
            changed = true;
        }
        if (isBlank(category.getDescription())) {
            category.setDescription(seed.description());
            changed = true;
        }

        if (changed) {
            categoryRepository.save(category);
            log.debug("Categorie completee: id={}, code={}", category.getId(), category.getCode());
        }
        return changed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
