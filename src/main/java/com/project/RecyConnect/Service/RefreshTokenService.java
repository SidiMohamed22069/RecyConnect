package com.project.RecyConnect.Service;

import com.project.RecyConnect.Model.UserSession;
import com.project.RecyConnect.Repository.UserSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Jetons de rafraichissement (point H4 de l'audit mobile).
 *
 * <p>Le jeton d'acces dure 23 heures. A son expiration, l'application ejectait
 * l'utilisateur vers l'ecran de connexion — au milieu d'une negociation le cas
 * echeant — et lui demandait de ressaisir son mot de passe. Un jeton de
 * rafraichissement de longue duree permet de remplacer le jeton d'acces sans
 * rien redemander.
 *
 * <p>Trois proprietes tiennent la securite de ce mecanisme:
 * <ul>
 *   <li><b>Rien en clair en base.</b> Seule l'empreinte SHA-256 est stockee.
 *       Un jeton de rafraichissement vaut un mot de passe: une fuite de la
 *       base ne doit pas ouvrir les sessions. Le hachage est direct, sans
 *       sel ni bcrypt, parce que la valeur d'entree est deja 256 bits
 *       d'aleatoire — il n'y a pas de dictionnaire a lui opposer.</li>
 *   <li><b>Rotation a chaque usage.</b> Le jeton presente est invalide et
 *       remplace. Un jeton intercepte et rejoue plus tard ne vaut plus rien,
 *       et l'appareil legitime perd sa session — ce qui rend le vol visible
 *       au lieu de le rendre silencieux.</li>
 *   <li><b>Lie a l'appareil.</b> Le renouvellement exige l'en-tete
 *       {@code X-Device-Id} de la session, comme toute requete authentifiee
 *       (modele mono-appareil). Voler le jeton ne suffit pas.</li>
 * </ul>
 *
 * <p>Le jeton vit sur {@link UserSession}, donc il disparait exactement quand
 * la session disparait: deconnexion, connexion depuis un autre appareil,
 * reinitialisation du mot de passe. Aucune revocation separee a maintenir.
 */
@Service
public class RefreshTokenService {

    /** 256 bits d'aleatoire: un jeton devinable vaudrait un mot de passe devinable. */
    private static final int TOKEN_BYTES = 32;

    private final UserSessionRepository userSessionRepository;
    private final SecureRandom random = new SecureRandom();
    private final long validityDays;

    public RefreshTokenService(UserSessionRepository userSessionRepository,
                               @Value("${jwt.refresh-expiration-days:30}") long validityDays) {
        this.userSessionRepository = userSessionRepository;
        this.validityDays = validityDays;
    }

    /**
     * Emet un jeton pour la session de {@code userId} et rend sa valeur en
     * clair — la seule fois ou elle existe hors de l'appareil.
     *
     * <p>Rend {@code null} si le compte n'a pas de session ouverte: c'est le
     * cas d'une inscription ou d'une connexion sans informations d'appareil,
     * ou le jeton d'acces est emis sans session unique. L'appelant n'a alors
     * rien a transmettre, et l'application retombe sur l'ancien comportement.
     */
    @Transactional
    public String issueFor(Long userId) {
        UserSession session = userSessionRepository.findById(userId).orElse(null);
        if (session == null) {
            return null;
        }

        String raw = newRawToken();
        session.setRefreshTokenHash(hash(raw));
        session.setRefreshTokenExpiresAt(OffsetDateTime.now().plusDays(validityDays));
        userSessionRepository.save(session);
        return raw;
    }

    /**
     * Valide le jeton presente, l'invalide, et en emet un nouveau.
     *
     * <p>Rend un {@link Optional} vide pour tous les refus — jeton inconnu,
     * perime, appareil different — sans distinguer les cas: dire lequel des
     * trois a echoue renseignerait un attaquant sur ce qu'il tient deja.
     * L'appelant repond 401 dans tous les cas, et l'application se deconnecte.
     */
    @Transactional
    public Optional<RefreshOutcome> rotate(String rawRefreshToken, String deviceId) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()
                || deviceId == null || deviceId.isBlank()) {
            return Optional.empty();
        }

        UserSession session = userSessionRepository
                .findByRefreshTokenHash(hash(rawRefreshToken))
                .orElse(null);
        if (session == null) {
            return Optional.empty();
        }

        if (!deviceId.equals(session.getDeviceId())) {
            return Optional.empty();
        }

        OffsetDateTime expiresAt = session.getRefreshTokenExpiresAt();
        if (expiresAt == null || !expiresAt.isAfter(OffsetDateTime.now())) {
            // Perime: on l'efface plutot que de le laisser trainer en base,
            // ou il resterait indefiniment comme cible d'une fuite.
            clearToken(session);
            return Optional.empty();
        }

        String next = newRawToken();
        session.setRefreshTokenHash(hash(next));
        session.setRefreshTokenExpiresAt(OffsetDateTime.now().plusDays(validityDays));
        userSessionRepository.save(session);

        // Volontairement des valeurs simples et non l'entite: `user` est en
        // chargement paresseux et `open-in-view` est desactive — y toucher
        // hors de cette transaction leverait une LazyInitializationException.
        return Optional.of(new RefreshOutcome(
                session.getUserId(),
                session.getSessionVersion(),
                session.getDeviceId(),
                next));
    }

    private void clearToken(UserSession session) {
        session.setRefreshTokenHash(null);
        session.setRefreshTokenExpiresAt(null);
        userSessionRepository.save(session);
    }

    private String newRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Empreinte hexadecimale sur 64 caracteres, alignee sur la colonne. */
    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible sur cette JVM", e);
        }
    }

    /** Ce qu'il faut pour reforger un jeton d'acces, sans entite JPA attachee. */
    public record RefreshOutcome(Long userId, Long sessionVersion, String deviceId, String refreshToken) {
    }
}
