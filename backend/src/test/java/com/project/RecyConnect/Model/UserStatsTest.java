package com.project.RecyConnect.Model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UserStatsTest {

    private UserStats userStats;
    private User user;
    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        now = OffsetDateTime.now();
        
        user = User.builder()
                .id(1L)
                .username("testuser")
                .build();
        
        userStats = UserStats.builder()
                .id(1L)
                .createdAt(now)
                .user(user)
                .totalProducts(100)
                .recycledCount(75)
                .availableCount(25)
                .recyclingRate("75%")
                .build();
    }

    // ==================== Tests pour getId() ====================

    @Test
    void testGetId_ReturnsCorrectId() {
        assertEquals(1L, userStats.getId());
    }

    @Test
    void testGetId_WhenNull() {
        UserStats empty = new UserStats();
        assertNull(empty.getId());
    }

    // ==================== Tests pour getCreatedAt() ====================

    @Test
    void testGetCreatedAt_ReturnsCorrectValue() {
        assertEquals(now, userStats.getCreatedAt());
    }

    @Test
    void testGetCreatedAt_WhenNull() {
        UserStats empty = new UserStats();
        assertNull(empty.getCreatedAt());
    }

    // ==================== Tests pour getUser() ====================

    @Test
    void testGetUser_ReturnsCorrectUser() {
        assertNotNull(userStats.getUser());
        assertEquals(1L, userStats.getUser().getId());
        assertEquals("testuser", userStats.getUser().getUsername());
    }

    @Test
    void testGetUser_WhenNull() {
        UserStats empty = new UserStats();
        assertNull(empty.getUser());
    }

    // ==================== Tests pour getTotalProducts() ====================

    @Test
    void testGetTotalProducts_ReturnsCorrectValue() {
        assertEquals(100, userStats.getTotalProducts());
    }

    @Test
    void testGetTotalProducts_WhenNull() {
        UserStats empty = new UserStats();
        assertNull(empty.getTotalProducts());
    }

    @Test
    void testGetTotalProducts_WithZero() {
        UserStats us = UserStats.builder().totalProducts(0).build();
        assertEquals(0, us.getTotalProducts());
    }

    // ==================== Tests pour getRecycledCount() ====================

    @Test
    void testGetRecycledCount_ReturnsCorrectValue() {
        assertEquals(75, userStats.getRecycledCount());
    }

    @Test
    void testGetRecycledCount_WhenNull() {
        UserStats empty = new UserStats();
        assertNull(empty.getRecycledCount());
    }

    @Test
    void testGetRecycledCount_WithZero() {
        UserStats us = UserStats.builder().recycledCount(0).build();
        assertEquals(0, us.getRecycledCount());
    }

    // ==================== Tests pour getAvailableCount() ====================

    @Test
    void testGetAvailableCount_ReturnsCorrectValue() {
        assertEquals(25, userStats.getAvailableCount());
    }

    @Test
    void testGetAvailableCount_WhenNull() {
        UserStats empty = new UserStats();
        assertNull(empty.getAvailableCount());
    }

    @Test
    void testGetAvailableCount_WithZero() {
        UserStats us = UserStats.builder().availableCount(0).build();
        assertEquals(0, us.getAvailableCount());
    }

    // ==================== Tests pour getRecyclingRate() ====================

    @Test
    void testGetRecyclingRate_ReturnsCorrectValue() {
        assertEquals("75%", userStats.getRecyclingRate());
    }

    @Test
    void testGetRecyclingRate_WhenNull() {
        UserStats empty = new UserStats();
        assertNull(empty.getRecyclingRate());
    }

    @Test
    void testGetRecyclingRate_WithEmptyString() {
        UserStats us = UserStats.builder().recyclingRate("").build();
        assertEquals("", us.getRecyclingRate());
    }

    @Test
    void testGetRecyclingRate_WithZeroPercent() {
        UserStats us = UserStats.builder().recyclingRate("0%").build();
        assertEquals("0%", us.getRecyclingRate());
    }

    @Test
    void testGetRecyclingRate_With100Percent() {
        UserStats us = UserStats.builder().recyclingRate("100%").build();
        assertEquals("100%", us.getRecyclingRate());
    }

    // ==================== Tests pour Setters ====================

    @Test
    void testSetId() {
        userStats.setId(99L);
        assertEquals(99L, userStats.getId());
    }

    @Test
    void testSetCreatedAt() {
        OffsetDateTime newTime = OffsetDateTime.now().plusDays(1);
        userStats.setCreatedAt(newTime);
        assertEquals(newTime, userStats.getCreatedAt());
    }

    @Test
    void testSetUser() {
        User newUser = User.builder().id(2L).username("newuser").build();
        userStats.setUser(newUser);
        assertEquals(2L, userStats.getUser().getId());
    }

    @Test
    void testSetTotalProducts() {
        userStats.setTotalProducts(200);
        assertEquals(200, userStats.getTotalProducts());
    }

    @Test
    void testSetRecycledCount() {
        userStats.setRecycledCount(150);
        assertEquals(150, userStats.getRecycledCount());
    }

    @Test
    void testSetAvailableCount() {
        userStats.setAvailableCount(50);
        assertEquals(50, userStats.getAvailableCount());
    }

    @Test
    void testSetRecyclingRate() {
        userStats.setRecyclingRate("50%");
        assertEquals("50%", userStats.getRecyclingRate());
    }

    // ==================== Tests pour Builder ====================

    @Test
    void testBuilder_CreatesWithAllFields() {
        assertNotNull(userStats);
        assertEquals(1L, userStats.getId());
        assertEquals(100, userStats.getTotalProducts());
        assertEquals(75, userStats.getRecycledCount());
        assertEquals(25, userStats.getAvailableCount());
        assertEquals("75%", userStats.getRecyclingRate());
    }

    @Test
    void testBuilder_CreatesWithPartialFields() {
        UserStats partial = UserStats.builder()
                .totalProducts(10)
                .build();
        
        assertEquals(10, partial.getTotalProducts());
        assertNull(partial.getId());
        assertNull(partial.getRecyclingRate());
    }

    // ==================== Tests pour NoArgsConstructor ====================

    @Test
    void testNoArgsConstructor() {
        UserStats empty = new UserStats();
        assertNotNull(empty);
        assertNull(empty.getId());
        assertNull(empty.getTotalProducts());
    }

    // ==================== Tests pour AllArgsConstructor ====================

    @Test
    void testAllArgsConstructor() {
        UserStats full = new UserStats(1L, now, user, 100, 75, 25, "75%");
        
        assertEquals(1L, full.getId());
        assertEquals(now, full.getCreatedAt());
        assertEquals(user, full.getUser());
        assertEquals(100, full.getTotalProducts());
        assertEquals(75, full.getRecycledCount());
        assertEquals(25, full.getAvailableCount());
        assertEquals("75%", full.getRecyclingRate());
    }

    // ==================== Tests de cohérence des statistiques ====================

    @Test
    void testStatsConsistency_TotalEqualsRecycledPlusAvailable() {
        int total = userStats.getTotalProducts();
        int recycled = userStats.getRecycledCount();
        int available = userStats.getAvailableCount();
        
        assertEquals(total, recycled + available);
    }

    // ==================== Tests pour equals et hashCode ====================

    @Test
    void testEquals_SameObject() {
        assertEquals(userStats, userStats);
    }

    @Test
    void testEquals_NullObject() {
        assertNotEquals(userStats, null);
    }

    @Test
    void testHashCode_NotNull() {
        assertNotNull(userStats.hashCode());
    }

    // ==================== Tests pour toString ====================

    @Test
    void testToString_ContainsRecyclingRate() {
        String str = userStats.toString();
        assertTrue(str.contains("75%"));
    }

    @Test
    void testToString_NotNull() {
        assertNotNull(userStats.toString());
    }
}
