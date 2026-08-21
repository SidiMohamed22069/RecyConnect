package com.project.RecyConnect.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Construit les URL publiques des fichiers servis par {@code /api/files}.
 *
 * <p>Les URL des photos sont enregistrees en base <em>en absolu</em> au moment
 * du televersement. Le jour ou le serveur change d'adresse, toutes les lignes
 * deja enregistrees pointent vers l'ancien hote, devenu injoignable : plus
 * aucune photo ne s'affiche, ni dans l'application mobile ni dans l'admin.
 *
 * <p>Cette classe est le seul endroit qui sait fabriquer une URL de fichier.
 * A la lecture, {@link #toPublicUrl(String)} reecrit systematiquement l'hote
 * vers celui configure dans {@code app.server.url} : les anciennes lignes
 * redeviennent valides sans migration de base, et un futur demenagement du
 * serveur ne cassera plus rien.
 */
@Service
public class FileUrlService {

    /** Prefixe de l'endpoint qui sert les fichiers (cf. FileController). */
    public static final String FILES_PATH = "/api/files/";

    private final String publicBaseUrl;

    public FileUrlService(@Value("${app.server.url}") String serverUrl,
                          @Value("${server.port:8081}") String serverPort) {
        this.publicBaseUrl = buildBaseUrl(serverUrl, serverPort);
    }

    /**
     * Base publique de l'API, sans slash final (ex. {@code http://1.2.3.4:8081}).
     *
     * <p>Le port n'est ajoute que s'il manque : {@code app.server.url} peut
     * deja le contenir, ou designer un reverse proxy HTTPS sur le port 443.
     */
    private static String buildBaseUrl(String serverUrl, String serverPort) {
        String base = serverUrl == null ? "" : serverUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.isEmpty()) {
            return "";
        }
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://" + base;
        }
        try {
            URI uri = new URI(base);
            if (uri.getPort() != -1 || "https".equals(uri.getScheme())) {
                return base;
            }
        } catch (URISyntaxException e) {
            return base;
        }
        return base + ":" + serverPort;
    }

    /** Base publique de l'API, sans slash final. */
    public String getBaseUrl() {
        return publicBaseUrl;
    }

    /** URL publique du fichier stocke sous {@code filename}. */
    public String urlForFilename(String filename) {
        return publicBaseUrl + FILES_PATH + filename;
    }

    /**
     * Reecrit une valeur enregistree en base vers l'hote courant.
     *
     * <p>Accepte tout ce qui a pu etre stocke au fil des versions : URL absolue
     * vers un ancien hote, {@code http://localhost:8081/...}, chemin relatif ou
     * simple nom de fichier. Les valeurs qui ne designent pas un fichier de
     * l'API (image externe, {@code data:} URI) sont laissees intactes.
     */
    public String toPublicUrl(String storedValue) {
        if (storedValue == null) {
            return null;
        }
        String value = storedValue.trim();
        if (value.isEmpty()) {
            return storedValue;
        }

        int index = value.indexOf(FILES_PATH);
        if (index >= 0) {
            return urlForFilename(value.substring(index + FILES_PATH.length()));
        }

        // Ressource hebergee ailleurs : on ne la touche pas.
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("data:")) {
            return storedValue;
        }

        // Chemin relatif ou nom de fichier nu : seul le dernier segment compte,
        // les fichiers sont tous a plat dans le dossier d'upload.
        int lastSlash = value.lastIndexOf('/');
        String filename = lastSlash >= 0 ? value.substring(lastSlash + 1) : value;
        return filename.isEmpty() ? storedValue : urlForFilename(filename);
    }

    /** Version liste de {@link #toPublicUrl(String)}. */
    public List<String> toPublicUrls(List<String> storedValues) {
        if (storedValues == null) {
            return null;
        }
        return storedValues.stream().map(this::toPublicUrl).collect(Collectors.toList());
    }
}
