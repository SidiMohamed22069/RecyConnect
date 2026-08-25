package com.project.RecyConnect.Service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Les photos d'un compte supprime doivent quitter le disque : laissees la,
 * elles resteraient accessibles par leur URL alors que le compte n'existe plus.
 */
class UploadedFileStoreTest {

    @Test
    @DisplayName("le nom de fichier est tire d'une URL absolue")
    void extraitLeNomDUneUrlAbsolue() {
        assertEquals("photo.jpg",
                UploadedFileStore.extractFilename("https://exemple.mr/api/files/photo.jpg"));
    }

    @Test
    @DisplayName("une URL d'un ancien serveur donne le meme nom")
    void extraitLeNomDUnAncienServeur() {
        assertEquals("photo.jpg",
                UploadedFileStore.extractFilename("http://149.202.43.173:8081/api/files/photo.jpg"));
    }

    @Test
    @DisplayName("un nom nu est accepte tel quel")
    void accepteUnNomNu() {
        assertEquals("photo.jpg", UploadedFileStore.extractFilename("photo.jpg"));
    }

    @Test
    @DisplayName("la chaine de requete ne fait pas partie du nom")
    void ignoreLaChaineDeRequete() {
        assertEquals("photo.jpg",
                UploadedFileStore.extractFilename("/api/files/photo.jpg?v=2"));
    }

    @Test
    @DisplayName("un nom qui sort du dossier est refuse")
    void refuseUneRemonteeDeChemin() {
        assertNull(UploadedFileStore.extractFilename("/api/files/../../etc/passwd"));
        assertNull(UploadedFileStore.extractFilename("dossier/photo.jpg"));
        assertNull(UploadedFileStore.extractFilename("   "));
        assertNull(UploadedFileStore.extractFilename(null));
    }

    @Test
    @DisplayName("le fichier est reellement efface du dossier d'upload")
    void effaceLeFichier(@TempDir Path uploads) throws IOException {
        Path photo = uploads.resolve("photo.jpg");
        Files.writeString(photo, "contenu");
        UploadedFileStore store = new UploadedFileStore(uploads.toString());

        assertTrue(store.deleteByPublicUrl("https://exemple.mr/api/files/photo.jpg"));
        assertFalse(Files.exists(photo));
    }

    @Test
    @DisplayName("un fichier deja absent n'est pas une erreur")
    void tolereUnFichierAbsent(@TempDir Path uploads) {
        UploadedFileStore store = new UploadedFileStore(uploads.toString());
        assertFalse(store.deleteByPublicUrl("/api/files/inexistant.jpg"));
    }

    @Test
    @DisplayName("le compte rendu porte le nombre de fichiers reellement effaces")
    void compteLesFichiersEffaces(@TempDir Path uploads) throws IOException {
        Files.writeString(uploads.resolve("a.jpg"), "a");
        Files.writeString(uploads.resolve("b.jpg"), "b");
        UploadedFileStore store = new UploadedFileStore(uploads.toString());

        int deleted = store.deleteAllByPublicUrl(List.of(
                "/api/files/a.jpg", "/api/files/b.jpg", "/api/files/absent.jpg"));

        assertEquals(2, deleted);
    }
}
