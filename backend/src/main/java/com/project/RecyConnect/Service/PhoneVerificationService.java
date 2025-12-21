package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.PhoneVerificationDTO;
import com.project.RecyConnect.Model.PhoneVerification;
import com.project.RecyConnect.Repository.PhoneVerificationRepository;
import com.project.RecyConnect.Repository.UserRepo;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PhoneVerificationService {
    private final PhoneVerificationRepository repo;
    private final UserRepo userRepo;

    public PhoneVerificationService(PhoneVerificationRepository repo, UserRepo userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    private PhoneVerificationDTO toDTO(PhoneVerification pv) {
        PhoneVerificationDTO dto = new PhoneVerificationDTO();
        dto.setId(pv.getId());
        dto.setCreatedAt(pv.getCreatedAt());
        dto.setUserId(pv.getUser() != null ? pv.getUser().getId() : null);
        dto.setPhone(pv.getPhone());
        dto.setCode(pv.getCode());
        // Check if expired (10 minutes)
        if (pv.getCreatedAt() != null) {
            long minutes = ChronoUnit.MINUTES.between(pv.getCreatedAt(), OffsetDateTime.now());
            dto.setExpired(minutes > 10);
        }
        return dto;
    }

    private PhoneVerification fromDTO(PhoneVerificationDTO dto) {
        PhoneVerification pv = new PhoneVerification();
        pv.setId(dto.getId());
        pv.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : OffsetDateTime.now());
        pv.setPhone(dto.getPhone());
        pv.setCode(dto.getCode());
        if (dto.getUserId() != null)
            userRepo.findById(dto.getUserId()).ifPresent(pv::setUser);
        return pv;
    }

    public List<PhoneVerificationDTO> findAll() {
        return repo.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public Optional<PhoneVerificationDTO> findById(Long id) {
        return repo.findById(id).map(this::toDTO);
    }

    public Optional<PhoneVerificationDTO> findByUserId(Long userId) {
        return repo.findByUserId(userId).map(this::toDTO);
    }

    public PhoneVerificationDTO save(PhoneVerificationDTO dto) {
        return toDTO(repo.save(fromDTO(dto)));
    }

    public PhoneVerificationDTO update(Long id, PhoneVerificationDTO dto) {
        return repo.findById(id).map(existing -> {
            existing.setUser(userRepo.findById(dto.getUserId()).orElse(null));
            existing.setCode(dto.getCode());
            return toDTO(repo.save(existing));
        }).orElseThrow(() -> new RuntimeException("PhoneVerification not found"));
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }

    public Optional<PhoneVerificationDTO> verifyCode(String phone, String code) {
        return repo.findTopByPhoneAndCodeOrderByCreatedAtDesc(phone, code)
                .map(this::toDTO);
    }
}
