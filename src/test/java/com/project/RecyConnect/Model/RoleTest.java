package com.project.RecyConnect.Model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoleTest {

    // ==================== Tests pour les valeurs de l'enum ====================

    @Test
    void testRole_HasUserValue() {
        Role role = Role.USER;
        assertNotNull(role);
        assertEquals("USER", role.name());
    }

    @Test
    void testRole_HasAdminValue() {
        Role role = Role.ADMIN;
        assertNotNull(role);
        assertEquals("ADMIN", role.name());
    }

    // ==================== Tests pour values() ====================

    @Test
    void testRole_ValuesLength() {
        Role[] roles = Role.values();
        assertEquals(2, roles.length);
    }

    @Test
    void testRole_ValuesContainsUser() {
        Role[] roles = Role.values();
        boolean containsUser = false;
        for (Role role : roles) {
            if (role == Role.USER) {
                containsUser = true;
                break;
            }
        }
        assertTrue(containsUser);
    }

    @Test
    void testRole_ValuesContainsAdmin() {
        Role[] roles = Role.values();
        boolean containsAdmin = false;
        for (Role role : roles) {
            if (role == Role.ADMIN) {
                containsAdmin = true;
                break;
            }
        }
        assertTrue(containsAdmin);
    }

    // ==================== Tests pour valueOf() ====================

    @Test
    void testRole_ValueOfUser() {
        Role role = Role.valueOf("USER");
        assertEquals(Role.USER, role);
    }

    @Test
    void testRole_ValueOfAdmin() {
        Role role = Role.valueOf("ADMIN");
        assertEquals(Role.ADMIN, role);
    }

    @Test
    void testRole_ValueOfInvalid() {
        assertThrows(IllegalArgumentException.class, () -> {
            Role.valueOf("INVALID");
        });
    }

    @Test
    void testRole_ValueOfNull() {
        assertThrows(NullPointerException.class, () -> {
            Role.valueOf(null);
        });
    }

    // ==================== Tests pour ordinal() ====================

    @Test
    void testRole_UserOrdinal() {
        assertEquals(0, Role.USER.ordinal());
    }

    @Test
    void testRole_AdminOrdinal() {
        assertEquals(1, Role.ADMIN.ordinal());
    }

    // ==================== Tests pour name() ====================

    @Test
    void testRole_UserName() {
        assertEquals("USER", Role.USER.name());
    }

    @Test
    void testRole_AdminName() {
        assertEquals("ADMIN", Role.ADMIN.name());
    }

    // ==================== Tests pour toString() ====================

    @Test
    void testRole_UserToString() {
        assertEquals("USER", Role.USER.toString());
    }

    @Test
    void testRole_AdminToString() {
        assertEquals("ADMIN", Role.ADMIN.toString());
    }

    // ==================== Tests pour equals ====================

    @Test
    void testRole_UserEqualsUser() {
        assertEquals(Role.USER, Role.USER);
    }

    @Test
    void testRole_AdminEqualsAdmin() {
        assertEquals(Role.ADMIN, Role.ADMIN);
    }

    @Test
    void testRole_UserNotEqualsAdmin() {
        assertNotEquals(Role.USER, Role.ADMIN);
    }

    // ==================== Tests pour compareTo ====================

    @Test
    void testRole_UserBeforeAdmin() {
        assertTrue(Role.USER.compareTo(Role.ADMIN) < 0);
    }

    @Test
    void testRole_AdminAfterUser() {
        assertTrue(Role.ADMIN.compareTo(Role.USER) > 0);
    }

    @Test
    void testRole_UserCompareToSelf() {
        assertEquals(0, Role.USER.compareTo(Role.USER));
    }
}
