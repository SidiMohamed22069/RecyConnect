package com.project.RecyConnect.DTO;

import com.fasterxml.jackson.annotation.JsonInclude;

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
        // IGNORE par /api/auth/register: tout compte cree est un USER.
        // La creation d'un admin passe par /api/auth/register-admin.
        private String role;
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

    /**
     * Changement de mot de passe par un utilisateur deja connecte.
     *
     * <p>Distinct de {@link ResetPasswordRequest}, qui s'appuie sur un code SMS:
     * ici c'est l'ancien mot de passe qui fait office de preuve.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangePasswordRequest {
        private String currentPassword;
        private String newPassword;
    }

    /**
     * Renouvellement d'un jeton d'acces expire (point H4 de l'audit mobile).
     *
     * <p>L'en-tete {@code X-Device-Id} accompagne l'appel: il est verifie
     * contre l'appareil de la session, comme sur toute requete authentifiee.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefreshRequest {
        private String refreshToken;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResetPasswordRequest {
        private String phone;
        private String verificationCode;
        private String password;
    }

    @Data
    public static class AuthResponse {
        private String token;
        private Long userId;
        private String username;
        private Long phone;
        private String role;
        private String message;

        /**
         * Jeton de rafraichissement, renseigne uniquement par les reponses qui
         * en emettent un: connexion, inscription et {@code /refresh}. Absent
         * du JSON partout ailleurs — le rendre a chaque reponse en multiplierait
         * les occasions de fuite pour rien.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String refreshToken;

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

