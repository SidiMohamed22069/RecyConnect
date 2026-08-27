package com.project.RecyConnect.Controller;

import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
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
import com.project.RecyConnect.Model.UserSession;
import com.project.RecyConnect.Repository.UserRepo;
import com.project.RecyConnect.Security.JwtUtil;
import com.project.RecyConnect.Service.FCMService;
import com.project.RecyConnect.Service.PhoneVerificationService;
import com.project.RecyConnect.Service.RefreshTokenService;
import com.project.RecyConnect.Service.UserSessionService;

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
    private final UserSessionService userSessionService;
    private final RefreshTokenService refreshTokenService;
    private final FCMService fcmService;

    /** Longueur minimale d'un mot de passe, alignee sur l'inscription mobile. */
    private static final int MIN_PASSWORD_LENGTH = 6;

    /**
     * Étape 1: Envoyer un code de vérification au numéro de téléphone
     */
    @PostMapping("/send-code")
    public ResponseEntity<?> sendVerificationCode(@RequestBody AuthDTO.SendCodeRequest request) {
        try {
            // Le code n'est JAMAIS renvoye dans la reponse: il ne doit transiter que par SMS.
            phoneVerificationService.sendVerificationCode(request.getPhone(), request.getIsForgetPassword());
            return ResponseEntity.ok(new AuthDTO.AuthResponse(
                    "Code de vérification envoyé par SMS."));
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
            Long phoneToVerify = Long.parseLong(
                    PhoneVerificationService.toLocalPhone(request.getPhone()));
            
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
                phoneVerificationService.sendVerificationCode(request.getPhone(), false);
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(new AuthDTO.AuthResponse("Code de vérification envoyé. Veuillez saisir le code reçu par SMS."));
            } catch (RuntimeException e) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new AuthDTO.AuthResponse(e.getMessage()));
            }
        }

        Long phoneNumberToSave;
        try {
            phoneNumberToSave = Long.parseLong(
                    PhoneVerificationService.toLocalPhone(request.getPhone()));
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new AuthDTO.AuthResponse("Numéro de téléphone invalide"));
        }
        // Vérifier ET consommer le code (usage unique) avec le numéro sans préfixe 222
        boolean isCodeValid = phoneVerificationService.consumeCode(
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
        // Le role est TOUJOURS USER: le champ "role" du corps de requete est ignore.
        // La creation d'un administrateur passe exclusivement par /api/auth/register-admin,
        // qui exige deja un appelant authentifie ayant le role ADMIN.
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

        String token;

        // Appliquer la session unique si les infos appareil sont fournies
        if (request.getFcmToken() != null && !request.getFcmToken().isEmpty()) {
            if (request.getDeviceId() == null || request.getDeviceId().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new AuthDTO.AuthResponse("deviceId is required when fcmToken is provided"));
            }

            UserSessionService.SessionReplacementResult sessionResult = userSessionService.replaceSession(
                savedUser.getId(),
                request.getDeviceId(),
                request.getDeviceName(),
                request.getFcmToken()
            );

            UserSession session = sessionResult.session();
            token = jwtUtil.generateToken(savedUser, session.getSessionVersion(), session.getDeviceId());

            String previousFcmToken = sessionResult.previousFcmToken();
            if (previousFcmToken != null && !Objects.equals(previousFcmToken, request.getFcmToken())) {
                fcmService.sendForceLogoutToToken(previousFcmToken, "session_replaced");
            }
        } else {
            token = jwtUtil.generateToken(savedUser);
        }

        AuthDTO.AuthResponse body = new AuthDTO.AuthResponse(
                token, savedUser.getId(), savedUser.getUsername(), savedUser.getPhone(),
                savedUser.getRole().name(), "Registration successful");
        // Nul quand l'inscription n'a pas ouvert de session unique (pas de
        // fcmToken): il n'y a alors rien a rafraichir.
        body.setRefreshToken(refreshTokenService.issueFor(savedUser.getId()));

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    /**
     * Endpoint pour créer un admin (réservé aux admins existants)
     */
    @PostMapping("/register-admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> registerAdmin(@RequestBody AuthDTO.RegisterRequest request) {
        Long phoneNumberToSave;
        try {
            phoneNumberToSave = Long.parseLong(
                    PhoneVerificationService.toLocalPhone(request.getPhone()));
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
        if (request.getDeviceId() == null || request.getDeviceId().isBlank()
                || request.getFcmToken() == null || request.getFcmToken().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new AuthDTO.AuthResponse("deviceId and fcmToken are required"));
        }

        Long phoneToSearch = Long.parseLong(PhoneVerificationService
                .toLocalPhone(String.valueOf(request.getPhone())));
        
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

        UserSessionService.SessionReplacementResult sessionResult = userSessionService.replaceSession(
                user.getId(),
                request.getDeviceId(),
                request.getDeviceName(),
                request.getFcmToken()
        );

        String previousFcmToken = sessionResult.previousFcmToken();
        if (previousFcmToken != null && !Objects.equals(previousFcmToken, request.getFcmToken())) {
            fcmService.sendForceLogoutToToken(previousFcmToken, "session_replaced");
        }

        UserSession session = sessionResult.session();
        String token = jwtUtil.generateToken(user, session.getSessionVersion(), session.getDeviceId());

        AuthDTO.AuthResponse body = new AuthDTO.AuthResponse(
            token, 
            user.getId(), 
            user.getUsername(), 
            user.getPhone(), 
            user.getRole().name(), 
            "Login successful"
        );
        body.setRefreshToken(refreshTokenService.issueFor(user.getId()));

        return ResponseEntity.ok(body);
    }

    /**
     * Renouvelle un jeton d'acces expire sans redemander le mot de passe.
     *
     * <p>Point H4 de l'audit mobile: le jeton d'acces dure 23 heures, et a son
     * expiration l'application ejectait l'utilisateur vers l'ecran de connexion,
     * au milieu d'une negociation le cas echeant. Elle intercepte desormais le
     * 401, appelle ce point d'entree une fois, rejoue la requete d'origine, et
     * ne deconnecte que si le renouvellement echoue.
     *
     * <p>Les refus rendent <b>401</b> — jeton inconnu, perime, ou appareil qui
     * n'est plus celui de la session. C'est ce code que l'application traite
     * comme "session terminee": elle efface ses jetons et repart sur l'ecran de
     * connexion, exactement comme avant ce point. Un corps mal forme rend 400:
     * c'est un defaut d'appelant, pas une session invalide.
     *
     * <p>Aucun jeton d'acces n'est exige ici, et c'est le principe meme: celui
     * de l'appelant vient d'expirer. La preuve est le jeton de rafraichissement,
     * double de l'appareil enregistre.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @RequestBody(required = false) AuthDTO.RefreshRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceIdHeader) {

        String presented = request == null ? null : request.getRefreshToken();
        if (presented == null || presented.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new AuthDTO.AuthResponse("refreshToken is required"));
        }

        RefreshTokenService.RefreshOutcome outcome =
                refreshTokenService.rotate(presented, deviceIdHeader).orElse(null);
        if (outcome == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthDTO.AuthResponse("Refresh token invalid or expired"));
        }

        User user = userRepository.findById(outcome.userId()).orElse(null);
        if (user == null) {
            // Compte supprime alors que la session survivait: rien a renouveler.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthDTO.AuthResponse("Refresh token invalid or expired"));
        }

        // Meme sessionVersion et meme deviceId: le renouvellement prolonge la
        // session, il n'en ouvre pas une nouvelle. La faire tourner couperait
        // le WebSocket et les jetons encore en vol de cet appareil.
        String token = jwtUtil.generateToken(user, outcome.sessionVersion(), outcome.deviceId());

        AuthDTO.AuthResponse body = new AuthDTO.AuthResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getPhone(),
                user.getRole().name(),
                "Token refreshed");
        body.setRefreshToken(outcome.refreshToken());

        return ResponseEntity.ok(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceIdHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Long userId = jwtUtil.extractUserId(token);
                Long sessionVersion = jwtUtil.extractSessionVersion(token);
                String tokenDeviceId = jwtUtil.extractDeviceId(token);

                if (userId != null && sessionVersion != null && tokenDeviceId != null && tokenDeviceId.equals(deviceIdHeader)) {
                    userSessionService.revokeIfCurrent(userId, sessionVersion, tokenDeviceId);
                }
            } catch (Exception ignored) {
                // Best effort logout
            }
            jwtUtil.expireToken(token);
        }
        return ResponseEntity.ok(new AuthDTO.AuthResponse("Logout successful"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody AuthDTO.ResetPasswordRequest request) {
        if (request.getPhone() == null || request.getPhone().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new AuthDTO.AuthResponse("Phone is required"));
        }

        if (request.getVerificationCode() == null || request.getVerificationCode().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new AuthDTO.AuthResponse("Verification code is required"));
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new AuthDTO.AuthResponse("Password is required"));
        }

        Long phoneToSearch;
        try {
            phoneToSearch = Long.parseLong(
                    PhoneVerificationService.toLocalPhone(request.getPhone()));
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new AuthDTO.AuthResponse("Numéro de téléphone invalide"));
        }

        boolean isCodeValid = phoneVerificationService.consumeCode(
                phoneToSearch, request.getVerificationCode());

        if (!isCodeValid) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new AuthDTO.AuthResponse("Code de vérification invalide ou expiré"));
        }

        User user = userRepository.findByPhone(phoneToSearch);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new AuthDTO.AuthResponse("User not found"));
        }

        user.setPwd(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        phoneVerificationService.cleanupExpiredCodes(phoneToSearch);
        userSessionService.revokeAllSessionsForUser(user.getId());

        return ResponseEntity.ok(new AuthDTO.AuthResponse("Password updated successfully"));
    }

    /**
     * Changement de son propre mot de passe, pour un compte deja connecte.
     *
     * <p>Distinct de {@code /reset-password}, qui exige un code SMS: envoyer un
     * SMS a quelqu'un qui vient de prouver son identite n'apporte rien. C'est
     * l'ancien mot de passe qui sert de preuve.
     *
     * <p>{@code /api/auth/**} est public dans la configuration de securite,
     * donc l'identite est relue du contexte rempli par {@code JwtRequestFilter}:
     * sans jeton valide, la requete repart en 401 sans rien lire du corps.
     *
     * <p>Un ancien mot de passe faux rend <b>400</b>, pas 401. Un 401 signifie
     * "session invalide" pour les clients, qui purgent alors la session et
     * renvoient vers l'ecran de connexion — or ici la session est parfaitement
     * valide, c'est la saisie qui est fausse.
     *
     * <p>Les sessions ne sont pas revoquees: celle qui appelle est la seule
     * ouverte (le modele n'en autorise qu'une par compte) et elle vient de
     * prouver qu'elle connait le mot de passe.
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody(required = false) AuthDTO.ChangePasswordRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails principal)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthDTO.AuthResponse("Authentication required"));
        }

        String newPassword = request == null ? null : request.getNewPassword();
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            return ResponseEntity.badRequest().body(new AuthDTO.AuthResponse(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters"));
        }

        User user = userRepository.findByUsername(principal.getUsername());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new AuthDTO.AuthResponse("User not found"));
        }

        String currentPassword = request.getCurrentPassword();
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            return ResponseEntity.badRequest()
                    .body(new AuthDTO.AuthResponse("Current password is incorrect"));
        }

        user.setPwd(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return ResponseEntity.noContent().build();
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

