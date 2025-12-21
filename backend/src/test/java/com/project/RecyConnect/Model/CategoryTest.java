package com.project.RecyConnect.Model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CategoryTest {

    private Category category;
    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        now = OffsetDateTime.now();
        
        category = Category.builder()
                .id(1L)
                .createdAt(now)
                .name("Electronics")
                .description("Electronic devices and accessories")
                .build();
    }

    // ==================== Tests pour getId() ====================

    @Test
    void testGetId_ReturnsCorrectId() {
        assertEquals(1L, category.getId());
    }

    @Test
    void testGetId_WhenNull() {
        Category empty = new Category();
        assertNull(empty.getId());
    }

    // ==================== Tests pour getName() ====================

    @Test
    void testGetName_ReturnsCorrectName() {
        assertEquals("Electronics", category.getName());
    }

    @Test
    void testGetName_WhenNull() {
        Category empty = new Category();
        assertNull(empty.getName());
    }

    @Test
    void testGetName_WithEmptyString() {
        Category c = Category.builder().name("").build();
        assertEquals("", c.getName());
    }

    // ==================== Tests pour getDescription() ====================

    @Test
    void testGetDescription_ReturnsCorrectDescription() {
        assertEquals("Electronic devices and accessories", category.getDescription());
    }

    @Test
    void testGetDescription_WhenNull() {
        Category empty = new Category();
        assertNull(empty.getDescription());
    }

    // ==================== Tests pour getCreatedAt() ====================

    @Test
    void testGetCreatedAt_ReturnsCorrectValue() {
        assertEquals(now, category.getCreatedAt());
    }

    @Test
    void testGetCreatedAt_WhenNull() {
        Category empty = new Category();
        assertNull(empty.getCreatedAt());
    }

    // ==================== Tests pour getProducts() ====================

    @Test
    void testGetProducts_WhenNull() {
        assertNull(category.getProducts());
    }

    @Test
    void testGetProducts_WhenSet() {
        List<Product> products = new ArrayList<>();
        products.add(Product.builder().id(1L).title("Product 1").build());
        products.add(Product.builder().id(2L).title("Product 2").build());
        
        category.setProducts(products);
        
        assertNotNull(category.getProducts());
        assertEquals(2, category.getProducts().size());
    }

    @Test
    void testGetProducts_WhenEmpty() {
        category.setProducts(new ArrayList<>());
        assertNotNull(category.getProducts());
        assertTrue(category.getProducts().isEmpty());
    }

    // ==================== Tests pour Setters ====================

    @Test
    void testSetId() {
        category.setId(99L);
        assertEquals(99L, category.getId());
    }

    @Test
    void testSetName() {
        category.setName("New Name");
        assertEquals("New Name", category.getName());
    }

    @Test
    void testSetDescription() {
        category.setDescription("New Description");
        assertEquals("New Description", category.getDescription());
    }

    @Test
    void testSetCreatedAt() {
        OffsetDateTime newTime = OffsetDateTime.now().plusDays(1);
        category.setCreatedAt(newTime);
        assertEquals(newTime, category.getCreatedAt());
    }

    @Test
    void testSetProducts() {
        List<Product> products = new ArrayList<>();
        products.add(Product.builder().id(1L).build());
        
        category.setProducts(products);
        assertEquals(1, category.getProducts().size());
    }

    // ==================== Tests pour Builder ====================

    @Test
    void testBuilder_CreatesWithAllFields() {
        assertNotNull(category);
        assertEquals(1L, category.getId());
        assertEquals("Electronics", category.getName());
        assertEquals("Electronic devices and accessories", category.getDescription());
        assertEquals(now, category.getCreatedAt());
    }

    @Test
    void testBuilder_CreatesWithPartialFields() {
        Category partial = Category.builder()
                .name("Partial")
                .build();
        
        assertEquals("Partial", partial.getName());
        assertNull(partial.getId());
        assertNull(partial.getDescription());
    }

    // ==================== Tests pour NoArgsConstructor ====================

    @Test
    void testNoArgsConstructor() {
        Category empty = new Category();
        assertNotNull(empty);
        assertNull(empty.getId());
        assertNull(empty.getName());
    }

    // ==================== Tests pour AllArgsConstructor ====================

    @Test
    void testAllArgsConstructor() {
        List<Product> products = new ArrayList<>();
        Category full = new Category(1L, now, "Full", "Full Description", products);
        
        assertEquals(1L, full.getId());
        assertEquals("Full", full.getName());
        assertEquals("Full Description", full.getDescription());
        assertEquals(now, full.getCreatedAt());
        assertEquals(products, full.getProducts());
    }

    // ==================== Tests pour equals et hashCode ====================

    @Test
    void testEquals_SameObject() {
        assertEquals(category, category);
    }

    @Test
    void testEquals_NullObject() {
        assertNotEquals(category, null);
    }

    @Test
    void testHashCode_NotNull() {
        assertNotNull(category.hashCode());
    }

    // ==================== Tests pour toString ====================

    @Test
    void testToString_ContainsName() {
        String str = category.toString();
        assertTrue(str.contains("Electronics"));
    }

    @Test
    void testToString_NotNull() {
        assertNotNull(category.toString());
    }
}
