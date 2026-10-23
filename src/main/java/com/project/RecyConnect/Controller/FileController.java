package com.project.RecyConnect.Controller;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.project.RecyConnect.Service.FileUrlService;

import jakarta.annotation.PostConstruct;

/**
 * Photos des annonces.
 *
 * <p>Seules des images sont acceptees, reconnues par leur signature binaire
 * et non par l'extension ou le {@code Content-Type} annonces par le client.
 * L'extension enregistree decoule du type detecte. A la lecture, le type
 * renvoye decoule de l'extension enregistree et {@code nosniff} interdit au
 * navigateur de deviner autre chose : un fichier HTML ou SVG televerse
 * devenait auparavant une page executee sur le domaine de l'API, celui des
 * pages legales et des liens partages.
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    /** Extension enregistree -> type MIME servi. */
    private static final Map<String, MediaType> IMAGE_TYPES = Map.of(
            "jpg", MediaType.IMAGE_JPEG,
            "jpeg", MediaType.IMAGE_JPEG,
            "png", MediaType.IMAGE_PNG,
            "gif", MediaType.IMAGE_GIF,
            "webp", MediaType.parseMediaType("image/webp"));

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    private final FileUrlService fileUrlService;

    private Path uploadPath;

    public FileController(FileUrlService fileUrlService) {
        this.fileUrlService = fileUrlService;
    }

    @PostConstruct
    public void init() {
        uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory", e);
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Please select a file to upload"));
        }
        try {
            String extension = detectImageExtension(file);
            if (extension == null) {
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                        .body(Map.of("message", "Seules les images JPEG, PNG, WebP ou GIF sont acceptees"));
            }
            return ResponseEntity.ok(store(file, extension));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to upload file"));
        }
    }

    @PostMapping("/upload-multiple")
    public ResponseEntity<?> uploadMultipleFiles(@RequestParam("files") MultipartFile[] files) {
        List<FileUploadResponse> responses = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            try {
                String extension = detectImageExtension(file);
                if (extension == null) {
                    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                            .body(Map.of("message", "Seules les images JPEG, PNG, WebP ou GIF sont acceptees"));
                }
                responses.add(store(file, extension));
            } catch (IOException e) {
                // Continue with other files
            }
        }
        return ResponseEntity.ok().body(responses);
    }

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> getFile(@PathVariable String filename) {
        try {
            Path filePath = uploadPath.resolve(filename).normalize();
            if (!filePath.startsWith(uploadPath)) {
                return ResponseEntity.badRequest().build();
            }
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }
            MediaType type = IMAGE_TYPES.get(extensionOf(filename));
            ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                    .header("X-Content-Type-Options", "nosniff");
            if (type != null) {
                return builder.contentType(type)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                        .body(resource);
            }
            // Fichier d'un autre type, televerse avant la liste blanche : on le
            // sert comme piece jointe opaque, jamais comme page.
            return builder.contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Suppression directe d'un fichier : administration seulement. Les noms
     * sont publics (ils figurent dans {@code imageUrls}) et rien ne liait
     * l'appelant au fichier. La suppression d'une annonce ou d'un compte fait
     * deja le menage de ses photos.
     */
    @DeleteMapping("/{filename:.+}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteFile(@PathVariable String filename) {
        try {
            Path filePath = uploadPath.resolve(filename).normalize();
            if (!filePath.startsWith(uploadPath)) {
                return ResponseEntity.badRequest().build();
            }
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                return ResponseEntity.ok().body(Map.of("message", "File deleted successfully"));
            }
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to delete file"));
        }
    }

    // ------------------------------------------------------------------

    private FileUploadResponse store(MultipartFile file, String extension) throws IOException {
        String newFilename = UUID.randomUUID() + "." + extension;
        Path targetLocation = uploadPath.resolve(newFilename);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        return new FileUploadResponse(fileUrlService.urlForFilename(newFilename), newFilename);
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * L'extension a enregistrer d'apres les premiers octets, ou {@code null}
     * si ce n'est pas une image acceptee.
     */
    static String detectImageExtension(MultipartFile file) throws IOException {
        byte[] head = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        }
        return detectImageExtension(Arrays.copyOf(head, Math.max(read, 0)));
    }

    static String detectImageExtension(byte[] head) {
        if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (head.length >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G'
                && head[4] == 0x0D && head[5] == 0x0A && head[6] == 0x1A && head[7] == 0x0A) {
            return "png";
        }
        if (head.length >= 6 && head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8'
                && (head[4] == '7' || head[4] == '9') && head[5] == 'a') {
            return "gif";
        }
        if (head.length >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && new String(head, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
            return "webp";
        }
        return null;
    }

    // Response class for file uploads
    public static class FileUploadResponse {
        private String url;
        private String filename;

        public FileUploadResponse(String url, String filename) {
            this.url = url;
            this.filename = filename;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }
    }
}
