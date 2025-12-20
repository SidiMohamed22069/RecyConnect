package com.project.RecyConnect.DTO;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class NotificationDTO {
    private Long id;
    private OffsetDateTime createdAt;
    private Long senderId;
    private Long receiverId;
    private String message;
    private String title;
}
