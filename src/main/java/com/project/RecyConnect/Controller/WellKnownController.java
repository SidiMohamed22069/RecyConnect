package com.project.RecyConnect.Controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Le fichier qui autorise Android a ouvrir les liens {@code https://.../p/123}
 * directement dans l'application, sans passer par le navigateur.
 *
 * <p>Il doit etre servi en 200, en {@code application/json}, sans redirection,
 * et porter l'empreinte SHA-256 du certificat de <em>signature</em> de
 * l'application telle que Play la publie (Play Console, "Integrite de
 * l'application") — pas celle de la cle de televersement si Play App Signing
 * est actif. Plusieurs empreintes se separent par des virgules
 * ({@code ANDROID_SHA256_FINGERPRINTS}).
 *
 * <p>Tant qu'aucune empreinte n'est configuree, le chemin repond 404 et
 * Android se rabat sur le choix navigateur / application, comme avant.
 */
@RestController
public class WellKnownController {

    private final String packageName;
    private final List<String> fingerprints;

    public WellKnownController(
            @Value("${app.android.package-name:com.recyconnect.app.neyan}") String packageName,
            @Value("${app.android.sha256-fingerprints:}") String fingerprints) {
        this.packageName = packageName;
        this.fingerprints = Arrays.stream(fingerprints.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/.well-known/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> assetLinks() {
        if (fingerprints.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String list = fingerprints.stream()
                .map(f -> "\"" + f + "\"")
                .collect(Collectors.joining(","));
        String body = "[{\"relation\":[\"delegate_permission/common.handle_all_urls\"],"
                + "\"target\":{\"namespace\":\"android_app\",\"package_name\":\"" + packageName + "\","
                + "\"sha256_cert_fingerprints\":[" + list + "]}}]";
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
