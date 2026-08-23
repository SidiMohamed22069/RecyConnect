package com.project.RecyConnect.Repository;


import com.project.RecyConnect.Model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** Recherche par identifiant stable, utilisee par l'amorcage du catalogue. */
    Optional<Category> findByCode(String code);
}
