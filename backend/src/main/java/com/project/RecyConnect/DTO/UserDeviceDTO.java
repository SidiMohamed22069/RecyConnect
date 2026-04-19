package com.project.RecyConnect.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDeviceDTO {
    
    private Long id;
    private Long userId;
    private String fcmToken;
    private String deviceName;
    private String deviceType;  // "ANDROID", "IOS", "WEB"
    private OffsetDateTime lastConnectedAt;
    private OffsetDateTime createdAt;
}
