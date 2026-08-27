package com.project.RecyConnect.Controller;

import com.project.RecyConnect.DTO.CategoryDTO;
import com.project.RecyConnect.Service.CategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/**
 * Catalogue des categories.
 *
 * <p>La lecture est publique — l'application mobile affiche le catalogue avant
 * toute connexion. Les ecritures sont reservees aux administrateurs: une
 * categorie est une donnee de reference partagee par tous les clients, et la
 * configuration de securite ne demandait jusqu'ici qu'un compte authentifie,
 * ce qui laissait n'importe quel utilisateur renommer ou supprimer le
 * catalogue entier.
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService service;
    public CategoryController(CategoryService service) { this.service = service; }

    @GetMapping
    public List<CategoryDTO> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryDTO> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> create(@RequestBody CategoryDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Name is required"));
        }
        // Le code identifie les categories d'amorcage et sert de cle de repli
        // aux versions du mobile deja installees: il n'est jamais pose depuis
        // l'API, sous peine de faire passer une nouvelle categorie pour une
        // ancienne.
        dto.setCode(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryDTO> update(@PathVariable Long id, @RequestBody CategoryDTO dto) {
        try {
            return ResponseEntity.ok(service.update(id, dto));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryDTO> patch(@PathVariable Long id, @RequestBody CategoryDTO dto) {
        try {
            return ResponseEntity.ok(service.patch(id, dto));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Supprime une categorie inutilisee.
     *
     * <p>Une categorie encore rattachee a des annonces rend {@code 409} avec le
     * nombre d'annonces concernees: les detruire en cascade effacerait des
     * annonces que personne n'a demande a supprimer.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            service.delete(id);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", e.getMessage()));
        }
        return ResponseEntity.noContent().build();
    }
}
