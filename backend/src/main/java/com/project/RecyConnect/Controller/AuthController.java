package com.project.RecyConnect.Controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

import com.project.RecyConnect.DTO.AuthDTO;
import com.project.RecyConnect.DTO.UserDeviceDTO;
import com.project.RecyConnect.Model.PendingLogin;
import com.project.RecyConnect.Model.PendingLogin.PendingLoginStatus;
import com.project.RecyConnect.Model.Role;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.UserRepo;
import com.project.RecyConnect.Security.JwtUtil;
import com.project.RecyConnect.Service.PendingLoginService;
import com.project.RecyConnect.Service.PhoneVerificationService;
import com.project.RecyConnect.Service.UserDeviceService;

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
    private final UserDeviceService userDeviceService;
    private final PendingLoginService pendingLoginService;

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
        // Définir le rôle (USER par défaut, ADMIN si spécifié)
        Role userRole = Role.USER;
        if (request.getRole() != null && request.getRole().equalsIgnoreCase("ADMIN")) {
            userRole = Role.ADMIN;
        }
        
        User user = User.builder()
            .username(request.getUsername())
            .pwd(passwordEncoder.encode(request.getPassword()))
            .phone(phoneNumberToSave)
            .role(userRole)
            .imageData(User.DEFAULT_IMAGE_DATA)
            .build();

        User savedUser = userRepository.save(user);

        // Nettoyer les codes de vérification expirés (avec le numéro sans préfixe 222)
        phoneVerificationService.cleanupExpiredCodes(phoneNumberToSave);

        // Enregistrer l'appareil si les infos sont fournies
        if (request.getFcmToken() != null && !request.getFcmToken().isEmpty()) {
            userDeviceService.registerDevice(
                savedUser.getId(),
                request.getFcmToken(),
                request.getDeviceName(),
                request.getDeviceType()
            );
        }

        // Generate token with user role
        String token = jwtUtil.generateToken(savedUser);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthDTO.AuthResponse(token, savedUser.getId(), savedUser.getUsername(), savedUser.getPhone(), savedUser.getRole().name(), "Registration successful"));
    }

    /**
     * Endpoint pour créer un admin (réservé aux admins existants)
     */
    @PostMapping("/register-admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> registerAdmin(@RequestBody AuthDTO.RegisterRequest request) {
        Long phoneNumberToSave;
        try {
            String phoneStr = request.getPhone();
            Long phoneNumber = Long.parseLong(phoneStr);
            if (phoneStr.startsWith("222")) {
                phoneNumberToSave = Long.parseLong(phoneStr.substring(3));
            } else {
                phoneNumberToSave = phoneNumber;
            }
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Numéro de téléphone invalide"));
        }

        // Vérifier que le téléphone n'existe pas déjà
        if (userRepository.findByPhone(phoneNumberToSave) != null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Phone already exists"));
        }

        // Vérifier que le username n'existe pas déjà
        if (userRepository.findByUsername(request.getUsername()) != null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username already exists"));
        }

        User user = User.builder()
            .username(request.getUsername())
            .pwd(passwordEncoder.encode(request.getPassword()))
            .phone(phoneNumberToSave)
            .role(Role.ADMIN) // Toujours ADMIN pour cet endpoint
            .imageData(User.DEFAULT_IMAGE_DATA)
            .build();

        User savedUser = userRepository.save(user);

        String token = jwtUtil.generateToken(savedUser);

        return ResponseEntity.ok(Map.of(
            "message", "Admin created successfully",
            "userId", savedUser.getId(),
            "username", savedUser.getUsername(),
            "role", savedUser.getRole().name(),
            "token", token
        ));
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

        // Vérifier si un appareil est déjà connecté
        if (pendingLoginService.hasExistingDevice(user.getId())) {
            // Un appareil existe déjà -> demander confirmation
            Optional<UserDeviceDTO> existingDevice = userDeviceService.getLastConnectedDevice(user.getId());
            String existingDeviceName = existingDevice.map(UserDeviceDTO::getDeviceName).orElse("Appareil inconnu");
            
            // Créer une demande de connexion en attente
            PendingLogin pending = pendingLoginService.createPendingLogin(
                user.getId(),
                request.getFcmToken(),
                request.getDeviceName(),
                request.getDeviceType()
            );
            
            // Retourner une réponse indiquant qu'on attend la confirmation
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(AuthDTO.AuthResponse.pendingConfirmation(pending.getRequestId(), existingDeviceName));
        }

        // Pas d'appareil existant -> connexion directe
        if (request.getFcmToken() != null && !request.getFcmToken().isEmpty()) {
            userDeviceService.registerDevice(
                user.getId(),
                request.getFcmToken(),
                request.getDeviceName(),
                request.getDeviceType()
            );
        }

        String token = jwtUtil.generateToken(user);

        return ResponseEntity.ok(new AuthDTO.AuthResponse(
            token, 
            user.getId(), 
            user.getUsername(), 
            user.getPhone(), 
            user.getRole().name(), 
            "Login successful"
        ));
    }

    /**
     * Vérifier le statut d'une demande de connexion (polling par le nouvel appareil)
     */
    @GetMapping("/login/status/{requestId}")
    public ResponseEntity<?> checkLoginStatus(@PathVariable String requestId) {
        Optional<PendingLogin> pending = pendingLoginService.checkStatus(requestId);
        
        if (pending.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        PendingLogin login = pending.get();
        
        if (login.isExpired() || login.getStatus() == PendingLoginStatus.EXPIRED) {
            return ResponseEntity.ok(AuthDTO.AuthResponse.expired());
        }
        
        if (login.getStatus() == PendingLoginStatus.REJECTED) {
            return ResponseEntity.ok(AuthDTO.AuthResponse.rejected());
        }
        
        if (login.getStatus() == PendingLoginStatus.APPROVED) {
            // Connexion approuvée -> générer le token
            User user = login.getUser();
            String token = jwtUtil.generateToken(user);
            
            return ResponseEntity.ok(new AuthDTO.AuthResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getPhone(),
                user.getRole().name(),
                "Login approved"
            ));
        }
        
        // Toujours en attente
        AuthDTO.AuthResponse response = new AuthDTO.AuthResponse("En attente de confirmation...");
        response.setStatus("PENDING_CONFIRMATION");
        response.setRequestId(requestId);
        return ResponseEntity.ok(response);
    }

    /**
     * Approuver ou refuser une demande de connexion (appelé depuis l'ancien appareil)
     */
    @PostMapping("/login/confirm")
    public ResponseEntity<?> confirmLogin(@RequestBody AuthDTO.LoginConfirmRequest request) {
        try {
            if (request.getApproved()) {
                pendingLoginService.approveLogin(request.getRequestId());
                return ResponseEntity.ok(Map.of(
                    "message", "Connexion approuvée. Vous serez déconnecté de cet appareil.",
                    "status", "APPROVED"
                ));
            } else {
                pendingLoginService.rejectLogin(request.getRequestId());
                return ResponseEntity.ok(Map.of(
                    "message", "Connexion refusée",
                    "status", "REJECTED"
                ));
            }
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "message", e.getMessage(),
                "status", "ERROR"
            ));
        }
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

        return ResponseEntity.ok(new AuthDTO.AuthResponse(token, user.getId(), user.getUsername(), user.getPhone(), user.getRole().name(), "User found"));
    }
}

