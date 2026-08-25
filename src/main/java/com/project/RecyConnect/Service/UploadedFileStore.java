package com.project.RecyConnect.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

/**
 * Les fichiers televerses, vus depuis le disque.
 *
 * <p>{@link com.project.RecyConnect.Controller.FileController} sait les ecrire
 * et les servir ; cette classe sait les effacer, ce dont la suppression de
 * compte a besoin. Les deux lisent la meme propriete {@code file.upload-dir} :
 * elles doivent designer le meme dossier.
 *
 * <p>Une photo qui resterait sur le disque apres la suppression du compte
 * qu'elle illustrait resterait accessible par son URL — c'est exactement ce
 * que le reglement "Donnees utilisateur" de Play interdit.
 */
@Service
public class UploadedFileStore {

    private static final Logger log = LoggerFactory.getLogger(UploadedFileStore.class);

    private final Path uploadPath;

    public UploadedFileStore(@Value("${file.upload-dir:uploads}") String uploadDir) {
        this.uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * Efface le fichier designe par une URL publique.
     *
     * @return {@code true} si un fichier a bien ete efface.
     */
    public boolean deleteByPublicUrl(String url) {
        String filename = extractFilename(url);
        if (filename == null) {
            return false;
        }
        try {
            Path filePath = uploadPath.resolve(filename).normalize();
            // Un nom de fichier venu de la base ne doit pas pouvoir sortir du
            // dossier d'upload, quelle qu'ait ete son origine.
            if (!filePath.startsWith(uploadPath)) {
                log.warn("Chemin de fichier hors du dossier d'upload, ignore");
                return false;
            }
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            // Un fichier qui resiste ne doit pas faire echouer la suppression
            // du compte : la ligne en base, elle, est bien partie.
            log.warn("Fichier non efface: {}", e.getMessage());
            return false;
        }
    }

    /** Efface une serie d'URL, et rend le nombre de fichiers reellement effaces. */
    public int deleteAllByPublicUrl(Collection<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return 0;
        }
        int deleted = 0;
        for (String url : urls) {
            if (deleteByPublicUrl(url)) {
                deleted++;
            }
        }
        return deleted;
    }

    /**
     * Le nom de fichier contenu dans une URL, ou {@code null} si l'URL ne
     * designe pas un fichier servi par cette API.
     *
     * <p>Les URL sont enregistrees en absolu et peuvent porter l'adresse d'un
     * ancien serveur : seul le segment qui suit {@code /api/files/} compte.
     */
    static String extractFilename(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String value = url.trim();
        int index = value.indexOf(FileUrlService.FILES_PATH);
        String filename = index >= 0
                ? value.substring(index + FileUrlService.FILES_PATH.length())
                : value;

        // Une eventuelle chaine de requete ne fait pas partie du nom.
        int query = filename.indexOf('?');
        if (query >= 0) {
            filename = filename.substring(0, query);
        }
        filename = filename.trim();

        if (filename.isEmpty() || filename.contains("/") || filename.contains("\\")
                || filename.contains("..")) {
            return null;
        }
        return filename;
    }
}
