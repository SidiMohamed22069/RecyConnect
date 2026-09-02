package com.project.RecyConnect.Controller;

import com.project.RecyConnect.DTO.ProductDTO;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Service.FavoriteService;
import com.project.RecyConnect.Service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Les annonces enregistrees par l'utilisateur connecte.
 *
 * <p>Toujours celles de l'appelant, jamais celles d'un tiers: la liste des
 * lots qu'on surveille dit ce que l'on cherche a acheter et a quel prix, ce
 * qui n'a pas a etre lisible par un concurrent. Aucun chemin ne prend donc
 * d'identifiant d'utilisateur.
 */
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService service;
    private final UserService userService;

    public FavoriteController(FavoriteService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<ProductDTO>> myFavorites() {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(service.findByUser(currentUser.getId()));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<?> isFavorite(@PathVariable Long productId) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Map.of(
                "productId", productId,
                "favorite", service.isFavorite(currentUser.getId(), productId)
        ));
    }

    @PostMapping("/{productId}")
    public ResponseEntity<?> add(@PathVariable Long productId) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!service.add(currentUser.getId(), productId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("productId", productId, "favorite", true));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(@PathVariable Long productId) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        service.remove(currentUser.getId(), productId);
        return ResponseEntity.noContent().build();
    }
}
