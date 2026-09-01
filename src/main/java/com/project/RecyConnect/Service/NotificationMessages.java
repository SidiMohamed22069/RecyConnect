package com.project.RecyConnect.Service;

import com.project.RecyConnect.Model.SupportedLanguage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

/**
 * Traduit un type de notification en titre et corps, dans une langue donnee.
 *
 * <p>Le point central de la traduction: avant, le texte etait redige par
 * l'appelant — le plus souvent {@code NegotiationService} — qui ne connait du
 * destinataire que son identifiant, et ne pouvait donc pas savoir dans quelle
 * langue s'adresser a lui. Ici, le type et les donnees variables voyagent
 * separement du texte, et la langue n'est appliquee qu'au dernier moment.
 *
 * <p>Ne consulte aucun depot: la langue lui est donnee. C'est ce qui permet a
 * {@code NotificationService} et a {@code FCMService} de l'utiliser sans se
 * disputer la responsabilite de charger l'utilisateur.
 */
@Slf4j
@Component
public class NotificationMessages {

    private static final String KEY_PREFIX = "notification.";
    private static final String TITLE_SUFFIX = ".title";
    private static final String BODY_SUFFIX = ".body";

    /** Remplacement d'un nom d'utilisateur introuvable. */
    private static final String FALLBACK_SENDER_KEY = "notification.fallback.sender";

    /** Remplacement de toute autre donnee manquante. */
    private static final String FALLBACK_VALUE_KEY = "notification.fallback.value";

    private final MessageSource messageSource;

    public NotificationMessages(
            @Qualifier("notificationMessageSource") MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Titre et corps d'une notification.
     *
     * @param title  jamais nul
     * @param body   jamais nul
     */
    public record Text(String title, String body) {
    }

    /**
     * Redige une notification de type {@code type} dans {@code language}.
     *
     * <p>Les {@code args} alimentent les emplacements {@code {0}}, {@code {1}}…
     * des libelles; un argument nul est remplace par un terme generique traduit
     * plutot que d'imprimer "null" sur l'ecran de quelqu'un.
     *
     * <p>Un type sans libelle ne provoque pas d'echec: il rend son propre code
     * en titre et un corps vide, et laisse une trace dans les journaux. Perdre
     * la notification serait pire que l'afficher mal — c'est souvent la seule
     * chose qui previent un vendeur qu'une offre l'attend.
     */
    public Text textFor(String type, SupportedLanguage language, Object... args) {
        SupportedLanguage effective = language == null ? SupportedLanguage.DEFAULT : language;
        Locale locale = effective.toLocale();
        Object[] safeArgs = substituteMissing(args, locale);

        String title = resolve(KEY_PREFIX + type + TITLE_SUFFIX, locale, safeArgs);
        String body = resolve(KEY_PREFIX + type + BODY_SUFFIX, locale, safeArgs);

        if (title == null) {
            log.warn("Aucun libelle pour la notification de type '{}' (langue {}).",
                    type, effective.getCode());
            return new Text(type == null ? "" : type, body == null ? "" : body);
        }
        return new Text(title, body == null ? "" : body);
    }

    /** Comme {@link #textFor}, a partir d'un code de langue brut. */
    public Text textFor(String type, String languageCode, Object... args) {
        return textFor(type, SupportedLanguage.resolve(languageCode), args);
    }

    /** Le nom a afficher quand l'auteur d'une notification est introuvable. */
    public String unknownSender(SupportedLanguage language) {
        String fallback = resolve(FALLBACK_SENDER_KEY,
                (language == null ? SupportedLanguage.DEFAULT : language).toLocale());
        return fallback == null ? "" : fallback;
    }

    /**
     * Remplace les arguments nuls par un terme generique traduit.
     *
     * <p>{@link java.text.MessageFormat} rend litteralement "null" pour un
     * argument absent. Cela s'est deja vu en production sur une annonce
     * supprimee entre l'evenement et l'envoi.
     */
    private Object[] substituteMissing(Object[] args, Locale locale) {
        if (args == null || args.length == 0) {
            return new Object[0];
        }
        Object[] copy = Arrays.copyOf(args, args.length);
        String placeholder = null;
        for (int i = 0; i < copy.length; i++) {
            if (copy[i] == null || (copy[i] instanceof String s && s.isBlank())) {
                if (placeholder == null) {
                    String resolved = resolve(FALLBACK_VALUE_KEY, locale);
                    placeholder = resolved == null ? "" : resolved;
                }
                copy[i] = placeholder;
            }
        }
        return copy;
    }

    /** Rend null plutot que de lever quand la cle n'existe pas. */
    private String resolve(String key, Locale locale, Object... args) {
        return messageSource.getMessage(key, args, null, locale);
    }
}
