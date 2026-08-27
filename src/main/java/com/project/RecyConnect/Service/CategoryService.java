package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.CategoryDTO;
import com.project.RecyConnect.Model.Category;
import com.project.RecyConnect.Repository.CategoryRepository;
import com.project.RecyConnect.Repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CategoryService {
    private final CategoryRepository repo;
    private final ProductRepository productRepo;

    public CategoryService(CategoryRepository repo, ProductRepository productRepo) {
        this.repo = repo;
        this.productRepo = productRepo;
    }

    private CategoryDTO toDTO(Category c) {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(c.getId());
        dto.setCreatedAt(c.getCreatedAt());
        dto.setCode(c.getCode());
        dto.setName(c.getName());
        dto.setNameFr(c.getNameFr());
        dto.setNameAr(c.getNameAr());
        dto.setNameEn(c.getNameEn());
        dto.setDescription(c.getDescription());
        return dto;
    }

    private Category fromDTO(CategoryDTO dto) {
        return Category.builder()
                .id(dto.getId())
                .createdAt(dto.getCreatedAt())
                .code(dto.getCode())
                .name(dto.getName())
                .nameFr(dto.getNameFr())
                .nameAr(dto.getNameAr())
                .nameEn(dto.getNameEn())
                .description(dto.getDescription())
                .build();
    }

    public List<CategoryDTO> findAll() {
        return repo.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public Optional<CategoryDTO> findById(Long id) {
        return repo.findById(id).map(this::toDTO);
    }

    public CategoryDTO save(CategoryDTO dto) {
        return toDTO(repo.save(fromDTO(dto)));
    }

    public CategoryDTO update(Long id, CategoryDTO dto) {
        return repo.findById(id).map(existing -> {
            existing.setName(dto.getName());
            existing.setNameFr(dto.getNameFr());
            existing.setNameAr(dto.getNameAr());
            existing.setNameEn(dto.getNameEn());
            existing.setDescription(dto.getDescription());
            // Le code n'est jamais reecrit depuis l'API: c'est la cle a laquelle
            // les clients rattachent leurs libelles de secours. Le changer
            // reviendrait a renommer la categorie pour toutes les versions de
            // l'application deja installees.
            return toDTO(repo.save(existing));
        }).orElseThrow(() -> new RuntimeException("Category not found"));
    }

    public CategoryDTO patch(Long id, CategoryDTO dto) {
        return repo.findById(id).map(existing -> {
            if (dto.getName() != null) existing.setName(dto.getName());
            if (dto.getNameFr() != null) existing.setNameFr(dto.getNameFr());
            if (dto.getNameAr() != null) existing.setNameAr(dto.getNameAr());
            if (dto.getNameEn() != null) existing.setNameEn(dto.getNameEn());
            if (dto.getDescription() != null) existing.setDescription(dto.getDescription());
            return toDTO(repo.save(existing));
        }).orElseThrow(() -> new RuntimeException("Category not found"));
    }

    /**
     * Supprime une categorie vide.
     *
     * <p>Une categorie encore utilisee est refusee: {@code deleteById} echouait
     * sur la contrainte d'integrite et rendait une 500 illisible, la ou
     * l'appelant a besoin de savoir que des annonces bloquent la suppression.
     *
     * @throws IllegalStateException si des annonces s'y rattachent encore.
     */
    public void delete(Long id) {
        long inUse = productRepo.countByCategoryId(id);
        if (inUse > 0) {
            throw new IllegalStateException(inUse + " product(s) still use this category");
        }
        repo.deleteById(id);
    }
}
