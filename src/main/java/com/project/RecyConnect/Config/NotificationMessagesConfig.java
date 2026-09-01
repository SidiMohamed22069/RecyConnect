package com.project.RecyConnect.Config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

/**
 * Source des libelles de notification.
 *
 * <p>Bean nomme, et non {@code messageSource}: ce nom-la est celui que Spring
 * reserve aux messages du framework (validation, securite). Le reprendre
 * ferait dependre les libelles de l'application d'une convention qui ne lui
 * appartient pas, et inversement.
 *
 * <p>Declare ici plutot que par {@code spring.messages.*} parce que le
 * comportement decrit ci-dessous doit tenir en test comme en production, alors
 * qu'{@code application.properties} n'est pas versionne.
 */
@Configuration
public class NotificationMessagesConfig {

    /** Racine des fichiers de libelles, sans le suffixe de langue. */
    public static final String BASENAME = "classpath:i18n/notifications";

    @Bean
    public MessageSource notificationMessageSource() {
        ReloadableResourceBundleMessageSource source =
                new ReloadableResourceBundleMessageSource();

        source.setBasename(BASENAME);

        // Les libelles arabes sont illisibles en ISO-8859-1, qui reste le
        // defaut historique de java.util.Properties.
        source.setDefaultEncoding("UTF-8");

        // Sans cela, une cle absente de notifications_en.properties serait
        // cherchee dans la langue du serveur avant de retomber sur le fichier
        // de base. Un serveur configure en anglais et un autre en francais ne
        // rendraient alors pas le meme texte.
        source.setFallbackToSystemLocale(false);

        // Une cle inconnue doit se signaler comme absente, pas se faire passer
        // pour un libelle en rendant son propre nom.
        source.setUseCodeAsDefaultMessage(false);

        // Les fichiers sont dans le jar: ils ne changeront pas en cours
        // d'execution, inutile de les relire.
        source.setCacheSeconds(-1);

        return source;
    }
}
