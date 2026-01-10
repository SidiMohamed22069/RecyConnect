package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.PhoneVerificationDTO;
import com.project.RecyConnect.Model.PhoneVerification;
import com.project.RecyConnect.Repository.PhoneVerificationRepository;
import com.project.RecyConnect.Repository.UserRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class PhoneVerificationService {
    private final PhoneVerificationRepository repo;
    private final UserRepo userRepo;
    private final Random random = new Random();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${sms.chinguisoft.validation-key}")
    private String validationKey;

    @Value("${sms.chinguisoft.token}")
    private String token;

    @Value("${sms.chinguisoft.base-url}")
    private String baseUrl;

    @Value("${sms.chinguisoft.lang}")
    private String lang;

    public PhoneVerificationService(PhoneVerificationRepository repo, UserRepo userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    private PhoneVerificationDTO toDTO(PhoneVerification pv) {
        PhoneVerificationDTO dto = new PhoneVerificationDTO();
        dto.setId(pv.getId());
        dto.setCreatedAt(pv.getCreatedAt());
        dto.setUserId(pv.getUser() != null ? pv.getUser().getId() : null);
            dto.setPhone(pv.getPhone() != null ? pv.getPhone() : null);
        dto.setCode(pv.getCode());
        // Check if expired (10 minutes)
        if (pv.getCreatedAt() != null) {
            long minutes = ChronoUnit.MINUTES.between(pv.getCreatedAt(), OffsetDateTime.now());
            dto.setExpired(minutes > 10);
        }
        return dto;
    }

    private PhoneVerification fromDTO(PhoneVerificationDTO dto) {
        PhoneVerification pv = new PhoneVerification();
        pv.setId(dto.getId());
        pv.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : OffsetDateTime.now());
            pv.setPhone(dto.getPhone() != null ? dto.getPhone() : null);
        pv.setCode(dto.getCode());
        if (dto.getUserId() != null)
            userRepo.findById(dto.getUserId()).ifPresent(pv::setUser);
        return pv;
    }

    public List<PhoneVerificationDTO> findAll() {
        return repo.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public Optional<PhoneVerificationDTO> findById(Long id) {
        return repo.findById(id).map(this::toDTO);
    }

    public Optional<PhoneVerificationDTO> findByUserId(Long userId) {
        return repo.findByUserId(userId).map(this::toDTO);
    }

    public PhoneVerificationDTO save(PhoneVerificationDTO dto) {
        return toDTO(repo.save(fromDTO(dto)));
    }

    public PhoneVerificationDTO update(Long id, PhoneVerificationDTO dto) {
        return repo.findById(id).map(existing -> {
            existing.setUser(userRepo.findById(dto.getUserId()).orElse(null));
            existing.setCode(dto.getCode());
            return toDTO(repo.save(existing));
        }).orElseThrow(() -> new RuntimeException("PhoneVerification not found"));
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }

    public Optional<PhoneVerificationDTO> verifyCode(Long phone, String code) {
        return repo.findTopByPhoneAndCodeOrderByCreatedAtDesc(phone, code)
                .map(this::toDTO);
    }

    /**
     * Génère et envoie un code de vérification pour un numéro de téléphone via Chinguisoft SMS API
     */
    public String sendVerificationCode(String phone) {
        // Valider le format du numéro mauritanien (+222)
        String normalizedPhone = normalizePhoneNumber(phone);
        if (!isValidMauritanianPhone(normalizedPhone)) {
            throw new RuntimeException("Le numéro doit être un numéro mauritanien valide (+222XXXXXXXX)");
        }

        // Vérifier si le téléphone est déjà utilisé
            Long normalizedPhoneLong = Long.parseLong(normalizedPhone);
            if (userRepo.findByPhone(normalizedPhoneLong) != null) {
                throw new RuntimeException("Ce numéro de téléphone est déjà utilisé");
            }

        // Générer un code à 6 chiffres
        String code = String.format("%06d", random.nextInt(1000000));

        // Envoyer le SMS via Chinguisoft API
        sendSmsViaChinguisoft(normalizedPhone, code);

        // Créer l'enregistrement de vérification
            PhoneVerification verification = PhoneVerification.builder()
                    .phone(normalizedPhoneLong)
                .code(code)
                .createdAt(OffsetDateTime.now())
                .build();

        repo.save(verification);

        return code; // En dev seulement, retirer en production
    }

    /**
     * Normalise le numéro de téléphone mauritanien
     * Accepte: +22212345678, 22212345678, 12345678
     * Retourne: 22212345678
     */
    private String normalizePhoneNumber(String phone) {
        if (phone == null) {
            return null;
        }
        // Supprimer tous les espaces et caractères non numériques sauf le +
        String cleaned = phone.replaceAll("[^0-9+]", "");
        
        // Supprimer le + au début si présent
        if (cleaned.startsWith("+")) {
            cleaned = cleaned.substring(1);
        }
        
        // Si le numéro ne commence pas par 222, l'ajouter
        if (!cleaned.startsWith("222")) {
            cleaned = "222" + cleaned;
        }
        
        return cleaned;
    }

    /**
     * Vérifie si le numéro est un numéro mauritanien valide
     * Format attendu: 222XXXXXXXX (11 chiffres au total)
     */
    private boolean isValidMauritanianPhone(String phone) {
        if (phone == null) {
            return false;
        }
        // Doit commencer par 222 et avoir 11 chiffres au total
        return phone.matches("^222[0-9]{8}$");
    }

    /**
     * Envoie un SMS via l'API Chinguisoft
     */
    private void sendSmsViaChinguisoft(String phone, String code) {
        try {
            String url = baseUrl + "/" + validationKey;

            // Préparer les headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // Essayer différents formats d'authentification
            headers.set("Authorization", "Bearer " + token);
            headers.set("Validation-token", token);
            headers.set("X-API-Key", token);
            headers.set("Token", token);

            // Préparer le body
            Map<String, String> body = new HashMap<>();
            body.put("phone", phone);
            body.put("lang", lang);
            body.put("code", code);
            body.put("token", token); // Ajouter le token dans le body aussi

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            System.out.println("=== DEBUG SMS API ===");
            System.out.println("URL: " + url);
            System.out.println("Headers: " + headers);
            System.out.println("Body: " + body);

            // Envoyer la requête
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            System.out.println("Response Status: " + response.getStatusCode());
            System.out.println("Response Body: " + response.getBody());

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Erreur lors de l'envoi du SMS: " + response.getBody());
            }

            System.out.println("SMS envoyé avec succès à " + phone);

        } catch (Exception e) {
            System.err.println("Erreur lors de l'envoi du SMS: " + e.getMessage());
            // En mode dev, continuer sans envoyer le SMS réellement
            System.out.println("⚠️ Mode DEV: SMS non envoyé, mais code généré: " + code);
            // throw new RuntimeException("Erreur lors de l'envoi du SMS: " + e.getMessage());
        }
    }

    /**
     * Vérifie le code de vérification avant l'inscription
     */
    public boolean verifyCodeBeforeRegistration(Long phone, String code) {
        Optional<PhoneVerification> verification = repo.findTopByPhoneAndCodeOrderByCreatedAtDesc(phone, code);

        if (verification.isEmpty()) {
            return false;
        }

        PhoneVerification pv = verification.get();

        // Vérifier l'expiration (10 minutes)
        long minutes = ChronoUnit.MINUTES.between(pv.getCreatedAt(), OffsetDateTime.now());
        if (minutes > 10) {
            return false;
        }

        return true;
    }

    /**
     * Supprime les codes de vérification expirés pour un téléphone
     */
    public void cleanupExpiredCodes(Long phone) {
        List<PhoneVerification> allCodes = repo.findByPhoneOrderByCreatedAtDesc(phone);
        for (PhoneVerification pv : allCodes) {
            long minutes = ChronoUnit.MINUTES.between(pv.getCreatedAt(), OffsetDateTime.now());
            if (minutes > 10) {
                repo.delete(pv);
            }
        }
    }
}
