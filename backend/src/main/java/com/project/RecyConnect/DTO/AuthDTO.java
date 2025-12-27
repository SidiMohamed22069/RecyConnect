package com.project.RecyConnect.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class AuthDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        private Long phone;
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterRequest {
        private String username;
        private String password;
        private String phone;
        private String verificationCode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendCodeRequest {
        private String phone;
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
        private String message;

        public AuthResponse(String token, Long userId, String username, Long phone, String message) {
            this.token = token;
            this.userId = userId;
            this.username = username;
            this.phone = phone;
            this.message = message;
        }

        public AuthResponse(String message) {
            this.message = message;
        }
    }
}

