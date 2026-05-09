package com.project.RecyConnect.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

public class AuthDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        private Long phone;
        private String password;
        private String deviceId;      // Identifiant unique de l'appareil
        private String fcmToken;      // Token FCM de l'appareil
        private String deviceName;    // Nom de l'appareil (ex: "iPhone de Sidi")
        private String deviceType;    // Type: "ANDROID", "IOS", "WEB"
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterRequest {
        private String username;
        private String password;
        private String phone;
        private String verificationCode;
        private String role; // optionnel, default "USER"
        private String deviceId;      // Identifiant unique de l'appareil
        private String fcmToken;      // Token FCM de l'appareil
        private String deviceName;    // Nom de l'appareil
        private String deviceType;    // Type: "ANDROID", "IOS", "WEB"
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendCodeRequest {
        private String phone;
        private Boolean isForgetPassword;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerifyCodeRequest {
        private String phone;
        private String code;
    }

    @Data
    public static class AuthResponse {
        private String token;
        private Long userId;
        private String username;
        private Long phone;
        private String role;
        private String message;

        public AuthResponse(String token, Long userId, String username, Long phone, String role, String message) {
            this.token = token;
            this.userId = userId;
            this.username = username;
            this.phone = phone;
            this.role = role;
            this.message = message;
        }

        public AuthResponse(String message) {
            this.message = message;
        }
    }
}

