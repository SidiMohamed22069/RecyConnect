package com.project.RecyConnect.Model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class NegotiationTest {

    private Negotiation negotiation;
    private User sender;
    private User receiver;
    private Product product;
    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        now = OffsetDateTime.now();
        
        sender = User.builder()
                .id(1L)
                .username("sender")
                .build();
        
        receiver = User.builder()
                .id(2L)
                .username("receiver")
                .build();
        
        product = Product.builder()
                .id(1L)
                .title("Test Product")
                .price(100.0)
                .build();
        
        negotiation = Negotiation.builder()
                .id(1L)
                .createdAt(now)
                .sender(sender)
                .receiver(receiver)
                .product(product)
                .status("pending")
                .price(80.0)
                .quantity(5)
                .build();
    }

    // ==================== Tests pour getId() ====================

    @Test
    void testGetId_ReturnsCorrectId() {
        assertEquals(1L, negotiation.getId());
    }

    @Test
    void testGetId_WhenNull() {
        Negotiation empty = new Negotiation();
        assertNull(empty.getId());
    }

    // ==================== Tests pour getCreatedAt() ====================

    @Test
    void testGetCreatedAt_ReturnsCorrectValue() {
        assertEquals(now, negotiation.getCreatedAt());
    }

    @Test
    void testGetCreatedAt_WhenNull() {
        Negotiation empty = new Negotiation();
        assertNull(empty.getCreatedAt());
    }

    // ==================== Tests pour getSender() ====================

    @Test
    void testGetSender_ReturnsCorrectSender() {
        assertNotNull(negotiation.getSender());
        assertEquals(1L, negotiation.getSender().getId());
        assertEquals("sender", negotiation.getSender().getUsername());
    }

    @Test
    void testGetSender_WhenNull() {
        Negotiation empty = new Negotiation();
        assertNull(empty.getSender());
    }

    // ==================== Tests pour getReceiver() ====================

    @Test
    void testGetReceiver_ReturnsCorrectReceiver() {
        assertNotNull(negotiation.getReceiver());
        assertEquals(2L, negotiation.getReceiver().getId());
        assertEquals("receiver", negotiation.getReceiver().getUsername());
    }

    @Test
    void testGetReceiver_WhenNull() {
        Negotiation empty = new Negotiation();
        assertNull(empty.getReceiver());
    }

    // ==================== Tests pour getProduct() ====================

    @Test
    void testGetProduct_ReturnsCorrectProduct() {
        assertNotNull(negotiation.getProduct());
        assertEquals(1L, negotiation.getProduct().getId());
        assertEquals("Test Product", negotiation.getProduct().getTitle());
    }

    @Test
    void testGetProduct_WhenNull() {
        Negotiation empty = new Negotiation();
        assertNull(empty.getProduct());
    }

    // ==================== Tests pour getStatus() ====================

    @Test
    void testGetStatus_ReturnsCorrectStatus() {
        assertEquals("pending", negotiation.getStatus());
    }

    @Test
    void testGetStatus_WhenNull() {
        Negotiation empty = new Negotiation();
        assertNull(empty.getStatus());
    }

    // ==================== Tests pour getPrice() ====================

    @Test
    void testGetPrice_ReturnsCorrectPrice() {
        assertEquals(80.0, negotiation.getPrice());
    }

    @Test
    void testGetPrice_WhenNull() {
        Negotiation empty = new Negotiation();
        assertNull(empty.getPrice());
    }

    @Test
    void testGetPrice_WithZero() {
        Negotiation n = Negotiation.builder().price(0.0).build();
        assertEquals(0.0, n.getPrice());
    }

    // ==================== Tests pour getQuantity() ====================

    @Test
    void testGetQuantity_ReturnsCorrectQuantity() {
        assertEquals(5, negotiation.getQuantity());
    }

    @Test
    void testGetQuantity_WhenNull() {
        Negotiation empty = new Negotiation();
        assertNull(empty.getQuantity());
    }

    @Test
    void testGetQuantity_WithZero() {
        Negotiation n = Negotiation.builder().quantity(0).build();
        assertEquals(0, n.getQuantity());
    }

    // ==================== Tests pour Setters ====================

    @Test
    void testSetId() {
        negotiation.setId(99L);
        assertEquals(99L, negotiation.getId());
    }

    @Test
    void testSetCreatedAt() {
        OffsetDateTime newTime = OffsetDateTime.now().plusDays(1);
        negotiation.setCreatedAt(newTime);
        assertEquals(newTime, negotiation.getCreatedAt());
    }

    @Test
    void testSetSender() {
        User newSender = User.builder().id(3L).username("newSender").build();
        negotiation.setSender(newSender);
        assertEquals(3L, negotiation.getSender().getId());
    }

    @Test
    void testSetReceiver() {
        User newReceiver = User.builder().id(4L).username("newReceiver").build();
        negotiation.setReceiver(newReceiver);
        assertEquals(4L, negotiation.getReceiver().getId());
    }

    @Test
    void testSetProduct() {
        Product newProduct = Product.builder().id(2L).title("New Product").build();
        negotiation.setProduct(newProduct);
        assertEquals(2L, negotiation.getProduct().getId());
    }

    @Test
    void testSetStatus() {
        negotiation.setStatus("accepted");
        assertEquals("accepted", negotiation.getStatus());
    }

    @Test
    void testSetPrice() {
        negotiation.setPrice(150.0);
        assertEquals(150.0, negotiation.getPrice());
    }

    @Test
    void testSetQuantity() {
        negotiation.setQuantity(10);
        assertEquals(10, negotiation.getQuantity());
    }

    // ==================== Tests pour Builder ====================

    @Test
    void testBuilder_CreatesWithAllFields() {
        assertNotNull(negotiation);
        assertEquals(1L, negotiation.getId());
        assertEquals("pending", negotiation.getStatus());
        assertEquals(80.0, negotiation.getPrice());
        assertEquals(5, negotiation.getQuantity());
    }

    @Test
    void testBuilder_CreatesWithPartialFields() {
        Negotiation partial = Negotiation.builder()
                .status("pending")
                .price(50.0)
                .build();
        
        assertEquals("pending", partial.getStatus());
        assertEquals(50.0, partial.getPrice());
        assertNull(partial.getId());
    }

    // ==================== Tests pour NoArgsConstructor ====================

    @Test
    void testNoArgsConstructor() {
        Negotiation empty = new Negotiation();
        assertNotNull(empty);
        assertNull(empty.getId());
        assertNull(empty.getStatus());
    }

    // ==================== Tests pour AllArgsConstructor ====================

    @Test
    void testAllArgsConstructor() {
        Negotiation full = new Negotiation(1L, now, sender, receiver, product, "accepted", 100.0, 10);
        
        assertEquals(1L, full.getId());
        assertEquals(now, full.getCreatedAt());
        assertEquals(sender, full.getSender());
        assertEquals(receiver, full.getReceiver());
        assertEquals(product, full.getProduct());
        assertEquals("accepted", full.getStatus());
        assertEquals(100.0, full.getPrice());
        assertEquals(10, full.getQuantity());
    }

    // ==================== Tests pour equals et hashCode ====================

    @Test
    void testEquals_SameObject() {
        assertEquals(negotiation, negotiation);
    }

    @Test
    void testEquals_NullObject() {
        assertNotEquals(negotiation, null);
    }

    @Test
    void testHashCode_NotNull() {
        assertNotNull(negotiation.hashCode());
    }

    // ==================== Tests pour toString ====================

    @Test
    void testToString_ContainsStatus() {
        String str = negotiation.toString();
        assertTrue(str.contains("pending"));
    }

    @Test
    void testToString_NotNull() {
        assertNotNull(negotiation.toString());
    }
}
