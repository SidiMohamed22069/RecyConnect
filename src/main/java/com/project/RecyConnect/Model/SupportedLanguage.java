package com.project.RecyConnect.Model;

import java.util.Locale;
import java.util.Optional;

/**
 * Les langues dans lesquelles l'application sait s'adresser a un utilisateur.
 *
 * <p>La liste reprend celle que {@link Category#getLocalizedName(String)} sert
 * deja au catalogue: le mobile ne doit pas avoir a connaitre deux ensembles de
 * codes selon qu'il lit une categorie ou recoit une notification.
 *
 * <p>Le francais est le recours. Un compte cree avant l'ajout de la colonne, un
 * code inconnu, une valeur vide: dans les trois cas on retombe ici plutot que
 * d'echouer, parce qu'une notification muette est pire qu'une notification dans
 * la mauvaise langue.
 */
public enum SupportedLanguage {

    FR("fr"),
    AR("ar"),
    EN("en");

    /** Langue servie a defaut de preference exploitable. */
    public static final SupportedLanguage DEFAULT = FR;

    private final String code;

    SupportedLanguage(String code) {
        this.code = code;
    }

    /** Le code tel qu'il est stocke en base et echange avec le mobile. */
    public String getCode() {
        return code;
    }

    /**
     * Le {@link Locale} correspondant, pour interroger un {@code MessageSource}.
     */
    public Locale toLocale() {
        return Locale.forLanguageTag(code);
    }

    /**
     * Reconnait un code fourni par un client.
     *
     * <p>Tolere la casse et les espaces, ainsi que les etiquettes regionales
     * ({@code fr-FR}, {@code ar_MR}): le mobile transmet parfois le locale du
     * systeme tel quel. Rend {@link Optional#empty()} pour tout le reste — le
     * point d'entree HTTP en fait un 400, la ou la resolution des notifications
     * prefere {@link #resolve(String)}.
     */
    public static Optional<SupportedLanguage> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        int separator = normalized.indexOf('-');
        if (separator > 0) {
            normalized = normalized.substring(0, separator);
        }
        for (SupportedLanguage language : values()) {
            if (language.code.equals(normalized)) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }

    /** Comme {@link #parse(String)}, mais retombe sur le francais. */
    public static SupportedLanguage resolve(String value) {
        return parse(value).orElse(DEFAULT);
    }

    /** La langue d'un utilisateur, francais compris pour un compte inconnu. */
    public static SupportedLanguage of(User user) {
        return user == null ? DEFAULT : resolve(user.getPreferredLanguage());
    }

    /** Les codes acceptes, pour les messages d'erreur et la documentation. */
    public static String supportedCodes() {
        return FR.code + ", " + AR.code + ", " + EN.code;
    }
}
