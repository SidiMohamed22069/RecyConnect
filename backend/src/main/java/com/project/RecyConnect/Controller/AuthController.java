package com.project.RecyConnect.Controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.RecyConnect.DTO.AuthDTO;
import com.project.RecyConnect.Model.Role;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.UserRepo;
import com.project.RecyConnect.Security.JwtUtil;
import com.project.RecyConnect.Service.PhoneVerificationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepo userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final PhoneVerificationService phoneVerificationService;

    /**
     * Étape 1: Envoyer un code de vérification au numéro de téléphone
     */
    @PostMapping("/send-code")
    public ResponseEntity<?> sendVerificationCode(@RequestBody AuthDTO.SendCodeRequest request) {
        try {
            String code = phoneVerificationService.sendVerificationCode(request.getPhone(), request.getIsForgetPassword());
            // En production, ne pas retourner le code dans la réponse !
            return ResponseEntity.ok(new AuthDTO.AuthResponse(
                    "Code de vérification envoyé. Code (dev only): " + code));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new AuthDTO.AuthResponse(e.getMessage()));
        }
    }

    /**
     * Étape 2: Vérifier le code de vérification
     */
    @PostMapping("/verify-code")
    public ResponseEntity<?> verifyCode(@RequestBody AuthDTO.VerifyCodeRequest request) {
        try {
            // Enlever le préfixe 222 si présent
            String phoneStr = request.getPhone();
            Long phoneToVerify = Long.parseLong(phoneStr);
            if (phoneStr.startsWith("222")) {
                phoneToVerify = Long.parseLong(phoneStr.substring(3));
            }
            
            boolean isValid = phoneVerificationService.verifyCodeBeforeRegistration(
                phoneToVerify, request.getCode());
            
            if (isValid) {
                return ResponseEntity.ok(new AuthDTO.AuthResponse(
                        "Code vérifié avec succès. Vous pouvez maintenant créer votre compte."));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new AuthDTO.AuthResponse("Code invalide ou expiré"));
            }
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new AuthDTO.AuthResponse("Numéro de téléphone invalide"));
        }
    }

    /**
     * Étape 3: Créer le compte après vérification du code
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthDTO.RegisterRequest request) {

        // Si aucun code n'est fourni, envoyer le SMS et demander la saisie du code
        if (request.getVerificationCode() == null || request.getVerificationCode().isEmpty()) {
            try {
                String code = phoneVerificationService.sendVerificationCode(request.getPhone(), false);
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(new AuthDTO.AuthResponse("Code de vérification envoyé. Veuillez saisir le code reçu par SMS."));
            } catch (RuntimeException e) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new AuthDTO.AuthResponse(e.getMessage()));
            }
        }

        Long phoneNumberToSave;
        try {
            // Enlever le préfixe 222 pour la sauvegarde et la vérification
            String phoneStr = request.getPhone();
            Long phoneNumber = Long.parseLong(phoneStr);
            if (phoneStr.startsWith("222")) {
                phoneNumberToSave = Long.parseLong(phoneStr.substring(3));
            } else {
                phoneNumberToSave = phoneNumber;
            }
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new AuthDTO.AuthResponse("Numéro de téléphone invalide"));
        }
        // Vérifier le code avec le numéro sans préfixe 222 (comme stocké dans la base)
        boolean isCodeValid = phoneVerificationService.verifyCodeBeforeRegistration(
           phoneNumberToSave, request.getVerificationCode());
        
        if (!isCodeValid) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new AuthDTO.AuthResponse("Code de vérification invalide ou expiré"));
        }

        // Check if username already exists
        if (userRepository.findByUsername(request.getUsername()) != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new AuthDTO.AuthResponse("Username already exists"));
        }

        // Check if phone already exists (vérifier avec le numéro sans préfixe)
        if (userRepository.findByPhone(phoneNumberToSave) != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthDTO.AuthResponse("Phone number already exists"));
        }

        // Create new user avec le numéro sans préfixe 222
        User user = User.builder()
            .username(request.getUsername())
            .pwd(passwordEncoder.encode(request.getPassword()))
            .phone(phoneNumberToSave)
            .role(Role.USER)
            .imageData(User.DEFAULT_IMAGE_DATA)
            .build();

        User savedUser = userRepository.save(user);

        // Nettoyer les codes de vérification expirés (avec le numéro sans préfixe 222)
        phoneVerificationService.cleanupExpiredCodes(phoneNumberToSave);

        // Generate token
        String token = jwtUtil.generateToken(savedUser.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthDTO.AuthResponse(token, savedUser.getId(), savedUser.getUsername(), savedUser.getPhone(), "Registration successful"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthDTO.LoginRequest request) {
        // Enlever le préfixe 222 si présent
        Long phoneToSearch = request.getPhone();
        String phoneStr = String.valueOf(request.getPhone());
        if (phoneStr.startsWith("222")) {
            phoneToSearch = Long.parseLong(phoneStr.substring(3));
        }
        
        // Find user by phone (sans préfixe 222)
        User user = userRepository.findByPhone(phoneToSearch);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthDTO.AuthResponse("User not found with this phone number"));
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthDTO.AuthResponse("Invalid phone or password"));
        }

        String token = jwtUtil.generateToken(user.getUsername());

        return ResponseEntity.ok(new AuthDTO.AuthResponse(token, user.getId(), user.getUsername(), user.getPhone(), "Login successful"));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            jwtUtil.expireToken(token);
        }
        return ResponseEntity.ok(new AuthDTO.AuthResponse("Logout successful"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthDTO.AuthResponse("No token provided"));
        }

        String token = authHeader.substring(7);
        String username = jwtUtil.extractEmail(token);

        User user = userRepository.findByUsername(username);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new AuthDTO.AuthResponse("User not found"));
        }

        return ResponseEntity.ok(new AuthDTO.AuthResponse(token, user.getId(), user.getUsername(), user.getPhone(), "User found"));
    }
}

