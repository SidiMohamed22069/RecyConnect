package com.project.RecyConnect.DTO;

import com.project.RecyConnect.Model.User;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class PhoneVerificationDTO {
    private Long id;
    private OffsetDateTime createdAt;
    private Long userId;
    private String code;
    private Boolean verified;
}
