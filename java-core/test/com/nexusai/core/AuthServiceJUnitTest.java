package com.nexusai.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class AuthServiceJUnitTest {


    @Test
    void validAdminCanLogIn() {
        AuthService auth = new AuthService(
                "admin", "Admin-Password-2026!",
                "viewer", "Viewer-Password-2026!"
        );

        String token = auth.login("admin", "Admin-Password-2026!");

        assertNotNull(token);

        AuthService.AuthenticatedUser user =
                auth.authenticate(token);

        assertNotNull(user);
        assertEquals(AuthService.Role.ADMIN, user.role());
        assertEquals("admin", user.username());
    }

    @Test
    void wrongPasswordIsRejected() {
        AuthService auth = new AuthService(
                "admin", "Admin-Password-2026!",
                "viewer", "Viewer-Password-2026!"
        );

        assertNull(auth.login("admin", "wrong-password"));
    }

    @Test
    void logoutRevokesToken() {
        AuthService auth = new AuthService(
                "admin", "Admin-Password-2026!",
                "viewer", "Viewer-Password-2026!"
        );

        String token = auth.login("admin", "Admin-Password-2026!");
        assertNotNull(token);

        assertTrue(auth.logout(token));
        assertNull(auth.authenticate(token));
    }

    @Test
    void invalidTokenCannotAuthenticate() {
        AuthService auth = new AuthService(
                "admin", "Admin-Password-2026!",
                "viewer", "Viewer-Password-2026!"
        );

        assertNull(auth.authenticate("not-a-valid-token"));
    }
}