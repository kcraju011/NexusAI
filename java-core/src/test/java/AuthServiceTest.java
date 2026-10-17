
package com.nexusai.core;

public class AuthServiceTest {
    public static void main(String[] args) {
        AuthService auth = new AuthService(
                "admin", "Admin-Password-2026!",
                "viewer", "Viewer-Password-2026!"
        );

        String adminToken =
                auth.login("admin", "Admin-Password-2026!");

        assert adminToken != null : "Admin login should succeed";

        AuthService.AuthenticatedUser admin =
                auth.authenticate(adminToken);

        assert admin != null;
        assert admin.role() == AuthService.Role.ADMIN;

        String viewerToken =
                auth.login("viewer", "Viewer-Password-2026!");

        assert viewerToken != null : "Viewer login should succeed";

        AuthService.AuthenticatedUser viewer =
                auth.authenticate(viewerToken);

        assert viewer != null;
        assert viewer.role() == AuthService.Role.VIEWER;

        assert auth.login("admin", "wrong-password") == null
                : "Wrong password must fail";

        assert auth.login("unknown", "Unknown-Password-2026!") == null
                : "Unknown user must fail";

        assert auth.authenticate("invalid-token") == null
                : "Invalid token must fail";

        assert auth.logout(adminToken)
                : "Logout should revoke the token";

        assert auth.authenticate(adminToken) == null
                : "Logged-out token must no longer authenticate";

        assert auth.authenticate(viewerToken) != null
                : "Logging out admin must not log out viewer";

        System.out.println("All AuthService tests passed.");
    }
}
