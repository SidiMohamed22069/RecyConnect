package com.project.RecyConnect.Service;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.project.RecyConnect.DTO.AppVersionDTO;

import jakarta.annotation.PostConstruct;

/**
 * La politique de mise a jour servie a l'application mobile.
 *
 * <p>Elle vit dans la configuration ({@code app.version.*}) et non en base:
 * elle change au rythme des publications sur les magasins, soit quelques fois
 * par an, et la changer ne doit demander ni migration ni ecran d'administration.
 *
 * <p>Cette classe est surtout un garde-fou. Le champ {@code minimum} est le
 * seul de toute l'API capable de rendre l'application inutilisable pour tout
 * le parc installe: une faute de frappe ({@code "1.2..0"}), une valeur
 * superieure a ce qui est reellement publie sur le magasin, et plus personne
 * ne peut se connecter — sans possibilite de correction cote client. Les trois
 * regles ci-dessous existent pour cela:
 *
 * <ul>
 *   <li>une version illisible est omise de la reponse, avec un avertissement,
 *       plutot que transmise telle quelle;</li>
 *   <li>un minimum superieur a la derniere version publiee est ramene a cette
 *       derniere: exiger une version qui n'existe pas sur le magasin
 *       enfermerait l'utilisateur devant un bouton sans effet;</li>
 *   <li>une URL de magasin qui n'est pas en {@code https} est omise: c'est un
 *       lien qu'un ecran bloquant ouvre hors de l'application, sur simple
 *       appui.</li>
 * </ul>
 */
@Service
public class AppVersionService {

    private static final Logger log = LoggerFactory.getLogger(AppVersionService.class);

    private final AppVersionDTO policy;

    public AppVersionService(
            @Value("${app.version.latest:}") String latest,
            @Value("${app.version.minimum:}") String minimum,
            @Value("${app.version.android-url:}") String androidUrl,
            @Value("${app.version.ios-url:}") String iosUrl) {
        this.policy = buildPolicy(latest, minimum, androidUrl, iosUrl);
    }

    /** La politique publiee, deja validee. Jamais {@code null}. */
    public AppVersionDTO getPolicy() {
        return policy;
    }

    @PostConstruct
    void logPolicy() {
        if (policy.getMinimumVersion() == null && policy.getLatestVersion() == null) {
            log.info("Politique de version non configuree: l'application mobile ne verifiera rien "
                    + "(renseigner app.version.minimum et app.version.latest).");
            return;
        }
        log.info("Politique de version: minimum={}, derniere={}, android={}, ios={}",
                policy.getMinimumVersion(), policy.getLatestVersion(),
                policy.getAndroidUrl(), policy.getIosUrl());
    }

    /**
     * Construit la reponse a partir de la configuration brute.
     *
     * <p>Extraite du constructeur pour etre testable sans contexte Spring.
     */
    static AppVersionDTO buildPolicy(String latest, String minimum, String androidUrl, String iosUrl) {
        SemanticVersion latestVersion = parseConfigured(latest, "app.version.latest");
        SemanticVersion minimumVersion = parseConfigured(minimum, "app.version.minimum");

        if (latestVersion != null && minimumVersion != null && minimumVersion.compareTo(latestVersion) > 0) {
            log.warn("app.version.minimum ({}) depasse app.version.latest ({}): le minimum est ramene "
                    + "a la derniere version publiee, faute de quoi aucune mise a jour disponible sur "
                    + "le magasin ne debloquerait l'application.", minimumVersion, latestVersion);
            minimumVersion = latestVersion;
        }

        return new AppVersionDTO(
                latestVersion == null ? null : latestVersion.toString(),
                minimumVersion == null ? null : minimumVersion.toString(),
                sanitizeStoreUrl(androidUrl, "app.version.android-url"),
                sanitizeStoreUrl(iosUrl, "app.version.ios-url"));
    }

    private static SemanticVersion parseConfigured(String raw, String property) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        SemanticVersion parsed = SemanticVersion.parseOrNull(raw);
        if (parsed == null) {
            log.warn("{} = \"{}\" n'est pas une version semantique: le champ est omis de la reponse.",
                    property, raw);
        }
        return parsed;
    }

    /**
     * N'accepte qu'une URL {@code https} absolue portant un hote.
     *
     * <p>Meme regle que cote mobile, appliquee des la source: le client refuse
     * de toute facon les autres schemas, autant ne pas les publier.
     */
    private static String sanitizeStoreUrl(String raw, String property) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            URI uri = new URI(trimmed);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank()) {
                return trimmed;
            }
        } catch (URISyntaxException e) {
            // Traite comme une URL invalide ci-dessous.
        }
        log.warn("{} = \"{}\" n'est pas une URL https absolue: le champ est omis de la reponse.",
                property, trimmed);
        return null;
    }
}
