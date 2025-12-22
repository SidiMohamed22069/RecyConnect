package com.project.RecyConnect.DTO;

import lombok.Data;

public class AuthDTO {

    @Data
    public static class LoginRequest {
        private Long phone;
        private String password;
    }

    @Data
    public static class RegisterRequest {
        private String username;
        private String password;
        private Long phone;
        private String verificationCode; // Code de vérification à valider
    }

    @Data
    public static class SendCodeRequest {
        private String phone;
    }

    @Data
    public static class VerifyCodeRequest {
        private String phone;
        private String code;
    }

    @Data
    public static class AuthResponse {
        private String token;
        private Long userId;
        private String username;
        private String message;

        public AuthResponse(String token, Long userId, String username, String message) {
            this.token = token;
            this.userId = userId;
            this.username = username;
            this.message = message;
        }

        public AuthResponse(String message) {
            this.message = message;
        }
    }
}

