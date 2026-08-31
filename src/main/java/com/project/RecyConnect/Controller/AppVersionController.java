package com.project.RecyConnect.Controller;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.RecyConnect.DTO.AppVersionDTO;
import com.project.RecyConnect.Service.AppVersionService;

/**
 * Politique de version de l'application mobile.
 *
 * <p>Public et sans jeton, a dessein: la verification a lieu au demarrage,
 * avant toute connexion. Derriere une authentification, une version devenue
 * incompatible avec le contrat de l'API — celle qui ne sait plus se connecter —
 * serait justement celle qui ne pourrait pas apprendre qu'elle doit se mettre a
 * jour.
 *
 * <p>La reponse ne contient que des donnees deja publiques (numeros de version
 * et adresses de fiches de magasin): la rendre anonyme n'expose rien.
 */
@RestController
@RequestMapping("/api/app")
public class AppVersionController {

    private final AppVersionService service;

    public AppVersionController(AppVersionService service) {
        this.service = service;
    }

    /**
     * {@code GET /api/app/version}
     *
     * <pre>
     * {
     *   "latestVersion": "1.3.0",
     *   "minimumVersion": "1.2.0",
     *   "androidUrl": "https://play.google.com/store/apps/details?id=com.recyconnect.app.neyan",
     *   "iosUrl": "https://apps.apple.com/app/id0000000000"
     * }
     * </pre>
     *
     * <p>Cinq minutes de cache: la politique change quelques fois par an, et
     * chaque demarrage d'application produit cet appel. Le delai reste assez
     * court pour qu'une mise a jour rendue obligatoire en urgence se propage
     * dans la foulee.
     */
    @GetMapping("/version")
    public ResponseEntity<AppVersionDTO> version() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(service.getPolicy());
    }
}
