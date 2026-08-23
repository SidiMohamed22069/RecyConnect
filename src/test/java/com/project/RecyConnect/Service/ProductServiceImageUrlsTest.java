package com.project.RecyConnect.Service;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.project.RecyConnect.DTO.ProductDTO;
import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Model.ProductStatus;
import com.project.RecyConnect.Model.Role;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.NotificationRepository;
import com.project.RecyConnect.Repository.ProductRepository;
import com.project.RecyConnect.Repository.UserRepo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Non-regression: {@code Product.imageUrls} est une @ElementCollection lazy et
 * {@code spring.jpa.open-in-view} est desactive. Sans transaction autour du
 * mapping vers le DTO, la lecture des photos levait une
 * {@code LazyInitializationException} sur toutes les routes produits.
 *
 * <p>Les tests ne portent volontairement pas {@code @Transactional}: c'est le
 * fait d'appeler le service depuis l'exterieur d'une transaction, comme le fait
 * le controleur, qui reproduit le bug.
 */
@SpringBootTest
class ProductServiceImageUrlsTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepo userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private Long productId;
    private Long userId;

    @BeforeEach
    void seedProductWithImages() {
        User owner = userRepository.save(User.builder()
                .username("Vendeur Photos")
                .phone(22299001L)
                .pwd("hash")
                .role(Role.USER)
                .build());
        userId = owner.getId();

        Product product = productRepository.save(Product.builder()
                .title("Bouteilles en plastique")
                .description("Lot de bouteilles triees")
                .price(150.0)
                .unit("kg")
                .quantityTotal(20L)
                .quantityAvailable(20L)
                .status(ProductStatus.AVAILABLE)
                .imageUrls(List.of("http://ancien-hote:8081/api/files/photo-1.jpg",
                        "http://ancien-hote:8081/api/files/photo-2.jpg"))
                .user(owner)
                .build());
        productId = product.getId();
    }

    @AfterEach
    void cleanUp() {
        productRepository.deleteById(productId);
        notificationRepository.deleteAll(notificationRepository.findByReceiverId(userId));
        notificationRepository.deleteAll(notificationRepository.findBySenderId(userId));
        userRepository.deleteById(userId);
    }

    @Test
    @DisplayName("findAll expose les photos sans LazyInitializationException")
    void findAllLoadsImageUrls() {
        ProductDTO dto = productService.findAll().stream()
                .filter(p -> productId.equals(p.getId()))
                .findFirst()
                .orElse(null);

        assertNotNull(dto, "l'annonce seedee doit etre listee");
        assertEquals(2, dto.getImageUrls().size());
        // L'hote enregistre en base est reecrit vers app.server.url.
        assertTrue(dto.getImageUrls().get(0).endsWith(FileUrlService.FILES_PATH + "photo-1.jpg"),
                "URL inattendue: " + dto.getImageUrls().get(0));
    }

    @Test
    @DisplayName("findById expose les photos sans LazyInitializationException")
    void findByIdLoadsImageUrls() {
        ProductDTO dto = productService.findById(productId).orElse(null);

        assertNotNull(dto);
        assertEquals(2, dto.getImageUrls().size());
    }

    @Test
    @DisplayName("findByUserId expose les photos sans LazyInitializationException")
    void findByUserIdLoadsImageUrls() {
        List<ProductDTO> dtos = productService.findByUserId(userId);

        assertEquals(1, dtos.size());
        assertEquals(2, dtos.get(0).getImageUrls().size());
    }

    @Test
    @DisplayName("search expose les photos sans LazyInitializationException")
    void searchLoadsImageUrls() {
        ProductDTO dto = productService.search("bouteilles", null, null).stream()
                .filter(p -> productId.equals(p.getId()))
                .findFirst()
                .orElse(null);

        assertNotNull(dto);
        assertEquals(2, dto.getImageUrls().size());
    }

    @Test
    @DisplayName("update remplace la liste de photos")
    void updateReplacesImageUrls() {
        ProductDTO dto = new ProductDTO();
        dto.setTitle("Bouteilles en plastique");
        dto.setDesc("Lot de bouteilles triees");
        dto.setPrice(150.0);
        dto.setUnit("kg");
        dto.setQuantityTotal(20L);
        dto.setQuantityAvailable(20L);
        dto.setStatus(ProductStatus.AVAILABLE);
        dto.setImageUrls(new ArrayList<>(List.of("http://ancien-hote:8081/api/files/photo-3.jpg")));

        ProductDTO updated = productService.update(productId, dto);

        assertEquals(1, updated.getImageUrls().size());
        assertTrue(updated.getImageUrls().get(0).endsWith(FileUrlService.FILES_PATH + "photo-3.jpg"),
                "URL inattendue: " + updated.getImageUrls().get(0));
        // Relecture: les anciennes lignes product_images ont bien ete remplacees.
        assertEquals(1, productService.findById(productId).orElseThrow().getImageUrls().size());
    }

    @Test
    @DisplayName("patch sans photos conserve celles deja enregistrees")
    void patchWithoutImageUrlsKeepsThem() {
        ProductDTO dto = new ProductDTO();
        dto.setPrice(175.0);

        ProductDTO patched = productService.patch(productId, dto);

        assertEquals(175.0, patched.getPrice());
        assertEquals(2, patched.getImageUrls().size());
    }
}
