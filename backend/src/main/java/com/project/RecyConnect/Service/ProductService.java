package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.ProductDTO;
import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Repository.CategoryRepository;
import com.project.RecyConnect.Repository.ProductRepository;
import com.project.RecyConnect.Repository.UserRepo;
import org.springframework.stereotype.Service;

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
        dto.setImageUrl(p.getImageUrl());
        dto.setCategoryId(p.getCategory() != null ? p.getCategory().getId() : null);
        dto.setUserId(p.getUser() != null ? p.getUser().getId() : null);
        return dto;
    }

    private Product fromDTO(ProductDTO dto) {
        Product p = Product.builder()
                .id(dto.getId())
                .createdAt(dto.getCreatedAt())
                .title(dto.getTitle())
                .description(dto.getDesc())
                .price(dto.getPrice())
                .unit(dto.getUnit())
                .quantityTotal(dto.getQuantityTotal())
                .quantityAvailable(dto.getQuantityAvailable())
                .status(dto.getStatus())
                .imageUrl(dto.getImageUrl())
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
            existing.setImageUrl(dto.getImageUrl());
            return toDTO(repo.save(existing));
        }).orElseThrow(() -> new RuntimeException("Product not found"));
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }
}
