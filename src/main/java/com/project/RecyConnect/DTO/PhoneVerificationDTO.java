package com.project.RecyConnect.DTO;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class PhoneVerificationDTO {
    private Long id;
    private OffsetDateTime createdAt;
    private Long userId;
    private Long phone;
    private String code;
    private Boolean verified;
    private Boolean expired;
}
