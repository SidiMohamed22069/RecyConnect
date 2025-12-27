package com.project.RecyConnect.Controller;

import com.project.RecyConnect.DTO.ProductDTO;
import com.project.RecyConnect.Service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;
    public ProductController(ProductService service) { this.service = service; }

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
    public ProductDTO create(@RequestBody ProductDTO dto) { return service.save(dto); }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDTO> update(@PathVariable Long id, @RequestBody ProductDTO dto) {
        try {
            return ResponseEntity.ok(service.update(id, dto));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ProductDTO> patch(@PathVariable Long id, @RequestBody ProductDTO dto) {
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
        try {
            Long quantityOffer = request.get("quantityOffer");
            return ResponseEntity.ok(service.updateQuantity(id, quantityOffer));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
