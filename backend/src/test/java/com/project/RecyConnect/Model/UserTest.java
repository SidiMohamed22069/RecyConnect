package com.project.RecyConnect.Model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .username("testuser")
                .pwd("password123")
                .phone(212612345678L)
                .role(Role.USER)
                .build();
    }

    // ==================== Tests pour getUsername() ====================

    @Test
    void testGetUsername_ReturnsCorrectUsername() {
        assertEquals("testuser", user.getUsername());
    }

    @Test
    void testGetUsername_WhenNull() {
        User emptyUser = new User();
        assertNull(emptyUser.getUsername());
    }

    @Test
    void testGetUsername_WithEmptyString() {
        User userWithEmptyName = User.builder().username("").build();
        assertEquals("", userWithEmptyName.getUsername());
    }

    // ==================== Tests pour getPassword() ====================

    @Test
    void testGetPassword_ReturnsCorrectPassword() {
        assertEquals("password123", user.getPassword());
    }

    @Test
    void testGetPassword_ReturnsPwdField() {
        // Vérifie que getPassword() retourne la valeur du champ pwd
        User user2 = User.builder().pwd("mySecret").build();
        assertEquals("mySecret", user2.getPassword());
    }

    @Test
    void testGetPassword_WhenNull() {
        User emptyUser = new User();
        assertNull(emptyUser.getPassword());
    }

    // ==================== Tests pour getAuthorities() ====================

    @Test
    void testGetAuthorities_ReturnsUserRole() {
        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();
        
        assertNotNull(authorities);
        assertEquals(1, authorities.size());
        assertTrue(authorities.contains(new SimpleGrantedAuthority("USER")));
    }

    @Test
    void testGetAuthorities_WithAdminRole() {
        User admin = User.builder()
                .username("admin")
                .role(Role.ADMIN)
                .build();
        
        Collection<? extends GrantedAuthority> authorities = admin.getAuthorities();
        
        assertNotNull(authorities);
        assertEquals(1, authorities.size());
        assertTrue(authorities.contains(new SimpleGrantedAuthority("ADMIN")));
    }

    @Test
    void testGetAuthorities_ReturnsListOfOneAuthority() {
        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();
        assertEquals(1, authorities.size());
    }

    // ==================== Tests pour isAccountNonExpired() ====================

    @Test
    void testIsAccountNonExpired_ReturnsTrue() {
        assertTrue(user.isAccountNonExpired());
    }

    @Test
    void testIsAccountNonExpired_NewUser() {
        User newUser = new User();
        assertTrue(newUser.isAccountNonExpired());
    }

    // ==================== Tests pour isAccountNonLocked() ====================

    @Test
    void testIsAccountNonLocked_ReturnsTrue() {
        assertTrue(user.isAccountNonLocked());
    }

    @Test
    void testIsAccountNonLocked_NewUser() {
        User newUser = new User();
        assertTrue(newUser.isAccountNonLocked());
    }

    // ==================== Tests pour isCredentialsNonExpired() ====================

    @Test
    void testIsCredentialsNonExpired_ReturnsTrue() {
        assertTrue(user.isCredentialsNonExpired());
    }

    @Test
    void testIsCredentialsNonExpired_NewUser() {
        User newUser = new User();
        assertTrue(newUser.isCredentialsNonExpired());
    }

    // ==================== Tests pour isEnabled() ====================

    @Test
    void testIsEnabled_ReturnsTrue() {
        assertTrue(user.isEnabled());
    }

    @Test
    void testIsEnabled_NewUser() {
        User newUser = new User();
        assertTrue(newUser.isEnabled());
    }

    // ==================== Tests pour getId() ====================

    @Test
    void testGetId_ReturnsCorrectId() {
        assertEquals(1L, user.getId());
    }

    @Test
    void testGetId_WhenNull() {
        User newUser = new User();
        assertNull(newUser.getId());
    }

    // ==================== Tests pour getPhone() ====================

    @Test
    void testGetPhone_ReturnsCorrectPhone() {
        assertEquals(212612345678L, user.getPhone());
    }

    @Test
    void testGetPhone_WhenNull() {
        User emptyUser = new User();
        assertNull(emptyUser.getPhone());
    }

    // ==================== Tests pour getRole() ====================

    @Test
    void testGetRole_ReturnsUserRole() {
        assertEquals(Role.USER, user.getRole());
    }

    @Test
    void testGetRole_ReturnsAdminRole() {
        User admin = User.builder().role(Role.ADMIN).build();
        assertEquals(Role.ADMIN, admin.getRole());
    }

    @Test
    void testGetRole_WhenNull() {
        User emptyUser = new User();
        assertNull(emptyUser.getRole());
    }

    // ==================== Tests pour setters ====================

    @Test
    void testSetUsername() {
        user.setUsername("newusername");
        assertEquals("newusername", user.getUsername());
    }

    @Test
    void testSetPwd() {
        user.setPwd("newpassword");
        assertEquals("newpassword", user.getPassword());
    }

    @Test
    void testSetPhone() {
        user.setPhone(212698765432L);
        assertEquals(212698765432L, user.getPhone());
    }

    @Test
    void testSetRole() {
        user.setRole(Role.ADMIN);
        assertEquals(Role.ADMIN, user.getRole());
    }

    @Test
    void testSetId() {
        user.setId(99L);
        assertEquals(99L, user.getId());
    }

    // ==================== Tests pour getImageData() ====================

    @Test
    void testGetImageData_WhenNull() {
        assertNull(user.getImageData());
    }

    @Test
    void testGetImageData_WhenSet() {
        user.setImageData("base64imagedata");
        assertEquals("base64imagedata", user.getImageData());
    }

    @Test
    void testDefaultImageData_Constant() {
        assertNotNull(User.DEFAULT_IMAGE_DATA);
        assertTrue(User.DEFAULT_IMAGE_DATA.startsWith("data:image/png;base64,"));
    }

    // ==================== Tests pour Builder ====================

    @Test
    void testBuilder_CreatesUserWithAllFields() {
        User builtUser = User.builder()
                .id(10L)
                .username("builderuser")
                .pwd("builderpwd")
                .phone(212600000000L)
                .role(Role.ADMIN)
                .imageData("imagedata")
                .build();
        
        assertEquals(10L, builtUser.getId());
        assertEquals("builderuser", builtUser.getUsername());
        assertEquals("builderpwd", builtUser.getPassword());
        assertEquals(212600000000L, builtUser.getPhone());
        assertEquals(Role.ADMIN, builtUser.getRole());
        assertEquals("imagedata", builtUser.getImageData());
    }

    @Test
    void testBuilder_CreatesUserWithPartialFields() {
        User partialUser = User.builder()
                .username("partial")
                .build();
        
        assertEquals("partial", partialUser.getUsername());
        assertNull(partialUser.getId());
        assertNull(partialUser.getPhone());
        assertNull(partialUser.getRole());
    }

    // ==================== Tests pour NoArgsConstructor ====================

    @Test
    void testNoArgsConstructor() {
        User emptyUser = new User();
        assertNotNull(emptyUser);
        assertNull(emptyUser.getId());
        assertNull(emptyUser.getUsername());
        assertNull(emptyUser.getPassword());
    }

    // ==================== Tests pour AllArgsConstructor ====================

    @Test
    void testAllArgsConstructor() {
        User fullUser = new User(
                1L,                    // id
                "fulluser",            // username
                212612345678L,         // phone
                "imagedata",           // imageData
                "password",            // pwd
                Role.USER,             // role
                null,                  // products
                null,                  // negotiationsSent
                null                   // negotiationsReceived
        );
        
        assertEquals(1L, fullUser.getId());
        assertEquals("fulluser", fullUser.getUsername());
        assertEquals(212612345678L, fullUser.getPhone());
        assertEquals("imagedata", fullUser.getImageData());
        assertEquals("password", fullUser.getPassword());
        assertEquals(Role.USER, fullUser.getRole());
    }

    // ==================== Tests pour equals et hashCode (Lombok @Data) ====================

    @Test
    void testEquals_SameObject() {
        assertEquals(user, user);
    }

    @Test
    void testEquals_NullObject() {
        assertNotEquals(user, null);
    }

    @Test
    void testHashCode_ConsistentWithEquals() {
        User user2 = User.builder()
                .id(1L)
                .username("testuser")
                .pwd("password123")
                .phone(212612345678L)
                .role(Role.USER)
                .build();
        
        // Si les objets sont égaux, leurs hashCodes doivent être égaux
        if (user.equals(user2)) {
            assertEquals(user.hashCode(), user2.hashCode());
        }
    }

    // ==================== Tests pour toString (Lombok @Data) ====================

    @Test
    void testToString_ContainsUsername() {
        String str = user.toString();
        assertTrue(str.contains("testuser"));
    }

    @Test
    void testToString_NotNull() {
        assertNotNull(user.toString());
    }
}
