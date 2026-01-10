package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.ProductDTO;
import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Repository.CategoryRepository;
import com.project.RecyConnect.Repository.ProductRepository;
import com.project.RecyConnect.Repository.UserRepo;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository repo;
    private final CategoryRepository categoryRepo;
    private final UserRepo userRepo;

    public ProductService(ProductRepository repo, CategoryRepository categoryRepo, UserRepo userRepo) {
        this.repo = repo;
        this.categoryRepo = categoryRepo;
        this.userRepo = userRepo;
    }

    private ProductDTO toDTO(Product p) {
        ProductDTO dto = new ProductDTO();
        dto.setId(p.getId());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setTitle(p.getTitle());
        dto.setDesc(p.getDescription());
        dto.setPrice(p.getPrice());
        dto.setUnit(p.getUnit());
        dto.setQuantityTotal(p.getQuantityTotal());
        dto.setQuantityAvailable(p.getQuantityAvailable());
        dto.setStatus(p.getStatus());
        dto.setImageUrls(p.getImageUrls());
        dto.setCategoryId(p.getCategory() != null ? p.getCategory().getId() : null);
        dto.setUserId(p.getUser() != null ? p.getUser().getId() : null);
        // Add nested info
        dto.setCategoryName(p.getCategory() != null ? p.getCategory().getName() : null);
        dto.setUserName(p.getUser() != null ? p.getUser().getUsername() : null);
        dto.setUserPhone(p.getUser() != null ? p.getUser().getPhone() : null);
        return dto;
    }

    private Product fromDTO(ProductDTO dto) {
        Product p = Product.builder()
                .id(dto.getId())
                .createdAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : OffsetDateTime.now())
                .title(dto.getTitle())
                .description(dto.getDesc())
                .price(dto.getPrice())
                .unit(dto.getUnit())
                .quantityTotal(dto.getQuantityTotal())
                .quantityAvailable(dto.getQuantityAvailable())
                .status(dto.getStatus())
                .imageUrls(dto.getImageUrls())
                .build();
        if (dto.getCategoryId() != null)
            categoryRepo.findById(dto.getCategoryId()).ifPresent(p::setCategory);
        if (dto.getUserId() != null)
            userRepo.findById(dto.getUserId()).ifPresent(p::setUser);
        return p;
    }

    public List<ProductDTO> findAll() {
        return repo.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public Optional<ProductDTO> findById(Long id) {
        return repo.findById(id).map(this::toDTO);
    }

    public List<ProductDTO> findByUserId(Long userId) {
        return repo.findByUserId(userId).stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<ProductDTO> findByCategoryId(Long categoryId) {
        return repo.findByCategoryId(categoryId).stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<ProductDTO> search(String query, Long categoryId, Long excludeUserId) {
        return repo.findAll().stream()
                .filter(p -> "available".equalsIgnoreCase(p.getStatus()))
                .filter(p -> excludeUserId == null || p.getUser() == null || !p.getUser().getId().equals(excludeUserId))
                .filter(p -> categoryId == null || (p.getCategory() != null && p.getCategory().getId().equals(categoryId)))
                .filter(p -> query == null || query.isEmpty() || 
                        (p.getTitle() != null && p.getTitle().toLowerCase().contains(query.toLowerCase())))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<ProductDTO> findByUserIdWithStatus(Long userId, String status) {
        return repo.findByUserId(userId).stream()
                .filter(p -> status == null || status.isEmpty() || status.equalsIgnoreCase(p.getStatus()))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public ProductDTO save(ProductDTO dto) {
        return toDTO(repo.save(fromDTO(dto)));
    }

    public ProductDTO update(Long id, ProductDTO dto) {
        return repo.findById(id).map(existing -> {
            existing.setTitle(dto.getTitle());
            existing.setDescription(dto.getDesc());
            existing.setPrice(dto.getPrice());
            existing.setUnit(dto.getUnit());
            existing.setQuantityTotal(dto.getQuantityTotal());
            existing.setQuantityAvailable(dto.getQuantityAvailable());
            existing.setStatus(dto.getStatus());
            existing.setImageUrls(dto.getImageUrls());
            return toDTO(repo.save(existing));
        }).orElseThrow(() -> new RuntimeException("Product not found"));
    }

    public ProductDTO patch(Long id, ProductDTO dto) {
        return repo.findById(id).map(existing -> {
            if (dto.getTitle() != null) existing.setTitle(dto.getTitle());
            if (dto.getDesc() != null) existing.setDescription(dto.getDesc());
            if (dto.getPrice() != null) existing.setPrice(dto.getPrice());
            if (dto.getUnit() != null) existing.setUnit(dto.getUnit());
            if (dto.getQuantityTotal() != null) existing.setQuantityTotal(dto.getQuantityTotal());
            if (dto.getQuantityAvailable() != null) existing.setQuantityAvailable(dto.getQuantityAvailable());
            if (dto.getStatus() != null) existing.setStatus(dto.getStatus());
            if (dto.getImageUrls() != null) existing.setImageUrls(dto.getImageUrls());
            if (dto.getCategoryId() != null)
                categoryRepo.findById(dto.getCategoryId()).ifPresent(existing::setCategory);
            if (dto.getUserId() != null)
                userRepo.findById(dto.getUserId()).ifPresent(existing::setUser);
            return toDTO(repo.save(existing));
        }).orElseThrow(() -> new RuntimeException("Product not found"));
    }

    public ProductDTO updateQuantity(Long productId, Long quantityOffer) {
        return repo.findById(productId).map(existing -> {
            Long newQuantity = existing.getQuantityAvailable() - quantityOffer;
            existing.setQuantityAvailable(newQuantity);
            if (newQuantity <= 0) {
                existing.setStatus("recycled");
            }
            return toDTO(repo.save(existing));
        }).orElseThrow(() -> new RuntimeException("Product not found"));
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }
}
