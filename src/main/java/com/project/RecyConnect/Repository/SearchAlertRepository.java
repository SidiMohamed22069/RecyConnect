package com.project.RecyConnect.Repository;

import com.project.RecyConnect.Model.SearchAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SearchAlertRepository extends JpaRepository<SearchAlert, Long> {

    List<SearchAlert> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<SearchAlert> findByActiveTrue();
}
