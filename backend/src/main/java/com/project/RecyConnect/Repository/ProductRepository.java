package com.project.RecyConnect.Repository;

import com.project.RecyConnect.Model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByUserId(Long userId);
    List<Product> findByCategoryId(Long categoryId);
}