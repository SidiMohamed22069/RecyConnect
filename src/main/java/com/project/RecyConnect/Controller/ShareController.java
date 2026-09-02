package com.project.RecyConnect.Controller;

import com.project.RecyConnect.DTO.ProductDTO;
import com.project.RecyConnect.Service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * La page web d'une annonce, destinee au partage.
 *
 * <p>Le bouche-a-oreille WhatsApp est le premier canal de diffusion local, et
 * un lien qui ne mene nulle part n'est pas partage deux fois. Cette page
 * porte les balises OpenGraph qui font apparaitre le titre, le prix et la
 * photo dans la conversation, puis renvoie vers l'application — installee ou
 * non.
 *
 * <p>Publique par construction: le destinataire n'a, par definition, pas de
 * compte. Elle ne montre donc que ce que montre deja le catalogue anonyme —
 * jamais le numero du vendeur.
 *
 * <p>Le passage direct dans l'application installee demande, cote Android, un
 * fichier {@code /.well-known/assetlinks.json} signe par l'empreinte du
 * certificat de publication, et cote iOS un {@code apple-app-site-association}.
 * Tant qu'ils ne sont pas deposes, le lien de secours par schema
 * {@code recyconnect://} assure le meme service depuis la page.
 */
@RestController
@RequestMapping("/p")
public class ShareController {

    private final ProductService productService;

    public ShareController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping(value = "/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> productPage(@PathVariable Long id) {
        ProductDTO product = productService.findById(id).orElse(null);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_HTML)
                    .body(page("RecyConnect", "Annonce introuvable",
                            "Cette annonce n'existe plus.", null, null));
        }

        String title = product.getTitle() != null ? product.getTitle() : "Annonce";
        String price = product.getPrice() != null
                ? formatPrice(product.getPrice()) + " MRU"
                        + (product.getUnit() != null ? " / " + product.getUnit() : "")
                : "";
        String description = product.getDesc() != null && !product.getDesc().isBlank()
                ? product.getDesc()
                : "Disponible sur RecyConnect.";

        List<String> images = product.getImageUrls();
        String image = images != null && !images.isEmpty() ? images.get(0) : null;

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(page("RecyConnect", title, price + " — " + description, image, id));
    }

    private String formatPrice(Double price) {
        // Separateur d'espace insecable, comme la convention francaise en
        // vigueur dans l'application: "25 500" et non "25500".
        return String.format("%,.0f", price).replace(',', ' ');
    }

    private String page(String site, String title, String description,
                        String image, Long productId) {
        String deepLink = productId != null ? "recyconnect://product/" + productId : null;
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"fr\"><head><meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
            .append("<title>").append(escape(title)).append(" — ").append(escape(site)).append("</title>")
            .append("<meta property=\"og:site_name\" content=\"").append(escape(site)).append("\">")
            .append("<meta property=\"og:title\" content=\"").append(escape(title)).append("\">")
            .append("<meta property=\"og:description\" content=\"").append(escape(description)).append("\">")
            .append("<meta property=\"og:type\" content=\"product\">");
        if (image != null) {
            html.append("<meta property=\"og:image\" content=\"").append(escape(image)).append("\">");
        }
        html.append("<style>")
            .append("body{margin:0;font:16px/1.5 system-ui,sans-serif;color:#1b2a22;background:#f6f8f7;")
            .append("display:flex;min-height:100vh;align-items:center;justify-content:center;padding:24px}")
            .append(".card{max-width:420px;width:100%;background:#fff;border-radius:20px;overflow:hidden;")
            .append("box-shadow:0 10px 30px rgba(0,0,0,.08)}")
            .append(".card img{width:100%;aspect-ratio:5/3;object-fit:cover;display:block}")
            .append(".body{padding:20px}h1{font-size:20px;margin:0 0 8px}")
            .append("p{margin:0 0 16px;color:#5a6b62}")
            .append("a.btn{display:block;text-align:center;background:#56B67F;color:#fff;text-decoration:none;")
            .append("padding:14px;border-radius:14px;font-weight:600}")
            .append("</style></head><body><div class=\"card\">");
        if (image != null) {
            html.append("<img src=\"").append(escape(image)).append("\" alt=\"\">");
        }
        html.append("<div class=\"body\"><h1>").append(escape(title)).append("</h1>")
            .append("<p>").append(escape(description)).append("</p>");
        if (deepLink != null) {
            html.append("<a class=\"btn\" href=\"").append(escape(deepLink)).append("\">")
                .append("Ouvrir dans RecyConnect</a>");
        }
        html.append("</div></div></body></html>");
        return html.toString();
    }

    /**
     * Echappe le texte d'une annonce avant de l'inserer dans la page.
     *
     * <p>Titre et description sont ecrits par les utilisateurs: sans cet
     * echappement, un titre contenant une balise script s'executerait chez
     * quiconque ouvre le lien partage.
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
