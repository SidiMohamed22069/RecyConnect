package com.project.RecyConnect.Model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_devices")
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true)
    private String fcmToken;

    private String deviceName;  // Ex: "iPhone de Sidi", "Samsung Galaxy S21"

    private String deviceType;  // "ANDROID", "IOS", "WEB"

    private OffsetDateTime lastConnectedAt;

    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        lastConnectedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastConnectedAt = OffsetDateTime.now();
    }
}
