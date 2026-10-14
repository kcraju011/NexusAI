
package com.nexusai.core;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class AuthService {
    public enum Role {
        ADMIN, VIEWER
    }

    public record AuthenticatedUser(String username, Role role) {}

    private record UserCredential(
            byte[] salt,
            byte[] passwordHash,
            Role role
    ) {}

    private record Session(
            String username,
            Role role,
            Instant expiresAt
    ) {}

    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final long SESSION_MINUTES = 30;

    private final Map<String, UserCredential> users =
            new ConcurrentHashMap<>();

    private final Map<String, Session> sessions =
            new ConcurrentHashMap<>();

    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            String adminUsername,
            String adminPassword,
            String viewerUsername,
            String viewerPassword
    ) {
        addUser(adminUsername, adminPassword, Role.ADMIN);
        addUser(viewerUsername, viewerPassword, Role.VIEWER);
    }

    private void addUser(String username, String password, Role role) {
        if (username == null || username.isBlank()
                || password == null || password.length() < 12) {
            throw new IllegalArgumentException(
                    "Usernames are required and passwords must be at least 12 characters."
            );
        }

        if (users.containsKey(username)) {
            throw new IllegalArgumentException("Duplicate username.");
        }

        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);

        byte[] hash = derivePasswordHash(password.toCharArray(), salt);

        users.put(username, new UserCredential(salt, hash, role));
    }

    private byte[] derivePasswordHash(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(
                password, salt, ITERATIONS, KEY_LENGTH_BITS
        );

        try {
            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Password hashing is unavailable.", exception
            );
        } finally {
            spec.clearPassword();
            Arrays.fill(password, '\0');
        }
    }

    public String login(String username, String password) {
        if (username == null || password == null) {
            return null;
        }

        UserCredential credential = users.get(username);

        // Perform a dummy derivation for unknown usernames too.
        // This reduces, but does not eliminate, timing differences.
        byte[] salt;
        byte[] expectedHash;
        Role role;

        if (credential == null) {
            salt = new byte[16];
            expectedHash = new byte[32];
            role = null;
        } else {
            salt = credential.salt();
            expectedHash = credential.passwordHash();
            role = credential.role();
        }

        byte[] actualHash = derivePasswordHash(
                password.toCharArray(), salt.clone()
        );

        boolean matches = MessageDigest.isEqual(
                expectedHash, actualHash
        );

        Arrays.fill(actualHash, (byte) 0);

        if (credential == null || !matches) {
            return null;
        }

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        String token = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        Instant expiresAt = Instant.now()
                .plusSeconds(SESSION_MINUTES * 60);

        sessions.put(
                token,
                new Session(username, role, expiresAt)
        );

        Arrays.fill(randomBytes, (byte) 0);
        return token;
    }

    public AuthenticatedUser authenticate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        Session session = sessions.get(token);

        if (session == null) {
            return null;
        }

        if (!session.expiresAt().isAfter(Instant.now())) {
            sessions.remove(token, session);
            return null;
        }

        return new AuthenticatedUser(
                session.username(), session.role()
        );
    }

    public boolean logout(String token) {
        if (token == null) {
            return false;
        }

        return sessions.remove(token) != null;
    }

    public int activeSessionCount() {
        removeExpiredSessions();
        return sessions.size();
    }

    private void removeExpiredSessions() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(
                entry -> !entry.getValue().expiresAt().isAfter(now)
        );
    }
}
