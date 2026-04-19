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
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginConfirmRequest {
        private String requestId;
        private Boolean approved;  // true = approuver, false = refuser
    }

    @Data
    public static class AuthResponse {
        private String token;
        private Long userId;
        private String username;
        private Long phone;
        private String role;
        private String message;
        
        // Pour le système de confirmation de connexion
        private String status;  // "SUCCESS", "PENDING_CONFIRMATION", "REJECTED", "EXPIRED"
        private String requestId;  // ID de la demande si en attente
        private String existingDeviceName;  // Nom de l'appareil déjà connecté

        public AuthResponse(String token, Long userId, String username, Long phone, String role, String message) {
            this.token = token;
            this.userId = userId;
            this.username = username;
            this.phone = phone;
            this.role = role;
            this.message = message;
            this.status = "SUCCESS";
        }

        public AuthResponse(String message) {
            this.message = message;
        }
        
        // Constructeur pour demande en attente
        public static AuthResponse pendingConfirmation(String requestId, String existingDeviceName) {
            AuthResponse response = new AuthResponse("Une demande de connexion a été envoyée à votre autre appareil");
            response.setStatus("PENDING_CONFIRMATION");
            response.setRequestId(requestId);
            response.setExistingDeviceName(existingDeviceName);
            return response;
        }
        
        // Constructeur pour connexion refusée
        public static AuthResponse rejected() {
            AuthResponse response = new AuthResponse("Connexion refusée par l'autre appareil");
            response.setStatus("REJECTED");
            return response;
        }
        
        // Constructeur pour demande expirée
        public static AuthResponse expired() {
            AuthResponse response = new AuthResponse("La demande de connexion a expiré");
            response.setStatus("EXPIRED");
            return response;
        }
    }
}

