package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.UserStatsDTO;
import com.project.RecyConnect.Model.UserStats;
import com.project.RecyConnect.Repository.UserRepo;
import com.project.RecyConnect.Repository.UserStatsRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserStatsService {
    private final UserStatsRepository repo;
    private final UserRepo userRepo;

    public UserStatsService(UserStatsRepository repo, UserRepo userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    private UserStatsDTO toDTO(UserStats s) {
        UserStatsDTO dto = new UserStatsDTO();
        dto.setId(s.getId());
        dto.setCreatedAt(s.getCreatedAt());
        dto.setUserId(s.getUser() != null ? s.getUser().getId() : null);
        dto.setTotalProducts(s.getTotalProducts());
        dto.setRecycledCount(s.getRecycledCount());
        dto.setAvailableCount(s.getAvailableCount());
        dto.setRecyclingRate(s.getRecyclingRate());
        return dto;
    }

    private UserStats fromDTO(UserStatsDTO dto) {
        UserStats s = new UserStats();
        s.setId(dto.getId());
        s.setCreatedAt(dto.getCreatedAt());
        s.setTotalProducts(dto.getTotalProducts());
        s.setRecycledCount(dto.getRecycledCount());
        s.setAvailableCount(dto.getAvailableCount());
        s.setRecyclingRate(dto.getRecyclingRate());
        if (dto.getUserId() != null)
            userRepo.findById(dto.getUserId()).ifPresent(s::setUser);
        return s;
    }

    public List<UserStatsDTO> findAll() {
        return repo.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public Optional<UserStatsDTO> findById(Long id) {
        return repo.findById(id).map(this::toDTO);
    }

    public Optional<UserStatsDTO> findByUserId(Long userId) {
        return repo.findByUserId(userId).map(this::toDTO);
    }

    public UserStatsDTO save(UserStatsDTO dto) {
        return toDTO(repo.save(fromDTO(dto)));
    }

    public UserStatsDTO update(Long id, UserStatsDTO dto) {
        return repo.findById(id).map(existing -> {
            existing.setTotalProducts(dto.getTotalProducts());
            existing.setRecycledCount(dto.getRecycledCount());
            existing.setAvailableCount(dto.getAvailableCount());
            existing.setRecyclingRate(dto.getRecyclingRate());
            return toDTO(repo.save(existing));
        }).orElseThrow(() -> new RuntimeException("UserStats not found"));
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }
}
