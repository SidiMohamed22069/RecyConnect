package com.project.RecyConnect.Model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class NotificationTest {

    private Notification notification;
    private User sender;
    private User receiver;
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
        
        notification = Notification.builder()
                .id(1L)
                .createdAt(now)
                .sender(sender)
                .receiver(receiver)
                .title("Test Notification")
                .message("This is a test notification message")
                .build();
    }

    // ==================== Tests pour getId() ====================

    @Test
    void testGetId_ReturnsCorrectId() {
        assertEquals(1L, notification.getId());
    }

    @Test
    void testGetId_WhenNull() {
        Notification empty = new Notification();
        assertNull(empty.getId());
    }

    // ==================== Tests pour getCreatedAt() ====================

    @Test
    void testGetCreatedAt_ReturnsCorrectValue() {
        assertEquals(now, notification.getCreatedAt());
    }

    @Test
    void testGetCreatedAt_WhenNull() {
        Notification empty = new Notification();
        assertNull(empty.getCreatedAt());
    }

    // ==================== Tests pour getSender() ====================

    @Test
    void testGetSender_ReturnsCorrectSender() {
        assertNotNull(notification.getSender());
        assertEquals(1L, notification.getSender().getId());
        assertEquals("sender", notification.getSender().getUsername());
    }

    @Test
    void testGetSender_WhenNull() {
        Notification empty = new Notification();
        assertNull(empty.getSender());
    }

    // ==================== Tests pour getReceiver() ====================

    @Test
    void testGetReceiver_ReturnsCorrectReceiver() {
        assertNotNull(notification.getReceiver());
        assertEquals(2L, notification.getReceiver().getId());
        assertEquals("receiver", notification.getReceiver().getUsername());
    }

    @Test
    void testGetReceiver_WhenNull() {
        Notification empty = new Notification();
        assertNull(empty.getReceiver());
    }

    // ==================== Tests pour getTitle() ====================

    @Test
    void testGetTitle_ReturnsCorrectTitle() {
        assertEquals("Test Notification", notification.getTitle());
    }

    @Test
    void testGetTitle_WhenNull() {
        Notification empty = new Notification();
        assertNull(empty.getTitle());
    }

    @Test
    void testGetTitle_WithEmptyString() {
        Notification n = Notification.builder().title("").build();
        assertEquals("", n.getTitle());
    }

    // ==================== Tests pour getMessage() ====================

    @Test
    void testGetMessage_ReturnsCorrectMessage() {
        assertEquals("This is a test notification message", notification.getMessage());
    }

    @Test
    void testGetMessage_WhenNull() {
        Notification empty = new Notification();
        assertNull(empty.getMessage());
    }

    @Test
    void testGetMessage_WithEmptyString() {
        Notification n = Notification.builder().message("").build();
        assertEquals("", n.getMessage());
    }

    @Test
    void testGetMessage_WithLongText() {
        String longMessage = "A".repeat(1000);
        Notification n = Notification.builder().message(longMessage).build();
        assertEquals(1000, n.getMessage().length());
    }

    // ==================== Tests pour Setters ====================

    @Test
    void testSetId() {
        notification.setId(99L);
        assertEquals(99L, notification.getId());
    }

    @Test
    void testSetCreatedAt() {
        OffsetDateTime newTime = OffsetDateTime.now().plusDays(1);
        notification.setCreatedAt(newTime);
        assertEquals(newTime, notification.getCreatedAt());
    }

    @Test
    void testSetSender() {
        User newSender = User.builder().id(3L).username("newSender").build();
        notification.setSender(newSender);
        assertEquals(3L, notification.getSender().getId());
    }

    @Test
    void testSetReceiver() {
        User newReceiver = User.builder().id(4L).username("newReceiver").build();
        notification.setReceiver(newReceiver);
        assertEquals(4L, notification.getReceiver().getId());
    }

    @Test
    void testSetTitle() {
        notification.setTitle("New Title");
        assertEquals("New Title", notification.getTitle());
    }

    @Test
    void testSetMessage() {
        notification.setMessage("New Message");
        assertEquals("New Message", notification.getMessage());
    }

    // ==================== Tests pour Builder ====================

    @Test
    void testBuilder_CreatesWithAllFields() {
        assertNotNull(notification);
        assertEquals(1L, notification.getId());
        assertEquals("Test Notification", notification.getTitle());
        assertEquals("This is a test notification message", notification.getMessage());
    }

    @Test
    void testBuilder_CreatesWithPartialFields() {
        Notification partial = Notification.builder()
                .title("Partial")
                .build();
        
        assertEquals("Partial", partial.getTitle());
        assertNull(partial.getId());
        assertNull(partial.getMessage());
    }

    // ==================== Tests pour NoArgsConstructor ====================

    @Test
    void testNoArgsConstructor() {
        Notification empty = new Notification();
        assertNotNull(empty);
        assertNull(empty.getId());
        assertNull(empty.getTitle());
    }

    // ==================== Tests pour AllArgsConstructor ====================

    @Test
    void testAllArgsConstructor() {
        Notification full = new Notification(1L, now, sender, receiver, "Full Message", "Full Title");
        
        assertEquals(1L, full.getId());
        assertEquals(now, full.getCreatedAt());
        assertEquals(sender, full.getSender());
        assertEquals(receiver, full.getReceiver());
        assertEquals("Full Message", full.getMessage());
        assertEquals("Full Title", full.getTitle());
    }

    // ==================== Tests pour equals et hashCode ====================

    @Test
    void testEquals_SameObject() {
        assertEquals(notification, notification);
    }

    @Test
    void testEquals_NullObject() {
        assertNotEquals(notification, null);
    }

    @Test
    void testHashCode_NotNull() {
        assertNotNull(notification.hashCode());
    }

    // ==================== Tests pour toString ====================

    @Test
    void testToString_ContainsTitle() {
        String str = notification.toString();
        assertTrue(str.contains("Test Notification"));
    }

    @Test
    void testToString_NotNull() {
        assertNotNull(notification.toString());
    }
}
