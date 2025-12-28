package com.project.RecyConnect.Repository;

import com.project.RecyConnect.Model.PhoneVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {
    Optional<PhoneVerification> findByUserId(Long userId);
    List<PhoneVerification> findByPhoneOrderByCreatedAtDesc(Long phone);
    Optional<PhoneVerification> findTopByPhoneAndCodeOrderByCreatedAtDesc(Long phone, String code);
    Optional<PhoneVerification> findTopByPhoneOrderByCreatedAtDesc(Long phone);
}
