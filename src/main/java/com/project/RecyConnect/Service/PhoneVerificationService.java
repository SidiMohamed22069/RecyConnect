package com.project.RecyConnect.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.security.SecureRandom;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.project.RecyConnect.Model.PhoneVerification;
import com.project.RecyConnect.Repository.PhoneVerificationRepository;
import com.project.RecyConnect.Repository.UserRepo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PhoneVerificationService {
    /** Durée de validité d'un code de vérification, en minutes. */
    private static final long CODE_TTL_MINUTES = 10;

    /** Délai minimum entre deux envois de code pour un même numéro, en secondes. */
    private static final long RESEND_DELAY_SECONDS = 60;

    private final PhoneVerificationRepository repo;
    private final UserRepo userRepo;
    // SecureRandom: un code de verification est un secret d'authentification,
    // un Random classique est predictible.
    private final SecureRandom random = new SecureRandom();
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

    /**
     * Génère et envoie un code de vérification pour un numéro de téléphone via Chinguisoft SMS API
     * @param phone Le numéro de téléphone
     * @param isForgetPassword Si true, vérifie que le numéro existe (pour réinitialisation mot de passe)
     *                         Si false ou null, vérifie que le numéro n'existe pas (pour inscription)
     *
     * Ne retourne rien volontairement: le code ne doit transiter que par SMS,
     * jamais dans une réponse HTTP.
     */
    public void sendVerificationCode(String phone, Boolean isForgetPassword) {
        // Valider le format du numéro mauritanien (+222)
        String normalizedPhone = normalizePhoneNumber(phone);
        if (!isValidMauritanianPhone(normalizedPhone)) {
            throw new RuntimeException("Le numéro doit être un numéro mauritanien valide (+222XXXXXXXX)");
        }

        // Enlever le préfixe 222 pour le stockage
        String phoneWithoutPrefix = normalizedPhone.startsWith("222") 
            ? normalizedPhone.substring(3) 
            : normalizedPhone;
        Long phoneLong = Long.parseLong(phoneWithoutPrefix);

        // Vérification conditionnelle selon le contexte (inscription ou oubli de mot de passe)
        if (Boolean.TRUE.equals(isForgetPassword)) {
            // Pour l'oubli de mot de passe : le numéro DOIT exister
            if (userRepo.findByPhone(phoneLong) == null) {
                throw new RuntimeException("Ce numéro de téléphone n'existe pas. Veuillez créer un compte.");
            }
        } else {
            // Pour l'inscription : le numéro NE DOIT PAS être déjà utilisé
            if (userRepo.findByPhone(phoneLong) != null) {
                throw new RuntimeException("Ce numéro de téléphone est déjà utilisé");
            }
        }

        // Anti-spam: un seul envoi par minute et par numéro (coût SMS + harcèlement).
        repo.findByPhoneOrderByCreatedAtDesc(phoneLong).stream()
                .findFirst()
                .filter(last -> last.getCreatedAt() != null
                        && ChronoUnit.SECONDS.between(last.getCreatedAt(), OffsetDateTime.now()) < RESEND_DELAY_SECONDS)
                .ifPresent(last -> {
                    throw new RuntimeException("Un code a déjà été envoyé. Veuillez patienter avant de réessayer.");
                });

        // Générer un code à 6 chiffres avec un generateur cryptographique
        String code = String.format("%06d", random.nextInt(1000000));

        // Invalider les codes precedents: un seul code actif a la fois par numero.
        repo.deleteAll(repo.findByPhoneOrderByCreatedAtDesc(phoneLong));

        // Envoyer le SMS via Chinguisoft API (avec préfixe 222).
        // Toute erreur remonte: on ne doit pas repondre 200 si le SMS n'est pas parti.
        sendSmsViaChinguisoft(normalizedPhone, code);

        // Créer l'enregistrement de vérification (sans préfixe 222)
        PhoneVerification verification = PhoneVerification.builder()
                .phone(phoneLong)
                .code(code)
                .createdAt(OffsetDateTime.now())
                .build();

        repo.save(verification);
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
        
        return cleaned;
    }

    /**
     * Vérifie si le numéro est un numéro mauritanien valide.
     * Accepte le format local (8 chiffres) et le format international (222 + 8 chiffres),
     * conformément à ce que normalizePhoneNumber produit et à ce que le message
     * d'erreur annonce à l'utilisateur.
     */
    private boolean isValidMauritanianPhone(String phone) {
        if (phone == null) {
            return false;
        }
        return phone.matches("^(222)?[0-9]{8}$");
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
            headers.set("Validation-token", token);
            headers.set("Content-Type", "application/json");

            // Préparer le body
            Map<String, String> body = new HashMap<>();
            body.put("phone", phone);
            body.put("lang", lang);
            body.put("code", code);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            // Envoyer la requête
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Le service SMS a refusé la demande");
            }

        } catch (RuntimeException e) {
            // Ne jamais journaliser le code: c'est un secret d'authentification.
            log.error("Echec de l'envoi du SMS de verification", e);
            // On propage: repondre 200 alors que le SMS n'est pas parti laisserait
            // l'utilisateur attendre indefiniment un code qui n'arrivera jamais.
            throw new RuntimeException("Impossible d'envoyer le SMS de vérification. Veuillez réessayer.");
        }
    }

    /**
     * Vérifie qu'un code est valide et non expiré, SANS le consommer.
     * Destiné à l'étape intermédiaire /verify-code, qui confirme la saisie à
     * l'utilisateur mais n'accorde aucun accès par elle-même.
     */
    public boolean verifyCodeBeforeRegistration(Long phone, String code) {
        return findValidCode(phone, code).isPresent();
    }

    /**
     * Vérifie ET consomme le code: à utiliser au moment où le code accorde
     * réellement un accès (création de compte, réinitialisation de mot de passe).
     * Un code consommé ne peut plus être rejoué.
     */
    @Transactional
    public boolean consumeCode(Long phone, String code) {
        Optional<PhoneVerification> valid = findValidCode(phone, code);
        valid.ifPresent(repo::delete);
        return valid.isPresent();
    }

    private Optional<PhoneVerification> findValidCode(Long phone, String code) {
        return repo.findTopByPhoneAndCodeOrderByCreatedAtDesc(phone, code)
                .filter(pv -> pv.getCreatedAt() != null
                        && ChronoUnit.MINUTES.between(pv.getCreatedAt(), OffsetDateTime.now()) <= CODE_TTL_MINUTES);
    }

    /**
     * Supprime les codes de vérification expirés pour un téléphone
     */
    @Transactional
    public void cleanupExpiredCodes(Long phone) {
        List<PhoneVerification> allCodes = repo.findByPhoneOrderByCreatedAtDesc(phone);
        for (PhoneVerification pv : allCodes) {
            long minutes = ChronoUnit.MINUTES.between(pv.getCreatedAt(), OffsetDateTime.now());
            if (minutes > CODE_TTL_MINUTES) {
                repo.delete(pv);
            }
        }
    }
}
