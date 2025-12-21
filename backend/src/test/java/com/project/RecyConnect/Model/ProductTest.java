package com.project.RecyConnect.Model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductTest {

    private Product product;
    private User user;
    private Category category;
    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        now = OffsetDateTime.now();
        
        user = User.builder()
                .id(1L)
                .username("testuser")
                .build();
        
        category = Category.builder()
                .id(1L)
                .name("Electronics")
                .build();
        
        product = Product.builder()
                .id(1L)
                .createdAt(now)
                .title("Test Product")
                .description("Test Description")
                .price(100.0)
                .unit("kg")
                .quantityTotal(50L)
                .quantityAvailable(40L)
                .status("available")
                .imageUrls(Arrays.asList("/images/1.jpg", "/images/2.jpg"))
                .category(category)
                .user(user)
                .build();
    }

    // ==================== Tests pour getId() ====================

    @Test
    void testGetId_ReturnsCorrectId() {
        assertEquals(1L, product.getId());
    }

    @Test
    void testGetId_WhenNull() {
        Product emptyProduct = new Product();
        assertNull(emptyProduct.getId());
    }

    // ==================== Tests pour getTitle() ====================

    @Test
    void testGetTitle_ReturnsCorrectTitle() {
        assertEquals("Test Product", product.getTitle());
    }

    @Test
    void testGetTitle_WhenNull() {
        Product emptyProduct = new Product();
        assertNull(emptyProduct.getTitle());
    }

    @Test
    void testGetTitle_WithEmptyString() {
        Product p = Product.builder().title("").build();
        assertEquals("", p.getTitle());
    }

    // ==================== Tests pour getDescription() ====================

    @Test
    void testGetDescription_ReturnsCorrectDescription() {
        assertEquals("Test Description", product.getDescription());
    }

    @Test
    void testGetDescription_WhenNull() {
        Product emptyProduct = new Product();
        assertNull(emptyProduct.getDescription());
    }

    // ==================== Tests pour getPrice() ====================

    @Test
    void testGetPrice_ReturnsCorrectPrice() {
        assertEquals(100.0, product.getPrice());
    }

    @Test
    void testGetPrice_WhenNull() {
        Product emptyProduct = new Product();
        assertNull(emptyProduct.getPrice());
    }

    @Test
    void testGetPrice_WithZero() {
        Product p = Product.builder().price(0.0).build();
        assertEquals(0.0, p.getPrice());
    }

    @Test
    void testGetPrice_WithNegative() {
        Product p = Product.builder().price(-10.0).build();
        assertEquals(-10.0, p.getPrice());
    }

    // ==================== Tests pour getUnit() ====================

    @Test
    void testGetUnit_ReturnsCorrectUnit() {
        assertEquals("kg", product.getUnit());
    }

    @Test
    void testGetUnit_WhenNull() {
        Product emptyProduct = new Product();
        assertNull(emptyProduct.getUnit());
    }

    // ==================== Tests pour getQuantityTotal() ====================

    @Test
    void testGetQuantityTotal_ReturnsCorrectValue() {
        assertEquals(50L, product.getQuantityTotal());
    }

    @Test
    void testGetQuantityTotal_WhenNull() {
        Product emptyProduct = new Product();
        assertNull(emptyProduct.getQuantityTotal());
    }

    // ==================== Tests pour getQuantityAvailable() ====================

    @Test
    void testGetQuantityAvailable_ReturnsCorrectValue() {
        assertEquals(40L, product.getQuantityAvailable());
    }

    @Test
    void testGetQuantityAvailable_WhenNull() {
        Product emptyProduct = new Product();
        assertNull(emptyProduct.getQuantityAvailable());
    }

    // ==================== Tests pour getStatus() ====================

    @Test
    void testGetStatus_ReturnsCorrectStatus() {
        assertEquals("available", product.getStatus());
    }

    @Test
    void testGetStatus_WhenNull() {
        Product emptyProduct = new Product();
        assertNull(emptyProduct.getStatus());
    }

    // ==================== Tests pour getImageUrls() ====================

    @Test
    void testGetImageUrls_ReturnsCorrectList() {
        List<String> urls = product.getImageUrls();
        assertNotNull(urls);
        assertEquals(2, urls.size());
        assertEquals("/images/1.jpg", urls.get(0));
        assertEquals("/images/2.jpg", urls.get(1));
    }

    @Test
    void testGetImageUrls_WhenNull() {
        Product emptyProduct = new Product();
        assertNull(emptyProduct.getImageUrls());
    }

    @Test
    void testGetImageUrls_WhenEmpty() {
        Product p = Product.builder().imageUrls(new ArrayList<>()).build();
        assertNotNull(p.getImageUrls());
        assertTrue(p.getImageUrls().isEmpty());
    }

    // ==================== Tests pour getCreatedAt() ====================

    @Test
    void testGetCreatedAt_ReturnsCorrectValue() {
        assertEquals(now, product.getCreatedAt());
    }

    @Test
    void testGetCreatedAt_WhenNull() {
        Product emptyProduct = new Product();
        assertNull(emptyProduct.getCreatedAt());
    }

    // ==================== Tests pour getCategory() ====================

    @Test
    void testGetCategory_ReturnsCorrectCategory() {
        assertNotNull(product.getCategory());
        assertEquals(1L, product.getCategory().getId());
        assertEquals("Electronics", product.getCategory().getName());
    }

    @Test
    void testGetCategory_WhenNull() {
        Product emptyProduct = new Product();
        assertNull(emptyProduct.getCategory());
    }

    // ==================== Tests pour getUser() ====================

    @Test
    void testGetUser_ReturnsCorrectUser() {
        assertNotNull(product.getUser());
        assertEquals(1L, product.getUser().getId());
        assertEquals("testuser", product.getUser().getUsername());
    }

    @Test
    void testGetUser_WhenNull() {
        Product emptyProduct = new Product();
        assertNull(emptyProduct.getUser());
    }

    // ==================== Tests pour Setters ====================

    @Test
    void testSetId() {
        product.setId(99L);
        assertEquals(99L, product.getId());
    }

    @Test
    void testSetTitle() {
        product.setTitle("New Title");
        assertEquals("New Title", product.getTitle());
    }

    @Test
    void testSetDescription() {
        product.setDescription("New Description");
        assertEquals("New Description", product.getDescription());
    }

    @Test
    void testSetPrice() {
        product.setPrice(200.0);
        assertEquals(200.0, product.getPrice());
    }

    @Test
    void testSetUnit() {
        product.setUnit("piece");
        assertEquals("piece", product.getUnit());
    }

    @Test
    void testSetQuantityTotal() {
        product.setQuantityTotal(100L);
        assertEquals(100L, product.getQuantityTotal());
    }

    @Test
    void testSetQuantityAvailable() {
        product.setQuantityAvailable(80L);
        assertEquals(80L, product.getQuantityAvailable());
    }

    @Test
    void testSetStatus() {
        product.setStatus("sold");
        assertEquals("sold", product.getStatus());
    }

    @Test
    void testSetImageUrls() {
        List<String> newUrls = Arrays.asList("/new1.jpg", "/new2.jpg", "/new3.jpg");
        product.setImageUrls(newUrls);
        assertEquals(3, product.getImageUrls().size());
    }

    @Test
    void testSetCreatedAt() {
        OffsetDateTime newTime = OffsetDateTime.now().plusDays(1);
        product.setCreatedAt(newTime);
        assertEquals(newTime, product.getCreatedAt());
    }

    @Test
    void testSetCategory() {
        Category newCategory = Category.builder().id(2L).name("New Category").build();
        product.setCategory(newCategory);
        assertEquals(2L, product.getCategory().getId());
    }

    @Test
    void testSetUser() {
        User newUser = User.builder().id(2L).username("newuser").build();
        product.setUser(newUser);
        assertEquals(2L, product.getUser().getId());
    }

    // ==================== Tests pour Builder ====================

    @Test
    void testBuilder_CreatesProductWithAllFields() {
        assertNotNull(product);
        assertEquals(1L, product.getId());
        assertEquals("Test Product", product.getTitle());
        assertEquals("Test Description", product.getDescription());
        assertEquals(100.0, product.getPrice());
        assertEquals("kg", product.getUnit());
        assertEquals(50L, product.getQuantityTotal());
        assertEquals(40L, product.getQuantityAvailable());
        assertEquals("available", product.getStatus());
    }

    @Test
    void testBuilder_CreatesProductWithPartialFields() {
        Product partial = Product.builder()
                .title("Partial")
                .price(50.0)
                .build();
        
        assertEquals("Partial", partial.getTitle());
        assertEquals(50.0, partial.getPrice());
        assertNull(partial.getId());
        assertNull(partial.getDescription());
    }

    // ==================== Tests pour NoArgsConstructor ====================

    @Test
    void testNoArgsConstructor() {
        Product emptyProduct = new Product();
        assertNotNull(emptyProduct);
        assertNull(emptyProduct.getId());
        assertNull(emptyProduct.getTitle());
    }

    // ==================== Tests pour equals et hashCode ====================

    @Test
    void testEquals_SameObject() {
        assertEquals(product, product);
    }

    @Test
    void testEquals_NullObject() {
        assertNotEquals(product, null);
    }

    @Test
    void testHashCode_NotNull() {
        assertNotNull(product.hashCode());
    }

    // ==================== Tests pour toString ====================

    @Test
    void testToString_ContainsTitle() {
        String str = product.toString();
        assertTrue(str.contains("Test Product"));
    }

    @Test
    void testToString_NotNull() {
        assertNotNull(product.toString());
    }
}
