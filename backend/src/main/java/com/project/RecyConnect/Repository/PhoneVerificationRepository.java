package com.project.RecyConnect.Repository;

import com.project.RecyConnect.Model.PhoneVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {
    Optional<PhoneVerification> findByPhone(String phone);
}
