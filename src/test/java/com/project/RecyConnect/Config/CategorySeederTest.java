package com.project.RecyConnect.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.project.RecyConnect.Model.Category;
import com.project.RecyConnect.Repository.CategoryRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verrouille l'amorcage du catalogue: creation unique, traductions completees
 * sur les bases anterieures, et aucun ecrasement d'un libelle saisi a la main.
 */
@ExtendWith(MockitoExtension.class)
class CategorySeederTest {

    @Mock
    private CategoryRepository categoryRepository;

    private CategorySeeder seeder(boolean enabled) {
        return new CategorySeeder(categoryRepository, enabled);
    }

    private List<Category> savedCategories() {
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("Cree les cinq categories, traduites, sur une base vide")
    void createsCatalogueWhenEmpty() {
        when(categoryRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(categoryRepository.findAll()).thenReturn(new ArrayList<>());
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        seeder(true).run(null);

        List<Category> saved = savedCategories();
        assertEquals(5, saved.size());
        for (Category category : saved) {
            assertNotNull(category.getCode(), "Chaque categorie amorcee porte un code stable");
            assertNotNull(category.getNameFr());
            assertNotNull(category.getNameAr());
            assertNotNull(category.getNameEn());
            assertNotNull(category.getCreatedAt());
        }
    }

    /**
     * L'ordre compte: sur une base neuve il fixe les identifiants 1 a 5, ceux
     * auxquels les versions deja installees du mobile rattachent leurs libelles.
     */
    @Test
    @DisplayName("Insere le catalogue dans l'ordre attendu par le mobile")
    void createsCatalogueInLegacyIdOrder() {
        when(categoryRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(categoryRepository.findAll()).thenReturn(new ArrayList<>());
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        seeder(true).run(null);

        assertEquals(
                List.of("PLASTIC", "PAPER", "IRON", "WOOD", "ELECTRONICS"),
                savedCategories().stream().map(Category::getCode).toList());
    }

    @Test
    @DisplayName("N'ecrit rien quand le catalogue est deja complet")
    void writesNothingWhenAlreadySeeded() {
        for (CategorySeeder.Seed seed : CategorySeeder.CATALOGUE) {
            when(categoryRepository.findByCode(seed.code())).thenReturn(Optional.of(Category.builder()
                    .id(1L)
                    .code(seed.code())
                    .name(seed.nameEn())
                    .nameEn(seed.nameEn())
                    .nameFr(seed.nameFr())
                    .nameAr(seed.nameAr())
                    .description(seed.description())
                    .build()));
        }

        seeder(true).run(null);

        verify(categoryRepository, never()).save(any(Category.class));
    }

    /**
     * Les bases anterieures au code portent les memes categories, reconnues par
     * leur nom canonique. Les dupliquer detacherait les annonces existantes de
     * la categorie desormais affichee.
     */
    @Test
    @DisplayName("Adopte une categorie sans code et la complete au lieu de la dupliquer")
    void adoptsLegacyCategoryByName() {
        Category legacy = Category.builder().id(3L).name("Iron").build();

        when(categoryRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(categoryRepository.findAll()).thenReturn(List.of(legacy));
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        seeder(true).run(null);

        assertEquals("IRON", legacy.getCode());
        assertEquals("Fer", legacy.getNameFr());
        assertEquals(3L, legacy.getId(), "La ligne existante est completee, pas remplacee");

        List<Category> saved = savedCategories();
        assertEquals(1, saved.stream().filter(c -> "IRON".equals(c.getCode())).count(),
                "Aucun doublon de la categorie adoptee");
    }

    @Test
    @DisplayName("Ne remplace jamais un libelle deja saisi")
    void keepsManuallyEditedLabels() {
        Category renamed = Category.builder()
                .id(1L)
                .code("PLASTIC")
                .name("Plastics")
                .nameFr("Plastique dur")
                .build();

        when(categoryRepository.findByCode("PLASTIC")).thenReturn(Optional.of(renamed));
        when(categoryRepository.findByCode(org.mockito.ArgumentMatchers.argThat(c -> !"PLASTIC".equals(c))))
                .thenReturn(Optional.empty());
        when(categoryRepository.findAll()).thenReturn(new ArrayList<>());
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        seeder(true).run(null);

        assertEquals("Plastique dur", renamed.getNameFr());
        assertEquals("البلاستيك", renamed.getNameAr(), "Les traductions absentes sont bien completees");
    }

    @Test
    @DisplayName("Ne touche a rien quand l'amorcage est desactive")
    void doesNothingWhenDisabled() {
        seeder(false).run(null);

        verify(categoryRepository, never()).save(any(Category.class));
        verify(categoryRepository, never()).findByCode(anyString());
    }

    @Test
    @DisplayName("Le catalogue couvre les trois langues de l'application")
    void catalogueIsFullyTranslated() {
        assertEquals(5, CategorySeeder.CATALOGUE.size());
        for (CategorySeeder.Seed seed : CategorySeeder.CATALOGUE) {
            assertTrue(seed.code().matches("[A-Z]+"), "Le code reste stable et lisible: " + seed.code());
            assertTrue(!seed.nameEn().isBlank() && !seed.nameFr().isBlank() && !seed.nameAr().isBlank(),
                    "Traduction manquante pour " + seed.code());
        }
    }
}
