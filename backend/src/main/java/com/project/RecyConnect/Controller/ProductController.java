package com.project.RecyConnect.Controller;

import com.project.RecyConnect.DTO.ProductDTO;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Service.ProductService;
import com.project.RecyConnect.Service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;
    private final UserService userService;
    
    public ProductController(ProductService service, UserService userService) { 
        this.service = service;
        this.userService = userService;
    }

    @GetMapping
    public List<ProductDTO> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    public List<ProductDTO> getByUser(@PathVariable Long userId) {
        return service.findByUserId(userId);
    }

    @GetMapping("/user/{userId}/status")
    public List<ProductDTO> getByUserWithStatus(
            @PathVariable Long userId,
            @RequestParam(required = false) String status) {
        return service.findByUserIdWithStatus(userId, status);
    }

    @GetMapping("/category/{categoryId}")
    public List<ProductDTO> getByCategory(@PathVariable Long categoryId) {
        return service.findByCategoryId(categoryId);
    }

    @GetMapping("/search")
    public List<ProductDTO> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long excludeUserId) {
        return service.search(query, categoryId, excludeUserId);
    }

    @PostMapping
    public ResponseEntity<ProductDTO> create(@RequestBody ProductDTO dto) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Définir automatiquement l'utilisateur connecté comme propriétaire du produit
        dto.setUserId(currentUser.getId());
        return ResponseEntity.ok(service.save(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDTO> update(@PathVariable Long id, @RequestBody ProductDTO dto) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Vérifier que l'utilisateur est propriétaire du produit
        ProductDTO existing = service.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!existing.getUserId().equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            return ResponseEntity.ok(service.update(id, dto));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ProductDTO> patch(@PathVariable Long id, @RequestBody ProductDTO dto) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Vérifier que l'utilisateur est propriétaire du produit
        ProductDTO existing = service.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!existing.getUserId().equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            return ResponseEntity.ok(service.patch(id, dto));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/accept-offer")
    public ResponseEntity<ProductDTO> acceptOffer(
            @PathVariable Long id,
            @RequestBody Map<String, Long> request) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Vérifier que l'utilisateur est propriétaire du produit
        ProductDTO existing = service.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!existing.getUserId().equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            Long quantityOffer = request.get("quantityOffer");
            return ResponseEntity.ok(service.updateQuantity(id, quantityOffer));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Vérifier que l'utilisateur est propriétaire du produit
        ProductDTO existing = service.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!existing.getUserId().equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
